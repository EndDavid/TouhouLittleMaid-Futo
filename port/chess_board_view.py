"""Print the chessboard's painted TOP FACE as ASCII art.

The board is one box cube in cchess.json:

    origin [-32, -8, -32]  size [64, 18, 64]  inflate -8  uv [0, 0]

so its box-UV "up" rect is

    u  = uv.x + size.z  ..  + size.x   =  64 .. 128   (declared units)
    v  = uv.y           ..  + size.z   =   0 ..  64

The model declares 256x256 but the shipped png is 512x512, so the rect lands on
texels x 128..256, y 0..128 of the image.  Printing that region lets us see the
painted grid, the two reset bands and the river characters without guessing --
which matters because "where is the grid inside the uv rect" is exactly what
decides whether the pieces sit on the lines.
"""
import os

from png_probe import load

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, '..')
TEX = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'touhou_little_maid',
                   'textures', 'bedrock', 'block', 'cchess.png')

# the box-UV "up" rect of the board slab, in actual texels
X0, X1 = 128, 256
Y0, Y1 = 0, 128
STEP = 2


def main():
    w, h, bpp, px = load(TEX)
    print('%s: %dx%d bpp=%d' % (TEX, w, h, bpp))
    print('up rect in texels: x %d..%d  y %d..%d  (each char = %d texels)'
          % (X0, X1, Y0, Y1, STEP))

    tens = '    ' + ''.join(str((X0 + c * STEP) // 100 % 10) for c in range((X1 - X0) // STEP))
    ones = '    ' + ''.join(str((X0 + c * STEP) // 10 % 10) for c in range((X1 - X0) // STEP))
    print(tens)
    print(ones)

    for y in range(Y0, Y1, STEP):
        line = ''
        for x in range(X0, X1, STEP):
            i = (y * w + x) * bpp
            r, g, b = px[i], px[i + 1], px[i + 2]
            a = px[i + 3] if bpp == 4 else 255
            v = (r + g + b) / 3.0
            if a < 8:
                line += ' '
            elif v < 70:
                line += '#'
            elif v < 110:
                line += '+'
            elif v < 150:
                line += '-'
            elif v < 200:
                line += '.'
            else:
                line += ' '
        print('%4d %s' % (y, line))


if __name__ == '__main__':
    main()

