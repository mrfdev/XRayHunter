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

## Server Platform

- Required platform family: Paper
- Compile target: Paper API `26.1.2`
- Declared plugin API floor: `1.21.11`
- Intended same-jar support: Paper `1.21.11` and Paper `26.1.2`
- Java target: `25`

## World And Archive Scope

The plugin can query:

- loaded Bukkit worlds
- CoreProtect database-only worlds that are not currently loaded
- explicit all-world archive scopes

Large archive behavior:

- uses batched aggregate lookups for wide scans
- reuses a temporary in-memory summary cache for repeated archive queries
- loads player detail data lazily instead of keeping every event from the first pass

## Other Integrations

### PlaceholderAPI

Not currently supported. The plugin does not register placeholders.

### Economy, Chat, GUI, And Reward Plugins

Not used directly by this project in the current codebase.
