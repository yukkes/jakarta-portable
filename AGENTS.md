# AGENTS.md

Jakarta EE 10 app (`app/`), written against the EE 10 APIs that EE 11 runtimes also implement, that runs unchanged on every runtime in `compose.yaml` (`bash scripts/portable list`). Overview for humans: `README.md`.

## Commands
`make` targets are short names for `scripts/portable` and Maven; `make help` lists them.
- Unit tests (no DB, no Docker): `make test`. Format: `make format`. Static checks: `make lint`
- Build: `make build-dev` (schema `drop-and-create`, sample data) / `make build` (schema `none`)
- Run one runtime (Docker): `make up RUNTIME=<name>`, `make verify RUNTIME=<name>`, `make logs RUNTIME=<name>`, `make down`
- Every runtime: `make matrix`
- Day-to-day coding: Quarkus dev mode (`docs/development.md`)

## Before finishing
- Always: `make lint test` passes.
- Changed `app/`, `api/`, `runtimes/` or `compose.yaml`: `make matrix` passes.

## Rules
Enforced by the build, a test or `make lint` where noted; the rest is checked in review.

Structure
- **Modules**: Business code, `@Entity` and UI (`src/main/webapp/`) live in `app/`. `runtimes/quarkus/` compiles `../../app/src/main/*` and adds only `QuarkusIdentityStoreHandler` and `QuarkusStartup`. Never duplicate code.
- **Scripts**: Run them with `bash` (`bash scripts/portable ...`, or `make`); no file is committed executable, so a Windows checkout shows no mode changes (`make lint`).
- **Runtimes**: One directory per runtime under `runtimes/<name>/` (`pom.xml` stages the image inputs, `Dockerfile`, `config/`) and one service in `compose.yaml`; follow `runtimes/README.md`. Runtime knowledge never goes into `scripts/`.
- **Deps**: `app` depends only on `api` and the `provided` `jakarta.*-api` jars of the root `pom.xml` (Enforcer bans vendor impls, EJB, Servlet, Faces, `jakarta.jakartaee-api`).
- **Layers**: `web -> service -> repository -> domain`. `EntityManager` and JPQL stay in `repository`.
- **CDI**: `@ApplicationScoped` beans need a no-args constructor or field `@Inject` (Weld proxies). Startup work observes `@Initialized(ApplicationScoped.class)`, not the CDI 4.1 `Startup` event (Quarkus: `QuarkusStartup`).

API
- **DTOs**: Edit `api/src/main/openapi/order-api.yaml`, then `mvn -pl api install -DskipTests`. Never edit `target/generated-sources`; never return an `@Entity` from JAX-RS.
- **Endpoints**: Each needs `@RequireRole`, `@PublicEndpoint` or `@OptionalAuth` (default 403; never `@RolesAllowed`). `@Produces` includes `"application/problem+json"`; `@Consumes(APPLICATION_JSON)` only on methods with a body.
- **Lists**: Build `PageQuery.of(page, size, sort)` and return `PageResponse<T>`. Sort fields come only from the repository's `SORTABLE` map via `Sorts`; never concatenate request values into JPQL.

Persistence
- **Projections**: Read-only lists are `record`s filled by `select new`.
- **Queries**: Counts use `getSingleResult()` without `setMaxResults`. Never alias `join fetch`. No `<jta-data-source>` in `persistence.xml`.

Configuration
- **Settings**: Application settings are `app.<key>` system properties or `APP_<KEY>` environment variables, read in `SecuritySettings` / `BusinessTime` only. Every runtime takes the database from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`.

Security and UI
- **Cookies**: Cookie-authenticated writes require `X-XSRF-TOKEN`.
- **CSV imports**: Bare `.csv` names in `data/import/` only (`ImportFileResolver`).
- **CSP**: UI uses external JS/CSS only. Keep `SecurityHeaders.CONTENT_SECURITY_POLICY`, the `<meta>` CSP of `index.html`/`login.html` (`SecurityHeadersTest`) and the static-file headers in `runtimes/*/config` in sync.

Style
- **Format**: `palantir-java-format` and `sortPom` (checked at `mvn validate`; fix with `make format`).
- **Javadoc**: Every public and protected element of `app/` has one in the standard form: a summary sentence (methods start with a verb: "Returns", "Creates"), then `@param` for every parameter, `@return`, and `@throws` with the HTTP status for each exception a caller handles. javac's doclint fails the build otherwise (`app/pom.xml`); `@Override` methods inherit theirs. Write what the signature cannot show: what empty or null means, side effects (stock, DB rows, cookies), transactions and locks; never restate the name. Fields of exceptions need one too (javadoc lists them in the serialized form).
- **Comments**: English, limited to what the code cannot say; measurements and history go to `docs/`.
- **Language**: Code, docs and UI text are English. Every file is printable ASCII, except `README.ja.md` and file-tree diagrams in Markdown, which may use box-drawing characters (`make lint`).

Git
- **Commits/PRs**: Never add `Co-Authored-By` trailers or other AI attribution lines (e.g. "Generated with Claude Code").

## Docs
- `runtimes/README.md`: the runtime contract (image inputs, environment, port, health check)
- `docs/development.md`: daily loop, where to edit, recipes by change type, settings, tests
- `docs/api-set.md`: the Jakarta APIs used and left out, layers, adding a dependency
- `docs/runtimes.md`: per-runtime setup, the conformance suite, pitfalls
- `docs/persistence.md`: JPA rules and the SQL that runs
- `docs/performance.md`: results, conditions, the settings that matter
