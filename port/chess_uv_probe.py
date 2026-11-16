"""For every glyph-plane cube in cchess_pieces.json, print the texture region its
box-UV "up" face would sample, so we can tell whether the atlas lines up."""
import json, os, sys
from png_probe import load

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
MODEL = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                     'models', 'entity', 'cchess_pieces.json')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                   'textures', 'bedrock', 'block', 'cchess_pieces.png')

d = json.load(open(MODEL, encoding='utf-8'))
bones = d['minecraft:geometry'][0]['bones']
tw = d['minecraft:geometry'][0]['description']['texture_width']
th = d['minecraft:geometry'][0]['description']['texture_height']

w, h, bpp, px = load(TEX)
SCALE = w / float(tw)          # actual texels per declared unit


def alpha(tx, ty):
    if tx < 0 or ty < 0 or tx >= w or ty >= h:
        return 0
    return px[(ty * w + tx) * 4 + 3] if bpp == 4 else 255


rows = []
for b in bones:
    for c in (b.get('cubes') or []):
        if not any(v == 0 for v in c['size']):
            continue
        uv = c['uv']
        sx, sy, sz = c['size']
        # GeoCube box-UV "up" rect: (uv.x + size.z, uv.y) with size (size.x, size.z)
        u0, v0, uw, vh = uv[0] + sz, uv[1], sx, sz
        tx, ty = u0 * SCALE, v0 * SCALE
        tw_, th_ = uw * SCALE, vh * SCALE
        ink = 0
        tot = 0
        for y in range(int(ty), int(ty + th_)):
            for x in range(int(tx), int(tx + tw_)):
                tot += 1
                if alpha(x, y) > 8:
                    ink += 1
        rows.append((b['name'], uv, (tx, ty, tw_, th_), ink / float(tot)))

print('declared %dx%d, actual %dx%d, scale %.3f' % (tw, th, w, h, SCALE))
for name, uv, rect, frac in rows:
    print('%-12s uv=%-14s rect=(%5.1f,%5.1f) %4.1fx%3.1f  ink=%4.1f%%'
          % (name, uv, rect[0], rect[1], rect[2], rect[3], frac * 100))

if len(sys.argv) > 1:
    want = sys.argv[1]
    for name, uv, rect, frac in rows:
        if name != want:
            continue
        print('\n--- %s uv=%s rect=%s' % (name, uv, rect))
        tx, ty = int(rect[0]), int(rect[1])
        for y in range(ty, ty + int(rect[3])):
            line = ''
            for x in range(tx, tx + int(rect[2])):
                a = alpha(x, y)
                line += '#' if a > 128 else ('+' if a > 8 else '.')
            print('%4d %s' % (y, line))

