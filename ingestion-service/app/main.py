from contextlib import asynccontextmanager
from fastapi import FastAPI
from prometheus_client import make_asgi_app
from app.config import settings
from app.db import connect, create_schema
from app.repository import Repository
from app.api import build_router
from app.pipeline import run_sweep
from app.producer import make_producer
from app.scheduler import start_scheduler

_conn = None
_producer = None

def get_repo() -> Repository:
    return Repository(_conn)

async def run_sweep_now(trigger: str):
    return await run_sweep(get_repo(), _producer, settings, trigger)

@asynccontextmanager
async def lifespan(app: FastAPI):
    global _conn, _producer
    _conn = connect(settings.db_url)
    create_schema(_conn)
    _producer = make_producer(settings.kafka_bootstrap)
    scheduler = start_scheduler(settings.schedule_cron, run_sweep_now)
    yield
    scheduler.shutdown(wait=False)
    _producer.close()
    _conn.close()

app = FastAPI(title="ingestion-service", lifespan=lifespan)
app.include_router(build_router(get_repo, run_sweep_now))
app.mount("/metrics", make_asgi_app())

@app.get("/health")
def health():
    return {"status": "UP"}
