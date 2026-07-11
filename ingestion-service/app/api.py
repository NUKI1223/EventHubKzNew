import asyncio
import logging

from fastapi import APIRouter, Depends, HTTPException, Header
from pydantic import BaseModel
from app.sources import normalize_tme_url

log = logging.getLogger("api")

def require_admin(x_user_role: str = Header(default="")):
    if x_user_role.upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="admin only")

router = APIRouter(prefix="/api/ingestion", dependencies=[Depends(require_admin)])

# Guards against a second manual sweep stacking on top of a running one (double-click).
_sweep_running = False

class SourceIn(BaseModel):
    name: str
    tmeUrl: str

class EnabledIn(BaseModel):
    enabled: bool

def build_router(get_repo, run_sweep_now):
    @router.get("/sources")
    def list_sources(repo=Depends(get_repo)):
        return [vars(s) for s in repo.list_sources()]

    @router.post("/sources", status_code=201)
    def add_source(body: SourceIn, repo=Depends(get_repo)):
        url = normalize_tme_url(body.tmeUrl)
        if url is None:
            raise HTTPException(status_code=400, detail="Некорректная ссылка на Telegram-канал (пример: https://t.me/s/durov)")
        return vars(repo.add_source(body.name, url))

    @router.patch("/sources/{sid}")
    def set_enabled(sid: int, body: EnabledIn, repo=Depends(get_repo)):
        repo.set_source_enabled(sid, body.enabled)
        return {"ok": True}

    @router.post("/run", status_code=202)
    async def run_now():
        # A throttled sweep (Gemini rate-limit pauses) can run for minutes — far longer
        # than the gateway's response timeout. Kick it off in the background and return
        # immediately; the admin polls GET /status for the result.
        global _sweep_running
        if _sweep_running:
            return {"status": "already_running"}

        async def _bg():
            global _sweep_running
            _sweep_running = True
            try:
                await run_sweep_now("MANUAL")
            except Exception:  # never let a sweep crash take down the loop silently
                log.exception("background sweep failed")
            finally:
                _sweep_running = False

        asyncio.create_task(_bg())
        return {"status": "started"}

    @router.get("/status")
    def status(repo=Depends(get_repo)):
        r = repo.latest_run()
        return vars(r) if r else {"trigger": None}

    return router
