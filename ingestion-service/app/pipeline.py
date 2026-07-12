import asyncio
import logging
from datetime import datetime
from app.telegram import fetch_channel, parse_posts
from app.prefilter import looks_like_event
from app.extractor import extract_events_batch
from app.validate import to_valid_candidate, clean_image_url, resolve_year
from app.producer import publish_candidate

log = logging.getLogger("pipeline")

def _cand_fields(cand):
    if cand is None:
        return (None, None, None, None)
    ed = cand.event_date.isoformat() if cand.event_date else None
    return (cand.title, ed, cand.city, cand.location)

def _chunks(lst, n):
    for i in range(0, len(lst), max(1, n)):
        yield lst[i:i + n]

async def run_sweep(repo, producer, settings, trigger, fetcher=fetch_channel,
                    batch_extractor=extract_events_batch):
    run_id = repo.start_run(trigger)
    counts = dict(sources_swept=0, posts_fetched=0, passed_prefilter=0, extracted=0, candidates_published=0,
                  gemini_rate_limited=0, gemini_errors=0, dropped_past=0, dropped_invalid=0)
    error = None

    def record(src_id, channel, post, stage, cand=None):
        t, ed, city, loc = _cand_fields(cand)
        repo.record_processed(run_id, src_id, channel, post.ref, f"https://t.me/{post.ref}", stage,
                              title=t, event_date=ed, city=city, location=loc, snippet=(post.text or "")[:300])

    try:
        for src in repo.list_sources(enabled_only=True):
            counts["sources_swept"] += 1
            try:
                channel = src.tme_url.rstrip("/").split("/")[-1]
                html = await fetcher(src.tme_url, settings.http_timeout_seconds)
                posts = parse_posts(html, channel)

                # Phase 1: dedup + cheap pre-filter → collect the posts worth an LLM call.
                newest_ref = None
                pending = []
                for post in posts:
                    counts["posts_fetched"] += 1
                    if repo.is_post_seen(src.id, post.ref):
                        continue
                    newest_ref = post.ref
                    if not looks_like_event(post.text):
                        record(src.id, channel, post, "PREFILTER_REJECTED")
                        repo.mark_post_seen(src.id, post.ref)
                        continue
                    counts["passed_prefilter"] += 1
                    pending.append(post)

                # Phase 2: extract in batches — one Gemini call per chunk, not per post.
                for chunk in _chunks(pending, settings.batch_size):
                    try:
                        cands = batch_extractor([p.text for p in chunk],
                                                settings.llm_base_url, settings.llm_api_key, settings.llm_model)
                    except Exception as e:  # transient (429/5xx/network): whole batch retried next sweep
                        status = getattr(getattr(e, "response", None), "status_code", None)
                        stage = "RATE_LIMITED" if status == 429 else "AI_ERROR"
                        counts["gemini_rate_limited" if status == 429 else "gemini_errors"] += len(chunk)
                        for post in chunk:
                            record(src.id, channel, post, stage)  # deferred — NOT marked seen
                        log.warning("batch extract failed (%s posts): %s — will retry next sweep", len(chunk), e)
                        await asyncio.sleep(settings.llm_delay_seconds)
                        continue
                    await asyncio.sleep(settings.llm_delay_seconds)  # throttle between batches

                    for post, cand in zip(chunk, cands):
                        if cand is None:
                            record(src.id, channel, post, "NOT_EVENT")  # AI: not an event
                            repo.mark_post_seen(src.id, post.ref)
                            continue
                        counts["extracted"] += 1
                        if cand.event_date is not None:  # fix LLM year guesses from the post date
                            cand.event_date = resolve_year(cand.event_date, post.date)
                        payload = to_valid_candidate(cand, post.text)
                        if payload is not None:
                            payload["sourceChannel"] = channel
                            payload["sourceUrl"] = f"https://t.me/{post.ref}"
                            payload["mainImageUrl"] = clean_image_url(post.image_url)
                            publish_candidate(producer, payload)
                            counts["candidates_published"] += 1
                            record(src.id, channel, post, "PUBLISHED", cand)
                        elif cand.event_date is None or cand.event_date <= datetime.now():
                            counts["dropped_past"] += 1
                            record(src.id, channel, post, "DROPPED_PAST", cand)
                        else:
                            counts["dropped_invalid"] += 1
                            record(src.id, channel, post, "DROPPED_INVALID", cand)
                        repo.mark_post_seen(src.id, post.ref)

                if newest_ref:
                    repo.update_last_seen(src.id, newest_ref)
                await asyncio.sleep(settings.fetch_delay_seconds)
            except Exception as e:  # per-source failure logged, run continues
                log.error("source %s failed: %s", src.tme_url, e)
                error = f"{src.tme_url}: {e}"
    finally:
        repo.finish_run(run_id, error=error, **counts)
    return counts
