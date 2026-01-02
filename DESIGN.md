\# Design Decisions



\## Why Async Query Execution

Heavy analytical queries can exceed API timeouts and block request threads.

This system executes long-running queries asynchronously and exposes a polling API

to retrieve results once computation completes.



\## Why Redis Caching

Repeated analytical queries over the same dimensions create unnecessary database load.

Redis is used to cache query results with TTL to reduce pressure on PostgreSQL and

improve p95 latency.



\## Why Separate Analytics from OLTP

Running analytics directly on OLTP databases causes lock contention and latency spikes.

This architecture isolates analytical workloads to maintain system stability.



\## Failure Handling

\- Queries are idempotent using request hashes

\- Async jobs are persisted to allow recovery

\- Timeouts and partial failures return safe responses



