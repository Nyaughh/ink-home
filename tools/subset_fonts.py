"""Rebuild Folio's Latin font assets. Requires fonttools==4.64.0 and Internet access."""
from io import BytesIO
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.parse import quote
from urllib.request import urlopen

from fontTools import subset
from fontTools.ttLib import TTFont

REV = "5026800714d07231937d92eb4cceabf06e05d74e"
UPSTREAM = f"https://raw.githubusercontent.com/google/fonts/{REV}/ofl/"
DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/fonts"
FONTS = [
    ("sourcesans3", "SourceSans3[wght].ttf", "FolioSans.ttf"),
    ("literata", "Literata[opsz,wght].ttf", "Literata.ttf"),
    ("literata", "Literata-Italic[opsz,wght].ttf", "Literata-Italic.ttf"),
]
# Latin plus combining marks, punctuation, currency and common book symbols.
RANGES = "U+0000-024F,U+0300-036F,U+1E00-1EFF,U+2000-206F,U+2070-209F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2212,U+2215,U+25CC,U+FEFF,U+FFFD"


def download(family, filename):
    with urlopen(UPSTREAM + family + "/" + quote(filename), timeout=60) as response:
        return response.read()


def rename_subset(font):
    # Adobe reserves "Source" for the original font. Replace family-identifying
    # names (including named instances), preserving trademark and license records.
    for record in font["name"].names:
        if record.nameID in {1, 3, 4, 6, 16, 21, 25} or record.nameID >= 256:
            name = record.toUnicode().replace("Source Sans 3", "Folio Sans")
            name = name.replace("SourceSans3", "FolioSans")
            record.string = name.encode(record.getEncoding())


def main():
    licenses = {}
    with TemporaryDirectory() as directory:
        stage = Path(directory)
        for family, original, filename in FONTS:
            if family not in licenses:
                ofl = download(family, "OFL.txt").decode("utf-8-sig")
                licenses[family] = ofl.replace("\r\r\n", "\n").replace("\r\n", "\n")
            options = subset.Options()
            options.layout_features = ["*"]
            options.name_IDs = ["*"]
            options.name_languages = ["*"]
            with TTFont(BytesIO(download(family, original)), recalcTimestamp=False) as font:
                subsetter = subset.Subsetter(options=options)
                subsetter.populate(unicodes=subset.parse_unicodes(RANGES))
                subsetter.subset(font)
                if family == "sourcesans3":
                    rename_subset(font)
                font.save(stage / filename)
        (stage / "OFL.txt").write_bytes("\n\n".join(
            f"{family}\n\n{license.strip()}" for family, license in licenses.items()
        ).encode("utf-8") + b"\n")
        DEST.mkdir(parents=True, exist_ok=True)
        for asset in stage.iterdir():
            (DEST / asset.name).write_bytes(asset.read_bytes())


if __name__ == "__main__":
    main()
