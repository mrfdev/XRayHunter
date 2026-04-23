# 1MB-XRayHunter

## Introduction

1MB-XRayHunter is a lightweight CoreProtect add-on for Paper servers that helps staff spot suspicious mining patterns quickly.
It reads CoreProtect block-break history, ranks the most suspicious miners in the current world or any archived CoreProtect world, and lets staff drill into cached vein details and teleport to those finds for manual review.
Recent lookup work is aimed at large archives too: wide console lookups now use batched aggregate queries, a temporary in-memory summary cache, and lazy detail loading so huge CoreProtect databases are less likely to exhaust heap space.

This repo is now aligned with the other 1MoreBlock plugins:

- Java target: `25`
- Paper target: `26.1.2`
- Bukkit `api-version`: `1.21.11`
- CoreProtect target: `23.4` with API version `11`
- Plugin data folder: `plugins/1MB-XRayHunter/`
- Build output folder: `build/libs/`

## Requirements

- Paper `26.1.2`
- Java `25`
- CoreProtect `23.4`

## Commands

- `/xrayhunter help`
- `/xrayhunter lookup [time|alltime] [world|allworlds] [-all]`
- `/xrayhunter <time|alltime>`
- `/xrayhunter detail <index|player> [page]`
- `/xrayhunter teleport <index>`
- `/xrayhunter debug`
- `/xrayhunter debug help`
- `/xrayhunter debug permissions`
- `/xrayhunter debug commands`
- `/xrayhunter debug config`
- `/xrayhunter debug set <key> <value>`
- `/xrayhunter debug whitelist <player|list|add|remove>`
- `/xrayhunter reload`

Aliases:

- `/xhunt`
- `/xr`

## Permissions

- `xrayhunter.use`: allows lookup, detail, and teleport.
- `xrayhunter.admin`: allows debug pages, config updates, and reload.
- `xhunt.use`: legacy alias for `xrayhunter.use`.
- `xhunt.admin`: legacy alias for `xrayhunter.admin`.

## Placeholders

This plugin does not register PlaceholderAPI placeholders yet.

## Command Examples

- `/xrayhunter 2d`
- `/xrayhunter lookup 30d`
- `/xrayhunter lookup 7d` from console for an all-world text report within the safe limit
- `/xrayhunter lookup 7d -all` from console to widen one lookup back to the full tracked column set and include lower-value/base materials in the query too
- `/xrayhunter lookup alltime allworlds` from console for an explicit full-archive scan across every CoreProtect world
- `/xrayhunter lookup 365d allworlds` from console for a deliberately large batched archive scan
- `/xrayhunter lookup 120d wild` from console for a database-world lookup that returns real historical results on the current 65 GB archive
- `/xrayhunter lookup 120d wild -all` from console for the full-width version of that database-world report
- `/xrayhunter lookup 1000d spawn` from console for a long single-world text report
- `/xrayhunter detail 1`
- `/xrayhunter detail Greymagic27 2`
- `/xrayhunter teleport 3`
- `/xrayhunter debug`
- `/xrayhunter debug config`
- `/xrayhunter debug set defaults.lookup-time 7d`
- `/xrayhunter debug set display.top-results 15`
- `/xrayhunter debug whitelist fumblehead`
- `/xrayhunter debug whitelist list`
- `/xrayhunter debug whitelist remove fumblehead`

## Config Notes

`config.yml` currently includes:

- startup self-check toggle
- default lookup time
- top result count
- detail page size
- console all-world lookup safety settings
- compact high-value-only console mode, enabled by default
- excluded-player filter list for vetted usernames that should never show in rankings
- `/xrayhunter debug whitelist ...` helpers to manage that excluded-player list without editing the file by hand
- automatic filtering of invalid CoreProtect pseudo-users such as `#piston`
- stale archive hints when the database is older than the requested lookup window
- batched aggregate lookups plus a temporary summary cache for large archive scans
- tracked overworld and nether material lists

Lookup notes:

- `/xrayhunter lookup [time|alltime] [world|allworlds]` can query loaded Bukkit worlds and CoreProtect database worlds that are not currently loaded on the server.
- Console lookups default to a narrower high-value-only table so smaller terminals stay readable; add `-all` to a lookup to show every tracked display column for that one run.
- Compact console mode now narrows the lookup scope too, not just the rendered table, so archive scans can stay fast on large CoreProtect databases.
- Compact-mode `ORE%` is now calculated against broader tracked totals for the shown players, while the `Shown` column keeps representing the visible high-value subtotal.
- Explicit `allworlds` scans use batched aggregate queries and can reuse a temporary in-memory summary cache when the same query is repeated soon after.
- `/xrayhunter detail` now loads one selected player's vein data lazily instead of caching every event from the original lookup.
- If a short lookup returns no data, XRayHunter now shows the latest tracked block timestamp and suggests a larger window.
- Console lookup tables use a compact pastel report with ore columns, `ORE%`, `BASE`, and either `Total` or `Shown` so large historical worlds stay readable.
- Use `-all` when you want the broader tracked-material picture, including lower-value ores, base-material counts, and the more traditional ratio context.

Tracked materials intentionally include:

- `ANCIENT_DEBRIS`
- `GILDED_BLACKSTONE`
- `NETHER_GOLD_ORE`
- `NETHER_QUARTZ_ORE`
- `DIAMOND_ORE`
- `EMERALD_ORE`
- `GOLD_ORE`
- `IRON_ORE`
- `RAW_IRON_BLOCK`
- `COPPER_ORE`
- `RAW_COPPER_BLOCK`
- `LAPIS_ORE`
- `REDSTONE_ORE`
- `COAL_ORE`
- `STONE`
- `DEEPSLATE`
- `NETHERRACK`

`RAW_GOLD_BLOCK` is intentionally not tracked.

## Build

Build with Gradle:

```bash
./gradlew build
```

Notes:

- Successful jar builds increment `version.properties`.
- Each successful build produces a new uniquely named jar in `build/libs/`, so older jars stay there unless you run `clean`.
- The latest verified local build from this repo pass is:
  `build/libs/1MB-XRayHunter-v2.0.0-029-j25-26.1.2.jar`
- The next successful build will increment from build `029`.
- When the local test server exists, Gradle compiles against `servers/Server-Two-Paper-26.1.2/plugins/CoreProtect-23.4b.jar` so the build matches the runtime CoreProtect jar exactly.
- The local `servers/` folder is only for test servers and is ignored by Git.

## Credits

- Original XRayHunter author: [R4zorax](https://github.com/rlf)
- 1MoreBlock maintenance, compatibility updates, packaging, and testing: [mrfloris](https://github.com/mrfloris)
- Thanks to the contributors in this repository history and to OpenAI for development assistance
