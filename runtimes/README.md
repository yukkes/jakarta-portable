# Runtimes

Each runtime is one directory here and one service in `../compose.yaml`. `scripts/portable` only drives compose, so a runtime is added or changed in those two places and nowhere else.

```
runtimes/<name>/
├── pom.xml       Maven module: stages what the Dockerfile copies (target/image/, a provisioned server)
├── Dockerfile    builds the image from this directory after `mvn package`
└── config/       the runtime's own configuration, copied or applied by the Dockerfile
```

`runtimes/quarkus/` differs in one way: its `pom.xml` is the Quarkus build of `../../app/src/main`, so its configuration is `src/main/resources/application.properties` instead of `config/`.

## The contract

Every image must:

| | |
|---|---|
| Serve the application | at `/` on port `8080` |
| Read the database | from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` at start, not at build time |
| Read application settings | from `APP_*` environment variables (passed through, nothing to configure) |
| Log | to standard output (`docker compose logs`) |
| Contain `bash` | for the health check in `compose.yaml` (`GET /api/health` returns 200, over `/dev/tcp`) |
| Use the dev or prod WAR as built | the schema mode is decided by `make build` / `make build-dev` |

and its service in `compose.yaml` must:

- use the `x-runtime` anchor (environment, port, health check, dependency on `postgres`),
- have a profile equal to the service name,
- set the label `portable.mp-health` to the MicroProfile Health path, or `""` when the runtime has none.

## How each runtime meets it

| Runtime | Server comes from | Configuration | Database settings resolved by |
|---|---|---|---|
| quarkus | `quarkus-maven-plugin` (fast-jar) | `src/main/resources/application.properties` | `${DB_HOST:localhost}` (MicroProfile Config) |
| liberty | `liberty-maven-plugin` (`target/liberty/wlp`) | `config/server.xml`, `config/jvm.options` | `${DB_HOST}` (Liberty reads the environment) |
| wildfly | `wildfly-maven-plugin`, Galleon layers (`target/server`) | `config/setup.cli`, applied offline at image build | `${env.DB_HOST}` expressions |
| payara-micro | `payara-micro.jar` (Maven dependency) | `config/post-boot.asadmin` | `${ENV=DB_HOST}` (Payara) |
| payara | `payara/server-full` image | `config/post-boot.asadmin`; thread pool written into `domain.xml` | `${ENV=DB_HOST}` (Payara) |
| glassfish | `ghcr.io/eclipse-ee4j/glassfish` image | `config/setup.asadmin` at image build; `config/init.sh` deploys at start | `${DB_HOST}` (GlassFish) |

Versions: the ones Maven downloads are properties of the root `pom.xml`; the base images are named in the Dockerfiles. The images built here start from `eclipse-temurin:25-jre` (JDK 25 on glibc: faster than the Alpine/musl and JDK 21 images, see `docs/performance.md`); Payara Server and GlassFish use their vendor images, which bring JDK 21. `app/` is compiled for Java 17, so the older vendor images that run JDK 17 (GlassFish 7) can run it too.

## Adding a runtime

1. Create `runtimes/<name>/` with the three parts above, and add the module to `runtimes/pom.xml`.
2. Add the service to `compose.yaml`.
3. `make build-dev up verify RUNTIME=<name>`, then `make matrix`.
4. Note its pitfalls in `docs/runtimes.md`.
