import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

public final class CmpPng {
    public static void main(String[] args) throws Exception {
        BufferedImage a = ImageIO.read(new File(args[0]));
        BufferedImage b = ImageIO.read(new File(args[1]));
        System.out.printf("A %s %dx%d%nB %s %dx%d%n",
                args[0], a.getWidth(), a.getHeight(), args[1], b.getWidth(), b.getHeight());
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            System.out.println("SIZE MISMATCH");
            return;
        }
        int w = a.getWidth(), h = a.getHeight();
        int diff = 0;
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (a.getRGB(x, y) != b.getRGB(x, y)) {
                    diff++;
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        System.out.printf("differing pixels: %d / %d (%.1f%%)%n", diff, w * h, 100.0 * diff / (w * h));
        if (diff > 0) {
            System.out.printf("bbox x[%d..%d] y[%d..%d]%n", minX, maxX, minY, maxY);
            for (int i = 0; i < 8 && i < 8; i++) {
                int x = minX + (maxX > minX ? i % (maxX - minX + 1) : 0);
                int y = minY + (maxY > minY ? i % (maxY - minY + 1) : 0);
                System.out.printf("  (%d,%d) A=%08X B=%08X%n", x, y, a.getRGB(x, y), b.getRGB(x, y));
            }
        }
    }
}

