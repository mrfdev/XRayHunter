# 1MB-XRayHunter Integrations

## Required Integration

### CoreProtect

1MB-XRayHunter is a CoreProtect add-on. It does not maintain its own mining history database. Instead, it reads CoreProtect block-break history and uses that data to produce suspicious-mining rankings and cached detail pages.

Verified integration surface in this branch:

- `CoreProtect.getAPI()`
- `CoreProtectAPI.APIVersion()`
- direct CoreProtect database lookups used by the plugin
- lazy player detail loading against the CoreProtect database

Maintained compatibility target:

- CoreProtect `24.0-dev1`
- CoreProtect API `12`

Runtime notes:

- The plugin also keeps a broader runtime acceptance gate for some older CoreProtect API values.
- The documented support target for this maintained branch is still API `11` or `12`.
- Direct CoreProtect database access is isolated in `com.onemb.cmiapi.xrayhunter.coreprotect` behind the feature-owned `MiningHistory` boundary.
- Database lookups and metadata refreshes run on the feature worker; command callbacks return to the Paper main thread before accessing Bukkit state.

### Planned Unified CoreProtect Add-on

XRayHunter remains a standalone plugin today. Its runtime is separated from the standalone Paper entry point so the feature package can later be hosted by the planned unified 1MB CoreProtect add-on.

There is intentionally no current runtime dependency on 1MB-Library or another 1MB feature plugin. The combined host identity, shared configuration layout, and installation migration must be decided as part of consolidation. The current compatibility contract and migration checklist are documented in [architecture.md](architecture.md).

## Server Platform

- Required platform family: Paper
- Server/API target: Paper `26.2`
- Compile version: Paper API `26.2.build.84-stable`
- Release channel: `STABLE`
- Declared plugin API version: `26.2`
- Java target: `25`

## World And Archive Scope

The plugin can query:

- loaded Bukkit worlds
- CoreProtect database-only worlds that are not currently loaded
- explicit all-world archive scopes

Large archive behavior:

- uses batched aggregate queries for wide lookups
- reuses a temporary in-memory summary cache for repeated archive queries
- loads player detail data lazily instead of keeping every event from the first pass

## Other Integrations

### PlaceholderAPI

Not currently supported. The plugin does not register placeholders.

### Economy, Chat, GUI, And Reward Plugins

Not used directly by this project in the current codebase.
