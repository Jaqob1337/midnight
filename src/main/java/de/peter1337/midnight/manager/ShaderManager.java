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

    // Track if program is created to avoid recreation
    private static boolean programCreated = false;
    private static int staticShaderProgram = 0;
    private static int staticVertexShader = 0;
    private static int staticFragmentShader = 0;

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
        // Clean up previously allocated VBO/VAO resources to avoid leaks
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }
        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }

        try {
            // Only create shaders and program once globally
            if (!programCreated) {
                staticVertexShader = createShader(GL20.GL_VERTEX_SHADER, ShaderSources.VERTEX_SHADER_SOURCE);
                staticFragmentShader = createShader(GL20.GL_FRAGMENT_SHADER, ShaderSources.FRAGMENT_SHADER_SOURCE);

                if (staticVertexShader == 0 || staticFragmentShader == 0) {
                    throw new RuntimeException("Failed to create shaders");
                }

                staticShaderProgram = createProgram(staticVertexShader, staticFragmentShader);
                if (staticShaderProgram == 0) {
                    throw new RuntimeException("Failed to create shader program");
                }

                programCreated = true;
                Midnight.LOGGER.info("Created shader program once globally");
            }

            // Use the static shaders and program
            vertexShader = staticVertexShader;
            fragmentShader = staticFragmentShader;
            shaderProgram = staticShaderProgram;

            // Always set up buffers fresh
            setupBuffers();

            // Cache uniform locations once
            cacheUniformLocations();

            // Set resolution uniform
            updateResolution(width, height);

            initialized = true;
            Midnight.LOGGER.info("ShaderManager initialized successfully");
        } catch (Exception e) {
            Midnight.LOGGER.error("Failed to initialize ShaderManager", e);
            cleanup();
        }
    }

    /**
     * Updates resolution-dependent uniforms
     */
    private void updateResolution(int width, int height) {
        if (shaderProgram == 0) return;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(shaderProgram);

        if (resolutionLocation != -1) {
            GL20.glUniform2f(resolutionLocation, width, height);
        }

        // Initialize transform matrix
        if (transformLocation != -1) {
            Matrix4f transform = new Matrix4f().identity();
            transform.get(matrixBuffer);
            GL20.glUniformMatrix4fv(transformLocation, false, matrixBuffer);
        }

        // Reset clip bounds
        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, 0, 0, 0, 0);
        }

        GL20.glUseProgram(previousProgram);
    }

    private void cacheUniformLocations() {
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
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

        GL20.glUseProgram(previousProgram);
    }

    private void setupBuffers() {
        // Using a full-screen quad with UV coordinates
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

        // Position attribute
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);

        // Texture coordinate attribute
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
        if (!initialized || shaderProgram == 0) return;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(shaderProgram);

        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float convertedY = screenHeight - y - height;

        // Set clip bounds uniforms if they exist
        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, x, convertedY, width, height);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, radius);
        }

        GL20.glUseProgram(previousProgram);
    }

    public void resetClipBounds() {
        if (!initialized || shaderProgram == 0) return;

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
        if (!initialized || shaderProgram == 0 || vao == 0) return;

        // Skip if shape has zero dimensions
        if (width <= 0 || height <= 0) return;

        // Save current GL state
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        boolean previousBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousBlendSrc = GL11.glGetInteger(GL11.GL_BLEND_SRC);
        int previousBlendDst = GL11.glGetInteger(GL11.GL_BLEND_DST);

        try {
            // Set up rendering state
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            // Use our shader and VAO
            GL20.glUseProgram(shaderProgram);
            GL30.glBindVertexArray(vao);

            // Get current screen dimensions
            float guiScale = (float) MinecraftClient.getInstance().options.getGuiScale().getValue();
            int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float convertedY = screenHeight - y - height;

            // Calculate optimal smoothing for this shape
            float optimalSmoothing = calculateOptimalSmoothing(width, height, radius, smoothing);

            // Set uniforms
            if (resolutionLocation != -1) GL20.glUniform2f(resolutionLocation, width, height);
            if (rectLocation != -1) GL20.glUniform4f(rectLocation, x, convertedY, width, height);
            if (radiusLocation != -1) GL20.glUniform1f(radiusLocation, radius);
            if (smoothingLocation != -1) GL20.glUniform1f(smoothingLocation, optimalSmoothing);
            if (guiScaleLocation != -1) GL20.glUniform1f(guiScaleLocation, guiScale);

            // Set fill color
            if (colorLocation != -1) {
                GL20.glUniform4f(colorLocation,
                        fillColor.getRed() / 255f,
                        fillColor.getGreen() / 255f,
                        fillColor.getBlue() / 255f,
                        fillColor.getAlpha() / 255f
                );
            }

            // Set outline if needed
            if (outlineColor != null && outlineColorLocation != -1) {
                GL20.glUniform4f(outlineColorLocation,
                        outlineColor.getRed() / 255f,
                        outlineColor.getGreen() / 255f,
                        outlineColor.getBlue() / 255f,
                        outlineColor.getAlpha() / 255f
                );

                if (outlineWidthLocation != -1) {
                    GL20.glUniform1f(outlineWidthLocation, outlineWidth);
                }
            } else if (outlineColorLocation != -1) {
                // No outline - set alpha to 0
                GL20.glUniform4f(outlineColorLocation, 0f, 0f, 0f, 0f);
                if (outlineWidthLocation != -1) {
                    GL20.glUniform1f(outlineWidthLocation, 0f);
                }
            }

            // Draw the quad
            GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        } catch (Exception e) {
            Midnight.LOGGER.error("Error in drawShape: " + e.getMessage());
        } finally {
            // Restore GL state
            GL20.glUseProgram(previousProgram);
            GL30.glBindVertexArray(previousVAO);

            if (!previousBlend) {
                GL11.glDisable(GL11.GL_BLEND);
            } else {
                GL11.glBlendFunc(previousBlendSrc, previousBlendDst);
            }
        }
    }

    /**
     * Calculate optimal smoothing for a shape based on its properties
     */
    private float calculateOptimalSmoothing(float width, float height, float radius, float baseSmoothing) {
        // For small UI elements, use fixed value for consistent appearance
        if (width < 50 || height < 20) {
            return 0.75f;
        }

        // For larger elements, calculate based on size and radius
        float sizeScale = Math.min(1.0f, Math.max(width, height) / 300f);
        float radiusScale = Math.min(1.0f, radius / 15f);

        // Calculate optimal smoothing (higher for larger shapes or radiuses)
        return 0.75f + (sizeScale * 0.5f) + (radiusScale * 0.5f);
    }

    public void cleanup() {
        // Clean up local resources
        if (vao != 0) {
            GL30.glDeleteVertexArrays(vao);
            vao = 0;
        }

        if (vbo != 0) {
            GL15.glDeleteBuffers(vbo);
            vbo = 0;
        }

        // Note: we don't delete the shared shader program and shaders here
        // as they're stored in static variables and shared between instances

        initialized = false;
    }

    /**
     * Full cleanup of all shared resources - call only when shutting down
     */
    public static void cleanupGlobal() {
        if (programCreated) {
            // Delete shader resources when fully shutting down
            if (staticShaderProgram != 0) {
                GL20.glDeleteProgram(staticShaderProgram);
                staticShaderProgram = 0;
            }

            if (staticVertexShader != 0) {
                GL20.glDeleteShader(staticVertexShader);
                staticVertexShader = 0;
            }

            if (staticFragmentShader != 0) {
                GL20.glDeleteShader(staticFragmentShader);
                staticFragmentShader = 0;
            }

            programCreated = false;
        }
    }
}