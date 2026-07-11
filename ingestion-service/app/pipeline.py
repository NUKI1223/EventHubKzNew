import asyncio
import logging
from datetime import datetime
from app.telegram import fetch_channel, parse_posts
from app.prefilter import looks_like_event
from app.extractor import extract_event
from app.validate import to_valid_candidate
from app.producer import publish_candidate

log = logging.getLogger("pipeline")

def _cand_fields(cand):
    if cand is None:
        return (None, None, None, None)
    ed = cand.event_date.isoformat() if cand.event_date else None
    return (cand.title, ed, cand.city, cand.location)

async def run_sweep(repo, producer, settings, trigger, fetcher=fetch_channel, extractor=extract_event):
    run_id = repo.start_run(trigger)
    counts = dict(sources_swept=0, posts_fetched=0, passed_prefilter=0, extracted=0, candidates_published=0,
                  gemini_rate_limited=0, gemini_errors=0, dropped_past=0, dropped_invalid=0)
    error = None
    try:
        for src in repo.list_sources(enabled_only=True):
            counts["sources_swept"] += 1
            try:
                channel = src.tme_url.rstrip("/").split("/")[-1]
                html = await fetcher(src.tme_url, settings.http_timeout_seconds)
                posts = parse_posts(html, channel)
                newest_ref = None
                for post in posts:
                    counts["posts_fetched"] += 1
                    if repo.is_post_seen(src.id, post.ref):
                        continue
                    newest_ref = post.ref
                    snippet = (post.text or "")[:300]
                    post_url = f"https://t.me/{post.ref}"

                    def record(stage, cand=None):
                        t, ed, city, loc = _cand_fields(cand)
                        repo.record_processed(run_id, src.id, channel, post.ref, post_url, stage,
                                              title=t, event_date=ed, city=city, location=loc, snippet=snippet)

                    if not looks_like_event(post.text):
                        record("PREFILTER_REJECTED")  # heuristic: no date+event keyword
                        repo.mark_post_seen(src.id, post.ref)
                        continue
                    counts["passed_prefilter"] += 1
                    transient = False
                    try:
                        cand = extractor(post.text, settings.gemini_api_key, settings.gemini_model)
                    except Exception as e:  # transient (network/quota/5xx): retry next sweep, don't burn the post
                        status = getattr(getattr(e, "response", None), "status_code", None)
                        counts["gemini_rate_limited" if status == 429 else "gemini_errors"] += 1
                        record("RATE_LIMITED" if status == 429 else "AI_ERROR")
                        log.warning("extract failed for %s: %s — will retry next sweep", post.ref, e)
                        cand, transient = None, True
                    # Throttle Gemini calls to respect the free-tier per-minute rate limit.
                    await asyncio.sleep(settings.gemini_delay_seconds)
                    if transient:
                        continue  # skip mark_post_seen so the post is reprocessed later
                    if cand is None:
                        record("NOT_EVENT")  # Gemini decided the post is not an event
                        repo.mark_post_seen(src.id, post.ref)
                        continue
                    counts["extracted"] += 1
                    payload = to_valid_candidate(cand, post.text)
                    if payload is not None:
                        payload["sourceChannel"] = channel
                        payload["sourceUrl"] = post_url
                        publish_candidate(producer, payload)
                        counts["candidates_published"] += 1
                        record("PUBLISHED", cand)
                    elif cand.event_date is None or cand.event_date <= datetime.now():
                        counts["dropped_past"] += 1     # extracted but date is past / missing
                        record("DROPPED_PAST", cand)
                    else:
                        counts["dropped_invalid"] += 1  # extracted but failed other constraints
                        record("DROPPED_INVALID", cand)
                    repo.mark_post_seen(src.id, post.ref)
                    await asyncio.sleep(0)  # cooperative
                if newest_ref:
                    repo.update_last_seen(src.id, newest_ref)
                await asyncio.sleep(settings.fetch_delay_seconds)
            except Exception as e:  # per-source failure logged, run continues
                log.error("source %s failed: %s", src.tme_url, e)
                error = f"{src.tme_url}: {e}"
    finally:
        repo.finish_run(run_id, error=error, **counts)
    return counts
