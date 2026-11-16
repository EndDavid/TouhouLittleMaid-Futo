"""Where is the painted grid on the chessboard's top face?

The board is one box cube.  Its box-UV "up" rect is what the top face samples:

    rect = (uv.x + size.z, uv.y, size.x, size.z)      # in the model's declared texture space

and that rect is mapped onto the world-space extent [loX..hiX] x [loZ..hiZ].
Scan the rect for straight dark runs to recover the grid, convert to world
coordinates, and compare with the intersections the *click* maths assumes:

    localX = k * 0.304 - 1.365     (k = 0..8)
    localZ = j * 0.304 - 1.370     (j = 0..9)

If these disagree the drawn pieces and the clickable cells disagree too.
"""
import json
import os
import sys

from png_probe import load

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
MODEL = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                     'models', 'bedrock', 'block', 'cchess.json')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                   'textures', 'bedrock', 'block', 'cchess.png')

STEP = 0.304
ORIGIN_X = -1.365
ORIGIN_Z = -1.370

d = json.load(open(MODEL, encoding='utf-8'))
geo = d['minecraft:geometry'][0]
tw = geo['description']['texture_width']
th = geo['description']['texture_height']

# the biggest cube is the board slab
main = max((c for b in geo['bones'] for c in (b.get('cubes') or [])),
           key=lambda c: c['size'][0] * c['size'][2])
ox, oy, oz = main['origin']
sx, sy, sz = main['size']
infl = main.get('inflate', 0)
uv = main['uv']

# mirror of GeoCube's maths
oX = -(ox + sx) / 16.0
oY = oy / 16.0
oZ = oz / 16.0
sX, sY, sZ = sx * 0.0625, sy * 0.0625, sz * 0.0625
f = infl / 16.0
loX, hiX = oX - f, oX + sX + f
loY, hiY = oY - f, oY + sY + f
loZ, hiZ = oZ - f, oZ + sZ + f
print('board cube %s size %s inflate %s uv %s' % (main['origin'], main['size'], infl, uv))
print('world extent x %.6f..%.6f  y %.6f..%.6f  z %.6f..%.6f' % (loX, hiX, loY, hiY, loZ, hiZ))

# box-UV up rect, in texture units
u0, v0, uw, vh = uv[0] + sz, uv[1], sx, sz
print('up rect in texture units: u %.1f..%.1f  v %.1f..%.1f' % (u0, u0 + uw, v0, v0 + vh))

w, h, bpp, px = load(TEX)
print('texture %dx%d bpp=%d, model declares %dx%d' % (w, h, bpp, tw, th))
su, sv = w / float(tw), h / float(th)
print('texel scale %.4f %.4f' % (su, sv))

x0, x1 = int(round(u0 * su)), int(round((u0 + uw) * su))
y0, y1 = int(round(v0 * sv)), int(round((v0 + vh) * sv))
print('up rect in texels: x %d..%d  y %d..%d' % (x0, x1, y0, y1))


def rgb(x, y):
    i = (y * w + x) * bpp
    return px[i], px[i + 1], px[i + 2], (px[i + 3] if bpp == 4 else 255)


def dark(x, y, thr=110):
    r, g, b, a = rgb(x, y)
    return a > 8 and (r + g + b) / 3.0 < thr


# column / row darkness profiles inside the rect
cols = []
for x in range(x0, x1):
    n = sum(1 for y in range(y0, y1) if dark(x, y))
    cols.append((n, x))
rows = []
for y in range(y0, y1):
    n = sum(1 for x in range(x0, x1) if dark(x, y))
    rows.append((n, y))

cw = x1 - x0
ch = y1 - y0
print('\nvertical lines (column dark count >= 60%% of %d):' % ch)
vs = [x for n, x in cols if n >= ch * 0.6]
print('  texels', vs)
print('\nhorizontal lines (row dark count >= 60%% of %d):' % cw)
hs = [y for n, y in rows if n >= cw * 0.6]
print('  texels', hs)


def to_world_x(tx):
    return loX + (tx + 0.5 - x0) / float(cw) * (hiX - loX)


def to_world_z(ty):
    return hiZ - (ty + 0.5 - y0) / float(ch) * (hiZ - loZ)


print('\nvertical lines -> world x  vs click intersections')
for tx in vs:
    wx = to_world_x(tx)
    k = (wx - ORIGIN_X) / STEP
    print('  texel %3d  world x %+8.4f   nearest intersection index %+6.2f' % (tx, wx, k))
print('click intersections: %s' % ', '.join('%+.3f' % (ORIGIN_X + i * STEP) for i in range(9)))

print('\nhorizontal lines -> world z  vs click intersections')
for ty in hs:
    wz = to_world_z(ty)
    j = (wz - ORIGIN_Z) / STEP
    print('  texel %3d  world z %+8.4f   nearest intersection index %+6.2f' % (ty, wz, j))
print('click intersections: %s' % ', '.join('%+.3f' % (ORIGIN_Z + j * STEP) for j in range(10)))

