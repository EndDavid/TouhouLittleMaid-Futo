"""Do the drawn pieces land on the painted intersections?  Computed, not eyeballed.

Two independent pipelines are simulated for the board's top face:

  port      GeoCube + GeoQuad      (models/bedrock/block/cchess.json)
  upstream  BedrockCubeBox#compile (the same json, simplebedrockmodel)

Each is paired with the matching render pose, and for every facing we take the
90 painted grid intersections, the 90 piece positions, and report the worst
piece-to-nearest-intersection distance.  A correct renderer gives ~0.

The upstream pipeline is the reference: if the port matches it for all four
facings, the pose is right.  `OLD` reproduces the pose this port used before
(`YP(toYRot)`, no N/S extra 180) so the regression stays measurable.
"""
import json
import math
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
MODEL = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                     'models', 'bedrock', 'block', 'cchess.json')

STEP = 0.304
ORIGIN_X = -1.365
ORIGIN_Z = -1.370
FILE_LEFT, FILE_RIGHT = 0, 8
RANK_TOP, RANK_BOTTOM = 3, 12
TEX_SCALE = 2.0                      # model declares 256x256, the png is 512x512
GRID_U = [133, 146, 159, 172, 185, 198, 211, 224, 237]
GRID_V = [5, 18, 31, 44, 57, 70, 83, 96, 109, 122]

FACINGS = [('SOUTH', 0), ('WEST', 90), ('NORTH', 180), ('EAST', 270)]
# upstream's per-facing piece start corner (x, z); the block centre is 0.5
START = {0: (-1.365, -1.370), 90: (1.365, -1.370), 180: (1.365, 1.370), 270: (-1.365, 1.370)}


def rot(a_deg):
    """Vec3#yRot: x' = x cos + z sin, z' = z cos - x sin."""
    a = math.radians(a_deg)
    c, s = math.cos(a), math.sin(a)
    return lambda x, z: (x * c + z * s, -x * s + z * c)


def ident(x, z):
    return (x, z)


def pose_port(to_yrot, n_s):
    """applyFacing(): mulPose(YP(toYRot)); if N/S mulPose(YP(180))."""
    a = rot(to_yrot)
    b = rot(180.0) if n_s else ident
    return lambda x, z: a(*b(x, z))


def pose_old(to_yrot):
    """What the port did before: mulPose(YP(toYRot)), no extra 180."""
    return rot(to_yrot)


def pose_upstream(to_yrot, n_s):
    """ZN(180); YN(toYRot); if N/S YN(180)."""
    y = rot(-to_yrot)
    b = rot(-180.0) if n_s else ident

    def f(x, z):
        px, pz = y(*b(x, z))
        return (-px, pz)

    return f


def board_cube():
    d = json.load(open(MODEL, encoding='utf-8'))
    geo = d['minecraft:geometry'][0]
    return max((c for b in geo['bones'] for c in (b.get('cubes') or [])),
               key=lambda c: c['size'][0] * c['size'][2])


def port_up_quad(c):
    """GeoCube up quad {P4,P8,P7,P3} with GeoQuad's non-mirror uv assignment."""
    ox, oy, oz = c['origin']
    sx, sy, sz = c['size']
    infl = c.get('inflate', 0) / 16.0
    oX, oZ = -(ox + sx) / 16.0, oz / 16.0
    loX, hiX = oX - infl, oX + sx / 16.0 + infl
    loZ, hiZ = oZ - infl, oZ + sz / 16.0 + infl
    usx, usy, usz = [float(math.floor(v)) for v in (sx, sy, sz)]
    u1, u2 = c['uv'][0] + usz, c['uv'][0] + usz + usx
    v1, v2 = float(c['uv'][1]), c['uv'][1] + usz
    return [(loX, hiZ, u2, v1), (hiX, hiZ, u1, v1), (hiX, loZ, u1, v2), (loX, loZ, u2, v2)]


def upstream_up_quad(c):
    """BedrockCubeBox: face index 0 is Y1, and Y1 is the TOP in ModelPart space."""
    ox, oy, oz = c['origin']
    sx, sy, sz = c['size']
    delta = c.get('inflate', 0)
    x1 = (ox - delta) / 16.0
    z1 = (oz - delta) / 16.0
    x2 = x1 + (sx + 2 * delta) / 16.0
    z2 = z1 + (sz + 2 * delta) / 16.0
    dx, dy, dz = [float(math.floor(v)) for v in (sx, sy, sz)]
    tx, ty = c['uv']
    us = [tx, tx + dz, tx + dz + dx, tx + dz + dx + dx]
    vs = [ty, ty + dz, ty + dz + dy]
    # VERTEX_ORDER[0] = {X2Y1Z2, X1Y1Z2, X1Y1Z1, X2Y1Z1}, uvOrder {1,2,6,7}
    return [(x2, z2, us[2], vs[0]), (x1, z2, us[1], vs[0]),
            (x1, z1, us[1], vs[1]), (x2, z1, us[2], vs[1])]


def uv_map(quad, pose):
    """Fit u = a + b x + c z and v = d + e x + f z from three corners."""
    pts = [(pose(x, z), u, v) for (x, z, u, v) in quad]
    (p0, u0, v0), (p1, u1, v1), (p2, u2, v2) = pts[0], pts[1], pts[2]
    e1 = (p1[0] - p0[0], p1[1] - p0[1])
    e2 = (p2[0] - p0[0], p2[1] - p0[1])
    det = e1[0] * e2[1] - e1[1] * e2[0]
    assert abs(det) > 1e-9, 'degenerate quad'

    def solve(du1, du2):
        b = (du1 * e2[1] - du2 * e1[1]) / det
        c = (e1[0] * du2 - e2[0] * du1) / det
        return b, c

    bu, cu = solve(u1 - u0, u2 - u0)
    bv, cv = solve(v1 - v0, v2 - v0)
    return (u0 - bu * p0[0] - cu * p0[1], bu, cu), (v0 - bv * p0[0] - cv * p0[1], bv, cv)


def world_of(um, vm, u, v):
    """Solve the two linear equations for (x, z)."""
    _, bu, cu = um
    _, bv, cv = vm
    du = u - um[0]
    dv = v - vm[0]
    det = bu * cv - cu * bv
    return (du * cv - cu * dv) / det, (bu * dv - du * bv) / det


def piece_cells():
    return [(ORIGIN_X + (i - FILE_LEFT) * STEP, ORIGIN_Z + (j - RANK_TOP) * STEP)
            for j in range(RANK_TOP, RANK_BOTTOM + 1)
            for i in range(FILE_LEFT, FILE_RIGHT + 1)]


def report(name, to_yrot, quad, pose, port_style):
    n_s = to_yrot in (0, 180)
    um, vm = uv_map(quad, pose)
    inter = [world_of(um, vm, t / TEX_SCALE, s / TEX_SCALE)
             for t in GRID_U for s in GRID_V]
    if port_style == 'old':
        pieces = [pose(-lx, -lz) for lx, lz in piece_cells()]
    elif port_style == 'new':
        # the port always negates: pose_port == R(180) * R(-theta) for all four
        pieces = [pose(-lx, -lz) for lx, lz in piece_cells()]
    else:
        # upstream applies its start translate OUTSIDE the pose, the step INSIDE
        sx, sz = START[to_yrot]
        pieces = []
        for lx, lz in piece_cells():
            mx, mz = pose(lx - ORIGIN_X, -(lz - ORIGIN_Z))
            pieces.append((sx + mx, sz + mz))
    worst = 0.0
    for p in pieces:
        worst = max(worst, min(math.hypot(p[0] - q[0], p[1] - q[1]) for q in inter))
    print('  %-8s %-4s worst piece -> nearest intersection: %.4f blocks  %s'
          % (name, 'N/S' if n_s else 'E/W', worst, 'OK' if worst < 0.03 else 'OFF'))
    return worst


def main():
    c = board_cube()
    pq, uq = port_up_quad(c), upstream_up_quad(c)
    print('board cube %s size %s inflate %s uv %s' % (c['origin'], c['size'],
                                                      c.get('inflate'), c['uv']))
    print('port     up quad:', ['(%+.3f,%+.3f) u%.0f v%.0f' % q for q in pq])
    print('upstream up quad:', ['(%+.3f,%+.3f) u%.0f v%.0f' % q for q in uq])
    print()
    for name, to_yrot in FACINGS:
        n_s = to_yrot in (0, 180)
        report('NEW', to_yrot, pq, pose_port(to_yrot, n_s), 'new')
        report('upstream', to_yrot, uq, pose_upstream(to_yrot, n_s), 'up')
        report('OLD', to_yrot, pq, pose_old(to_yrot), 'old')
        print()


if __name__ == '__main__':
    main()

