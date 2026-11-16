"""Where is the painted grid on the gomoku / wchess boards?

Same question as ``chess_grid_probe.py`` asked for the Chinese chessboard, and
for the same reason: the drawn pieces must land on the *painted* lattice, not
on a lattice that only the click maths believes in.

Both boards are a single box cube, so the top face samples one box-UV rect:

    rect = (uv.x + size.z, uv.y, size.x, size.z)      # model's declared texture space

and that rect is mapped onto the world extents [loX..hiX] x [loZ..hiZ] by the
same maths ``GeoCube`` uses (see the mirror below).  Scan the rect for straight
dark runs, convert them to world coordinates, and compare with the intersections
the click maths assumes:

    wchess:  8 x 8 cells of 0.25, cell centres at -0.875 + 0.25 * k
             (WChessUtil#getClickPosition: floor((x + 1) / 0.25), so cell k
              covers [-1 + 0.25k, -1 + 0.25(k+1)))
    gomoku:  15 x 15 stones, spacing 0.1316
             (BlockGomoku#getChessPos: xStart = offset + i * 0.1316, box 0.07)

Usage:  python board_grid_probe.py wchess | gomoku
"""
import json
import os
import sys

from png_probe import load

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid')


def geometry(name):
    path = os.path.join(ASSETS, 'models', 'bedrock', 'block', name + '.json')
    d = json.load(open(path, encoding='utf-8'))
    geo = d['minecraft:geometry'][0]
    return geo


def biggest_cube(geo):
    return max((c for b in geo['bones'] for c in (b.get('cubes') or [])),
               key=lambda c: c['size'][0] * c['size'][2])


def cube_extent(cube):
    """Mirror of GeoCube's vertex maths, in blocks."""

    def lerp(p):
        return -(p + 1.0) / 16.0

    # vertices run from (origin - inflate) to (origin + size + inflate)
    # with x negated:  oX = -(ox + sx) / 16,  sX = sx / 16
    ox, oy, oz = cube['origin']
    sx, sy, sz = cube['size']
    f = cube.get('inflate', 0) / 16.0
    oX = -(ox + sx) / 16.0
    oY = oy / 16.0
    oZ = oz / 16.0
    sX, sY, sZ = sx / 16.0, sy / 16.0, sz / 16.0
    return (oX - f, oX + sX + f,
            oY - f, oY + sY + f,
            oZ - f, oZ + sZ + f)


def scan(name, cube, step, origin, count, thr=110, frac=0.5):
    tex = os.path.join(ASSETS, 'textures', 'bedrock', 'block', name + '.png')
    geo = geometry(name)
    tw = geo['description'].get('texture_width', 16)
    th = geo['description'].get('texture_height', 16)
    uv = cube['uv']
    sx, sy, sz = cube['size']
    loX, hiX, loY, hiY, loZ, hiZ = cube_extent(cube)

    print('%s: cube origin %s size %s inflate %s uv %s'
          % (name, cube['origin'], cube['size'], cube.get('inflate', 0), uv))
    print('  world x %.5f..%.5f   y %.5f..%.5f   z %.5f..%.5f'
          % (loX, hiX, loY, hiY, loZ, hiZ))

    # box-UV "up" rect, in the model's declared texture space
    u0, v0, uw, vh = uv[0] + sz, uv[1], sx, sz
    print('  up rect in model uv: u %.1f..%.1f  v %.1f..%.1f'
          % (u0, u0 + uw, v0, v0 + vh))

    w, h, bpp, px = load(tex)
    print('  texture %dx%d bpp=%d, model declares %dx%d' % (w, h, bpp, tw, th))
    su, sv = w / float(tw), h / float(th)
    x0, x1 = int(round(u0 * su)), int(round((u0 + uw) * su))
    y0, y1 = int(round(v0 * sv)), int(round((v0 + vh) * sv))
    print('  up rect in texels: x %d..%d  y %d..%d' % (x0, x1, y0, y1))

    def rgb(x, y):
        i = (y * w + x) * bpp
        return px[i], px[i + 1], px[i + 2], (px[i + 3] if bpp == 4 else 255)

    def dark(x, y):
        r, g, b, a = rgb(x, y)
        return a > 8 and (r + g + b) / 3.0 < thr

    cw, ch = x1 - x0, y1 - y0
    vs = [x for x in range(x0, x1)
          if sum(1 for y in range(y0, y1, 2) if dark(x, y)) >= (ch / 2.0) * frac]
    hs = [y for y in range(y0, y1)
          if sum(1 for x in range(x0, x1, 2) if dark(x, y)) >= (cw / 2.0) * frac]

    def to_world_x(tx):
        return loX + (tx + 0.5 - x0) / float(cw) * (hiX - loX)

    def to_world_z(ty):
        return hiZ - (ty + 0.5 - y0) / float(ch) * (hiZ - loZ)

    print('\n  vertical runs at texels %s' % vs)
    for tx in vs:
        wx = to_world_x(tx)
        k = (wx - origin) / step
        print('    texel %4d  world x %+8.4f   lattice index %+7.3f' % (tx, wx, k))
    print('  click lattice: %s' % ', '.join('%+.3f' % (origin + i * step) for i in range(count)))

    print('\n  horizontal runs at texels %s' % hs)
    for ty in hs:
        wz = to_world_z(ty)
        j = (wz - origin) / step
        print('    texel %4d  world z %+8.4f   lattice index %+7.3f' % (ty, wz, j))
    print('  click lattice: %s' % ', '.join('%+.3f' % (origin + j * step) for j in range(count)))


def main():
    what = sys.argv[1] if len(sys.argv) > 1 else 'wchess'
    if what == 'wchess':
        # 8 x 8 cells of 0.25; cell k spans [-1 + 0.25k, -1 + 0.25(k+1)]
        scan('wchess', biggest_cube(geometry('wchess')), 0.25, -1.0, 5, thr=150, frac=0.35)
    elif what == 'gomoku':
        # 15 x 15 stones, spacing 0.1316, first box at about -0.925
        scan('gomoku', biggest_cube(geometry('gomoku')), 0.1316, -0.925, 15, thr=150, frac=0.35)
    else:
        raise SystemExit('unknown board: ' + what)


if __name__ == '__main__':
    main()

