"""Does the drawn stone land on the intersection the click returns?

Lattice (measured from the model, see board_grid_probe.py):

    lineAt(k) = -0.927152 + k * 0.132450      k = 0..14, block-centre relative

The click side is fixed: BlockGomoku#use takes the hit in plain block-local
axes and rounds it to the nearest line, so clicking world point lineAt(k)
returns index k, whatever FACING the board has.  (It does not rotate the hit
vector -- unlike BlockCChess, which is why the chessboard needs a different
treatment.)

Three render conventions can be written, and they differ only in whether the
stone is drawn inside the board's pose and whether the index is negated:

    OLD   inside the pose, negated     world = R(theta) * (-lineAt(k))
    PLAIN inside the pose, not negated world = R(theta) *  lineAt(k)
    NEW   outside the pose             world =          lineAt(k)

PLAIN is what "remove the negation" alone would give; NEW is what the code does
now (the stones are drawn without applyFacing at all).  A convention is correct
only if the stone's world position rounds back to k for every facing.

``Vector3f.YP.rotationDegrees`` rotates (x, z) as (x cos + z sin, -x sin + z cos)
in 1.18.2; the opposite sign is tested too, because a conclusion that depends on
it would be untrustworthy.
"""
import math

GRID_ORIGIN = -0.927152
GRID_STEP = 0.132450
GRID_SIZE = 15

# Direction#toYRot(): SOUTH=0, WEST=90, NORTH=180, EAST=270.  NORTH is 180, not 0.
FACINGS = [('NORTH', 180), ('SOUTH', 0), ('EAST', 270), ('WEST', 90)]


def line_at(k):
    return GRID_ORIGIN + k * GRID_STEP


def nearest_index(x, z):
    """Index of the lattice point nearest to (x, z), plus its distance."""
    best = None
    for i in range(GRID_SIZE):
        for j in range(GRID_SIZE):
            d = math.hypot(x - line_at(i), z - line_at(j))
            if best is None or d < best[0]:
                best = (d, i, j)
    return best


def rotate(x, z, degrees, sign):
    t = math.radians(degrees)
    c, s = math.cos(t), math.sin(t)
    if sign > 0:
        return x * c + z * s, -x * s + z * c
    return x * c - z * s, x * s + z * c


def evaluate(convention, theta, sign):
    negate = convention == 'OLD'
    inside_pose = convention in ('OLD', 'PLAIN')
    worst = 0.0
    mismatches = 0
    mapping = []
    for k in range(GRID_SIZE):
        p = line_at(k) * (-1.0 if negate else 1.0)
        if inside_pose:
            wx, wz = rotate(p, p, theta, sign)
        else:
            wx, wz = p, p
        dist, i, j = nearest_index(wx, wz)
        worst = max(worst, dist)
        mapping.append(i)
        if (i, j) != (k, k):
            mismatches += 1
    return worst, mismatches, mapping


def main():
    print('lattice %+.6f .. %+.6f, step %.6f, %d lines'
          % (line_at(0), line_at(GRID_SIZE - 1), GRID_STEP, GRID_SIZE))
    print('click -> index is the identity by construction, for every facing\n')

    for convention, note in (
            ('OLD', 'stone inside the pose, negated (what shipped)'),
            ('PLAIN', 'stone inside the pose, not negated'),
            ('NEW', 'stone outside the pose (current code)')):
        print('=' * 78)
        print('%s   %s' % (convention, note))
        for sign in (+1, -1):
            print('  rotation sign %+d' % sign)
            for name, theta in FACINGS:
                worst, bad, mapping = evaluate(convention, theta, sign)
                print('    %-5s theta=%3d  on-a-line err %.9f   wrong index %2d / 15   k -> %s'
                      % (name, theta, worst, bad,
                         ' '.join(str(v) for v in mapping[:6]) + ' ...'))
        print()

    print('=' * 78)
    print('Conclusion')
    print('  OLD   is right only for SOUTH (180 degrees); at NORTH it maps k -> 14-k,')
    print('        and at EAST / WEST it swaps both axes.  "Click one corner, the')
    print('        stone lands in the other corner" is that k -> 14-k mirror.')
    print('  PLAIN is right only for NORTH: the pose still turns the stone, so')
    print('        merely dropping the negation moves the bug to the other facings.')
    print('  NEW   is the identity for all four, because the stone is now drawn in')
    print('        the same unrotated frame the click is measured in.  The board art')
    print('        is still rotated, but a 15x15 square lattice centred on the block')
    print('        centre maps onto itself, so the stone lands on a painted line.')


if __name__ == '__main__':
    main()

