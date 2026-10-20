# Neiro changes

What changed in each Neiro release, on top of the Mihon version it is built on. The release
workflow publishes the section for the tagged version as the release notes, which is also what the
in-app update screen shows.

## 0.21.2.7
- Redrew the icon's 音 in a font with taller proportions, so it no longer looks squashed on the
  launch and About screens.

## 0.21.2.6
- The mute and repeat buttons now only appear with the reader menu, stacked vertically, instead of
  staying on screen while reading.

## 0.21.2.5
- Extensions that play background music now show a small music note on their icon, in the extension
  list and in the extension store.

## 0.21.2.4
- Floating mute and repeat buttons in the top right corner of the reader while a chapter has music.
  Mute lasts for the current reading session. The repeat setting moved here from the reader settings.
- Removed the "Support us" menu, which pointed at Mihon's donation pages.

## 0.21.2.3
- Fixed background music not playing for chapters first opened in 0.21.2.1 or 0.21.2.2. Those
  chapters' cached page lists are cleared once on update.
- Each track is now downloaded once instead of again for every page that loads.

## 0.21.2.2
- Fixed background music never playing in release builds.

## 0.21.2.1
- First Neiro release: Mihon 0.21.2 with background music for sources that provide it.
