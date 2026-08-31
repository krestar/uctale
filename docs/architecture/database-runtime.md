# Database runtime policy

UCTale production uses Neon PostgreSQL. The service is intentionally low traffic, so the database connection pool must cooperate with Neon's scale-to-zero behavior instead of keeping compute active while no user requests are being processed.

## Why Hikari defaults are overridden

Spring Boot 4.1.1 manages HikariCP 7.0.2. Without explicit configuration, Hikari keeps an idle pool and enables a periodic connection keepalive. Neon Free compute suspends after an inactivity window, so periodic application-level database traffic can prevent the compute endpoint from becoming inactive and continuously consume CU-hours.

UCTale therefore uses these production defaults:

| Setting | Default | Purpose |
| --- | ---: | --- |
| `spring.datasource.hikari.keepalive-time` | `0` | Disable Hikari application-level keepalive pings. |
| `spring.datasource.hikari.minimum-idle` | `0` | Permit the pool to drain to zero idle connections. |
| `spring.datasource.hikari.maximum-pool-size` | `3` | Bound concurrent database connections for the current low-traffic service. |
| `spring.datasource.hikari.idle-timeout` | `60000` ms | Close idle connections well before Neon's five-minute inactivity window. |

The values can be overridden without changing code:

- `DATABASE_POOL_KEEPALIVE_TIME_MS`
- `DATABASE_POOL_MINIMUM_IDLE`
- `DATABASE_POOL_MAXIMUM_SIZE`
- `DATABASE_POOL_IDLE_TIMEOUT_MS`

The production defaults are a cost-control policy, not a throughput target. If real traffic grows, increase the maximum pool size only after measuring request concurrency and database saturation. Do not restore a periodic keepalive merely to hide cold-start latency, because doing so defeats scale-to-zero.

## Cold starts

After the Hikari pool drains and Neon suspends compute, the next database-backed request may pay both a new JDBC connection cost and a Neon compute resume cost. UCTale accepts this latency trade-off because the current priority is keeping an infrequently used portfolio service inside the free compute allowance.

A cold start must not be worked around with cron requests, synthetic SQL pings, health checks that continuously query PostgreSQL, or Hikari keepalive traffic. Such workarounds would recreate the original CU-hour problem.

## Production verification after deployment

The code change can make scale-to-zero possible, but repository tests cannot prove that the live Neon endpoint actually suspends. After deploying this configuration:

1. Stop manual game/API activity for at least 10–15 minutes.
2. In Neon Console, verify that the compute endpoint transitions to inactive/suspended rather than remaining continuously active.
3. Check `Active time` and CU-hour usage after metrics have had time to update. Neon metrics can be delayed.
4. Repeat the observation across several idle windows, not only once.
5. Confirm that the first game request after an idle period succeeds after the expected cold-start delay.

Before the change, the observed usage was 45 / 100 CU-hours for the consumption period starting 2026-08-24. Treat this as a baseline only; traffic and Neon metric delay mean exact before/after rates are not directly comparable over a short interval.

## If compute still does not suspend

Do not assume Hikari is the only possible source of database activity. Investigate, in order:

1. Render or another platform health check that reaches an endpoint performing SQL.
2. External uptime monitors or scheduled requests that execute database queries.
3. Application background jobs, scheduled tasks, or startup/reconnect loops.
4. Multiple running backend instances using the same Neon endpoint.
5. Neon project/compute settings and connection activity visible in Neon Console.

The goal is not to force disconnects while useful work is active. The goal is simply that a truly idle UCTale deployment generates no periodic database traffic from its own connection pool.

## Rollback

If production exhibits unacceptable connection churn or failures, adjust the four `DATABASE_POOL_*` environment variables first. A code rollback should be unnecessary because every setting is externally overrideable.

When changing these values, preserve the scale-to-zero invariant: no periodic keepalive and no permanently retained idle connection unless there is a measured operational reason to accept continuous Neon compute usage.
