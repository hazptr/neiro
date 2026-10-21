# Keeping Neiro on top of Mihon

Neiro is Mihon plus a small set of changes. The goal is that each new Mihon version merges with few or
no conflicts, so the rules below keep Neiro's changes out of the files Mihon edits most.

## Updating to a new Mihon version

```sh
scripts/sync-mihon.sh v0.22.0     # or: scripts/sync-mihon.sh main
```

The script merges Mihon into the current branch (merging, not rebasing, so history and release tags stay
intact) and, when Mihon's version changed, resets `neiro.properties` to `release=1`. The **Mihon drift**
workflow test-merges Mihon's main branch every week and lists the files that would conflict, so a
conflict shows up before a release is cut.

After the merge:

1. Build and test: `./gradlew spotlessCheck testDebugUnitTest assembleRelease -Pdist=github`.
2. On a device, open a chapter with background music: it plays, the mute and repeat buttons appear in
   the reader menu, a downloaded chapter plays offline, and the extension store shows the music badge.
3. Compare the release APK's permissions with the last release
   (`aapt2 dump badging <apk> | grep uses-permission`). Any new permission, network call or stored data
   has to go into the privacy policy (`site/privacy/index.html`).
4. Add a section for the new version to `NEIRO_CHANGES.md` and tag `v<mihon version>.<release>`.

## Rules for Neiro changes

- **Put code in Neiro's own files.** Features live under `app/src/main/java/neiro/` (for example
  `neiro.bgm.ChapterAudioSupport`, `neiro.bgm.ReaderBgmController`). Mihon's files only get short hooks
  that call into them: a constructor parameter, one call, one line in a `when`.
- **Don't delete Mihon's code or files.** Stop calling it instead. A deleted file conflicts every time
  Mihon edits it; an unused one never does. The exceptions are files that would put Mihon's name on
  Neiro's repo or app (the funding file, issue templates, Mihon's Firebase config).
- **Don't rewrite Mihon's lines when a separate one will do.** Neiro's package name and version are set in
  one block at the end of `app/build.gradle.kts`, derived from Mihon's untouched `versionCode` and
  `versionName` lines plus `neiro.properties`. Neiro's dependencies are in `gradle/neiro.versions.toml`,
  not Mihon's catalog.
- **Use Neiro's own copies of whole files Mihon also has.** Neiro's README is `.github/README.md` (GitHub
  shows it ahead of the root one), and Neiro's release workflow is `neiro-release.yml`; Mihon's own
  workflows only run in Mihon's repository.
- **Keep hooks away from lines Mihon changes often.** Git reports a conflict when both sides edit
  neighbouring lines, so a hook goes next to a stable line, not next to a version number.

## Where Neiro touches Mihon

The changes inside Mihon's files, so a conflict is quick to place:

- **Background music:** hooks in `ReaderViewModel`, `ReaderActivity`, `WebtoonViewer`, `ChapterLoader`,
  `HttpPageLoader`, `DownloadPageLoader`, `Downloader`; the API on `Page` (`source-api`) and the
  `ReaderPage.name` field; the preferences and their settings rows; the keep rules in
  `source-api/consumer-proguard.pro`.
- **Extension music badge:** `Extension.hasAudio`, read in `ExtensionLoader` and
  `NetworkLegacyExtension`, drawn by `ExtensionIcon` through `neiro.ui.AudioBadgeBox`.
- **Identity:** the end of `app/build.gradle.kts`, the launcher icons and logo drawables, `app_name`,
  the `neiro://add-repo` deep link, the updater's repository, the About screen's links.
- **Privacy:** telemetry is hard-wired off in `gradle/build-logic/.../BuildConfig.kt`, analytics and
  crash reporting default to off, a fixed name replaces the random package in `WebViewUtil`, and the
  update check time is stored (`BasePreferences.lastAppUpdateCheck`).
- **Startup:** `neiro.app.NeiroStartup`, called from `App`.
