"""Генерация JSON-дашбордов Grafana для EventHub.kz"""
import json
import os

OUT = "/home/ngcvfb/IdeaProjects/EventHubKz/monitoring/grafana/provisioning/dashboards"
os.makedirs(OUT, exist_ok=True)

PROM_DS = {"type": "prometheus", "uid": "prometheus"}

SERVICES = [
    "eureka-server", "api-gateway", "auth-service", "user-service",
    "event-service", "like-service", "search-service",
    "notification-service", "tag-service", "file-service",
]


def panel(id_, title, x, y, w, h, targets, kind="timeseries", unit="short",
          decimals=None, thresholds=None):
    p = {
        "id": id_,
        "title": title,
        "type": kind,
        "datasource": PROM_DS,
        "gridPos": {"x": x, "y": y, "w": w, "h": h},
        "targets": targets,
        "fieldConfig": {
            "defaults": {"unit": unit},
            "overrides": [],
        },
        "options": {
            "legend": {"displayMode": "table", "placement": "bottom",
                       "showLegend": True, "calcs": ["last", "max"]},
            "tooltip": {"mode": "multi", "sort": "desc"},
        },
    }
    if decimals is not None:
        p["fieldConfig"]["defaults"]["decimals"] = decimals
    if thresholds:
        p["fieldConfig"]["defaults"]["thresholds"] = thresholds
    if kind == "stat":
        p["options"] = {
            "colorMode": "background",
            "graphMode": "none",
            "justifyMode": "auto",
            "textMode": "auto",
            "reduceOptions": {"calcs": ["lastNotNull"], "fields": "",
                              "values": False},
        }
    return p


def target(expr, legend, ref="A"):
    return {"refId": ref, "expr": expr, "legendFormat": legend,
            "datasource": PROM_DS}


# ─────────────────────────────────────────────────────────────────
# DASHBOARD 1 — Service overview (один сервис, выбирается из дропдауна)
# ─────────────────────────────────────────────────────────────────
service_overview = {
    "uid": "eventhub-service",
    "title": "EventHub — Service overview",
    "tags": ["eventhub"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "10s",
    "time": {"from": "now-15m", "to": "now"},
    "templating": {
        "list": [{
            "name": "service",
            "label": "Service",
            "type": "query",
            "datasource": PROM_DS,
            "query": "label_values(up, job)",
            "refresh": 1,
            "current": {"text": "event-service", "value": "event-service"},
            "includeAll": False,
            "multi": False,
        }],
    },
    "panels": [
        panel(1, "Health", 0, 0, 4, 3,
              [target('up{job="$service"}', "{{instance}}")],
              kind="stat", unit="none",
              thresholds={"mode": "absolute", "steps": [
                  {"color": "red", "value": 0},
                  {"color": "green", "value": 1},
              ]}),
        panel(2, "Uptime", 4, 0, 5, 3,
              [target('process_uptime_seconds{job="$service"}', "uptime")],
              kind="stat", unit="s"),
        panel(3, "JVM threads", 9, 0, 5, 3,
              [target('jvm_threads_live_threads{job="$service"}', "live"),
               target('jvm_threads_peak_threads{job="$service"}', "peak", "B")],
              kind="stat", unit="short"),
        panel(4, "HTTP RPS (5m)", 14, 0, 5, 3,
              [target(
                  'sum(rate(http_server_requests_seconds_count{job="$service"}[5m]))',
                  "RPS")],
              kind="stat", unit="reqps", decimals=2),
        panel(5, "JVM memory used", 0, 3, 12, 7, [
            target(
                'jvm_memory_used_bytes{job="$service", area="heap"}',
                "heap {{id}}"),
            target(
                'jvm_memory_used_bytes{job="$service", area="nonheap"}',
                "nonheap {{id}}", "B"),
        ], unit="bytes"),
        panel(6, "CPU usage", 12, 3, 12, 7, [
            target('system_cpu_usage{job="$service"}', "host", "A"),
            target('process_cpu_usage{job="$service"}', "process", "B"),
        ], unit="percentunit", decimals=2),
        panel(7, "HTTP request rate by URI", 0, 10, 12, 8, [
            target(
                'sum by (uri) (rate(http_server_requests_seconds_count{job="$service"}[5m]))',
                "{{uri}}"),
        ], unit="reqps"),
        panel(8, "HTTP latency p95 / p99", 12, 10, 12, 8, [
            target(
                'histogram_quantile(0.95, sum by (le, uri)'
                '(rate(http_server_requests_seconds_bucket{job="$service"}[5m])))',
                "p95 {{uri}}", "A"),
            target(
                'histogram_quantile(0.99, sum by (le)'
                '(rate(http_server_requests_seconds_bucket{job="$service"}[5m])))',
                "p99", "B"),
        ], unit="s"),
        panel(9, "HTTP error rate (5xx, 4xx)", 0, 18, 12, 7, [
            target(
                'sum by (status) (rate(http_server_requests_seconds_count{job="$service",'
                ' status=~"5.."}[5m]))',
                "5xx {{status}}", "A"),
            target(
                'sum by (status) (rate(http_server_requests_seconds_count{job="$service",'
                ' status=~"4.."}[5m]))',
                "4xx {{status}}", "B"),
        ], unit="reqps"),
        panel(10, "DB connections (HikariCP)", 12, 18, 12, 7, [
            target('hikaricp_connections_active{job="$service"}', "active", "A"),
            target('hikaricp_connections_idle{job="$service"}', "idle", "B"),
            target('hikaricp_connections_pending{job="$service"}', "pending", "C"),
        ], unit="short"),
        panel(11, "GC pause time", 0, 25, 12, 7, [
            target(
                'rate(jvm_gc_pause_seconds_sum{job="$service"}[5m])',
                "{{action}} {{cause}}"),
        ], unit="s"),
        panel(12, "Logback errors", 12, 25, 12, 7, [
            target(
                'sum by (level) (rate(logback_events_total{job="$service"}[5m]))',
                "{{level}}"),
        ], unit="short"),
    ],
}

with open(f"{OUT}/01-service-overview.json", "w") as f:
    json.dump(service_overview, f, indent=2)
print("wrote 01-service-overview.json")


# ─────────────────────────────────────────────────────────────────
# DASHBOARD 2 — Platform overview (все сервисы рядом)
# ─────────────────────────────────────────────────────────────────
platform = {
    "uid": "eventhub-platform",
    "title": "EventHub — Platform overview",
    "tags": ["eventhub", "overview"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "10s",
    "time": {"from": "now-1h", "to": "now"},
    "panels": [
        panel(1, "Services up", 0, 0, 24, 4, [
            target('up{job=~"(eureka-server|api-gateway|auth-service|user-service|'
                   'event-service|like-service|search-service|notification-service|'
                   'tag-service|file-service)"}', "{{job}}"),
        ], kind="stat", unit="none",
              thresholds={"mode": "absolute", "steps": [
                  {"color": "red", "value": 0},
                  {"color": "green", "value": 1},
              ]}),
        panel(2, "HTTP RPS per service", 0, 4, 12, 8, [
            target(
                'sum by (job) (rate(http_server_requests_seconds_count[5m]))',
                "{{job}}"),
        ], unit="reqps"),
        panel(3, "p95 latency per service", 12, 4, 12, 8, [
            target(
                'histogram_quantile(0.95, sum by (le, job)'
                '(rate(http_server_requests_seconds_bucket[5m])))',
                "{{job}}"),
        ], unit="s"),
        panel(4, "5xx errors per service", 0, 12, 12, 8, [
            target(
                'sum by (job) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))',
                "{{job}}"),
        ], unit="reqps"),
        panel(5, "JVM heap usage per service", 12, 12, 12, 8, [
            target(
                'sum by (job) (jvm_memory_used_bytes{area="heap"})',
                "{{job}}"),
        ], unit="bytes"),
        panel(6, "JVM threads per service", 0, 20, 12, 7, [
            target(
                'jvm_threads_live_threads',
                "{{job}}"),
        ], unit="short"),
        panel(7, "Process CPU per service", 12, 20, 12, 7, [
            target('process_cpu_usage', "{{job}}"),
        ], unit="percentunit", decimals=2),
    ],
}

with open(f"{OUT}/02-platform-overview.json", "w") as f:
    json.dump(platform, f, indent=2)
print("wrote 02-platform-overview.json")


# ─────────────────────────────────────────────────────────────────
# DASHBOARD 3 — Kafka & messaging
# ─────────────────────────────────────────────────────────────────
kafka_dash = {
    "uid": "eventhub-kafka",
    "title": "EventHub — Kafka & messaging",
    "tags": ["eventhub", "kafka"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "10s",
    "time": {"from": "now-1h", "to": "now"},
    "panels": [
        panel(1, "Kafka producers — sent records", 0, 0, 12, 8, [
            target(
                'sum by (job, topic) (rate(kafka_producer_record_send_total[5m]))',
                "{{job}} → {{topic}}"),
        ], unit="ops"),
        panel(2, "Kafka producers — send errors", 12, 0, 12, 8, [
            target(
                'sum by (job) (rate(kafka_producer_record_error_total[5m]))',
                "{{job}}"),
        ], unit="ops"),
        panel(3, "Kafka consumers — records consumed", 0, 8, 12, 8, [
            target(
                'sum by (job, client_id) (rate(kafka_consumer_records_consumed_total[5m]))',
                "{{job}} {{client_id}}"),
        ], unit="ops"),
        panel(4, "Kafka consumers — consumer lag", 12, 8, 12, 8, [
            target(
                'sum by (job, client_id, topic) (kafka_consumer_fetch_manager_records_lag)',
                "{{job}} {{topic}}"),
        ], unit="short"),
        panel(5, "Listener processing time p95", 0, 16, 12, 8, [
            target(
                'histogram_quantile(0.95, sum by (le, job, name)'
                '(rate(spring_kafka_listener_seconds_bucket[5m])))',
                "{{job}} {{name}}"),
        ], unit="s"),
        panel(6, "Listener invocations", 12, 16, 12, 8, [
            target(
                'sum by (job, name, result) (rate(spring_kafka_listener_seconds_count[5m]))',
                "{{job}} {{name}} {{result}}"),
        ], unit="ops"),
    ],
}

with open(f"{OUT}/03-kafka.json", "w") as f:
    json.dump(kafka_dash, f, indent=2)
print("wrote 03-kafka.json")


# ─────────────────────────────────────────────────────────────────
# DASHBOARD 4 — Gateway routing
# ─────────────────────────────────────────────────────────────────
gateway = {
    "uid": "eventhub-gateway",
    "title": "EventHub — API Gateway",
    "tags": ["eventhub", "gateway"],
    "timezone": "browser",
    "schemaVersion": 39,
    "version": 1,
    "refresh": "10s",
    "time": {"from": "now-30m", "to": "now"},
    "panels": [
        panel(1, "Gateway up", 0, 0, 6, 4, [
            target('up{job="api-gateway"}', "api-gateway"),
        ], kind="stat", unit="none",
              thresholds={"mode": "absolute", "steps": [
                  {"color": "red", "value": 0},
                  {"color": "green", "value": 1},
              ]}),
        panel(2, "Gateway RPS (total)", 6, 0, 6, 4, [
            target(
                'sum(rate(spring_cloud_gateway_requests_seconds_count[5m]))',
                "total"),
        ], kind="stat", unit="reqps", decimals=2),
        panel(3, "Gateway p95 (total)", 12, 0, 6, 4, [
            target(
                'histogram_quantile(0.95, sum by (le)'
                '(rate(spring_cloud_gateway_requests_seconds_bucket[5m])))',
                "p95"),
        ], kind="stat", unit="s", decimals=3),
        panel(4, "Gateway errors", 18, 0, 6, 4, [
            target(
                'sum(rate(spring_cloud_gateway_requests_seconds_count{outcome="SERVER_ERROR"}[5m]))',
                "5xx"),
        ], kind="stat", unit="reqps", decimals=2),
        panel(5, "RPS by route", 0, 4, 12, 8, [
            target(
                'sum by (routeId) (rate(spring_cloud_gateway_requests_seconds_count[5m]))',
                "{{routeId}}"),
        ], unit="reqps"),
        panel(6, "p95 by route", 12, 4, 12, 8, [
            target(
                'histogram_quantile(0.95, sum by (le, routeId)'
                '(rate(spring_cloud_gateway_requests_seconds_bucket[5m])))',
                "{{routeId}}"),
        ], unit="s"),
        panel(7, "Status codes", 0, 12, 12, 8, [
            target(
                'sum by (status) (rate(spring_cloud_gateway_requests_seconds_count[5m]))',
                "{{status}}"),
        ], unit="reqps"),
        panel(8, "Outcome breakdown", 12, 12, 12, 8, [
            target(
                'sum by (outcome) (rate(spring_cloud_gateway_requests_seconds_count[5m]))',
                "{{outcome}}"),
        ], unit="reqps"),
    ],
}

with open(f"{OUT}/04-gateway.json", "w") as f:
    json.dump(gateway, f, indent=2)
print("wrote 04-gateway.json")

print("\nAll dashboards generated to:", OUT)
