# APIs and Design Rules

`app/` compiles against 12 Jakarta API jars (`provided`) and nothing else besides the generated DTOs of `api/`. Each runtime brings the implementations. The umbrella `jakarta.jakartaee-api` is not used: it would make APIs that some runtimes lack (EJB, Servlet, Faces) look available.

## The APIs

The versions are those of Jakarta EE 10. EE 11 runtimes implement newer versions of the same specifications, and code written against these runs on both. Not used on purpose, because EE 10 lacks them: the CDI 4.1 `Startup` event and `@Inject EntityManager` (Persistence 3.2).

| Area | Specification | Version | Used for |
|---|---|---|---|
| DI and events | CDI Lite (`cdi-api`, `inject-api`, `annotation-api`) | 4.0.1 / 2.0.1 / 2.1.1 | Beans, startup seeding (`@Observes @Initialized(ApplicationScoped.class)`), the post-commit audit log (`AFTER_SUCCESS`) |
| Interceptors | Jakarta Interceptors | 2.1.0 | `@Logged`: timing and business-exception logging |
| REST | Jakarta REST | 3.1.0 | Endpoints, the auth, CSRF, role and header filters, exception mappers. Kept at 3.1 for the runtimes that ship it |
| Persistence | Jakarta Persistence | 3.1.0 | Entities, `EntityManager`, JPQL, `@Version`, lifecycle callbacks |
| Transactions | Jakarta Transactions | 2.0.1 | `@Transactional` |
| Validation | Jakarta Validation | 3.0.2 | Request DTOs and parameters |
| JSON | JSON-B / JSON-P | 3.0.1 / 2.1.3 | Request and response bodies / the JWT payload (no JWT library) |
| Security | Jakarta Security | 3.0.0 | Password check (`@DatabaseIdentityStoreDefinition`, `IdentityStoreHandler`) and hashing (`Pbkdf2PasswordHash`) |
| Batch | Jakarta Batch | 2.1.1 | The invoice chunk job and the CSV import batchlet, started and restarted through `JobOperator` |

**Left out on purpose**

| Not used | Instead |
|---|---|
| Servlet API | JAX-RS `ContainerRequestContext`, `HttpHeaders`, `NewCookie`, `SecurityContext` |
| EJB | `@ApplicationScoped` + `@Transactional` (Quarkus has no EJB) |
| MicroProfile Config | `app.*` system properties and `APP_*` environment variables (`SecuritySettings`); dropping MicroProfile also halved Liberty's startup |
| MicroProfile Health in the app | `GET /api/health`, the same path everywhere |
| Jakarta Data | JPA ([persistence.md](persistence.md#why-not-jakarta-data)) |

**Enforced at build time**: `maven-enforcer-plugin` in `app/pom.xml` fails `mvn validate` if any of these reaches `app`, transitively included: Quarkus, Quarkiverse, SmallRye, Spring, Hibernate ORM and Validator, EclipseLink, RESTEasy, Weld, Jersey, EJB, Faces, Servlet, `jakartaee-api`.

## Jakarta Security on Quarkus

The EE servers implement Jakarta Security and read the `@DatabaseIdentityStoreDefinition` on `IdentityStoreSetup` (WildFly needs the `ee-security` layer). Quarkus does not, so `QuarkusIdentityStoreHandler` (about 50 lines, `runtimes/quarkus/`) runs the same `callerQuery` and checks the hash with Soteria's `Pbkdf2PasswordHashImpl`, the reference implementation. The hash format (`PBKDF2WithHmacSHA256:600000:<salt>:<hash>`) is fixed by the specification: a hash made on any runtime validates on all six.

Lockout (15 minutes after 5 failures), the concurrent-login limit (`LoginGate`: half the cores, then 429) and the tokens are application code.

## Layers

```text
web          core.web.*          JAX-RS resources, filters, exception mappers, PageQuery/PageResponse
service      core.service.*      @Transactional business logic
             core.batch.*        Jakarta Batch readers, processors, writers, batchlet
             core.security.*     identity store, tokens, settings
repository   core.repository.*   JPQL reads, EntityManager writes, paging and sorting
domain       domain.*            JPA entities
```

- **No entity crosses into `web`**: responses are the `api/` DTOs or `PageResponse`, so lazy loading and provider proxies never meet JSON-B.
- **Paging and sorting end in `repository`**: `web` passes a 0-based page and `SortOrder`s; `PageQuery` checks only the `field,asc|desc` format, and the repository's `SORTABLE` map decides which fields exist (400 otherwise). No request value becomes query text.
- **CDI**: `@ApplicationScoped` beans have a no-args constructor or field injection (Weld needs it, Quarkus does not). Interceptors are enabled by `@Priority`, without `beans.xml`.

## Adding a dependency

Pick the scope first; `compile` is the last resort, because it goes into the WAR on every runtime.

| Scope | In the WAR | For |
|---|---|---|
| `provided` | no | Jakarta APIs the runtimes implement (only those the Enforcer allows) |
| `test` | no | Test libraries |
| plugin or annotation processor | no | Generation and checks at build time |
| `compile` / `runtime` | yes | Code the business logic cannot do without. None today besides `api` |

The WAR is about 299 KB: application classes 295 KB, UI 82 KB, `jackson-annotations` 82 KB, `api.jar` 53 KB (unpacked). Measure it before and after any `compile` dependency (`mvn -pl api,app package`, `ls -l app/target/app.war`).
