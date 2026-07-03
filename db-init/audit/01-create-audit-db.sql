-- Выполняется только при инициализации СВЕЖЕГО тома postgres-notifications.
-- На существующем томе базу создаёт ручной шаг из README (docker exec ... CREATE DATABASE).
CREATE DATABASE audit_db;
