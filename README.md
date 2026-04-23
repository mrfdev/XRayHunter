# 1MB-XRayHunter

## Introduction

1MB-XRayHunter is a lightweight CoreProtect add-on for Paper servers that helps staff spot suspicious mining patterns quickly.
It reads CoreProtect block-break history, ranks the most suspicious miners in the current world, and lets staff drill into cached vein details and teleport to those finds for manual review.

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
- `/xrayhunter lookup [time] [world]`
- `/xrayhunter <time>`
- `/xrayhunter detail <index|player> [page]`
- `/xrayhunter teleport <index>`
- `/xrayhunter debug`
- `/xrayhunter debug help`
- `/xrayhunter debug permissions`
- `/xrayhunter debug commands`
- `/xrayhunter debug config`
- `/xrayhunter debug set <key> <value>`
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
- `/xrayhunter lookup 1000d spawn` from console for a long single-world text report
- `/xrayhunter detail 1`
- `/xrayhunter detail Greymagic27 2`
- `/xrayhunter teleport 3`
- `/xrayhunter debug`
- `/xrayhunter debug config`
- `/xrayhunter debug set defaults.lookup-time 7d`
- `/xrayhunter debug set display.top-results 15`

## Config Notes

`config.yml` currently includes:

- startup self-check toggle
- default lookup time
- top result count
- detail page size
- console all-world lookup safety settings
- tracked overworld and nether material lists

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
- With the current version file, the next successful jar is expected to be:
  `build/libs/1MB-XRayHunter-v2.0.0-012-j25-26.1.2.jar`
- When the local test server exists, Gradle compiles against `servers/Server-Two-Paper-26.1.2/plugins/CoreProtect-23.4b.jar` so the build matches the runtime CoreProtect jar exactly.
- The local `servers/` folder is only for test servers and is ignored by Git.

## Credits

- Original XRayHunter author: [R4zorax](https://github.com/rlf)
- 1MoreBlock maintenance, compatibility updates, packaging, and testing: [mrfloris](https://github.com/mrfloris)
- Thanks to the contributors in this repository history and to OpenAI for development assistance
