"""Regenerate the mod's own icons from the artwork committed in `pack.png`.

Why this exists
---------------
`src/main/resources/pack.png` is the built-in resource pack's icon (the jar has
a root `pack.mcmeta`, so the mod itself is a resource pack).  Its artwork is the
Boutoku-no-Futatsuhime "pack.png" homage, 32x32, and it is now the single source
of truth for any icon this mod draws.

Three consumers need it in three different shapes:

    src/main/resources/icon.png             fabric.mod.json "icon", read by
                                            ModMenu.  ModMenu's
                                            FabricIconHandler.createIcon runs
                                            Validate.validState(w == h, ...), so
                                            this one MUST be square.  32x32 is
                                            exactly the size ModMenu blits, so
                                            no resampling happens at all.
    .../textures/gui/tab_icon.png           the 16x16 icon of the main creative
                                            tab (see the client mixin
                                            CreativeModeInventoryScreenMixin).
    (pack.png itself is the third consumer, copied verbatim.)

Usage (from the repository root, no Pillow needed):
    python port/make-icons.py                # majority filter (default)
    python port/make-icons.py --filter box   # area average instead
    python port/make-icons.py --tab-size 16

The artwork is 32x32 pixel art, so shrinking it to the 16x16 the creative tab
draws must not blur it: `majority` keeps the author's own palette (each output
pixel is one colour that actually exists in the source block) while `box`
averages and therefore invents colours.  Both filters are deterministic:
running the script twice on the same input produces byte-identical output, so
`git status` stays clean.
"""
import argparse
import collections
import struct
import zlib

REPO_PACK = 'src/main/resources/pack.png'
REPO_ICON = 'src/main/resources/icon.png'
REPO_TAB_ICON = ('src/main/resources/assets/touhou_little_maid/textures/gui/tab_icon.png')


def read_png(path):
    """Return (w, h, color_type, channels, palette, pixels) for a non-interlaced 8-bit PNG."""
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a png: %s' % path
    pos = 8
    idat = b''
    plte = b''
    trns = None
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
            trns = body
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
    return w, h, ctype, channels, palette, out, trns


def write_png(path, w, h, rows, ctype):
    """Write 8-bit rows (each a bytes-like of length w*channels) as a PNG of the given colour type."""
    def chunk(typ, body):
        return (struct.pack('>I', len(body)) + typ + body
                + struct.pack('>I', zlib.crc32(typ + body) & 0xFFFFFFFF))

    raw = b''.join(b'\x00' + bytes(r) for r in rows)
    data = (b'\x89PNG\r\n\x1a\n'
            + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, ctype, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw, 9))
            + chunk(b'IEND', b''))
    open(path, 'wb').write(data)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--filter', choices=('majority', 'box', 'nearest'), default='majority',
                    help='how to resample pack.png down to the tab icon size (default majority)')
    ap.add_argument('--tab-size', type=int, default=16,
                    help='side of the creative tab icon in GUI pixels (default 16)')
    args = ap.parse_args()

    w, h, ctype, channels, palette, px, trns = read_png(REPO_PACK)
    assert ctype == 6, ('pack.png must be 8-bit RGBA, got colour type %d' % ctype)
    assert w == h, 'pack.png must stay square (ModMenu rejects anything else): %dx%d' % (w, h)
    assert args.tab_size > 0 and w % args.tab_size == 0, \
        'pack.png side (%d) must be an integer multiple of --tab-size (%d)' % (w, args.tab_size)

    def pixel(x, y):
        i = (y * w + x) * channels
        return px[i], px[i + 1], px[i + 2], px[i + 3]

    # 1. The mod icon is the art itself, byte for byte.  ModMenu blits 32x32, and
    #    pack.png is 32x32, so nothing is resampled on the way to the screen.
    raw_pack = open(REPO_PACK, 'rb').read()
    open(REPO_ICON, 'wb').write(raw_pack)
    print('wrote %s: %dx%d (verbatim copy of %s)' % (REPO_ICON, w, h, REPO_PACK))

    # 2. The creative tab icon is drawn in a 16x16 slot, so pre-filter it here
    #    instead of letting the GPU point-sample 32x32 -> 16x16 (which drops
    #    every other row and can lose a one-pixel feature such as an eye).
    step = w // args.tab_size
    rows = []
    for oy in range(args.tab_size):
        row = bytearray()
        for ox in range(args.tab_size):
            if args.filter == 'nearest':
                r, g, b, a = pixel(ox * step + step // 2, oy * step + step // 2)
                a = 255 if a >= 128 else 0
                row += bytes((r, g, b, a))
                continue
            if args.filter == 'majority':
                # Most frequent colour inside the block, ties broken towards the
                # brighter one.  Output colours therefore all exist in the
                # source, which is what keeps 32x32 pixel art looking like pixel
                # art after the 2:1 shrink.
                counts = collections.Counter()
                for sy in range(oy * step, (oy + 1) * step):
                    for sx in range(ox * step, (ox + 1) * step):
                        cr, cg, cb, ca = pixel(sx, sy)
                        if ca >= 128:
                            counts[(cr, cg, cb)] += 1
                if sum(counts.values()) * 2 < step * step:
                    row += b'\x00\x00\x00\x00'
                else:
                    r, g, b = max(counts.items(), key=lambda kv: (kv[1], kv[0][0] + kv[0][1] + kv[0][2]))[0]
                    row += bytes((r, g, b, 255))
                continue
            r = g = b = 0.0
            weight = 0.0
            covered = 0
            for sy in range(oy * step, (oy + 1) * step):
                for sx in range(ox * step, (ox + 1) * step):
                    cr, cg, cb, ca = pixel(sx, sy)
                    if ca >= 128:
                        r += cr
                        g += cg
                        b += cb
                        weight += 1.0
                        covered += 1
            # Keep the silhouette hard-edged (MC blits with NEAREST, partial
            # alpha here would only show up as speckle) but average the colour
            # over the opaque samples so 2:1 does not turn into a checkerboard.
            if covered * 2 >= step * step:
                n = float(covered)
                row += bytes((int(round(r / n)), int(round(g / n)), int(round(b / n)), 255))
            else:
                row += b'\x00\x00\x00\x00'
        rows.append(row)
    write_png(REPO_TAB_ICON, args.tab_size, args.tab_size, rows, 6)
    print('wrote %s: %dx%d (%s filter, %d:%d)'
          % (REPO_TAB_ICON, args.tab_size, args.tab_size, args.filter, w, args.tab_size))

    # 3. Self-checks: a bad crop or a lost alpha channel should fail here, not
    #    one game launch later.
    assert open(REPO_ICON, 'rb').read() == raw_pack, 'icon.png drifted from pack.png'
    iw, ih = read_png(REPO_ICON)[:2]
    assert iw == ih, 'icon.png is %dx%d, ModMenu will reject it' % (iw, ih)
    tw, th, tctype, tchannels, _, tpx, _ = read_png(REPO_TAB_ICON)
    assert (tw, th) == (args.tab_size, args.tab_size), 'tab icon is %dx%d' % (tw, th)
    assert tctype == 6 and tchannels == 4, 'tab icon must be RGBA'
    alphas = set(tpx[3::4])
    assert alphas <= {0, 255}, 'tab icon alpha must stay binary, got %s' % sorted(alphas)
    opaque = sum(1 for a in tpx[3::4] if a)
    assert opaque > args.tab_size * args.tab_size // 4, \
        'tab icon is only %d opaque pixels, the crop is probably empty' % opaque
    print('self-check ok: tab icon %d opaque pixels, %d distinct colours'
          % (opaque, len({bytes(tpx[i * 4:i * 4 + 3]) for i in range(args.tab_size ** 2) if tpx[i * 4 + 3]})))


if __name__ == '__main__':
    main()

