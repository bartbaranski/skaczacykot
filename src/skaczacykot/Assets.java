package skaczacykot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.URL;

final class Assets {
    private Assets() {}

    static BufferedImage img(String name) {
        try {
            
            String full = "/resources/images/" + name;
            URL url = Assets.class.getResource(full);
            if (url == null) {
                System.err.println("Nie znaleziono zasobu: " + full);
                return null;
            }
            return ImageIO.read(url);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
