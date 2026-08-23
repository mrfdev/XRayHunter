---
status: accepted
---

# Preserve XRayHunter identity while preparing a reusable CoreProtect feature

The code-owned namespace is `com.onemb.cmiapi.xrayhunter`, with Gradle group `com.onemb.cmiapi`, because XRayHunter is a stable feature in the existing 1MB Java namespace and may later move into a unified CoreProtect add-on. The standalone `XRayHunterPlugin` owns only the Paper lifecycle and command registration; `XRayHunterFeature` owns configuration, investigation sessions, caches, worker resources, and the `MiningHistory` boundary. CoreProtect schema access remains inside `coreprotect`, feature resources live below `xrayhunter/` in the jar, and layout plus history factories provide the future composition seams. This allows another host to reuse the feature package without carrying a second plugin lifecycle, colliding with sibling root resources, or exposing CoreProtect database internals to commands and models.

## Considered Options

- Keeping `dk.lockfuglsang` would preserve an implementation namespace that no longer represents project ownership and would defer the same disruptive move until consolidation.
- Using a package such as `com.onemb.cmiapi.coreprotectaddons.xrayhunter` would make an unresolved future host identity part of the feature's permanent identity.
- Adding a 1MB-Library runtime dependency now would change standalone deployment without being required to establish a merge-ready boundary.

## Consequences

The internal package move is intentionally separate from server-facing compatibility identities. Paper name `1MB-XRayHunter`, plugin data folder `plugins/1MB-XRayHunter`, `/xrayhunter` and its aliases, `xrayhunter.*` and `xhunt.*` permissions, configuration keys, documentation URL, log prefix, and release artifact naming remain unchanged. The unified add-on's name, module framework, shared configuration layout, and deployment migration remain separate decisions. A future merger should import or deliberately migrate the existing data folder and must not load the standalone and consolidated implementations together.
