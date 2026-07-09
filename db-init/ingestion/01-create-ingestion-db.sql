-- Fresh-volume init for the postgres-notifications instance (reused for ingestion_db).
-- Existing volume: create manually — docker exec postgres-notifications psql -U postgres -c "CREATE DATABASE ingestion_db;"
CREATE DATABASE ingestion_db;
