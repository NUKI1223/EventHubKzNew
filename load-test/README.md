# Нагрузочное тестирование (k6)

Скрипт `events-load.js` нагружает ключевые публичные read-эндпоинты EventHub.kz:
список мероприятий, карточку события, полнотекстовый поиск.

## Требования
- Поднятый стек: `./start.sh` (gateway на http://localhost:8180), залитые тестовые данные.
- k6 — запускается через Docker, локальная установка не нужна.

## Запуск

```bash
docker run --rm -i --network host grafana/k6 run - < load-test/events-load.js
```

Другой адрес gateway:
```bash
docker run --rm -i --network host -e GW=http://localhost:8180 grafana/k6 run - < load-test/events-load.js
```

## Результат
k6 печатает сводку: `http_req_duration` (avg / p90 / p95), `http_reqs` (RPS),
`http_req_failed`. Значения p95 и RPS используются для замены «модельных»
значений в таблице 1.2 отчёта на измеренные. Профиль нагрузки (stages) и
пороги (thresholds p95<500ms, ошибки<1%) заданы в начале скрипта.
