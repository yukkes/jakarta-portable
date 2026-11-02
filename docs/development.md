# Development

## Setup

JDK 21+, Maven 3.9+ and Docker with Compose v2.

## Daily loop

Code against Quarkus dev mode (changes apply in about 1 s), then check every runtime before you commit.

```bash
docker compose up -d --wait postgres                        # PostgreSQL on localhost:5432
mvn -pl api install -DskipTests                             # again only after order-api.yaml changes
mvn -pl runtimes/quarkus quarkus:dev -Dapp.demo-users=true -Pdev-schema
```

`-Pdev-schema` recreates the schema and the sample data on every reload (about 4 s). Once the schema exists, leave it off.

Before committing: `make format`, then `make lint test`; after changing `app/`, `api/`, `runtimes/` or `compose.yaml` also `make matrix`.

| Command | What it does |
|---|---|
| `make test` | Unit tests; no database or Docker |
| `make format` | Formats Java and the POMs (checked at `mvn validate`) |
| `make lint` | ASCII-only files, `compose.yaml`, shell scripts |
| `make build-dev` / `make build` | Build with the schema recreated at start / left alone (production) |
| `make up RUNTIME=<name>` | Build the image, start it with PostgreSQL, wait until healthy (default `quarkus`) |
| `make verify RUNTIME=<name>` | Run the conformance suite against the running runtime |
| `make logs RUNTIME=<name>` / `make down` | Follow its log / remove every container, the database included |
| `make matrix` | `build-dev`, then up, verify and down for every runtime |
| `make bench` | Throughput table ([performance.md](performance.md)) |

## Where to edit

| Directory | Contents | Rule |
|---|---|---|
| `api/` | `src/main/openapi/order-api.yaml` and the DTOs generated from it | Edit only the YAML |
| `app/` | Business code, entities, the UI (`src/main/webapp/`), unit tests | Only `provided` Jakarta APIs (Enforcer) |
| `runtimes/<name>/` | One runtime: image inputs, `Dockerfile`, `config/` ([runtimes/README.md](../runtimes/README.md)) | No business logic |
| `conformance/` | The HTTP suite every runtime must pass | Add a test with every feature |
| `compose.yaml` | PostgreSQL and one service per runtime | |
| `scripts/` | `portable`, `bench.sh`, `seed.sql` | Nothing runtime-specific |

## Recipes

**A REST endpoint or DTO**

1. Add the schema to `order-api.yaml`, then `mvn -pl api install -DskipTests`. Never return an entity.
2. Write the resource in `app/.../core/web/`:
   - It needs `@RequireRole("USER" | "ADMIN")`, `@PublicEndpoint` or `@OptionalAuth`; without one every call gets 403.
   - `@Produces({MediaType.APPLICATION_JSON, "application/problem+json"})`, or `Accept: application/problem+json` gets 406.
   - `@Consumes(APPLICATION_JSON)` only on methods with a body, or body-less POSTs get 415 on Quarkus.
3. Lists take `PageQuery.of(page, size, sort)` and return `PageResponse<T>` (`page` 0-based, `size` clamped to 1..100). A new sortable field goes into the repository's `SORTABLE` map; nothing else decides what can be sorted.

**An entity or query**

- Standard JPA annotations only. Associations the DTO mapping always reads must not fail lazily outside the transaction.
- `@ApplicationScoped` beans need a no-args constructor or field `@Inject` (Weld).
- Writes use `EntityManager`; reads are JPQL. Read-only lists are records filled by `select new`.
- Counts use `getSingleResult()` without `setMaxResults`; `join fetch` takes no alias (WildFly rejects it).

**The UI**

- Files in `app/src/main/webapp/`; the Quarkus build serves the same directory.
- The CSP forbids inline scripts, styles and handlers: render with `lit-html` (`vendor/`), handle events through `data-action` delegation.
- Changing `SecurityHeaders.CONTENT_SECURITY_POLICY` means changing the `<meta>` CSP in `index.html` and `login.html` too (`SecurityHeadersTest` fails otherwise).

## Configuration

Settings are system properties `app.<key>` (first) or environment variables `APP_<KEY>`. `compose.yaml` passes `APP_DEMO_USERS`; add others to its `x-runtime` environment. The database always comes from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

| Setting | Default | Purpose |
|---|---|---|
| `app.demo-users` | `false` | Create `admin`/`admin` and `user`/`user` at start (local use only; `scripts/portable` turns it on) |
| `app.admin-password`, `app.user-password` | unset | Create `admin` / `user` with this password when no user exists |
| `app.token-secret` | random per start | JWT signing key, at least 32 bytes. Set it in production: a random key invalidates every access token on restart and differs between instances |
| `app.access-token-ttl-seconds` | `600` | Access token lifetime |
| `app.cookie-secure` | `auto` | `Secure` on the auth cookies: `always`, `auto` (when the request is HTTPS) or `never` |
| `app.trust-forwarded-proto` | `false` | Treat `X-Forwarded-Proto: https` as HTTPS (only behind a proxy you control) |
| `app.import-dir` | `data/import` | Where the CSV import reads |
| `app.time-zone` | `Asia/Tokyo` | Zone of order dates, billing months, timestamps and session expiry (not the JVM default, which differs per runtime) |

**Sessions**

- **Access token**: a JWT checked without the database, so it cannot be revoked: logout, role changes and disabled users take effect when it expires.
- **Refresh token**: expires after 30 minutes unused and 8 hours after login, whichever comes first.
- **Browsers** log in with `POST /api/auth/session` and get HttpOnly cookies; every write then needs an `X-XSRF-TOKEN` header equal to the `XSRF-TOKEN` cookie. **API clients** use `POST /api/auth/login` and a Bearer header (no CSRF check).

## Trying the API

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}' | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

curl -s -H "$AUTH" 'http://localhost:8080/api/products?page=0&size=5&sort=unitPrice,desc' | jq .

ORDER_ID=$(curl -s -X POST http://localhost:8080/api/orders -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"customerCode":"C001","lines":[{"productCode":"P001","quantity":2}]}' | jq -r .id)
curl -s -X POST "http://localhost:8080/api/orders/$ORDER_ID/confirm" -H "$AUTH" | jq .   # takes the stock

curl -s -X POST 'http://localhost:8080/api/batch/invoices?month=2026-10' -H "$AUTH" | jq .   # ADMIN only
```

## Tests

| Level | Command | Covers |
|---|---|---|
| Unit | `make test` | Domain rules, tokens, paging and sort validation, the login limit, CSP consistency. Seconds, no containers |
| One runtime | `make verify RUNTIME=<name>` | The 32 HTTP scenarios of `conformance/` against the running server; repeatable on one instance |
| Every runtime | `make matrix` | The same, on each runtime in turn (CI runs it nightly) |

## Troubleshooting

- **Logs**: `make logs RUNTIME=<name>`; `make matrix` saves them to `target/portable/<runtime>.log`, and failed conformance runs to `target/portable/<runtime>-conformance.log`.
- **Port 8080 or 5432 taken**: set `PORT` or `POSTGRES_PORT`, or `make down`.
- **Another database**: set `DB_HOST` (for example `host.docker.internal`) and the other `DB_*`; `make up` then does not start the compose one.
- **Fresh data**: `make build-dev && make up RUNTIME=<name>` recreates the schema and the sample data.
- **The build fails**: at `validate`, formatting (`make format`) or a dependency the Enforcer bans; at `compile` in `app`, a missing or incomplete Javadoc (doclint names the element; the form is in `AGENTS.md`).
