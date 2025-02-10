package de.peter1337.midnight.manager.shader;

import de.peter1337.midnight.render.shader.ShaderSources;
import de.peter1337.midnight.Midnight;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.BufferUtils;
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

    public void init(int width, int height) {
        if (initialized) {
            cleanup();
        }

        try {
            setupShaders();
            setupBuffers();

            // Get uniform locations after shader setup
            GL20.glUseProgram(shaderProgram);
            clipBoundsLocation = GL20.glGetUniformLocation(shaderProgram, "clipBounds");
            clipRadiusLocation = GL20.glGetUniformLocation(shaderProgram, "clipRadius");
            GL20.glUseProgram(0);

            initialized = true;
            Midnight.LOGGER.info("ShaderManager initialized successfully");
        } catch (Exception e) {
            Midnight.LOGGER.error("Failed to initialize ShaderManager", e);
            cleanup();
        }
    }

    private void setupShaders() {
        vertexShader = createShader(GL20.GL_VERTEX_SHADER, ShaderSources.VERTEX_SHADER_SOURCE);
        if (vertexShader == 0) {
            throw new RuntimeException("Failed to create vertex shader");
        }

        fragmentShader = createShader(GL20.GL_FRAGMENT_SHADER, ShaderSources.FRAGMENT_SHADER_SOURCE);
        if (fragmentShader == 0) {
            throw new RuntimeException("Failed to create fragment shader");
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

        // Create and bind VAO first
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        // Create and setup VBO
        vbo = GL30.glGenBuffers();
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
        vertexBuffer.put(vertices).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertexBuffer, GL30.GL_STATIC_DRAW);

        // Setup vertex attributes while VAO is bound
        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        GL30.glEnableVertexAttribArray(1);

        // Unbind VAO and VBO
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, 0);
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

        GL20.glUseProgram(shaderProgram);

        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float convertedY = screenHeight - y - height;

        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, x, convertedY, width, height);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, radius);
        }
    }

    public void resetClipBounds() {
        if (!initialized) return;

        GL20.glUseProgram(shaderProgram);
        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, 0, 0, 0, 0);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, 0);
        }
    }

    public void drawShape(float x, float y, float width, float height, float radius, float smoothing,
                          Color fillColor, Color outlineColor, float outlineWidth) {
        if (!initialized) return;

        // Save previous state
        int previousVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        try {
            GL20.glUseProgram(shaderProgram);
            GL30.glBindVertexArray(vao);

            float guiScale = (float) MinecraftClient.getInstance().options.getGuiScale().getValue();
            int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
            float convertedY = screenHeight - y - height;

            setUniform2f("resolution", width, height);
            setUniform4f("rect", x, convertedY, width, height);
            setUniform1f("radius", radius);
            setUniform1f("smoothing", smoothing);
            setUniform4f("color",
                    fillColor.getRed() / 255f,
                    fillColor.getGreen() / 255f,
                    fillColor.getBlue() / 255f,
                    fillColor.getAlpha() / 255f
            );

            if (outlineColor != null) {
                setUniform4f("outlineColor",
                        outlineColor.getRed() / 255f,
                        outlineColor.getGreen() / 255f,
                        outlineColor.getBlue() / 255f,
                        outlineColor.getAlpha() / 255f
                );
                setUniform1f("outlineWidth", outlineWidth);
            } else {
                setUniform4f("outlineColor", 0f, 0f, 0f, 0f);
                setUniform1f("outlineWidth", 0f);
            }

            setUniform1f("guiScale", guiScale);
            Matrix4f transform = new Matrix4f().identity();
            setUniformMatrix4f("transform", transform);

            GL30.glDrawArrays(GL30.GL_TRIANGLE_FAN, 0, 4);
        } finally {
            // Restore previous state
            GL30.glBindVertexArray(previousVAO);
            GL20.glUseProgram(0);
        }
    }

    private void setUniform4f(String name, float x, float y, float z, float w) {
        int location = GL20.glGetUniformLocation(shaderProgram, name);
        if (location != -1) {
            GL20.glUniform4f(location, x, y, z, w);
        }
    }

    private void setUniform2f(String name, float x, float y) {
        int location = GL20.glGetUniformLocation(shaderProgram, name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    private void setUniform1f(String name, float x) {
        int location = GL20.glGetUniformLocation(shaderProgram, name);
        if (location != -1) {
            GL20.glUniform1f(location, x);
        }
    }

    private void setUniformMatrix4f(String name, Matrix4f matrix) {
        int location = GL20.glGetUniformLocation(shaderProgram, name);
        if (location != -1) {
            matrix.get(matrixBuffer);
            GL20.glUniformMatrix4fv(location, false, matrixBuffer);
        }
    }

    public void cleanup() {
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) GL30.glDeleteBuffers(vbo);
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