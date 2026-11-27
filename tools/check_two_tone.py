"""Report non-black/non-white pixels in a PNG or directory of screenshots."""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageChops


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("path", type=Path)
    parser.add_argument("--max-percent", type=float, help="fail at or above this percentage")
    args = parser.parse_args()
    files = sorted(args.path.rglob("*.png")) if args.path.is_dir() else [args.path]
    if not files:
        parser.error("no PNG screenshots found")
    failed = False
    for file in files:
        try:
            with Image.open(file) as source:
                rgb = source.convert("RGB")
                pixels = rgb.width * rgb.height
                # Each histogram bin of the channel extrema represents whole pixels.
                red, green, blue = rgb.split()
                black = ImageChops.lighter(ImageChops.lighter(red, green), blue).histogram()[0]
                white = ImageChops.darker(ImageChops.darker(red, green), blue).histogram()[255]
        except OSError as error:
            print(error, file=sys.stderr)
            failed = True
            continue
        other = pixels - black - white
        percent = 100 * other / pixels
        print(f"{file}: {other}/{pixels} intermediate or coloured pixels ({percent:.3f}%)")
        if args.max_percent is not None and percent >= args.max_percent:
            failed = True
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
