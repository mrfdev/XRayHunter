# 1MB-XRayHunter Troubleshooting

## Plugin Does Not Enable

Check these first:

- CoreProtect is installed
- CoreProtect enabled before XRayHunter
- Java `25` is being used
- the server is Paper, not a different fork or platform

Typical cause:

- CoreProtect was missing or did not expose a supported API version

What to verify:

- startup log lines for `CoreProtect hooked`
- `/xrayhunter debug`

## `/xrayhunter lookup` Returns No Results

Possible reasons:

- the requested window is newer than the latest tracked CoreProtect block data
- that world has no tracked activity in the requested time range
- the sender expected a large archive scan but only ran a short window

Helpful follow-up:

- run `/xrayhunter debug`
- run `/xrayhunter debug config`
- try a larger window such as `/xrayhunter lookup 30d`
- try an explicit archive scan such as `/xrayhunter lookup alltime allworlds`

## Console Says The Lookup Is Too Large

That means an implicit all-world console query exceeded `console.max-all-world-lookup-time`.

Options:

- run the lookup against one world
- lower the window
- use an explicit archive command such as `/xrayhunter lookup alltime allworlds`
- increase the console limit in config if that is safe for your archive size

## World Not Found

The plugin can only query:

- loaded Bukkit worlds
- CoreProtect database world names

If a world name is rejected:

- verify the spelling
- confirm the world exists in CoreProtect
- use `/xrayhunter help` and tab completion to see suggested names

## `/xrayhunter detail` Cannot Find A Player Or Index

`detail` works from a recent lookup cache.

Do this:

1. run a lookup first
2. use the visible player index or exact cached player name
3. try the next page number if the output spans multiple pages

## `/xrayhunter teleport` Does Not Work

Common reasons:

- the command was run from console
- there was no cached lookup result
- no safe teleport location was found near the selected vein

## Trusted Players Keep Showing Up In Results

Use the vetted-player exclusion list:

- `/xrayhunter debug whitelist <player>`
- `/xrayhunter debug whitelist list`
- `/xrayhunter debug whitelist remove <player>`

Notes:

- names are normalized to lowercase
- pseudo-users such as `#piston` are ignored automatically

## Large Archive Performance

Helpful practices:

- use the compact high-value-only console mode for broad scans
- add `-all` only when you need the wider material context
- prefer world-specific lookups first
- reserve `alltime allworlds` for deliberate review sessions

If you are diagnosing performance:

- run `/xrayhunter debug`
- note the summary cache entries, hits, and misses
- verify the latest tracked block timestamp
