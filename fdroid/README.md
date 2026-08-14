# F-Droid recipe draft

`metadata/com.rrajath.grove.yml` is a draft of the build recipe that goes into a merge
request against [`fdroiddata`](https://gitlab.com/fdroid/fdroiddata) — F-Droid's
own metadata repository. It does **not** get read by F-Droid's build server from
this location; it's kept here so the recipe has a home in version control and
doesn't need to be reconstructed from scratch when it's time to submit.

## Before submitting

1. Update `Builds[0].commit` (and `versionName`/`versionCode`, and matching
   `CurrentVersion`/`CurrentVersionCode`) to the actual commit being submitted for
   initial review if it's no longer `45a0dbe67034be9bac9898b91c6e60aacf906083` /
   `1.0.0` / `266`. `versionName` is a manually-bumped SemVer string in
   `gradle.properties`, unrelated to git tags; `versionCode` is
   `git rev-list --count HEAD` at the pinned commit — check both directly rather
   than assuming the last submitted values still hold.
2. Confirm the `Categories` entry against F-Droid's current category list (subject
   to change; check the live `fdroiddata` repo).
3. Install `fdroidserver` and run `fdroid readmeta`, `fdroid lint`, and
   `fdroid build --verbose com.rrajath.grove:266` against a local checkout of
   `fdroiddata` with this file copied into its `metadata/` directory — this
   catches YAML and build errors before a reviewer does.
4. Copy the validated file into a fork of `fdroiddata` at `metadata/com.rrajath.grove.yml`
   and open the merge request.

See `internal-docs/FDROID_READINESS.md` for the full submission checklist this
recipe is one part of.
