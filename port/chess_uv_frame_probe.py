"""Does the port paint the board texture the same way upstream does?
Both pipelines are reduced to one question: at a given point of the *click*
frame (the frame `CChessUtil#getClickPosition` works in), which texel is
sampled?  If the two agree, the port's board looks exactly like upstream's and
nothing about the art needs touching.  If they disagree, the port has an
orientation bug that no amount of grid-lattice measuring can see.
Then the port's top face is rasterised into ASCII, top-down, in world
orientation -- the direct check for "are the river characters readable".
"""
import math
import os
from chess_piece_align_probe import (board_cube, port_up_quad, upstream_up_quad,
                                     uv_map, pose_port, pose_upstream, rot, world_of,
                                     GRID_U, GRID_V, TEX_SCALE)
from png_probe import load
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                   'textures', 'bedrock', 'block', 'cchess.png')
FACINGS = [('SOUTH', 0), ('WEST', 90), ('NORTH', 180), ('EAST', 270)]
def frame(quad, pose, to_yrot):
    """Return f(click) -> (u, v) in declared uv units."""
    um, vm = uv_map(quad, pose)
    inv = rot(-to_yrot)          # click -> posed, since click = R(theta) * posed
    def f(cx, cz):
        px, pz = inv(cx, cz)
        return (um[0] + um[1] * px + um[2] * pz, vm[0] + vm[1] * px + vm[2] * pz)
    return f
def main():
    c = board_cube()
    pq, uq = port_up_quad(c), upstream_up_quad(c)
    probes = [('lattice corner (0,0)', -1.365, -1.370),
              ('lattice corner (8,9)', 1.067, 1.366),
              ('reset strip centre +x', 1.315, 0.0),
              ('reset strip centre -x', -1.315, 0.0)]
    print('%-6s %-22s %-20s %-20s' % ('facing', 'point', 'port (u,v)', 'upstream (u,v)'))
    for name, th in FACINGS:
        n_s = th in (0, 180)
        fp = frame(pq, pose_port(th, n_s), th)
        fu = frame(uq, pose_upstream(th, n_s), th)
        for label, cx, cz in probes:
            a, b = fp(cx, cz), fu(cx, cz)
            flag = '' if (abs(a[0] - b[0]) < 1e-6 and abs(a[1] - b[1]) < 1e-6) else '  <-- DIFFERS'
            print('%-6s %-22s u%7.2f v%7.2f   u%7.2f v%7.2f%s'
                  % (name, label, a[0], a[1], b[0], b[1], flag))
        print()
    # ---- top-down raster of the port's top face, in click/world orientation ----
    th = 0
    f = frame(pq, pose_port(th, True), th)
    w, h, bpp, px = load(TEX)
    su, sv = w / 256.0, h / 256.0
    def sample(u, v):
        tx, ty = int(u * su), int(v * sv)
        if tx < 0 or ty < 0 or tx >= w or ty >= h:
            return ' '
        i = (ty * w + tx) * bpp
        r, g, b = px[i], px[i + 1], px[i + 2]
        val = (r + g + b) / 3.0
        if val < 70:
            return '#'
        if val < 110:
            return '+'
        if val < 150:
            return '-'
        if val < 200:
            return '.'
        return ' '
    N = 64
    print('port top face, click frame, +x to the right, +z downward '
          '(a player standing at +z looks at this with -x on their right):')
    for row in range(N):
        cz = -1.5 + (row + 0.5) * 3.0 / N
        line = ''
        for col in range(N):
            cx = -1.5 + (col + 0.5) * 3.0 / N
            u, v = f(cx, cz)
            line += sample(u, v)
        print(line)
if __name__ == '__main__':
    main()
