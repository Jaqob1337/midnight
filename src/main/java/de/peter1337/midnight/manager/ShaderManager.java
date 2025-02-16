package de.peter1337.midnight.manager;

import de.peter1337.midnight.render.shader.ShaderSources;
import de.peter1337.midnight.Midnight;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.joml.Matrix4f;
import java.awt.Color;
import java.nio.FloatBuffer;

public class ShaderManager {
    private int vao;
    private int vbo;
    private int shaderProgram;
    private int vertexShader;
    private int fragmentShader;
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private boolean initialized = false;

    // Cache uniform locations
    private int clipBoundsLocation = -1;
    private int clipRadiusLocation = -1;
    private int transformLocation = -1;
    private int resolutionLocation = -1;
    private int rectLocation = -1;
    private int radiusLocation = -1;
    private int smoothingLocation = -1;
    private int colorLocation = -1;
    private int outlineColorLocation = -1;
    private int outlineWidthLocation = -1;
    private int guiScaleLocation = -1;

    public void init(int width, int height) {
        if (initialized) {
            cleanup();
        }

        try {
            setupShaders();
            setupBuffers();
            cacheUniformLocations();
            initialized = true;
            Midnight.LOGGER.info("ShaderManager initialized successfully");
        } catch (Exception e) {
            Midnight.LOGGER.error("Failed to initialize ShaderManager", e);
            cleanup();
        }
    }

    private void cacheUniformLocations() {
        GL20.glUseProgram(shaderProgram);
        clipBoundsLocation = GL20.glGetUniformLocation(shaderProgram, "clipBounds");
        clipRadiusLocation = GL20.glGetUniformLocation(shaderProgram, "clipRadius");
        transformLocation = GL20.glGetUniformLocation(shaderProgram, "transform");
        resolutionLocation = GL20.glGetUniformLocation(shaderProgram, "resolution");
        rectLocation = GL20.glGetUniformLocation(shaderProgram, "rect");
        radiusLocation = GL20.glGetUniformLocation(shaderProgram, "radius");
        smoothingLocation = GL20.glGetUniformLocation(shaderProgram, "smoothing");
        colorLocation = GL20.glGetUniformLocation(shaderProgram, "color");
        outlineColorLocation = GL20.glGetUniformLocation(shaderProgram, "outlineColor");
        outlineWidthLocation = GL20.glGetUniformLocation(shaderProgram, "outlineWidth");
        guiScaleLocation = GL20.glGetUniformLocation(shaderProgram, "guiScale");
        GL20.glUseProgram(0);
    }

    private void setupShaders() {
        vertexShader = createShader(GL20.GL_VERTEX_SHADER, ShaderSources.VERTEX_SHADER_SOURCE);
        fragmentShader = createShader(GL20.GL_FRAGMENT_SHADER, ShaderSources.FRAGMENT_SHADER_SOURCE);
        if (vertexShader == 0 || fragmentShader == 0) {
            throw new RuntimeException("Failed to create shaders");
        }

        shaderProgram = createProgram(vertexShader, fragmentShader);
        if (shaderProgram == 0) {
            throw new RuntimeException("Failed to create shader program");
        }
    }

    private void setupBuffers() {
        float[] vertices = {
                -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 0.0f, 1.0f
        };

        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
        vertexBuffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);

        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        GL20.glEnableVertexAttribArray(1);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    private int createShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            Midnight.LOGGER.error("Shader compilation error: " + log);
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private int createProgram(int vertexShader, int fragmentShader) {
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL20.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            Midnight.LOGGER.error("Shader program linking error: " + log);
            GL20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    public void setClipBounds(float x, float y, float width, float height, float radius) {
        if (!initialized) return;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        GL20.glUseProgram(shaderProgram);

        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float convertedY = screenHeight - y - height;

        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, x, convertedY, width, height);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, radius);
        }

        GL20.glUseProgram(previousProgram);
    }

    public void resetClipBounds() {
        if (!initialized) return;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);

        GL20.glUseProgram(shaderProgram);
        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, 0, 0, 0, 0);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, 0);
        }

        GL20.glUseProgram(previousProgram);
    }

    public void drawShape(float x, float y, float width, float height, float radius, float smoothing,
                          Color fillColor, Color outlineColor, float outlineWidth) {
        if (!initialized) return;

        // Save complete GL state
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] previousTextures = new int[32];  // Save all texture bindings
        for (int i = 0; i < 32; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            previousTextures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        int previousVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int previousArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        boolean previousBlend = GL11.glGetBoolean(GL11.GL_BLEND);
        int previousBlendSrcRGB = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousBlendDstRGB = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int previousBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int previousBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        boolean previousDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        boolean previousCullFace = GL11.glGetBoolean(GL11.GL_CULL_FACE);
        int[] previousViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, previousViewport);

        try {
            // Reset texture state
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

            // Set our required state
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(
                    GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA
            );
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // Use our shader
            GL20.glUseProgram(shaderProgram);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

            float guiScale = (float) MinecraftClient.getInstance().options.getGuiScale().getValue();
            int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float convertedY = screenHeight - y - height;

            // Set uniforms using cached locations
            if (resolutionLocation != -1) GL20.glUniform2f(resolutionLocation, width, height);
            if (rectLocation != -1) GL20.glUniform4f(rectLocation, x, convertedY, width, height);
            if (radiusLocation != -1) GL20.glUniform1f(radiusLocation, radius);
            if (smoothingLocation != -1) GL20.glUniform1f(smoothingLocation, smoothing);
            if (colorLocation != -1) {
                GL20.glUniform4f(colorLocation,
                        fillColor.getRed() / 255f,
                        fillColor.getGreen() / 255f,
                        fillColor.getBlue() / 255f,
                        fillColor.getAlpha() / 255f
                );
            }

            if (outlineColor != null && outlineColorLocation != -1) {
                GL20.glUniform4f(outlineColorLocation,
                        outlineColor.getRed() / 255f,
                        outlineColor.getGreen() / 255f,
                        outlineColor.getBlue() / 255f,
                        outlineColor.getAlpha() / 255f
                );
                if (outlineWidthLocation != -1) GL20.glUniform1f(outlineWidthLocation, outlineWidth);
            } else if (outlineColorLocation != -1) {
                GL20.glUniform4f(outlineColorLocation, 0f, 0f, 0f, 0f);
                if (outlineWidthLocation != -1) GL20.glUniform1f(outlineWidthLocation, 0f);
            }

            if (guiScaleLocation != -1) GL20.glUniform1f(guiScaleLocation, guiScale);
            if (transformLocation != -1) {
                Matrix4f transform = new Matrix4f().identity();
                transform.get(matrixBuffer);
                GL20.glUniformMatrix4fv(transformLocation, false, matrixBuffer);
            }

            // Draw
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        } finally {
            // Restore complete GL state in reverse order
            GL11.glViewport(previousViewport[0], previousViewport[1], previousViewport[2], previousViewport[3]);

            if (previousCullFace) GL11.glEnable(GL11.GL_CULL_FACE);
            else GL11.glDisable(GL11.GL_CULL_FACE);

            if (previousDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST);
            else GL11.glDisable(GL11.GL_DEPTH_TEST);

            if (previousBlend) GL11.glEnable(GL11.GL_BLEND);
            else GL11.glDisable(GL11.GL_BLEND);

            GL14.glBlendFuncSeparate(
                    previousBlendSrcRGB, previousBlendDstRGB,
                    previousBlendSrcAlpha, previousBlendDstAlpha
            );

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousArrayBuffer);
            GL30.glBindVertexArray(previousVAO);

            // Restore all texture bindings
            for (int i = 31; i >= 0; i--) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextures[i]);
            }
            GL13.glActiveTexture(previousActiveTexture);

            GL20.glUseProgram(previousProgram);
        }
    }

    public void cleanup() {
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
        if (vertexShader != 0) GL20.glDeleteShader(vertexShader);
        if (fragmentShader != 0) GL20.glDeleteShader(fragmentShader);
        if (shaderProgram != 0) GL20.glDeleteProgram(shaderProgram);

        vao = 0;
        vbo = 0;
        vertexShader = 0;
        fragmentShader = 0;
        shaderProgram = 0;
        initialized = false;
    }
}