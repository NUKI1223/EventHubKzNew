# Spike load test

Validates the 3-5k login spike hardening: zero hard errors (5xx / timeouts) under a
~250 logins/s peak, ~13k logins over 60s — models a 3-5k-person login herd compressed
into ~1 minute, with margin — plus 500 rps background browse.

## Run

```bash
k6 run -e GW=http://localhost:8180 loadtest/spike.js
```

## Pass criteria

- `login_fail ... rate==0` — k6 marks threshold green.
- No container restarts / OOM: `docker compose ps` clean afterward.

## Registration spike (optional, needs a large event pool)

Registering the same user to the same event twice is a logical 409, not an overload
error. To test registration throughput, register distinct (user, event) pairs from a
large seeded event-id range and treat only status 0/5xx as failures.
