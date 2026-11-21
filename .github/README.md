<div align="center">

<img src="assets/icon.svg" width="128" alt="Neiro icon">

# Neiro

### A Mihon fork with a few extras

*Requires Android 8.0 or higher.*

[hazptr.github.io/neiro](https://hazptr.github.io/neiro/)

</div>

Neiro is [Mihon](https://github.com/mihonapp/mihon) with a few features I wanted that it doesnt have:

- Background music for chapters that come with it
- Chapter upload settings: one upload per chapter, and preferred or blocked scanlator groups
- Related titles in the series menu, for sources that suggest them
- Extensions from the Keiyoushi and Neiro stores trusted without asking

Everything else is Mihon, and regular extensions work unchanged. Neiro follows Mihon's releases closely: a new
Mihon version usually reaches Neiro within a day or two.

## Download

Get the latest APK from [Releases](https://github.com/hazptr/neiro/releases). It installs alongside Mihon
(package `app.neiro`) and updates itself from this repository.

## Background music

Turn it on in the reader's settings sheet, under **General → Play background music**. It is off by default. While a chapter with music is open, the reader menu shows translucent buttons in the top right corner to mute it for the session and toggle repeat.

A source has to provide the music. Add the Neiro extension store in **Settings → Browse → Extension stores**:

```
https://raw.githubusercontent.com/hazptr/neiro-extensions/repo/index.min.json
```

and install an extension from it. Extensions that play music show a small music note on their icon.
Sources built against
[neiro-extensions-lib](https://github.com/hazptr/neiro-extensions-lib) can attach a `ChapterAudio` timeline
to their pages; Neiro plays each track over the pages it is cued to, crossfades between tracks, ducks
other audio instead of stopping it, and stores the tracks with downloaded chapters so they play offline
(**Settings → Downloads → Download background music**).

## Chapter uploads

**Settings → Appearance → Chapter uploads** sets the defaults: all uploads or one per chapter, plus preferred
and blocked scanlator groups. Each series starts from them, and its scanlator filter (chapter list filter →
**Scanlator**) can change them for that series.

## Relationship to Mihon

Neiro follows Mihon's releases: each Neiro version is a Mihon version plus a Neiro release number, so
`0.21.2.1` is the first Neiro release on Mihon 0.21.2. Please report problems with Neiro here, not to Mihon.

## Credits

The icon's glyph is 音 from [M PLUS Rounded 1c](https://fonts.google.com/specimen/M+PLUS+Rounded+1c), licensed under
the SIL Open Font License 1.1.

## License

    Copyright © 2015 Javier Tomás
    Copyright © 2024 Mihon Open Source Project
    Copyright © 2026 hazptr

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
