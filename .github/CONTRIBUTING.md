Looking to report a bug or ask for a feature? Open an [issue](https://github.com/hazptr/neiro/issues). Please report problems with Neiro here, not to Mihon.

---

# Code contributions

Pull requests are welcome.

Neiro tracks every Mihon release, so changes to Mihon's own files are kept to small hooks. New features go
under `app/src/main/java/neiro/`, and Mihon's files only get the call that reaches them. [PORTING.md](../PORTING.md)
explains the layout and lists every place Neiro touches Mihon's code; add yours there.

## Prerequisites

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- An emulator or a phone with developer options enabled to test changes.

# Translations

Text that Neiro adds is in English only for now. Everything else comes with Mihon.

# Forks

Forks are allowed so long as they abide by [the project's LICENSE](../LICENSE).

When creating a fork, remember to:

- To avoid confusion with Neiro:
    - Change the app name
    - Change the app icon
    - Change or disable the app update checker, so it doesn't offer Neiro's releases
- To avoid installation conflicts:
    - Change the `applicationId` in the Neiro block at the end of [`app/build.gradle.kts`](../app/build.gradle.kts)
