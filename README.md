# 1MB-XRayHunter

1MB-XRayHunter is a standalone 1MoreBlock Paper plugin and CoreProtect add-on for reviewing suspicious mining patterns. It turns CoreProtect block-break history into ranked lookup reports, cached vein detail pages, and in-game teleport targets so staff can investigate potential X-ray abuse without digging through raw database data.

This repository is the technical source of truth for the plugin. Public-safe player and admin guides live under [`docs/`](docs/), and the canonical published page is intended to be:

- <https://docs.1moreblock.com/custom-server-plugins/1mb-xrayhunter/>

## At A Glance

- Main command: `/xrayhunter`
- Public info entry point: `/xrayhunter info`
- Paper compile target: `26.1.2`
- Declared plugin compatibility floor: `1.21.11`
- Java target: `25`
- Required dependency: CoreProtect
- Maintained CoreProtect target: `24.0-dev1` with API `12`
- Plugin data folder: `plugins/1MB-XRayHunter/`
- Build output folder: `build/libs/`

## Features

- Suspicious-mining lookups across loaded worlds and CoreProtect database-only worlds
- Explicit all-world archive scans for staff review sessions
- Compact console reporting with a high-value-only mode enabled by default
- Lazy player detail loading so large archive lookups do not keep every tracked event in memory
- Cached vein detail pages and safe in-game teleport targets
- Automatic filtering for invalid CoreProtect pseudo-users such as `#piston`
- Operator-managed vetted-player exclusions through config and `/xrayhunter debug whitelist ...`
- Startup self-check logging that reports build/runtime/CoreProtect status

## Documentation

- [Player Guide](docs/player-guide.md)
- [Commands](docs/commands.md)
- [Permissions](docs/permissions.md)
- [Placeholders](docs/placeholders.md)
- [Configuration](docs/configuration.md)
- [Installation](docs/installation.md)
- [Integrations](docs/integrations.md)
- [Troubleshooting](docs/troubleshooting.md)
- [Docs Manifest](docs/plugin-docs.yml)

## Commands

Primary commands:

- `/xrayhunter info`
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

The plugin declares four permission nodes, all defaulting to `op`:

- `xrayhunter.use`
- `xrayhunter.admin`
- `xhunt.use`
- `xhunt.admin`

See [docs/permissions.md](docs/permissions.md) for the detailed matrix and inherited alias behavior.

## Configuration Overview

`config.yml` currently covers:

- startup self-check behavior
- default lookup window
- top result and detail page sizing
- console all-world safety limits
- compact high-value-only console mode
- vetted-player exclusions
- overworld and nether tracking lists

Important config notes:

- command-driven config edits are currently limited to the scalar keys exposed by `/xrayhunter debug set`
- whitelist management writes directly to `filters.excluded-players`
- deepslate ore variants are tracked in lookup-material lists and normalized back into their base ore rows for summary display
- `raw_iron_block` and `raw_copper_block` are tracked
- `raw_gold_block` is intentionally not tracked

See [docs/configuration.md](docs/configuration.md) for the full setting-by-setting reference.

## Compatibility And Integrations

This branch is intended to load the same jar on:

- Paper `1.21.11`
- Paper `26.1.2`

Build/runtime metadata:

- compiled against Paper API `26.1.2`
- declares plugin.yml `api-version: 1.21.11`
- targets Java `25`

CoreProtect notes:

- the maintained integration target is CoreProtect `24.0-dev1`
- the maintained API target is CoreProtect API `12`
- the startup guidance for this branch expects CoreProtect API `11` or `12`
- the runtime gate still accepts some older API values internally, but older CoreProtect versions are not the documented target for this maintained branch

This plugin does not currently register PlaceholderAPI placeholders.

## Building

Build the plugin with Gradle:

```bash
./gradlew build
```

Optional clean rebuild:

```bash
./gradlew clean build
```

Artifact naming pattern:

```text
build/libs/1MB-XRayHunter-v<plugin-version>-<build-number>-j25-26.1.2.jar
```

Build behavior:

- each successful jar build increments `version.properties`
- each successful jar build writes a new jar into `build/libs/`
- older jars remain unless you run `clean`

## Installation

1. Install CoreProtect on a Paper server.
2. Start the server once so CoreProtect initializes.
3. Stop the server cleanly.
4. Copy the XRayHunter jar into `plugins/`.
5. Start the server again.
6. Confirm `/xrayhunter info`, `/xrayhunter help`, and `/xrayhunter debug` work.
7. Review `plugins/1MB-XRayHunter/config.yml`.

## Limitations And Operational Notes

- `teleport` is in-game only.
- `detail` depends on a recent lookup cache.
- implicit console all-world lookups are capped by config for safety.
- explicit `allworlds` scans are supported for large archive reviews.
- compact console mode narrows both the displayed columns and the query scope unless `-all` is supplied.

## Credits

- Original XRayHunter author: [R4zorax](https://github.com/rlf)
- 1MoreBlock maintenance, compatibility work, packaging, and documentation: [mrfloris](https://github.com/mrfloris)
- Thanks to repository contributors and OpenAI for development assistance
