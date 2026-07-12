# 1MB-XRayHunter Installation

## Requirements

- Paper server
- Java `25`
- CoreProtect installed and enabled

Verified branch targets:

- Paper `1.21.11`
- Paper `26.1.2`

Compile target:

- Paper API `26.1.2`

Declared plugin compatibility floor:

- `api-version: 1.21.11`

CoreProtect target:

- CoreProtect `24.0-dev1`
- CoreProtect API `12`

Notes:

- The current maintained support target is CoreProtect API `11` or `12`.
- The runtime hook still accepts some older API numbers internally, but those older versions are not the documented target for this branch.

## Installing The Release Jar

1. Install CoreProtect and start the server once so its data folder is created.
2. Stop the server cleanly.
3. Copy the XRayHunter jar into the server `plugins/` folder.
4. Start the server.
5. Confirm the plugin enables and reports that CoreProtect hooked successfully.
6. Review `plugins/1MB-XRayHunter/config.yml`.
7. Run `/xrayhunter info` and `/xrayhunter help`.

## Updating An Existing Installation

1. Stop the server cleanly.
2. Replace the old XRayHunter jar in `plugins/`.
3. Start the server again.
4. Review the startup self-check and `config.yml`.
5. Run `/xrayhunter debug` to confirm the build metadata and CoreProtect hook status.

The plugin keeps its own data folder as:

- `plugins/1MB-XRayHunter/`

## Building From Source

Use the normal Gradle build:

```bash
./gradlew build
```

Jar naming pattern:

```text
build/libs/1MB-XRayHunter-v<plugin-version>-<build-number>-j25-26.1.2.jar
```

Build behavior:

- a successful jar build increments `version.properties`
- a new jar is written into `build/libs/`
- older jars remain until you run `clean`

Optional clean rebuild:

```bash
./gradlew clean build
```

## First-Run Readiness Checks

After installation, verify:

- CoreProtect is enabled before XRayHunter
- `/xrayhunter info` works
- `/xrayhunter help` works
- `/xrayhunter debug` reports `CoreProtect hooked: true`
- `/xrayhunter lookup 2d` returns either results or a clear no-data message
