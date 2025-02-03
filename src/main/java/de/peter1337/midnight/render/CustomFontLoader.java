package de.peter1337.midnight.render;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

public class CustomFontLoader {

    /**
     * Loads a TrueType font from the mod’s resource folder.
     *
     * @param path the path to the font file (e.g., "/assets/midnight/fonts/Liter.ttf")
     * @param size the desired font size
     * @return the loaded and derived Font; if loading fails, a fallback font is returned.
     */
    public static Font loadFont(String path, float size) {
        try (InputStream is = CustomFontLoader.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Font resource not found: " + path);
            }
            Font baseFont = Font.createFont(Font.TRUETYPE_FONT, is);
            return baseFont.deriveFont(Font.PLAIN, size);
        } catch (FontFormatException | IOException e) {
            e.printStackTrace();
            return new Font("Arial", Font.PLAIN, (int) size);
        }
    }
}
