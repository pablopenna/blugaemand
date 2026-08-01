#!/usr/bin/env python3
"""Regenerates the launcher icon density assets from art/icon/icon.png.

Run after editing icon.aseprite and re-exporting icon.png:

    python3 art/icon/generate-launcher-icons.py

Produces two adaptive-icon layers at all five densities:

  ic_launcher_foreground.png  the art as-is
  ic_launcher_monochrome.png  an alpha-only silhouette for Android 13+ themed icons

Every export is an integer nearest-neighbour upscale, which is the whole reason the source is a
54x54 canvas: 54 scales to all five density buckets on whole pixels (x2, x3, x4, x6, x8). Any other
canvas size forces a fractional scale at one bucket or another and the pixel grid stops being
square.

Pre-scaling here rather than shipping one file also sidesteps Android's own resampling, which
applies bilinear filtering both when decoding a bitmap into a mismatched density bucket and again
when drawing it. Both would soften the art.
"""
from collections import deque
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
# parents[1] is art/, parents[2] the repo root. Derived by index rather than by walking up with
# .parent so that moving this script again fails loudly on the assert below instead of quietly
# building a res/ tree in the wrong place — mkdir(parents=True) further down is happy to do that.
ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"

assert (ROOT / "settings.gradle.kts").exists(), f"{ROOT} is not the repo root; fix the path above"

SOURCE_SIZE = 54

# Adaptive icon layers are 108x108dp; these are that canvas at each density bucket.
DENSITIES = {
    "mdpi": 2,     # 108px
    "hdpi": 3,     # 162px
    "xhdpi": 4,    # 216px
    "xxhdpi": 6,   # 324px
    "xxxhdpi": 8,  # 432px
}

# Palette roles, used to derive the monochrome layer. Update these if the art's colours change.
FIELD = (0x00, 0x2E, 0xFF, 255)   # the blue the art sits on
FRAME = (0x5F, 0xCD, 0xE4, 255)   # the border ring — same colour as the gamepad body
DETAILS = {                        # punched out as holes so the silhouette stays readable
    (0x5F, 0x8A, 0xE4, 255),      # face buttons and d-pad
    (0x76, 0x5F, 0xE4, 255),      # button outlines
}


def outer_shell(image: Image.Image) -> set[tuple[int, int]]:
    """Pixels reachable from the canvas edge through transparency, the field colour or the frame.

    This is how the transparent margin, the border ring and the blue field behind the artwork get
    dropped from the monochrome layer, leaving only the robot and the gamepad. It relies on the
    frame not touching the gamepad body — they share a colour, so if the art ever connects them
    this flood will eat the body too. The sanity check in `derive_monochrome` catches that.
    """
    width, height = image.size
    pixels = image.load()
    seen: set[tuple[int, int]] = set()
    queue = deque(
        [(x, y) for x in range(width) for y in (0, height - 1)]
        + [(x, y) for y in range(height) for x in (0, width - 1)]
    )
    while queue:
        x, y = queue.popleft()
        if (x, y) in seen or not (0 <= x < width and 0 <= y < height):
            continue
        pixel = pixels[x, y]
        if pixel[3] != 0 and pixel != FIELD and pixel != FRAME:
            continue
        seen.add((x, y))
        queue.extend([(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)])
    return seen


def derive_monochrome(image: Image.Image) -> Image.Image:
    """Alpha-only silhouette of the artwork.

    Themed icons use nothing but the alpha channel, so a straight copy of the art would render as a
    featureless block. Dropping the background and punching the button details through as holes is
    what keeps it recognisable as a gamepad.
    """
    width, height = image.size
    pixels = image.load()
    shell = outer_shell(image)

    mono = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    mono_pixels = mono.load()
    kept = 0
    for y in range(height):
        for x in range(width):
            pixel = pixels[x, y]
            if pixel[3] == 0 or (x, y) in shell or pixel in DETAILS:
                continue
            mono_pixels[x, y] = (0, 0, 0, 255)
            kept += 1

    coverage = kept / (width * height)
    if not 0.05 < coverage < 0.45:
        print(
            f"  warning: monochrome layer covers {coverage:.0%} of the canvas, which looks wrong. "
            f"Check the FIELD/FRAME/DETAILS colours against the current art."
        )
    return mono


def export(image: Image.Image, name: str) -> None:
    for bucket, scale in DENSITIES.items():
        out_dir = RES / f"mipmap-{bucket}"
        out_dir.mkdir(parents=True, exist_ok=True)
        size = SOURCE_SIZE * scale
        image.resize((size, size), Image.NEAREST).save(out_dir / f"{name}.png")
    print(f"  {name}.png  x{'/x'.join(str(s) for s in DENSITIES.values())}")


def main() -> None:
    source = Image.open(HERE / "icon.png").convert("RGBA")

    if source.size != (SOURCE_SIZE, SOURCE_SIZE):
        raise SystemExit(
            f"icon.png is {source.width}x{source.height}, expected {SOURCE_SIZE}x{SOURCE_SIZE}. "
            f"A different canvas size will not scale to every density bucket on whole pixels."
        )

    # Content must sit inside the central 72 of 108dp — the rest is reserved for launcher masks.
    bbox = source.getbbox()
    safe_lo = SOURCE_SIZE * 18 // 108
    safe_hi = SOURCE_SIZE - safe_lo
    if bbox and (bbox[0] < safe_lo or bbox[1] < safe_lo or bbox[2] > safe_hi or bbox[3] > safe_hi):
        print(
            f"  warning: content {bbox} extends past the safe zone {safe_lo}..{safe_hi}; "
            f"launcher masks may clip it"
        )

    export(source, "ic_launcher_foreground")
    export(derive_monochrome(source), "ic_launcher_monochrome")


if __name__ == "__main__":
    main()
