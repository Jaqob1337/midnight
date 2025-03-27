package de.peter1337.midnight.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

/**
 * Utility class for rendering animated GIFs in Minecraft
 */
public class GifRenderer {

    private static final Map<String, GifData> loadedGifs = new HashMap<>();

    /**
     * Class to hold all the data for a GIF
     */
    private static class GifData {
        final List<GifFrame> frames = new ArrayList<>();
        long lastFrameTime;
        int currentFrameIndex;
        boolean isPlaying = true;

        // Dynamic scaling support
        float targetWidth, targetHeight;

        public GifData(float width, float height) {
            this.targetWidth = width;
            this.targetHeight = height;
            this.lastFrameTime = System.currentTimeMillis();
            this.currentFrameIndex = 0;
        }

        public GifFrame getCurrentFrame() {
            if (frames.isEmpty()) return null;
            return frames.get(currentFrameIndex);
        }

        public void update() {
            if (!isPlaying || frames.isEmpty()) return;

            long currentTime = System.currentTimeMillis();
            GifFrame currentFrame = frames.get(currentFrameIndex);

            // Check if it's time to advance to the next frame
            if (currentTime - lastFrameTime > currentFrame.delay) {
                currentFrameIndex = (currentFrameIndex + 1) % frames.size();
                lastFrameTime = currentTime;
            }
        }

        public void setDimensions(float width, float height) {
            this.targetWidth = width;
            this.targetHeight = height;
        }
    }

    /**
     * Class to represent a single frame in a GIF animation
     */
    private static class GifFrame {
        final Identifier textureId;
        final int delay; // in milliseconds

        public GifFrame(Identifier textureId, int delay) {
            this.textureId = textureId;
            this.delay = delay;
        }
    }

    /**
     * Loads a GIF from the given resource path.
     * This should typically be called during initialization.
     *
     * @param resourcePath The path to the GIF resource, e.g. "textures/gui/animation.gif"
     * @param identifier The identifier to use for the gif, e.g. "animation"
     * @param width Initial target width for rendering
     * @param height Initial target height for rendering
     * @throws IOException If there's an error loading the GIF
     */
    public static void loadGif(String resourcePath, String identifier, float width, float height) throws IOException {
        // Check if already loaded
        if (loadedGifs.containsKey(identifier)) {
            // Update dimensions if needed
            loadedGifs.get(identifier).setDimensions(width, height);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        InputStream is = MinecraftClient.class.getResourceAsStream("/assets/midnight/" + resourcePath);

        if (is == null) {
            throw new IOException("Could not find GIF resource: " + resourcePath);
        }

        // Create a new GifData instance
        GifData gifData = new GifData(width, height);

        try {
            // Get a GIF reader
            ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();

            // Create an ImageInputStream from the resource
            ImageInputStream stream = ImageIO.createImageInputStream(is);
            reader.setInput(stream);

            // Get the number of frames
            int numFrames = reader.getNumImages(true);

            // Process each frame
            for (int i = 0; i < numFrames; i++) {
                // Read the frame image
                BufferedImage frame = reader.read(i);

                // Get the frame delay from metadata
                IIOMetadata metadata = reader.getImageMetadata(i);
                int delay = getFrameDelay(metadata);

                // Convert BufferedImage to NativeImage and create texture
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(frame, "png", baos);

                NativeImage nativeImage = NativeImage.read(NativeImage.Format.RGBA,
                        new ByteArrayInputStream(baos.toByteArray()));

                // Create a unique identifier for this frame
                Identifier frameId = Identifier.of("midnight", "gif/" + identifier + "/frame" + i);

                // Register the texture and store frame data
                NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
                client.getTextureManager().registerTexture(frameId, texture);

                // Add the frame to our GIF data
                gifData.frames.add(new GifFrame(frameId, delay));
            }

            // Store the loaded GIF
            loadedGifs.put(identifier, gifData);

        } catch (Exception e) {
            throw new IOException("Error loading GIF: " + e.getMessage(), e);
        } finally {
            is.close();
        }
    }

    /**
     * Extract frame delay from GIF metadata (in milliseconds)
     */
    private static int getFrameDelay(IIOMetadata metadata) {
        final int DEFAULT_DELAY = 100; // Default to 100ms if delay not specified

        try {
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
            IIOMetadataNode gce = (IIOMetadataNode) root.getElementsByTagName("GraphicControlExtension").item(0);

            if (gce != null) {
                // Get the delay value in hundredths of a second
                String delayStr = gce.getAttribute("delayTime");
                if (delayStr != null && !delayStr.isEmpty()) {
                    try {
                        int delay = Integer.parseInt(delayStr) * 10; // Convert to milliseconds
                        // GIF specs say 0 should be treated as 100ms
                        return delay == 0 ? 100 : delay;
                    } catch (NumberFormatException e) {
                        // Ignore and use default
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors and use default
        }

        return DEFAULT_DELAY;
    }

    /**
     * Updates all loaded GIFs' animation state.
     * Should be called every frame.
     */
    public static void updateAll() {
        for (GifData gifData : loadedGifs.values()) {
            gifData.update();
        }
    }

    /**
     * Renders a loaded GIF at the specified position
     *
     * @param context    The DrawContext for rendering
     * @param identifier The identifier used when loading the GIF
     * @param x1
     * @param v
     * @param x          The x position to render at
     * @param y          The y position to render at
     * @param clipX      The clip region x position (0 for no clipping)
     * @param clipY      The clip region y position (0 for no clipping)
     * @param clipWidth  The clip region width (Integer.MAX_VALUE for no clipping)
     * @param clipHeight The clip region height (Integer.MAX_VALUE for no clipping)
     * @return True if the GIF was rendered, false if not found
     */
    public static boolean renderGif(DrawContext context, String identifier,
                                    float x1, float v, float x, float y,
                                    float clipX, float clipY,
                                    float clipWidth, float clipHeight) {
        GifData gifData = loadedGifs.get(identifier);
        if (gifData == null) return false;

        GifFrame currentFrame = gifData.getCurrentFrame();
        if (currentFrame == null) return false;

        // Render the current frame
        de.peter1337.midnight.manager.TextureManager.drawTexture(
                context,
                currentFrame.textureId,
                x, y,
                gifData.targetWidth, gifData.targetHeight,
                clipX, clipY, clipWidth, clipHeight
        );

        return true;
    }

    /**
     * Sets whether a loaded GIF should be playing or paused
     *
     * @param identifier The identifier of the GIF
     * @param playing True to play, false to pause
     * @return True if the GIF was found, false otherwise
     */
    public static boolean setPlaying(String identifier, boolean playing) {
        GifData gifData = loadedGifs.get(identifier);
        if (gifData == null) return false;

        gifData.isPlaying = playing;
        return true;
    }

    /**
     * Resets a GIF to its first frame
     *
     * @param identifier The identifier of the GIF
     * @return True if the GIF was found, false otherwise
     */
    public static boolean resetGif(String identifier) {
        GifData gifData = loadedGifs.get(identifier);
        if (gifData == null) return false;

        gifData.currentFrameIndex = 0;
        gifData.lastFrameTime = System.currentTimeMillis();
        return true;
    }

    /**
     * Updates the dimensions of a loaded GIF
     *
     * @param identifier The identifier of the GIF
     * @param width The new width for rendering
     * @param height The new height for rendering
     * @return True if the GIF was found, false otherwise
     */
    public static boolean setGifDimensions(String identifier, float width, float height) {
        GifData gifData = loadedGifs.get(identifier);
        if (gifData == null) return false;

        gifData.targetWidth = width;
        gifData.targetHeight = height;
        return true;
    }
}