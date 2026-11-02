# Performance

Startup time, memory and throughput of the same WAR (and the Quarkus build of the same sources) on every runtime, measured with `make bench`.

## Results

2026-11-02, one `make bench` run with the default settings, on the current code (the EE 10 APIs) and the repository's default versions. An earlier run the same day, before the code moved to the EE 10 APIs, gave the same ranking but different absolute numbers (Quarkus 15,533 and 8,295 req/s), more than the variance below; compare runtimes within one run, not across runs.

| Runtime | Startup | Memory | `/api/products` (req/s) | p99 | `/api/orders` (req/s) | p99 | Image |
|---|---:|---:|---:|---:|---:|---:|---:|
| **Quarkus 3.40.1.1 (JVM)** | **3.5 s** | **255 MiB** | **18,504** | **6.93 ms** | **10,185** | **11.32 ms** | **561 MB** |
| Open Liberty 26.0.0.10 | 6.0 s | 393 MiB | 12,076 | 11.78 ms | 7,842 | 13.42 ms | 1,306 MB |
| WildFly 42.0.0.Final (Galleon slim) | 6.7 s | 490 MiB | 13,006 | 15.60 ms | 7,908 | 21.52 ms | 753 MB |
| Payara Micro 7.2026.10 | 9.0 s | 524 MiB | 15,000 | 9.79 ms | 7,789 | 14.56 ms | 676 MB |
| Payara Server 7.2026.10 (full) | 12.4 s | 813 MiB | 10,538 | 13.10 ms | 7,078 | 16.78 ms | 1,229 MB |
| GlassFish 8.0.5 | 10.2 s | 619 MiB | 4,844 | 12.73 ms | 2,983 | 20.17 ms | 1,212 MB |

- **Startup**: from `docker compose up` of the built image until `GET /api/health` answers 200, polled every 0.1 s. It includes recreating the schema and the sample data (dev build).
- **Memory**: `docker stats` of the container right after startup.
- **Image**: unpacked size (`docker image inspect`).
- **How much a difference means**: within one session, reruns differ by up to 5% on `/api/products` and 15% on `/api/orders`; smaller differences do not rank runtimes. Payara Server varies more (8,675 to 10,256 in three runs the same hour). Between sessions every number can move by 15-20% (CPU power and temperature), so compare only numbers from one `make bench`.

**Reading it**

- **Quarkus** leads on every column.
- **Payara Micro** has the best `/api/products` throughput of the WAR runtimes; on `/api/orders` Open Liberty, WildFly and Payara Micro are within the variance. Open Liberty starts fastest of them, and Payara Micro and WildFly have the smallest images.
- **Payara Server** needs the most memory and the longest start.
- **GlassFish** passes every conformance test, at about 25-45% of the others' throughput.

## Conditions

- **Machine**: 16 vCPUs, 7 GB RAM, WSL2. wrk, the runtime and PostgreSQL share it, and the CPU is saturated during the load.
- **Containers**: every runtime and PostgreSQL 17 on the compose network. The load goes to the runtime container's address, not the published port (which would add docker-proxy).
- **JDK**: 25 (`eclipse-temurin:25-jre`) in the images built here; Payara Server and GlassFish run the JDK 21 of their vendor images.
- **Load**: `wrk -t4 -c50` per endpoint: a 10 s warm-up, then the median of three 5 s runs.
- **Data**: 200 products, 50 customers, 200 orders with 400 lines (`scripts/seed.sql`, loaded after startup). Small enough to stay in caches.
- **Request**: every request carries a Bearer JWT and goes through the whole stack: the authentication filter, a CDI interceptor, JPA, PostgreSQL and JSON-B. A product page is 2 queries (page and count), an order page 3 (orders, their lines, count).
- **Aligned**: database pool 10/50 (Quarkus: max 50). HTTP workers are each runtime's default, except Payara Server and GlassFish (64/200; see below).

## How to reproduce

Needs `wrk`.

```bash
make build-dev
make bench                     # every runtime, about 7 minutes; prints the table above
make bench RUNTIMES=quarkus    # one runtime
```

`bash scripts/bench.sh <url>` measures a runtime that is already running; `WARMUP`, `DURATION` and `RUNS` change the load.

## Settings that matter

These are in place; without them the numbers above are not reproducible. Each effect is from the measurement that led to the setting.

| Setting | Where | Effect |
|---|---|---|
| `http-thread-pool` 64/200 (it ships empty: 5 workers) | `runtimes/payara/Dockerfile`, `runtimes/glassfish/config/setup.asadmin` | Payara Server 3,959 -> 9,409 req/s |
| EclipseLink shared cache off, statement cache on, weaving off | `persistence.xml`; JVM options of Liberty, Payara and GlassFish | Liberty +51%, Payara Micro +58% (lock contention in the shared cache) |
| Database pool max 50 everywhere | each runtime's config | WildFly at 5/20 was 1.5x slower |
| Order list in two queries (orders with customers, then lines by `IN`) | `OrderRepository.findPage` | 4.5x over the N+1 version |
| Read-only lists projected into records | `OrderRepository`, `ProductRepository` | `/api/orders` +16-30% (no dirty checking) |
| `JsonProvider` resolved once | `TokenService` | 20-37% on every authenticated request |
| Liberty: only `mpHealth-4.0` instead of `microProfile-7.1` | `runtimes/liberty/config/server.xml` | startup 12.4 -> 6.5 s |
| Liberty: workarea filled at image build | `runtimes/liberty/Dockerfile` | first answer 7.7 -> 6.4 s, and higher throughput right after start |
| Payara Micro 7.2026.10 | 9.0 s | 524 MiB | 15,000 | 9.79 ms | 7,789 | 14.56 ms | 676 MB |
| JDK 25 on glibc instead of JDK 21 on Alpine | the Dockerfiles | Quarkus +11%, Liberty +13% |

Tried and not applied:

- **Host network** for the runtimes: `/api/products` +5-10%, no change in startup. Not used: it would hide a cost every containerized deployment pays, and Docker Desktop supports it only partly.
- **Jandex index on Liberty**: no startup gain (188 classes are too few to matter).
- **Reusing `Mac` per thread** in `TokenService`: +3-6%, within the noise, and it once kept an old key after the key changed.
- **More Liberty executor threads** (`coreThreads=64`): no change.

## Pitfalls that distort the numbers

| Pitfall | Effect | Guard |
|---|---|---|
| Traffic through docker-proxy (a published port) | -20% to -77% | `make bench` targets the container address; the runtimes reach PostgreSQL over the compose network |
| Measuring right after the first start | Payara Server 9,400 instead of 13,800; `/api/orders` 25-30% low with a 5 s warm-up | 10 s warm-up per endpoint |
| The dev build recreates the schema at start | only the few seed rows get measured | `make bench` loads `scripts/seed.sql` after startup |
| A missing data source falls back to the embedded H2 | fast, empty answers | `bench.sh` aborts if `/api/products` is empty |
| Comparing numbers from different sessions | 15-20% swings | compare within one `make bench` |
