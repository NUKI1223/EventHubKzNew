from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/api/ingestion")

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
        return vars(repo.add_source(body.name, body.tmeUrl))

    @router.patch("/sources/{sid}")
    def set_enabled(sid: int, body: EnabledIn, repo=Depends(get_repo)):
        repo.set_source_enabled(sid, body.enabled)
        return {"ok": True}

    @router.post("/run")
    async def run_now():
        counts = await run_sweep_now("MANUAL")
        return counts

    @router.get("/status")
    def status(repo=Depends(get_repo)):
        r = repo.latest_run()
        return vars(r) if r else {"trigger": None}

    return router
