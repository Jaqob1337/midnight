package de.peter1337.midnight.utils;

import java.awt.Color;

public class ColorUtil {

    /**
     * Creates an ARGB color from individual color components.
     *
     * @param red   the red component (0-255)
     * @param green the green component (0-255)
     * @param blue  the blue component (0-255)
     * @param alpha the alpha component (0-255)
     * @return the ARGB color as an int
     */
    public static int getColor(int red, int green, int blue, int alpha) {
        return (alpha & 0xFF) << 24 |
                (red   & 0xFF) << 16 |
                (green & 0xFF) << 8  |
                (blue  & 0xFF);
    }

    /**
     * Decomposes an ARGB color into its individual components.
     *
     * @param color the ARGB color as an int
     * @return an array containing alpha, red, green, and blue values (in that order)
     */
    public static int[] toARGB(int color) {
        int alpha = (color >> 24) & 0xFF;
        int red   = (color >> 16) & 0xFF;
        int green = (color >> 8)  & 0xFF;
        int blue  = color & 0xFF;
        return new int[]{alpha, red, green, blue};
    }

    /**
     * Blends two colors together based on a specified ratio.
     *
     * @param color1 the first color (ARGB)
     * @param color2 the second color (ARGB)
     * @param ratio  the blend ratio (0.0 to 1.0). A ratio of 0.0 returns color1, 1.0 returns color2.
     * @return the blended color as an ARGB int
     */
    public static int blendColors(int color1, int color2, float ratio) {
        ratio = Math.max(0f, Math.min(1f, ratio));
        int[] argb1 = toARGB(color1);
        int[] argb2 = toARGB(color2);

        int alpha = (int) (argb1[0] * (1 - ratio) + argb2[0] * ratio);
        int red   = (int) (argb1[1] * (1 - ratio) + argb2[1] * ratio);
        int green = (int) (argb1[2] * (1 - ratio) + argb2[2] * ratio);
        int blue  = (int) (argb1[3] * (1 - ratio) + argb2[3] * ratio);

        return getColor(red, green, blue, alpha);
    }

    /**
     * Generates a rainbow color that cycles over time.
     *
     * @param offset an offset to modify the phase of the cycle (useful when generating multiple distinct rainbow colors)
     * @param saturation the saturation for the color (0f to 1f)
     * @param brightness the brightness for the color (0f to 1f)
     * @return the ARGB color as an int (alpha is set to 255)
     */
    public static int getRainbowColor(long offset, float saturation, float brightness) {
        // The hue cycles between 0 and 1 over a period (here using 3600ms for a full cycle)
        float hue = (float) (((System.currentTimeMillis() + offset) % 3600L) / 3600.0);
        int rgb = Color.HSBtoRGB(hue, saturation, brightness);
        // The returned int is in 0x00RRGGBB format, so add full alpha.
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    /**
     * Parses a hexadecimal color string (with or without a '#' prefix) into an ARGB int.
     * If only RGB is provided, it will assume full opacity.
     *
     * @param hex the hexadecimal string (e.g., "#FF00FF" or "FF00FF" or "80FF00FF")
     * @return the ARGB color as an int
     * @throws NumberFormatException if the string cannot be parsed as a hexadecimal number
     */
    public static int parseColor(String hex) {
        String cleanHex = hex.replace("#", "");
        // If hex is in RGB format (6 digits), prepend alpha value FF for full opacity.
        if (cleanHex.length() == 6) {
            cleanHex = "FF" + cleanHex;
        }
        return (int) Long.parseLong(cleanHex, 16);
    }
}
