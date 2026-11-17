# Architecture

Fabrio JVM generates a Kotlin service from a Rialto contract in four steps:

```text
contract ──frontend──▶ ContractIr ──passes──▶ contributions ──emitters──▶ files
```

Everything lives in `fabrio-codegen`, package `dev.buijs.fabrio.codegen`.

## The IR

`ContractFrontend.load(file)` validates the contract with rialto-core, loads its sources, and
builds a `ContractIr` (package `ir`). The IR is the contract, typed, with its references resolved:

- every section of contract format 1.0.0: sources, types, shapes, operations, mappings, storage,
  events, streams, subscriptions, views, navigation, errors and auth;
- each element keeps its rialto-core `declaration`, and adds what it refers to: the kind of each
  named type (`TypeRef.Named`), the OpenAPI operation of an operation (`RestBinding`), the SQL
  table of a storage (`TableRef`), the AsyncAPI channel of an event and the security scheme of a
  scope or API key (`DocumentRef`);
- maps keep the order of the contract.

The IR is read-only and not Fabrio-specific: what Fabrio decides is a contribution. It reads no
source text: what the generator knows, the contract says.

## Passes and contributions

A `Pass` implements one part of a profile. It names the Rialto capabilities it implements, reads
the IR and what earlier passes contributed, and writes contributions. `Contributions` is a typed
store: a `ContributionKey<T>` has one writer, and `update` lets several passes add to one value.

## Emitters

An `Emitter` turns the IR and the contributions into `GeneratedFile`s: a relative path and UTF-8
text. Emitters decide nothing a pass could decide; they format.

`Pipeline(passes, emitters).run(contract)` runs the passes in order, then the emitters, and
returns the files sorted by path. Two emitters writing one path is an error. The same contract
always gives the same files. `Pipeline.fabrio` is Fabrio's own pipeline; slice 0 has no passes or
emitters yet.

## The safety net

The reference outputs under `fabrio-codegen/src/test/resources/reference` are the target output:
the files that the passes and emitters must reproduce byte for byte, per case.

| Path | Holds |
|---|---|
| `cases/<case>.properties` | the `contract` of the case, and the generator options of the case |
| `inputs/` | the 3 example applications and the 32 valid test contracts of rialto-spec, with their sources |
| `expected/<case>/output/` | the target files of the case (822 in all) |
| `expected/<case>/outcome.txt` | whether the case has output, or the errors that the contract gives |
| `coverage.properties` | per case, how many reference files the pipeline must produce at least |

`ReferenceOutputTest` runs `Pipeline.fabrio` on every case. A file the pipeline produces must be in
the reference and equal to it byte for byte; a file the reference does not have fails too. The
test reports the coverage, the reference files produced so far, in
`build/reports/reference/coverage.txt`; slice 0 produces 0 of 822. `coverage.properties` is the
ratchet: per case, how many files the pipeline already reproduces. Later slices raise it, one
profile at a time; it never goes down.

`tools/reference`, a build of its own, generates the reference outputs from the cases and their
inputs. It runs the reference generator `dev.buijs.fabrio:fabrio-codegen:1.0.0-SNAPSHOT` through
Rialto's `Generation.run`, resolved from Maven Local: `./gradlew -p tools/reference run`. The
inputs are 0.7 MB and the expected files 1.8 MB, about 340 KB compressed.

## How a slice adds a pass

1. Write the passes of the profile: each implements `Pass`, declares its capabilities and
   contributes under its own keys.
2. Write the emitters of the files that the profile owns, from the IR and the contributions.
3. Add both to `Pipeline.fabrio`.
4. Run `ReferenceOutputTest`, fix every file that differs, and raise `coverage.properties` to the
   new numbers.

```kotlin
val ENTITY_CLASSES = ContributionKey.of<Map<String, String>>("domain.entity-classes")

object EntityClassPass : Pass {
    override val name = "domain.entity-classes"
    override val capabilities = setOf("domain.entity")

    override fun contribute(ir: ContractIr, contributions: ContributionWriter) {
        contributions.put(ENTITY_CLASSES, ir.typesOf<TypeIr.Entity>().associate { it.name to "${it.name}Entity" })
    }
}

object EntityClassEmitter : Emitter {
    override val name = "domain.entity-classes"

    override fun emit(ir: ContractIr, contributions: Contributions) =
        contributions.require(ENTITY_CLASSES).map { (type, name) ->
            GeneratedFile("com/example/domain/$name.kt", "class $name // $type\n")
        }
}
```
