"""Keep the mod's icon files in sync with the artwork in `pack.png`.

Why this exists
---------------
`src/main/resources/pack.png` is the built-in resource pack's icon (the jar has
a root `pack.mcmeta`, so the mod itself is a resource pack).  Its artwork is the
Boutoku-no-Futatsuhime "pack.png" homage, 32x32, and it is the single source of
truth for every icon the mod draws:

    src/main/resources/icon.png          fabric.mod.json "icon".  ModMenu blits a
                                        fixed 32x32 quad, so 32x32 is 1:1.
    .../textures/gui/tab_icon.png        the main creative tab's icon.  It is blitted
                                        into a 24x24 square by the mixin (the
                                        vanilla item-icon slot is 16x16, the tab
                                        art is 28x32), so the file stays 32x32.

Both files are byte-for-byte copies of `pack.png`, deliberately.  There is no
resampling here at all, because the GPU already does exactly one 32 -> drawn
size point sample at draw time, and a pre-scaled copy would either be a second
(compounded) resample or freeze the size: the drawn size lives in
`CreativeModeInventoryScreenMixin#futo$tabIconSize` and the same texture file
has to serve 16, 20 and 24.

History worth remembering: an earlier revision of this script downsampled
32x32 -> 16x16 here, and its output looked like a smear in game.  Part of the
blame was this very file's `read_png`: the filter-type-3 (Average) branch
forgot to add the row above, and `pack.png` uses that filter, so every
downsample was computed from a garbled image.  `pack.png` has no soft alpha and
594 opaque pixels; if `soft_alpha` below reports anything else, the decoder is
broken again.

Usage (from the repository root, no Pillow needed):
    python port/make-icons.py

Re-running is idempotent, so `git status` stays clean.
"""
import struct
import zlib

REPO_PACK = 'src/main/resources/pack.png'
COPIES = (
    'src/main/resources/icon.png',
    'src/main/resources/assets/touhou_little_maid/textures/gui/tab_icon.png',
)


def read_png(path):
    """Return (w, h, color_type, channels, palette, pixels, trns) for a non-interlaced 8-bit PNG."""
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
            # Average: raw = line - floor((left + up) / 2).  Dropping the `up`
            # term here decodes to garbage on rows that use this filter, and the
            # PNG spec allows a different filter per row.
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
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


def soft_alpha(px):
    """Count pixels whose alpha is neither fully opaque nor fully transparent."""
    return sum(1 for a in px[3::4] if 0 < a < 255)


def main():
    w, h, ctype, channels, _palette, px, _trns = read_png(REPO_PACK)
    assert ctype == 6 and channels == 4, \
        'pack.png must be 8-bit RGBA, got colour type %d' % ctype
    assert w == h, 'pack.png must stay square (ModMenu rejects anything else): %dx%d' % (w, h)
    # A few anti-aliased edge pixels are fine (MC blends them over the tab graphic),
    # but a large soft-alpha region means the art was scaled by a filter and will
    # show a halo against the tab.  This art has none at all, and a decoder bug
    # (see the module docstring) shows up here immediately as stray 1/2/4/8/16
    # alphas, so the printed numbers double as a decoder self-test.
    assert soft_alpha(px) * 20 <= w * h, \
        'pack.png has %d soft-alpha pixels, that looks filtered rather than pixel art' % soft_alpha(px)
    print('%s: %dx%d, %d opaque / %d soft pixels'
          % (REPO_PACK, w, h, sum(1 for a in px[3::4] if a == 255), soft_alpha(px)))

    master = open(REPO_PACK, 'rb').read()
    for path in COPIES:
        open(path, 'wb').write(master)

    # Self-checks: a stale or hand-edited icon file is the one failure mode this
    # script exists to prevent, so re-read every product instead of trusting the
    # write above.
    for path in COPIES + (REPO_PACK,):
        cw, ch, cctype, cchannels, _, cpx, _ = read_png(path)
        assert cw == ch, '%s is %dx%d, ModMenu rejects non-square icons' % (path, cw, ch)
        assert cctype == 6 and cchannels == 4, '%s must be 8-bit RGBA' % path
        assert soft_alpha(cpx) == soft_alpha(px), '%s alpha drifted from %s' % (path, REPO_PACK)
        assert open(path, 'rb').read() == master, '%s drifted from %s' % (path, REPO_PACK)
        print('ok  %s  %dx%d  %d bytes' % (path, cw, ch, len(master)))
    print('all three files are byte-identical copies of %s' % REPO_PACK)


if __name__ == '__main__':
    main()
