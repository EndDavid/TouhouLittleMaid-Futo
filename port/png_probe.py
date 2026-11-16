"""Minimal PNG reader (8-bit RGB/RGBA, non-interlaced) so we can inspect textures
without Pillow. Prints a coarse alpha map plus per-cell ink coverage."""
import struct, sys, zlib

def load(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not a png'
    pos = 8
    idat = b''
    w = h = bitdepth = ctype = None
    while pos < len(data):
        ln, typ = struct.unpack('>I4s', data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b'IHDR':
            w, h, bitdepth, ctype, comp, filt, inter = struct.unpack('>IIBBBBB', body)
            assert bitdepth == 8 and inter == 0 and ctype in (2, 6), (bitdepth, ctype, inter)
        elif typ == b'IDAT':
            idat += body
        elif typ == b'IEND':
            break
    raw = zlib.decompress(idat)
    bpp = 3 if ctype == 2 else 4
    stride = w * bpp
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if f == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xff
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xff
        elif f == 3:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xff
        elif f == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prev[i]
                c = prev[i - bpp] if i >= bpp else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xff
        out[y * stride:(y + 1) * stride] = line
        prev = line
    return w, h, bpp, out


def main(path):
    w, h, bpp, px = load(path)
    print('%s: %dx%d bpp=%d' % (path, w, h, bpp))

    def alpha(x, y):
        if bpp == 4:
            return px[(y * w + x) * 4 + 3]
        r = px[(y * w + x) * 3]
        g = px[(y * w + x) * 3 + 1]
        b = px[(y * w + x) * 3 + 2]
        return 255 if (r, g, b) != (0, 0, 0) else 0

    # ink per 64x64 cell on a 512 grid
    print('ink per 64x64 cell (rows=y/64, cols=x/64), values = opaque pixel count:')
    hdr = '     ' + ''.join('%7d' % c for c in range(0, w // 64))
    print(hdr)
    for cy in range(h // 64):
        row = '%4d ' % cy
        for cx in range(w // 64):
            n = 0
            for y in range(cy * 64, cy * 64 + 64):
                for x in range(cx * 64, cx * 64 + 64):
                    if alpha(x, y) > 8:
                        n += 1
            row += '%7d' % n
        print(row)

    # bounding box of the whole sheet's ink
    xs = [x for y in range(0, h, 2) for x in range(0, w, 2) if alpha(x, y) > 8]
    ys = [y for y in range(0, h, 2) for x in range(0, w, 2) if alpha(x, y) > 8]
    if xs:
        print('ink extent: x %d..%d  y %d..%d' % (min(xs), max(xs), min(ys), max(ys)))


if __name__ == '__main__':
    for p in sys.argv[1:]:
        main(p)
