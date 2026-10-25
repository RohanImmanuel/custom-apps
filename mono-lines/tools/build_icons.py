"""Build the icon pack from Arcticons line icons (CC BY-SA 4.0, https://arcticons.com).

Icons are rendered to PNG because launcher icon pickers (Niagara's included) only list bitmaps;
vector drawables theme apps fine but never show up in "choose icon".

    pip install cairosvg            # needs the cairo library (brew install cairo)
    python3 tools/build_icons.py    # on macOS: DYLD_FALLBACK_LIBRARY_PATH=/opt/homebrew/lib python3 ...

Inputs:  tools/mapping.local.tsv if present (your apps, git-ignored), else tools/mapping.tsv (example)
         rows: launcher activity <TAB> Arcticons icon name <TAB> category
Outputs: src/main/res/drawable-nodpi/<name>.png, res/xml/appfilter.xml + drawable.xml (+ assets copies)
SVGs are downloaded from Arcticons on first use into tools/svg/ (a git-ignored cache).
"""
import pathlib
import re
import urllib.request

import cairosvg

ROOT = pathlib.Path(__file__).resolve().parent.parent
RES = ROOT / "src/main/res"
SVG_CACHE = ROOT / "tools/svg"
SVG_URL = "https://raw.githubusercontent.com/Arcticons-Team/Arcticons/main/icons/white/{}.svg"
SIZE = 256
APP_ICON = "android"  # the pack's own launcher icon (see AndroidManifest)

# soft versions of the weather widget's palette
CATEGORY_COLORS = {
    "comms": "#4DD0E1",  # chat, calls, people, mail, mobile network
    "media": "#FF6B6B",  # camera, video, music, recorder
    "web": "#4FC3F7",    # browsers, maps, transport, weather, cloud
    "money": "#7BE495",  # banking, payments, security
    "shop": "#FFD54F",   # shopping, food, home, reading
    "fun": "#C77DFF",    # AI, games
    "tools": "#FFFFFF",  # tools and system
}
WHITE_IN_SVG = re.compile(r"#fff(?:fff)?\b|\bwhite\b", re.I)


def res_name(icon):
    # plain names: icon pickers show and search them as-is ("youtube", not "ic_youtube")
    return re.sub(r"[^a-z0-9_]", "_", icon.lower())


def svg_for(icon):
    path = SVG_CACHE / f"{icon}.svg"
    if not path.exists():
        SVG_CACHE.mkdir(parents=True, exist_ok=True)
        with urllib.request.urlopen(SVG_URL.format(icon), timeout=20) as r:
            path.write_bytes(r.read())
    return path


def render(icon, color, out):
    svg = WHITE_IN_SVG.sub(color, svg_for(icon).read_text())
    cairosvg.svg2png(bytestring=svg.encode(), write_to=str(out), output_width=SIZE, output_height=SIZE)


def main():
    mapping = ROOT / "tools/mapping.local.tsv"
    if not mapping.exists():
        mapping = ROOT / "tools/mapping.tsv"
    rows = [l.split("\t") for l in mapping.read_text().splitlines() if l.strip() and not l.startswith("#")]

    out_dir = RES / "drawable-nodpi"
    out_dir.mkdir(parents=True, exist_ok=True)
    for old in out_dir.glob("*.png"):
        old.unlink()
    render(APP_ICON, CATEGORY_COLORS["tools"], out_dir / f"{APP_ICON}.png")
    items, built = [], {APP_ICON}
    for line, icon, category in rows:
        name = res_name(icon)
        if name not in built:  # shared icons take the colour of the first app that uses them
            render(icon, CATEGORY_COLORS[category], out_dir / f"{name}.png")
            built.add(name)
        pkg, cls = line.split("/", 1)
        comp = f"{pkg}/{pkg + cls if cls.startswith('.') else cls}"
        items.append(f'    <item component="ComponentInfo{{{comp}}}" drawable="{name}" />')

    appfilter = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n' + "\n".join(items) + "\n</resources>\n"
    # catalogue for launchers' "choose icon" pickers; appfilter alone only themes matching apps
    catalogue = ('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <version>1</version>\n'
                 '    <category title="All" />\n'
                 + "\n".join(f'    <item drawable="{n}" />' for n in sorted(built)) + "\n</resources>\n")
    (RES / "xml").mkdir(parents=True, exist_ok=True)
    (ROOT / "src/main/assets").mkdir(parents=True, exist_ok=True)
    for fname, text in (("appfilter.xml", appfilter), ("drawable.xml", catalogue)):
        (RES / "xml" / fname).write_text(text)
        (ROOT / "src/main/assets" / fname).write_text(text)  # some launchers read assets/, some res/xml/
    print(f"{mapping.name}: {len(built)} icons, {len(items)} apps mapped")


if __name__ == "__main__":
    main()
