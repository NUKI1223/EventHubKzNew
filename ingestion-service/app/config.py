import os

class Settings:
    db_url = os.getenv("INGESTION_DB_URL", "postgresql://postgres:postgres@localhost:5436/ingestion_db")
    kafka_bootstrap = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    # LLM provider — any OpenAI-compatible chat/completions endpoint (Groq / Cerebras /
    # OpenRouter / Mistral / OpenAI). Default: Groq (14.4k req/day free, no card).
    llm_base_url = os.getenv("LLM_BASE_URL", "https://api.groq.com/openai/v1")
    llm_api_key = os.getenv("LLM_API_KEY", "")
    llm_model = os.getenv("LLM_MODEL", "llama-3.3-70b-versatile")  # qwen3-32b = better RU/KK
    fetch_delay_seconds = float(os.getenv("INGESTION_FETCH_DELAY", "2.0"))
    # Pause between LLM calls to stay under the provider's per-minute limit.
    llm_delay_seconds = float(os.getenv("INGESTION_LLM_DELAY", "3.0"))
    # Posts per LLM call — batching cuts call count ~Nx so the free tier lasts.
    batch_size = int(os.getenv("INGESTION_BATCH_SIZE", "8"))
    http_timeout_seconds = float(os.getenv("INGESTION_HTTP_TIMEOUT", "15.0"))
    schedule_cron = os.getenv("INGESTION_SCHEDULE_CRON", "0 6 * * *")  # daily 06:00

settings = Settings()
