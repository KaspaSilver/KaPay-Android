"""Draws the KaPay logo and app icons: a teal wallet with KaChat's backward K in the middle.

The same artwork as the iPhone wallet's tools/make_logo.py, with Android's adaptive-icon layers
added (`foreground`, `monochrome`).

Usage: python3 tools/make_logo.py OUT.png [SIZE] [VARIANT]

VARIANT (default "logo"):
  logo        white rounded square, transparent corners - the in-app logo (kachat_wallet_logo)
  light       full white square, no transparency - the legacy launcher icon
  foreground  the adaptive icon's foreground layer: the wallet on transparent, shrunk into the
              middle 72 of 108 dp the launcher shows (the background layer is plain white)
  monochrome  the adaptive icon's themed (Android 13+) layer: the wallet in white with the K cut
              out, on transparent, sized like foreground; the launcher tints it
  dark        full near-black square, teal wallet, dark K - the dark-mode app icon on iOS, and
              the privacy cover's logo while the app is dark (kachat_wallet_logo_dark)
  tinted      (iOS only) grayscale on black, for iOS to tint
"""
import sys
from PIL import Image, ImageDraw

out = sys.argv[1]
size = int(sys.argv[2]) if len(sys.argv) > 2 else 1024
variant = sys.argv[3] if len(sys.argv) > 3 else "logo"
if variant not in ("logo", "light", "foreground", "monochrome", "dark", "tinted"):
    sys.exit(f"unknown variant: {variant}")

S = 4  # supersampling
W = 1024 * S

def p(*xy):
    return [v * S for v in xy]

WHITE = (255, 255, 255)
if variant == "tinted":
    BACKGROUND = (0, 0, 0)
    BODY_TOP, BODY_BOTTOM = (245, 245, 245), (200, 200, 200)
    ACCENT = (150, 150, 150)
    MARK = (0, 0, 0)
elif variant == "dark":
    BACKGROUND = (21, 25, 31)
    BODY_TOP, BODY_BOTTOM = (138, 214, 201), (95, 191, 176)
    ACCENT = (72, 166, 151)
    MARK = BACKGROUND
elif variant == "monochrome":
    # Only alpha counts: the launcher fills it with its own colour. The K is a hole.
    BACKGROUND = (0, 0, 0, 0)
    BODY_TOP = BODY_BOTTOM = (255, 255, 255)
    ACCENT = (255, 255, 255)
    MARK = (0, 0, 0)
else:
    BACKGROUND = WHITE
    BODY_TOP, BODY_BOTTOM = (138, 214, 201), (95, 191, 176)
    ACCENT = (72, 166, 151)
    MARK = WHITE

if variant == "logo":
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # White rounded square, like the KaChat logo's tile.
    d.rounded_rectangle(p(0, 0, 1023, 1023), radius=230 * S, fill=BACKGROUND + (255,))
elif variant in ("foreground", "monochrome"):
    img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
else:
    # App icons are full squares with no transparency; iOS applies the corner mask itself.
    img = Image.new("RGBA", (W, W), BACKGROUND + (255,))
    d = ImageDraw.Draw(img)

# The note peeking out of the wallet's top.
d.rounded_rectangle(p(250, 225, 790, 380), radius=48 * S, fill=ACCENT)

# Wallet body, with a soft top-to-bottom gradient.
body = (165, 300, 859, 790)
mask = Image.new("L", (W, W), 0)
ImageDraw.Draw(mask).rounded_rectangle(p(*body), radius=96 * S, fill=255)
grad = Image.new("RGBA", (W, W))
gd = ImageDraw.Draw(grad)
for y in range(body[1] * S, body[3] * S + 1):
    t = (y - body[1] * S) / ((body[3] - body[1]) * S)
    c = tuple(round(a + (b - a) * t) for a, b in zip(BODY_TOP, BODY_BOTTOM))
    gd.line([(0, y), (W, y)], fill=c + (255,))
img.paste(grad, (0, 0), mask)

# Clasp on the right edge, with its button.
d.rounded_rectangle(p(690, 465, 899, 625), radius=56 * S, fill=ACCENT)
d.ellipse(p(752, 521, 800, 569), fill=MARK + ((0,) if variant == "monochrome" else (255,)))

# KaChat's backward K (measured off KaChat's app icon, in its 1024 px coordinates): a bar bent
# into a slight ">" and two thick arms meeting in a "<" against it. Scaled and centred on the
# wallet body.
K_CENTRE = (513, 471)
K_SCALE = 0.76
K_AT = (500, 545)
K_SHAPES = [
    [(556, 267), (636, 255), (666, 471), (636, 687), (557, 675), (586, 471)],  # bar
    [(408, 293), (637, 471), (506, 471), (361, 355)],                          # upper arm
    [(408, 649), (637, 471), (506, 471), (361, 587)],                          # lower arm
]
mark = MARK + ((0,) if variant == "monochrome" else (255,))
for shape in K_SHAPES:
    pts = []
    for x, y in shape:
        pts += [K_AT[0] + (x - K_CENTRE[0]) * K_SCALE, K_AT[1] + (y - K_CENTRE[1]) * K_SCALE]
    d.polygon(p(*pts), fill=mark)

if variant in ("foreground", "monochrome"):
    # The launcher masks a 108 dp layer down to its middle 72 dp: the whole iOS tile goes there.
    inner = round(W * 72 / 108)
    layer = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    layer.paste(img.resize((inner, inner), Image.LANCZOS), ((W - inner) // 2,) * 2)
    img = layer

img = img.resize((size, size), Image.LANCZOS)
if variant in ("light", "dark", "tinted"):
    img = img.convert("RGB")
img.save(out)
