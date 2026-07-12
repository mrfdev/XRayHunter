# 1MB-XRayHunter Commands

## Command Summary

Main command:

- `/xrayhunter`

Aliases:

- `/xhunt`
- `/xr`

Subcommand aliases:

- `lookup` also accepts `l`
- `detail` also accepts `d`
- `teleport` also accepts `tp`
- `debug whitelist` also accepts `wl`
- `debug whitelist remove` also accepts `delete` and `del`

## Public-Facing Commands

### `/xrayhunter info`

Shows:

- the player-facing plugin name
- a short introduction
- quick-start commands
- the canonical docs link
- installed version/build metadata

This command is available even when the sender does not have lookup permissions.

### `/xrayhunter help`

Shows the command summary and important build/runtime notes such as:

- default lookup time
- declared plugin API floor
- targeted CoreProtect version
- console all-world safety limit
- known CoreProtect world count

`/xrayhunter ?` works as a help alias.

## Staff Lookup Commands

### `/xrayhunter lookup [time|alltime] [world|allworlds] [-all]`

Runs a suspicious-mining lookup against CoreProtect data.

Arguments:

- `time`
  Duration such as `30m`, `12h`, `2d`, `7d`, `30d`, `365d`
- `alltime`
  Explicit full-archive lookup
- `world`
  Loaded Bukkit world or a CoreProtect database world name
- `allworlds`
  Explicit all-world archive scope
- `-all`
  Expands the lookup back to the full tracked-material set for that one run

Examples:

- `/xrayhunter 2d`
- `/xrayhunter lookup 7d`
- `/xrayhunter lookup 30d wild`
- `/xrayhunter lookup 30d wild -all`
- `/xrayhunter lookup alltime allworlds`

Console behavior:

- Implicit console all-world lookups are allowed only when `console.allow-server-wide-lookups` is true.
- Implicit console all-world lookups are capped by `console.max-all-world-lookup-time`.
- Explicit `allworlds` scans bypass that implicit cap.
- Compact console mode uses the high-value material set unless `-all` is supplied.

### `/xrayhunter <time|alltime>`

Shortcut form that behaves the same as `lookup`.

Examples:

- `/xrayhunter 7d`
- `/xrayhunter alltime`

### `/xrayhunter detail <index|player> [page]`

Shows cached vein details for a lookup result.

Notes:

- Run a lookup first so the cache exists.
- You can address a result by numeric index or by player name.
- Detail output is paginated using `display.detail-page-size`.

Examples:

- `/xrayhunter detail 1`
- `/xrayhunter detail BeardedAdventur`
- `/xrayhunter detail JahLion 2`

### `/xrayhunter teleport <index>`

Teleports an in-game sender to a cached vein location.

Notes:

- In-game only
- Depends on a cached lookup result
- Uses a safe-location search around the recorded vein point

Example:

- `/xrayhunter teleport 2`

## Administrative Commands

### `/xrayhunter debug`

Shows:

- plugin/build metadata
- running Java and server version
- CoreProtect hook status and API version
- config path and data path
- latest tracked block timestamp
- cache counters
- loaded settings

### `/xrayhunter debug help`

Lists the available debug pages.

### `/xrayhunter debug permissions`

Lists the permission nodes and legacy aliases.

### `/xrayhunter debug commands`

Lists the command surface and built-in examples.

### `/xrayhunter debug config`

Prints the active config values and tracked material lists exactly as the plugin currently resolved them.

### `/xrayhunter debug set <key> <value>`

Saves one supported config value and reloads the plugin configuration immediately.

Supported keys:

- `startup.self-check-enabled`
- `defaults.lookup-time`
- `display.top-results`
- `display.detail-page-size`
- `console.allow-server-wide-lookups`
- `console.high-value-only`
- `console.max-all-world-lookup-time`

Examples:

- `/xrayhunter debug set defaults.lookup-time 7d`
- `/xrayhunter debug set display.top-results 15`
- `/xrayhunter debug set console.high-value-only true`

### `/xrayhunter debug whitelist <player>`

Adds a vetted player to `filters.excluded-players` and reloads the config.

### `/xrayhunter debug whitelist list`

Shows the current vetted-player exclusion list.

### `/xrayhunter debug whitelist add <player>`

Explicit add form for the whitelist.

### `/xrayhunter debug whitelist remove <player>`

Removes a player from `filters.excluded-players` and reloads the config.

Whitelist notes:

- Names are normalized to lowercase.
- Names with spaces are rejected.
- Names starting with `#` are rejected.
- CoreProtect pseudo-users such as `#piston` are already filtered automatically and do not need to be listed.

### `/xrayhunter reload`

Reloads `plugins/1MB-XRayHunter/config.yml`.

## Permissions

- `xrayhunter.use` is required for lookup, detail, and teleport.
- `xrayhunter.admin` is required for debug, reload, config edits, and whitelist management.

See [permissions.md](permissions.md) for the detailed permission matrix.
