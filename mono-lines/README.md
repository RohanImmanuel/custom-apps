# Mono Lines

A line-icon pack for Android launchers (built for Niagara; works with any launcher that reads Nova/ADW-style packs). Thin white line icons, tinted by category to match the palette of the [Dotboard](../dotboard) widgets:

| Colour | Category |
|---|---|
| Cyan | chat, calls, people, mail, mobile network |
| Coral | camera, video, music, recorder |
| Blue | browsers, maps, transport, weather, cloud |
| Green | banking, payments, security |
| Amber | shopping, food, home, reading |
| Purple | AI, games |
| White | tools and system |

## How it works

- A mapping file lists, per line: launcher activity, an [Arcticons](https://github.com/Arcticons-Team/Arcticons/tree/main/icons/white) icon name, and a category (`comms`, `media`, `web`, `money`, `shop`, `fun`, `tools`).
- `tools/build_icons.py` downloads each icon's SVG from Arcticons (cached in `tools/svg/`), recolours it by category, renders it to a 256 px PNG in `res/drawable-nodpi/`, and writes `appfilter.xml` (which app gets which icon) and `drawable.xml` (the catalogue launchers show in "choose icon").

The repo ships `tools/mapping.tsv`, a short example with common apps. For your own phone, create `tools/mapping.local.tsv` in the same format; it's git-ignored and used instead when present, so your app list never gets committed. List your phone's launcher activities with:

```
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
```

Then build and install:

```
pip install cairosvg     # needs the cairo library: brew install cairo
python3 tools/build_icons.py
./gradlew installDebug
```

On macOS, prefix the Python command with `DYLD_FALLBACK_LIBRARY_PATH=/opt/homebrew/lib` so cairo is found. The generated icons and XML are git-ignored too, since they'd reveal the app list.

Apply it in Niagara: Niagara settings → Icons → Icon pack → Mono Lines.

## Credits and licence

The icons are from **[Arcticons](https://arcticons.com)** by the Arcticons team and contributors, licensed under [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). This pack recolours them and renders them to PNG; the modified icons are shared under the same licence.
