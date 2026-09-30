# F-Droid recipe

`metadata/com.rrajath.grove.yml` mirrors the build recipe in
[`fdroiddata`](https://gitlab.com/fdroid/fdroiddata), F-Droid's own metadata
repository (MR !45754 while under review). F-Droid's build server does **not**
read it from this location; it's kept here so the recipe has a home in version
control. When you change it in `fdroiddata`, copy it back here (or the other way
round) so the two stay identical.

## Versions and commits

- `versionName` and `versionCode` both live in `gradle.properties`.
  `versionCode` is `MAJOR*10000 + MINOR*100 + PATCH`; the build fails if it
  doesn't match `versionName`. `scripts/release.sh` bumps both.
- Pin `Builds[].commit` to the commit a `v*.*.*` tag points at.
  `scripts/release.sh` commits the version bump and the archived CHANGELOG.md
  heading before tagging, so that commit is byte-for-byte what CI built into the
  published APK, which F-Droid's reproducible-build check compares against.
- Exception: 1.8.1 builds from `0684a3a3`, the commit after the `v1.8.1` tag,
  because CI still added the CHANGELOG.md heading after tagging back then.

## After 1.8.2 is tagged

The `versionCode=` line in `gradle.properties` first ships in 1.8.2. Once
`v1.8.2` exists, add this to the recipe here and in `fdroiddata` so
`checkupdates` can pick up new releases automatically:

```yaml
UpdateCheckData: gradle.properties|versionCode=(\d+)|.|versionName=(.+)
```

Adding it earlier fails the `fdroiddata` MR's `checkupdates` job, which reads
the latest tag (`v1.8.1`, which has no such line).

## Validating a change

Install `fdroidserver` and run `fdroid readmeta`, `fdroid lint com.rrajath.grove`,
and `fdroid build --verbose com.rrajath.grove:<versionCode>` against a local
checkout of `fdroiddata` with this file copied into its `metadata/` directory.
This catches YAML and build errors before a reviewer does.

See `internal-docs/FDROID_READINESS.md` for the full submission checklist this
recipe is one part of.
