# Fabrio JVM

[![](https://img.shields.io/badge/Buijs-Software-blue)](https://buijs.dev/)
[![GitHub](https://img.shields.io/github/license/buijs-dev/fabrio-jvm?color=black)](https://github.com/buijs-dev/fabrio-jvm/blob/main/LICENSE)

Fabrio is the opinionated framework on [Rialto](https://github.com/buijs-dev/rialto-spec): libraries
and code generation for Kotlin services on Quarkus and PostgreSQL. Fabrio JVM is its reference
server generator and runtime: it reads a Rialto contract and generates the persistence, search,
commands, REST resources and mappers around it.

The documentation is the developer portal, [fabrio.buijs.dev](https://fabrio.buijs.dev), built from
[`portal/`](portal).

## Status

Fabrio JVM is built in slices on the way to 1.0.0. The generator reads the contract into an IR,
a pass per profile adds its meaning, and emitters write the files; see
[docs/architecture.md](docs/architecture.md). Every file the pipeline produces is byte for byte
equal to the reference output, for the example applications and the test contracts of rialto-spec;
each slice reproduces more of it, one profile at a time.

| # | Slice | fabrio-jvm | Status |
|---|---|---|---|
| 0 | Foundation | the build, `ContractIr`, passes, contributions, emitters, the pipeline and the safety net | in review |
| 1 | Domain and mapping | types, value objects, unions, mappers | planned |
| 2 | REST and errors | wire models, resources, problem details | planned |
| 3 | Persistence | statements, row mapping, migration checks | planned |
| 4 | Query | filter, sort, pagination, count | planned |
| 5 | Lifecycle | create, update, delete, transitions, idempotency | planned |
| 6 | Auth | principals, policies, API keys, `via` | planned |
| 7 | Events | outbox, transport, SSE | planned |
| 8 | Presentation | none (a client concern) | planned |
| 9 | Versions and compatibility | several API versions | planned |
| 10 | Ecosystem | the generator protocol, claims | planned |

The runtime modules (`fabrio-foundation`, `fabrio-http`, the persistence, Quarkus and event
modules) and the Gradle plugin `dev.buijs.fabrio` come with the slice that needs them.

## Modules

| Module | Contents |
|---|---|
| `fabrio-codegen` | The generator: `ContractIr` from a Rialto contract, the passes per profile and the emitters |
| `fabrio-bom` | Module versions as a platform |

Snapshots are on Repsy: `https://repo.repsy.io/buijs-dev/maven2`.

## Building

```shell
./gradlew build kotlinStyleCheck   # build does not run the style check
```

The build logic is in [`build-logic/`](build-logic) (the `fabrio.*` convention plugins) and the
versions of third-party libraries in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
`fabrio-codegen` depends on `rialto-core` (the contract model) and the build on the style plugin
`dev.buijs.kotlin-style`; both resolve from Maven Local or Repsy.

The test `ReferenceOutputTest` prints how much of the reference output the pipeline produces, and
writes it to `fabrio-codegen/build/reports/reference/coverage.txt`.

The portal:

```shell
cd portal
npm ci
npm run dev      # or: npm run build (checks every link) and npm run preview
```

## Workflows

The workflows in [`.github/workflows`](.github/workflows) call
[ci-templates](https://github.com/buijs-dev/ci-templates) where one fits:

| Workflow | When | What |
|---|---|---|
| `ci.yml` | pull requests, pushes to `main` and `develop` | `build` and `kotlinStyleCheck`, and the portal build |
| `publish-snapshot.yml` | push to `develop` | the `-SNAPSHOT` version to Repsy |
| `portal.yml` | push to `main` | the portal to GitHub Pages |
