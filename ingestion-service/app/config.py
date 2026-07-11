import os

class Settings:
    db_url = os.getenv("INGESTION_DB_URL", "postgresql://postgres:postgres@localhost:5436/ingestion_db")
    kafka_bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    gemini_api_key = os.getenv("GEMINI_API_KEY", "")
    gemini_model = os.getenv("GEMINI_MODEL", "gemini-2.5-flash-lite")
    fetch_delay_seconds = float(os.getenv("INGESTION_FETCH_DELAY", "2.0"))
    # Pause between Gemini calls to stay under the free-tier rate limit (~15 req/min → 1 per 4s).
    gemini_delay_seconds = float(os.getenv("INGESTION_GEMINI_DELAY", "4.5"))
    http_timeout_seconds = float(os.getenv("INGESTION_HTTP_TIMEOUT", "15.0"))
    schedule_cron = os.getenv("INGESTION_SCHEDULE_CRON", "0 6 * * *")  # daily 06:00

settings = Settings()
