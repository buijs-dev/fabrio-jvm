---
title: "Getting started"
description: "A Fabrio service from a Rialto contract"
---

A Fabrio service starts from a Rialto contract: the types, the operations, the mappings, the
storage and the sources (OpenAPI, AsyncAPI, SQL migrations) the contract refers to. Fabrio is a
[Rialto generator](https://rialto.buijs.dev/): it generates what lies between the HTTP request and
the database, and you write the rules and the service.

This page grows with the slices. The first profile that generates code, domain and mapping
(slice 1), adds the build setup; until then, Fabrio JVM has no published generator or plugin.

## Repositories and versions

Every Fabrio module has the same version. Import the BOM and leave the versions of the modules
out. Snapshots (`-SNAPSHOT`) are on Repsy:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.repsy.io/buijs-dev/maven2") // snapshots only
    }
}
```

```kotlin
// build.gradle.kts
dependencies {
    implementation(platform("dev.buijs.fabrio:fabrio-bom:1.0.0-SNAPSHOT"))
}
```
