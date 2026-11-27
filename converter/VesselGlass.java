import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * The port's own glass for set-down bottles and wine glasses: a pale, see-through, seamless sheet with two soft
 * highlights and no frame (vanilla's glass block texture put a framed square on every face). Drawn translucent.
 *
 *   java converter/VesselGlass.java lotr/src/main/resources/assets/lotr_port/textures/block/vessel_glass.png
 */
public class VesselGlass {
    public static void main(String[] args) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 16; x++) {
            // a bright streak left of centre, a fainter one on the right, the rest a light blue-white tint
            int a = switch (x) {
                case 3 -> 150;
                case 2, 4 -> 95;
                case 12 -> 90;
                default -> 55;
            };
            int c = x >= 2 && x <= 4 || x == 12 ? 0xF4FAFF : 0xCFE3EE;
            for (int y = 0; y < 16; y++) {
                img.setRGB(x, y, a << 24 | c);
            }
        }
        ImageIO.write(img, "png", new File(args[0]));
    }
}
