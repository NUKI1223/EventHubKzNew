from fastapi import FastAPI

app = FastAPI(title="ingestion-service")

@app.get("/health")
def health():
    return {"status": "UP"}
