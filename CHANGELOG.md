# Changelog

## [Unreleased]

### Slice 0: foundation

- The build: the `fabrio.*` convention plugins in `build-logic/`, the version catalog, the Gradle
  wrapper, and `fabrio-bom` with the modules that exist. The runtime modules come with the slice
  that needs them.
- `fabrio-codegen`, the new architecture: the contract is read into `ContractIr`, passes add the
  meaning of each profile as typed contributions, and emitters write the files.
  - `ContractIr`: every section of contract format 1.0.0 (sources, types, shapes, operations,
    mappings, storage, events, streams, subscriptions, views, navigation, errors and auth), built
    by `ContractFrontend` from the rialto-core model and the loaded sources, with every reference
    resolved: an operation's OpenAPI operation, the SQL table of a storage, the AsyncAPI channel
    of an event, the security scheme of a scope or API key. It reads no source text.
  - `Pass`, `Contributions` (`ContributionKey`, one writer per key), `Emitter`, `GeneratedFile`
    and `Pipeline`: frontend, passes, emitters and files, deterministic. `Pipeline.fabrio` has no
    passes and no emitters yet.
- The safety net: the reference output of the current generator for the 3 example applications
  and the 32 valid test contracts of rialto-spec (822 files, with their inputs), under
  `fabrio-codegen/src/test/resources/reference`. `ReferenceOutputTest` compares every file the
  pipeline produces with it byte for byte and reports the coverage: 0 of 822 in slice 0.
  `tools/reference` regenerates it with the legacy generator.
- `docs/architecture.md`: the IR, passes, contributions, emitters and the safety net, and how a
  slice adds a pass.
- The portal skeleton: the landing page, getting started as a stub, and the releases.
- CI: build, tests and the style check on pull requests and on pushes to `develop` and `main`,
  the portal build, snapshots from `develop`, and the portal on GitHub Pages from `main`.
