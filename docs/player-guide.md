# 1MB-XRayHunter Player Guide

## Introduction

1MB-XRayHunter is a staff-facing CoreProtect add-on for reviewing suspicious mining patterns on a Paper server. It turns CoreProtect block-break history into readable lookup tables, cached detail pages, and teleport targets so moderators can investigate possible X-ray abuse faster.

This plugin is meant for trusted staff, not for general survival gameplay. If your rank does not have the required permission nodes, you can still use `/xrayhunter info` to see what the plugin is for and where to find the documentation.

## How Players Use It

Staff usually follow this flow:

1. Run `/xrayhunter info` for a quick summary and the canonical docs link.
2. Run `/xrayhunter help` to see the command surface.
3. Start with a short lookup such as `/xrayhunter 2d` or `/xrayhunter lookup 7d wild`.
4. Review the ranked results table.
5. Run `/xrayhunter detail <index|player>` for cached vein details.
6. If the result was run in-game, use `/xrayhunter teleport <index>` to inspect a flagged location.
7. If a trusted long-term player keeps appearing in rankings, add them to the exclusion list with `/xrayhunter debug whitelist <player>`.

## Available Features

- Looks up suspicious mining activity in loaded worlds and CoreProtect database-only worlds.
- Supports short lookups, long historical lookups, and explicit all-world archive scans.
- Uses a compact high-value console view by default so large reports stay readable.
- Lets staff drill into cached vein detail pages after a lookup.
- Teleports in-game staff to a cached vein location when a safe spot is found nearby.
- Filters invalid CoreProtect pseudo-users such as `#piston` automatically.
- Supports an operator-maintained vetted-player whitelist through config and commands.

## Quick Start

Run these commands in order:

- `/xrayhunter info`
- `/xrayhunter help`
- `/xrayhunter 2d`
- `/xrayhunter detail 1`

Useful console examples:

- `/xrayhunter lookup 7d`
- `/xrayhunter lookup 30d wild`
- `/xrayhunter lookup alltime allworlds`
- `/xrayhunter lookup alltime allworlds -all`

## Commands

- `/xrayhunter info`
  Shows the plugin name, short introduction, useful starter commands, the canonical docs URL, and the installed version/build metadata.
- `/xrayhunter help`
  Shows the full command summary.
- `/xrayhunter lookup [time|alltime] [world|allworlds] [-all]`
  Runs a suspicious-mining lookup.
- `/xrayhunter <time|alltime>`
  Shortcut form for the same lookup command.
- `/xrayhunter detail <index|player> [page]`
  Shows cached vein details for one lookup result.
- `/xrayhunter teleport <index>`
  Teleports an in-game staff member to one cached vein location.
- `/xrayhunter debug`
  Shows build, status, cache, CoreProtect, and config details.
- `/xrayhunter debug config`
  Shows the currently loaded config values and tracked material lists.
- `/xrayhunter debug whitelist <player|list|add|remove>`
  Manages the vetted-player exclusion list.
- `/xrayhunter reload`
  Reloads `config.yml`.

## Permissions Or Rank Requirements

- `xrayhunter.use`
  Allows `lookup`, `detail`, `teleport`, and the time shortcut form.
- `xrayhunter.admin`
  Allows `debug`, `reload`, `debug set`, and `debug whitelist`.
- `xhunt.use`
  Legacy alias for `xrayhunter.use`.
- `xhunt.admin`
  Legacy alias for `xrayhunter.admin`.

All documented permission nodes default to `op`.

## Rewards, Costs, Limits, And Cooldowns

This plugin does not grant rewards, charge costs, or apply gameplay cooldowns.

It does enforce a few safety limits:

- Console all-world lookups can be disabled in config.
- Console implicit all-world lookups have a configurable maximum time window.
- Explicit `allworlds` lookups can still scan much larger archives when staff asks for them deliberately.
- Console output defaults to a compact high-value-only view unless `-all` is supplied.

## Placeholders

1MB-XRayHunter does not register PlaceholderAPI placeholders at this time.

## Important Notes

- `teleport` is in-game only.
- `detail` depends on a recent lookup cache. Run a lookup first.
- Manual config edits need `/xrayhunter reload` or a server restart.
- `/xrayhunter debug set ...` and `/xrayhunter debug whitelist ...` save and reload the config immediately.
- Deepslate ore variants are tracked and merged back into their base ore rows in summary output.
- `RAW_GOLD_BLOCK` is intentionally not tracked.

## Related Features

- Required integration: CoreProtect
- Supported runtime target: Paper
- Main verification targets for this branch: Paper `1.21.11` and Paper `26.1.2`
- Java target: `25`

## Technical Documentation

- Technical overview: [README.md](../README.md)
- Commands: [commands.md](commands.md)
- Permissions: [permissions.md](permissions.md)
- Configuration: [configuration.md](configuration.md)
- Installation: [installation.md](installation.md)
- Integrations: [integrations.md](integrations.md)
- Troubleshooting: [troubleshooting.md](troubleshooting.md)
- Canonical docs URL: <https://docs.1moreblock.com/custom-server-plugins/1mb-xrayhunter/>
