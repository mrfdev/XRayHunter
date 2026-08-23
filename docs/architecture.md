# 1MB-XRayHunter Architecture

The code is organized as a feature that currently runs inside a small standalone Paper host. This keeps the present jar independently deployable while making the XRayHunter capability portable into the planned unified CoreProtect add-on.

```text
plugin.yml
  -> XRayHunterPlugin                 standalone Paper host
       +-> XRayHunterFeature          feature lifecycle and owned state
       |    +-> InvestigationSessionRegistry
       |    +-> MiningHistory         authoritative-history boundary
       |         -> CoreProtectLookupService
       +-> MainCommand -> feature     command routing
```

## Ownership Boundaries

`com.onemb.cmiapi.xrayhunter` is the stable feature package. It must remain independent of the eventual combined plugin's product name so the module does not need another package migration when that host is chosen.

`XRayHunterPlugin` is the current standalone entry point. It constructs and starts the feature, registers the existing command, and delegates shutdown. It does not own lookup state.

`XRayHunterFeature` owns one runtime instance's settings, investigation sessions, lookup cache, CoreProtect hook, background worker, and lifecycle. None of that state is static, so stopping this feature cannot clear a future sibling add-on's state. Its stable feature id is `xrayhunter`.

`MiningHistory` is the feature-facing read boundary. Commands and investigation models depend on its lookup results, not CoreProtect SQL classes. `CoreProtectLookupService` is the current implementation and is the only package allowed to depend on CoreProtect database internals. A combined host can supply another implementation through `MiningHistoryFactory` without changing commands or models.

`XRayHunterFeatureLayout` separates bundled resource paths from the installed configuration target. Defaults and build metadata live under `xrayhunter/` inside the jar, while standalone deployment still reads and writes `plugins/1MB-XRayHunter/config.yml`. This avoids root-resource collisions when sibling add-ons enter the same jar.

Feature utilities live under `com.onemb.cmiapi.xrayhunter.util`; they are not presented as a shared library contract. Utilities should move to a common host library only when at least two merged features have the same semantic need.

## Threading Boundary

CoreProtect SQL work runs on the feature-owned single-thread executor. Results return to the Paper main thread before commands access sender, player, world, or teleport state. Vein grouping uses only recorded coordinates off-thread and resolves Bukkit worlds only after the callback returns to the main thread. Tab completion and debug output read cached CoreProtect metadata and can request an asynchronous refresh, rather than querying the database on the main thread.

Each start/stop cycle has a generation identity. A database task from a stopped generation cannot cache a result or deliver a callback after the feature starts again. Shutdown requests worker interruption and clears feature-owned sessions and caches without affecting sibling modules. A JDBC statement that does not honor interruption may finish in the retired daemon worker, but its generation can no longer publish state.

## Compatibility Contract

The package refactor does not change these installed-server identities:

| Identity | Preserved value |
| --- | --- |
| Paper plugin name | `1MB-XRayHunter` |
| Data folder | `plugins/1MB-XRayHunter/` |
| Main command | `/xrayhunter` |
| Command aliases | `/xhunt`, `/xr` |
| Permissions | `xrayhunter.*`, legacy `xhunt.*` |
| Configuration file and keys | `config.yml`, unchanged schema |
| Log prefix | `XRayHunter` |
| Jar naming | `1MB-XRayHunter-v<version>-<build>-j25-26.2.jar` |

Java classes were not a documented public API, and the old package was not used for serialization, service registration, reflection, or persisted namespaced data. The namespace change therefore has no installed-data migration step.

## Consolidation Path

When the unified CoreProtect add-on is implemented:

1. Keep the XRayHunter sources in `com.onemb.cmiapi.xrayhunter`; give the combined Paper host its own package and lifecycle.
2. Have that host create and start one `XRayHunterFeature`, then route the preserved `/xrayhunter` command to `MainCommand`. Use `MiningHistoryFactory` if the host supplies a shared CoreProtect history integration.
3. Choose the combined configuration layout through `XRayHunterFeatureLayout`. Import or continue using `plugins/1MB-XRayHunter/config.yml` before removing standalone deployment, rather than silently starting with defaults.
4. Preserve the command aliases and permission nodes during at least the initial consolidated release. Treat any later identity cutover as a separate compatibility decision.
5. Keep direct CoreProtect SQL behind `MiningHistory`; do not let sibling features reach into XRayHunter commands, sessions, or caches.
6. Stop and clear XRayHunter independently from sibling modules during reload or disable.
7. Reject startup or provide an operator-visible migration error if both standalone XRayHunter and the combined implementation are present. They must never process the same command and configuration concurrently.

The future host name, whether it adopts the 1MB-Library `FeatureModule` API, and how multiple CoreProtect add-ons share configuration are intentionally deferred. Those choices are not needed for this repository to expose a stable feature boundary today.
