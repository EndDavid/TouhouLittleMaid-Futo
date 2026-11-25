"""Derive a square mod icon from the rectangular upstream logo.

Why this exists
---------------
`src/main/resources/logo.png` is the upstream banner: 1200x240, and it is
byte-identical in all three upstream jars (Forge 1.18.2 / Forge 1.20.1 /
Fabric 1.20.1).  On Forge that is fine, because `mods.toml` draws it as a wide
banner.  Fabric itself never draws a mod icon; ModMenu does, and
`FabricIconHandler.createIcon` refuses anything that is not square:

    Validate.validState(image.getWidth() == image.getHeight(), "Must be square icon");

Upstream 1.20.1 Fabric has the same defect, so the banner cannot simply be
reused.  This script cuts a square window out of the banner and pre-filters it
down to a normal icon size, so ModMenu's 32x32 (or 19x19 compact) quad samples
a clean image instead of aliasing a 7.5:1 minification.

Usage (from the repository root, no Pillow needed):
    python port/make-icon.py                  # centre window, 128x128
    python port/make-icon.py --x 760          # different window, same size
    python port/make-icon.py --window 240 --out 240

The default x is the window with the highest horizontal edge density among all
961 candidates, measured on the banner; for this artwork it lands on the centre
(x=480), which is the same place a plain centre crop would pick.
"""
import argparse
import struct
import zlib

REPO_LOGO = 'src/main/resources/logo.png'
REPO_ICON = 'src/main/resources/icon.png'


def read_png(path):
    """Return (w, h, color_type, channels, palette, pixels) for a non-interlaced 8-bit PNG."""
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a png'
    pos = 8
    idat = b''
    plte = b''
    w = h = ctype = None
    while pos < len(data):
        ln, typ = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b'IHDR':
            w, h, bitdepth, ctype, comp, filt, inter = struct.unpack('>IIBBBBB', body)
            assert bitdepth == 8 and inter == 0, (bitdepth, inter)
        elif typ == b'PLTE':
            plte = body
        elif typ == b'tRNS':
            # The crop below is RGB-only; silent transparency would turn into
            # whatever colour happens to sit under the alpha channel.
            raise NotImplementedError('%s has a tRNS chunk; add alpha handling' % path)
        elif typ == b'IDAT':
            idat += body
        elif typ == b'IEND':
            break
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat)
    stride = w * channels
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        bpp = channels
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                line[i] = (line[i] + ((line[i - bpp] if i >= bpp else 0) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    palette = None
    if ctype == 3:
        palette = [tuple(plte[i * 3:i * 3 + 3]) for i in range(len(plte) // 3)]
    return w, h, ctype, channels, palette, out


def write_png(path, w, h, rows):
    """Write 8-bit truecolour RGB rows (each a bytes-like of length w*3)."""
    def chunk(typ, body):
        return (struct.pack('>I', len(body)) + typ + body
                + struct.pack('>I', zlib.crc32(typ + body) & 0xFFFFFFFF))

    raw = b''.join(b'\x00' + bytes(r) for r in rows)
    data = (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))
    open(path, 'wb').write(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--window', type=int, default=240,
                    help='side of the square window cut out of the banner (default 240 = banner height)')
    ap.add_argument('--x', type=int, default=None,
                    help='left edge of the window (default: centre)')
    ap.add_argument('--out', type=int, default=128, help='output icon side (default 128)')
    args = ap.parse_args()

    w, h, ctype, channels, palette, px = read_png(REPO_LOGO)
    side = args.window
    assert side > 0 and args.out > 0, '--window and --out must be positive'

    def colour(x, y):
        i = (y * w + x) * channels
        if ctype == 3:
            return palette[px[i]]
        if ctype in (2, 6):
            return px[i], px[i + 1], px[i + 2]
        return (px[i],) * 3

    x0 = args.x if args.x is not None else (w - side) // 2
    y0 = (h - side) // 2
    assert 0 <= x0 <= w - side and 0 <= y0 <= h - side, 'window falls outside the banner'

    # Area-average (box filter) downsample: each output pixel is the weighted mean
    # of the source pixels it covers, so 240 -> 128 keeps every pixel's energy
    # instead of point-sampling and losing thin lines.
    scale = float(side) / args.out
    rows = []
    for oy in range(args.out):
        sy0 = oy * scale
        sy1 = (oy + 1) * scale
        row = bytearray()
        for ox in range(args.out):
            sx0 = ox * scale
            sx1 = (ox + 1) * scale
            r = g = b = wsum = 0.0
            for sy in range(int(sy0), min(int(sy1) + 1, side)):
                wy = min(sy + 1.0, sy1) - max(float(sy), sy0)
                if wy <= 0:
                    continue
                for sx in range(int(sx0), min(int(sx1) + 1, side)):
                    wx = min(sx + 1.0, sx1) - max(float(sx), sx0)
                    if wx <= 0:
                        continue
                    cr, cg, cb = colour(x0 + sx, y0 + sy)
                    wgt = wx * wy
                    r += cr * wgt
                    g += cg * wgt
                    b += cb * wgt
                    wsum += wgt
            row += bytes((int(round(r / wsum)), int(round(g / wsum)), int(round(b / wsum))))
        rows.append(row)

    write_png(REPO_ICON, args.out, args.out, rows)
    print('wrote %s: %dx%d (from banner window x=%d..%d, y=%d..%d)'
          % (REPO_ICON, args.out, args.out, x0, x0 + side, y0, y0 + side))

    # Self-check: ModMenu rejects anything that is not square, so fail here
    # rather than one game launch later.
    out_w, out_h = read_png(REPO_ICON)[:2]
    assert out_w == out_h == args.out, 'wrote %dx%d, expected %dx%d' % (out_w, out_h, args.out, args.out)


if __name__ == '__main__':
    main()

