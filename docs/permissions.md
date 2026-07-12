# 1MB-XRayHunter Permissions

## Permission Matrix

| Permission | Default | Purpose |
| --- | --- | --- |
| `xrayhunter.use` | `op` | Allows `lookup`, `detail`, `teleport`, and the time shortcut form. |
| `xrayhunter.admin` | `op` | Allows `debug`, `reload`, config edits, and whitelist management. Includes `xrayhunter.use` and `xhunt.use`. |
| `xhunt.use` | `op` | Legacy alias for `xrayhunter.use`. Also grants `xrayhunter.use`. |
| `xhunt.admin` | `op` | Legacy alias for `xrayhunter.admin`. Also grants `xhunt.use` and `xrayhunter.use`. |

## Practical Notes

- `/xrayhunter help` and `/xrayhunter info` are the least sensitive entry points and are intended as documentation surfaces.
- `teleport` is still protected by `xrayhunter.use` and only works in-game.
- `debug`, `reload`, `debug set`, and `debug whitelist` are admin-only.
- The plugin metadata declares all four permission nodes in `plugin.yml`.
