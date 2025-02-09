package de.peter1337.midnight.manager.shader;

import de.peter1337.midnight.render.shader.ShaderSources;
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

    // Cache uniform locations
    private int clipBoundsLocation = -1;
    private int clipRadiusLocation = -1;

    public void init(int width, int height) {
        setupShaders();
        setupBuffers();

        // Get uniform locations after shader setup
        clipBoundsLocation = GL20.glGetUniformLocation(shaderProgram, "clipBounds");
        clipRadiusLocation = GL20.glGetUniformLocation(shaderProgram, "clipRadius");
    }

    private void setupShaders() {
        vertexShader = createShader(GL20.GL_VERTEX_SHADER, ShaderSources.VERTEX_SHADER_SOURCE);
        fragmentShader = createShader(GL20.GL_FRAGMENT_SHADER, ShaderSources.FRAGMENT_SHADER_SOURCE);
        shaderProgram = createProgram(vertexShader, fragmentShader);
    }

    private void setupBuffers() {
        float[] vertices = {
                -1.0f, -1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, -1.0f, 0.0f, 1.0f, 0.0f,
                1.0f, 1.0f, 0.0f, 1.0f, 1.0f,
                -1.0f, 1.0f, 0.0f, 0.0f, 1.0f
        };

        vao = GL30.glGenVertexArrays();
        vbo = GL30.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, vbo);

        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertices.length);
        vertexBuffer.put(vertices).flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, vertexBuffer, GL30.GL_STATIC_DRAW);

        GL30.glVertexAttribPointer(0, 3, GL30.GL_FLOAT, false, 5 * Float.BYTES, 0);
        GL30.glEnableVertexAttribArray(0);
        GL30.glVertexAttribPointer(1, 2, GL30.GL_FLOAT, false, 5 * Float.BYTES, 3 * Float.BYTES);
        GL30.glEnableVertexAttribArray(1);
        GL30.glBindVertexArray(0);
    }

    private int createShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL20.GL_FALSE) {
            System.err.println("Shader compilation error: " + GL20.glGetShaderInfoLog(shader));
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
            System.err.println("Shader program linking error: " + GL20.glGetProgramInfoLog(program));
            return 0;
        }
        return program;
    }

    public void setClipBounds(float x, float y, float width, float height, float radius) {
        GL20.glUseProgram(shaderProgram);

        // Convert Y coordinate to match OpenGL coordinate system
        int screenHeight = MinecraftClient.getInstance().getWindow().getScaledHeight();
        float convertedY = screenHeight - y - height;

        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, x, convertedY, width, height);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, radius);
        }

        GL20.glUseProgram(0);
    }

    public void resetClipBounds() {
        GL20.glUseProgram(shaderProgram);
        if (clipBoundsLocation != -1) {
            GL20.glUniform4f(clipBoundsLocation, 0, 0, 0, 0);
        }
        if (clipRadiusLocation != -1) {
            GL20.glUniform1f(clipRadiusLocation, 0);
        }
        GL20.glUseProgram(0);
    }

    public void drawShape(float x, float y, float width, float height, float radius, float smoothing,
                          Color fillColor, Color outlineColor, float outlineWidth) {
        float guiScale = (float) MinecraftClient.getInstance().options.getGuiScale().getValue();
        GL20.glUseProgram(shaderProgram);

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

        GL30.glBindVertexArray(vao);
        GL30.glDrawArrays(GL30.GL_TRIANGLE_FAN, 0, 4);
        GL30.glBindVertexArray(0);
        GL20.glUseProgram(0);
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
        GL30.glDeleteVertexArrays(vao);
        GL30.glDeleteBuffers(vbo);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
        GL20.glDeleteProgram(shaderProgram);
    }
}