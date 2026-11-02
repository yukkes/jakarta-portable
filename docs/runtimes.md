# Runtimes

What each runtime needs to run `app/` unchanged, and the differences that bite. The contract every runtime image meets (port, environment, health check) and how to add a runtime are in [runtimes/README.md](../runtimes/README.md).

| Runtime | Version | Runs | MicroProfile Health |
|---|---|---|---|
| Quarkus | 3.40.1.1 | the `app/` sources, built as a fast-jar | `/q/health` |
| Open Liberty | 26.0.0.10 | `app.war` | `/health` |
| WildFly (Galleon slim) | 42.0.0.Final | `app.war` | none |
| Payara Micro | 7.2026.10 | `app.war` | `/health` |
| Payara Server (full) | 7.2026.10 | `app.war` | `/health` |
| GlassFish | 8.0.5 | `app.war` | none |

These are the newest releases, which implement Jakarta EE 11 (Open Liberty with `webProfile-11.0`; Quarkus a subset). `app/` is written against the EE 10 APIs, which they also implement, so the code is the same for EE 10 and EE 11. The open source editions fix only their newest release, which is why the repository builds those.

The EE 10 versions also pass the same 32 tests (2026-11-02). Run one by overriding the version; none of these is a `make` target:

| Runtime | EE 10 version | How to run it |
|---|---|---|
| Quarkus | 3.20.6 (LTS) | `-Dquarkus.platform.version=3.20.6 -Djberet.version=2.6.0 -Djakarta.persistence-api.version=3.1.0` |
| Open Liberty | 26.0.0.10 | `webProfile-11.0` -> `webProfile-10.0` in `server.xml` |
| WildFly | 39.0.1.Final | `-Dwildfly.version=39.0.1.Final` |
| Payara Micro | 6.2025.11 | `-Dpayara.version=6.2025.11` |
| Payara Server | 6.2025.11 | the image `payara/server-full:6.2025.11-jdk21` |
| GlassFish | 7.0.25 | the image `ghcr.io/eclipse-ee4j/glassfish:7.0.25`, with the three changes listed below |

GlassFish 7 differs from the GlassFish 8 files in the repository in three places:
- `Dockerfile`: the image already has `domain1` (its path is `glassfish/domains/domain1`, there is no `PATH_GF_DOMAIN`), so the `docker-entrypoint.sh true` step goes, and `asadmin` takes no `--passwordfile` (the admin password is empty). The image runs JDK 17.
- `config/setup.asadmin`: GlassFish 7 does not expand `${DB_HOST}` and the like in pool properties (the port is parsed as a number), so the pool gets literal defaults (`localhost`, `5432`, `orders`, ...).
- `config/init.sh`: before `asadmin deploy`, it runs `asadmin set resources.jdbc-connection-pool.AppPool.property.<serverName|portNumber|databaseName|user|password>=${DB_*}` for the five properties, and it waits for `domain1 running` instead of `${AS_DOMAIN_NAME}`.

Open Liberty also passes with `webProfile-10.0` in the newest release of each older major version (`-Dopenliberty.version=23.0.0.12`, `24.0.0.12`, `25.0.0.12`; the Dockerfile base image `eclipse-temurin:17-jre` for 23, `21-jre` for 24, `25-jre` from 25). EE 11 needs 26.0.0.5 or later.

Run each with `mvn clean` on its module first (`mvn -pl runtimes/<name> clean`), or a cached build keeps the old version.

Every runtime answers at `http://localhost:8080/`, and `GET /api/health` (the application's own check, 503 when the database is down) works on all of them.

## Per runtime

**Quarkus**
- `runtimes/quarkus/` compiles `app/src/main` itself and serves `src/main/webapp` from the classpath; no file is copied.
- Settings: `src/main/resources/application.properties`.
- Quarkus does not implement Jakarta Security: `QuarkusIdentityStoreHandler` provides `IdentityStoreHandler` and `Pbkdf2PasswordHash` from the application's `@DatabaseIdentityStoreDefinition` ([api-set.md](api-set.md#jakarta-security-on-quarkus)).

**Open Liberty**
- Features: `webProfile-10.0`, `batch-2.1`, `jdbc-4.3`, `mpHealth-4.0` only (`microProfile-7.1` doubled the startup time).
- `<httpEndpoint host="*">`: the default `localhost` is unreachable from outside the container.
- The Dockerfile starts the server once so the image ships a filled workarea (1.3 s faster start).
- `batch-2.1` checks container roles, and this app has its own authentication, so `batchAdmin` is granted to everyone; `@RequireRole("ADMIN")` guards the endpoints.

**WildFly**
- Galleon provisions only the needed layers (141 MB instead of 1.3 GB); `batch-jberet` and `ee-security` must be listed.
- `config/setup.cli` (data source `java:jboss/datasources/AppDS` as the default, static-file headers) is applied offline at image build.

**Payara Micro**
- One JAR; `--nocluster` skips the Hazelcast join (about 2.7 s).
- `config/post-boot.asadmin` replaces the default H2 data source with PostgreSQL; one command per line (no `\` continuation).

**Payara Server**
- The official image runs `config/post-boot.asadmin` and deploys from `DEPLOY_DIR` with `DEPLOY_PROPS="--contextroot / --name app"`.
- `http-thread-pool` ships empty (5 workers) and `asadmin set` ignores unknown attributes silently, so the Dockerfile writes 64/200 into `domain.xml` and fails if it did not apply.

**GlassFish**
- Configured at image build: the Dockerfile creates the domain, applies `config/setup.asadmin` and stops it. `asadmin multimode` exits 0 even when a command fails, so the build greps its output for `failed.`.
- `config/init.sh` deploys the WAR with `--contextroot /` on every start (there is no post-boot file).

## Same on every runtime

- **Database**: `persistence.xml` has no `<jta-data-source>`; each runtime points its default data source at PostgreSQL, read from `DB_*` at start. A runtime without that setting falls back to an embedded H2 and still deploys.
- **Schema**: decided at build time (`make build` none, `make build-dev` drop-and-create); no runtime can override it per persistence unit.
- **Demo users**: off unless `APP_DEMO_USERS=true` (`scripts/portable` sets it for local runs).
- **Security headers**: `SecurityHeadersFilter` adds CSP, `nosniff` and `Referrer-Policy` to every API response. Static files go around JAX-RS, so:

| Runtime | Headers on static files |
|---|---|
| Quarkus | `quarkus.http.header.*` in `application.properties` |
| Open Liberty | `<httpEndpoint><headers>` in `server.xml` |
| WildFly | Undertow `response-header` filters in `setup.cli` |
| Payara Micro, Payara Server, GlassFish | No setting exists. `index.html` and `login.html` declare the CSP (without `frame-ancestors`) and `Referrer-Policy` in `<meta>`, and `frame-guard.js` hides the page inside a foreign frame. `nosniff` is missing on static files |

## Conformance suite

`conformance/` sends HTTP requests to a running runtime; passing it on every runtime is what "portable" means here.

```bash
make verify RUNTIME=liberty    # one running runtime
make matrix                    # every runtime
```

The 32 tests cover: health; Bearer and cookie login, refresh rotation (one winner under concurrency), logout, CSRF; roles (401 vs 403); paging, size clamping, sort allow-list and stable paging on duplicate values; orders (totals, confirm takes stock, cancel returns it, 409 on short stock and on concurrent confirms); validation errors and RFC 9457 bodies; invoices and the batch jobs (start, restart errors, rerunning bills nothing twice, CSV import idempotency and file-name checks); 405 for unsupported methods. All pass on all six runtimes; the MicroProfile Health test is skipped where there is none (WildFly, GlassFish). The suite fetches its product and customer codes from the API, so it runs against demo or benchmark data, repeatedly.

## Pitfalls

Places where the implementations of the same specification differ, and how this code avoids them.

**JAX-RS and static files**

| Symptom | Runtime | Fix |
|---|---|---|
| Every resource 404 | EE servers | An `Application` subclass with `@ApplicationPath` is required (`OrderApplication`) |
| Static files 404 with `@ApplicationPath("/")` | EE servers | It hides the default servlet; the API is at `/api` |
| Static files under `WEB-INF/classes/META-INF/resources` 404 | EE servers | The UI lives in `src/main/webapp`; only the Quarkus build maps it to `META-INF/resources` |
| Body-less POST 415 | Quarkus | `@Consumes` on the class applies to every method; put it on methods with a body |
| `Accept: application/problem+json` 406 | all | `@Produces` must list `application/problem+json` |
| `WELD-001408` for `@Inject UriInfo` and similar | Payara, GlassFish | Use `@Context` (see below) |

`@Inject` of JAX-RS context types (Jakarta REST 3.1 does not require it):

| Type | Quarkus, Liberty, WildFly | GlassFish | Payara Server, Payara Micro |
|---|---|---|---|
| `UriInfo`, `ResourceInfo` | yes | yes | no |
| `HttpHeaders`, `SecurityContext` | yes | no | no |

**CDI and configuration**

| Symptom | Runtime | Fix |
|---|---|---|
| `WELD-001435` (bean not proxyable) | Weld (all but Quarkus) | `@ApplicationScoped` beans need a no-args constructor; Quarkus does not, so it passes there first |
| `@ConfigProperty` fails in resources; MicroProfile slows Liberty | WildFly, Liberty | Settings are system properties and environment variables (`SecuritySettings`); no MicroProfile API |

**Persistence**

| Symptom | Runtime | Fix |
|---|---|---|
| `StrictJpaComplianceViolation: aliased fetch join` | WildFly | `join fetch` without an alias |
| `LazyInitializationException` on `Invoice.customer` | WildFly | Single-valued associations the DTO always reads are `EAGER` |
| `EclipseLink-4002` / `CWWJP9991W` (`constraint ... does not exist`) at a dev start | Liberty, Payara | Harmless: FK drop order during drop-and-create |
| An order referring to a new product fails ("not marked cascade PERSIST") | EclipseLink | Flush the products first (`SeedService`) |
| `persistence.xml` with `version="3.1"` is rejected (`ivPersistence is null`) | Liberty | Declare version 3.0, which every EE 10 and 11 provider reads |
| Startup observer runs before Hibernate is ready | Quarkus | `@Initialized(ApplicationScoped.class)` fires during static init; `QuarkusStartup` seeds on `StartupEvent` and `application.properties` excludes `StartupDataSeeder` |

**Provisioning and deployment**

| Symptom | Runtime | Fix |
|---|---|---|
| `CWWKY0304W: User UNAUTHENTICATED is not authorized to start batch jobs` | Liberty | Grant `batchAdmin` to `EVERYONE` |
| `NoClassDefFoundError: jakarta/batch/...` | WildFly | Add the Galleon layer `batch-jberet` |
| `WFLYNAM0030: Illegal context in name: java:app/...` | WildFly | Server-level data sources live in `java:jboss/`; point `ee:default-bindings` at it |
| The WAR deploys at `/ROOT` or `/app`; `<context-root>` ignored | Payara, GlassFish | Only `deploy --contextroot /` works |
| A failed `asadmin` command still builds an image | GlassFish | Grep the output for `failed.` |
| A driver in `domain1/lib` is not loaded | GlassFish | Copy it before `start-domain` |
| `asadmin list-domains` exits 0 when the domain is down | GlassFish | Match the output, captured first (`grep -q` under `pipefail` breaks the pipe) |
| Unknown thread-pool attributes are dropped silently | Payara | Write `domain.xml` and check it at build |
| `localhost` resolves to `::1` in the container | Payara Micro | Health checks use `127.0.0.1` |
