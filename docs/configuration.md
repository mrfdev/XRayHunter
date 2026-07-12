# 1MB-XRayHunter Configuration

The plugin stores its runtime settings in `plugins/1MB-XRayHunter/config.yml`.

The bundled `config.yml` already includes comment-preserving inline guidance for every setting. Manual edits are preserved, missing defaults are added safely, and existing comments are meant to survive reloads, restarts, and command-driven config edits.

## Reload Behavior

- Manual file edits take effect after `/xrayhunter reload` or the next server start.
- `/xrayhunter debug set ...` saves and reloads immediately.
- `/xrayhunter debug whitelist ...` saves and reloads immediately.
- Normal startup/reload config I/O is lightweight and happens during enable/reload work, not continuously during gameplay.

## Settings

### `startup.self-check-enabled`

- Default: `true`
- Type: boolean
- Controls whether the plugin prints a short self-check summary when it enables.
- Safe values: `true`, `false`
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set startup.self-check-enabled <true|false>`

### `defaults.lookup-time`

- Default: `2d`
- Type: duration string
- Controls the default lookup window used when a command does not provide its own time argument.
- Expected format: values like `30m`, `12h`, `2d`, `7d`, `30d`, `365d`
- Safe values: keep the default small on very large archives if you want fast staff lookups
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set defaults.lookup-time <duration>`

### `display.top-results`

- Default: `10`
- Type: positive integer
- Controls how many player rows are shown in the first lookup table.
- Safe values: `5` to `20` is typical
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set display.top-results <number>`

### `display.detail-page-size`

- Default: `10`
- Type: positive integer
- Controls how many vein rows are shown on each detail page.
- Safe values: `5` to `20` is typical
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set display.detail-page-size <number>`

### `console.allow-server-wide-lookups`

- Default: `true`
- Type: boolean
- Controls whether the console may run lookups without naming a world.
- When false, console users must provide a world name and cannot use implicit all-world lookups.
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set console.allow-server-wide-lookups <true|false>`

### `console.high-value-only`

- Default: `true`
- Type: boolean
- Controls whether console lookups default to the narrower high-value-only query and table view.
- When true, add `-all` to a lookup command when you want the broader tracked-material set for that one run.
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set console.high-value-only <true|false>`

### `console.max-all-world-lookup-time`

- Default: `30d`
- Type: duration string
- Controls the maximum implicit all-world console time window when no world argument is supplied.
- Explicit `allworlds` scans are still possible for deliberate archive work.
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug set console.max-all-world-lookup-time <duration>`

### `console.high-value-display-materials`

- Default values:
  - `ancient_debris`
  - `diamond_ore`
  - `emerald_ore`
  - `gold_ore`
  - `gilded_blackstone`
  - `nether_gold_ore`
  - `lapis_ore`
  - `redstone_ore`
- Type: list of Bukkit/Paper material names
- Controls the curated high-value material set used by compact console lookups and compact console tables.
- Invalid material names are skipped with a warning.
- If every configured value is invalid, the plugin falls back to the bundled defaults.
- Reload behavior: reload or restart required after manual edits
- Command edit: no direct list editor yet

### `filters.excluded-players`

- Default: `[]`
- Type: list of player names
- Controls which vetted usernames are excluded from ranking output.
- Use this for staff accounts or long-term vetted players that should not keep appearing in reports.
- Names are normalized to lowercase.
- Pseudo-users beginning with `#` are filtered automatically and do not need to be added here.
- Reload behavior: reload or restart required after manual edits
- Command edit: `/xrayhunter debug whitelist <player|list|add|remove>`

## Tracking Lists

### `tracking.overworld.lookup-materials`

- Type: list of material names
- Controls which overworld materials are queried from CoreProtect.
- Default tracked families include:
  - diamond ore and deepslate diamond ore
  - emerald ore and deepslate emerald ore
  - gold ore and deepslate gold ore
  - iron ore and deepslate iron ore
  - `raw_iron_block`
  - copper ore and deepslate copper ore
  - `raw_copper_block`
  - lapis ore and deepslate lapis ore
  - redstone ore and deepslate redstone ore
  - coal ore and deepslate coal ore
  - `stone`
  - `deepslate`
- `raw_gold_block` is intentionally not tracked.
- Invalid values are skipped with a warning.

### `tracking.overworld.display-materials`

- Type: list of material names
- Controls the displayed overworld summary column order after normalization.
- Default display columns:
  - `diamond_ore`
  - `emerald_ore`
  - `gold_ore`
  - `iron_ore`
  - `raw_iron_block`
  - `copper_ore`
  - `raw_copper_block`
  - `lapis_ore`
  - `redstone_ore`
  - `coal_ore`
  - `stone`

### `tracking.nether.lookup-materials`

- Type: list of material names
- Controls which nether materials are queried from CoreProtect.
- Default tracked materials:
  - `ancient_debris`
  - `gilded_blackstone`
  - `nether_gold_ore`
  - `nether_quartz_ore`
  - `netherrack`

### `tracking.nether.display-materials`

- Type: list of material names
- Controls the displayed nether summary column order.
- The default display order matches the default nether lookup list.

## Normalization Notes

- Deepslate ore variants are tracked separately in the lookup lists.
- Summary output merges deepslate ore variants back into their base ore rows.
- Base blocks such as `stone`, `deepslate`, and `netherrack` help produce ratio context in the tables.
