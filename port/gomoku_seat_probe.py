"""Which stone bowl is beside the maid's seat?

The maid always plays WHITE (client sends Point.WHITE, the server forces it),
so she is supposed to sit next to the white bowl.  She does not -- she sits
next to the black one for some board facings.  This script proves it and
checks the replacement rule.

Geometry, all read from models/bedrock/block/gomoku.json and converted with
GeoCube's own maths (x negated, /16):

    blackBox  origin [-22,0,-13] size [5,1,5]
        x = -(-22+5)/16 .. +5/16  =  1.0625 .. 1.375
        z =      -13/16  .. +5/16 = -0.8125 .. -0.5
    whiteBox  origin [ 17,0, 8] size [5,1,5]
        x = -(17+5)/16 .. +5/16   = -1.375  .. -1.0625
        z =       8/16  .. +5/16  =  0.5    ..  0.8125

i.e. the two bowls are antipodal through the block centre, on the diagonals:

    black ( 1.21875, -0.65625)      white (-1.21875,  0.65625)

The board art then goes through BoardRenderUtil#applyFacing, which is
YP(facing.toYRot()) plus another 180 degrees for the Z-axis facings.  In
1.18.2 `Vector3f.YP.rotationDegrees(a)` maps (x, z) to
(x cos a + z sin a, -x sin a + z cos a) -- the same convention as Vec3#yRot.

The current seat rule is "one and a half blocks along facing.getClockWise()".
"""
import math

BLACK = (1.21875, -0.65625)
WHITE = (-1.21875, 0.65625)

# Direction#toYRot() is (2d data value * 90): SOUTH=0, WEST=1, NORTH=2, EAST=3.
# NORTH is 180, not 0 -- getting that backwards silently swaps exactly the two
# Z-axis facings, which is how this probe first "proved" the wrong thing.
FACINGS = [('NORTH', 180, 'Z'), ('SOUTH', 0, 'Z'), ('EAST', 270, 'X'), ('WEST', 90, 'X')]
CLOCKWISE = {'NORTH': 'EAST', 'EAST': 'SOUTH', 'SOUTH': 'WEST', 'WEST': 'NORTH'}
STEPS = {'NORTH': (0, -1), 'SOUTH': (0, 1), 'EAST': (1, 0), 'WEST': (-1, 0)}
SEAT_DISTANCE = 1.5


def pose_angle(to_y_rot, axis):
    return to_y_rot + (180.0 if axis == 'Z' else 0.0)


def rotate(x, z, degrees):
    t = math.radians(degrees)
    c, s = math.cos(t), math.sin(t)
    return x * c + z * s, -x * s + z * c


def nearest_orthogonal(x, z):
    best, best_dot = None, -1e9
    for name, (sx, sz) in STEPS.items():
        dot = sx * x + sz * z
        if dot > best_dot:
            best, best_dot = name, dot
    return best


def main():
    print('%-6s %-8s %-22s %-22s %-9s %-9s %-9s' % (
        'facing', 'pose', 'black bowl (world)', 'white bowl (world)',
        'seat now', 'beside', 'seat fix'))
    print('-' * 104)
    bad = 0
    fixed_bad = 0
    for name, y_rot, axis in FACINGS:
        angle = pose_angle(y_rot, axis)
        bx, bz = rotate(BLACK[0], BLACK[1], angle)
        wx, wz = rotate(WHITE[0], WHITE[1], angle)

        # current rule: getClockWise
        seat = CLOCKWISE[name]
        sx, sz = STEPS[seat]
        px, pz = sx * SEAT_DISTANCE, sz * SEAT_DISTANCE
        d_white = math.hypot(px - wx, pz - wz)
        d_black = math.hypot(px - bx, pz - bz)
        beside = 'white' if d_white < d_black else 'BLACK'
        if beside != 'white':
            bad += 1

        # replacement rule: whichever orthogonal direction the white bowl lies in
        fix = nearest_orthogonal(wx, wz)
        fx, fz = STEPS[fix]
        qx, qz = fx * SEAT_DISTANCE, fz * SEAT_DISTANCE
        q_white = math.hypot(qx - wx, qz - wz)
        q_black = math.hypot(qx - bx, qz - bz)
        if q_white >= q_black:
            fixed_bad += 1
        print('%-6s %-8s (%+.4f, %+.4f)     (%+.4f, %+.4f)     %-9s %-9s %-9s' % (
            name, '%g' % angle, bx, bz, wx, wz, seat, beside,
            '%s (%s)' % (fix, 'ok' if q_white < q_black else 'WRONG')))

    print()
    print('current rule seats her beside the BLACK bowl for %d of %d facings' % (bad, len(FACINGS)))
    print('replacement rule fails for %d of %d facings' % (fixed_bad, len(FACINGS)))
    print()
    # the reported board is WEST facing: pose angle 90 + 0 (X axis) = 90
    print('distances for the WEST board in the bug report:')
    bx, bz = rotate(BLACK[0], BLACK[1], 90.0)
    wx, wz = rotate(WHITE[0], WHITE[1], 90.0)
    print('  black bowl at (%+.4f, %+.4f), white at (%+.4f, %+.4f)'
          % (bx, bz, wx, wz))
    print('  current seat NORTH (0, -1.5): to black %.3f, to white %.3f'
          % (math.hypot(bx, bz + 1.5), math.hypot(wx, wz + 1.5)))
    print('  fixed    seat SOUTH (0, +1.5): to black %.3f, to white %.3f'
          % (math.hypot(bx, bz - 1.5), math.hypot(wx, wz - 1.5)))


if __name__ == '__main__':
    main()

