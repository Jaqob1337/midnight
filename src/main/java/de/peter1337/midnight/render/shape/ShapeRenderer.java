package de.peter1337.midnight.render.shape;

import de.peter1337.midnight.render.shader.ShaderProgram;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.joml.Matrix4f;

import java.nio.FloatBuffer;

public class ShapeRenderer {
    private final int vaoId;
    private final int vboId;
    private final int vertexCount;
    private final ShaderProgram shader;

    public ShapeRenderer(float[] vertices, ShaderProgram shader) {
        this.shader = shader;
        vaoId = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vaoId);

        vboId = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vboId);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);
        // Attribute 0: 3 floats per vertex.
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0);
        GL20.glEnableVertexAttribArray(0);
        GL30.glBindVertexArray(0);
        vertexCount = vertices.length / 3;
    }

    /**
     * Renders the shape using an orthographic projection that matches the screen dimensions.
     *
     * @param screenWidth  current screen width
     * @param screenHeight current screen height
     */
    public void render(int screenWidth, int screenHeight) {
        shader.bind();
        // Set up an orthographic projection: x:[0, screenWidth], y:[0, screenHeight].
        Matrix4f projection = new Matrix4f().setOrtho2D(0, screenWidth, screenHeight, 0);
        int projLoc = GL20.glGetUniformLocation(shader.getProgramId(), "uProjection");
        FloatBuffer projBuffer = BufferUtils.createFloatBuffer(16);
        projection.get(projBuffer);
        GL20.glUniformMatrix4fv(projLoc, false, projBuffer);
        // Set modelview to identity.
        Matrix4f modelView = new Matrix4f().identity();
        int mvLoc = GL20.glGetUniformLocation(shader.getProgramId(), "uModelView");
        FloatBuffer mvBuffer = BufferUtils.createFloatBuffer(16);
        modelView.get(mvBuffer);
        GL20.glUniformMatrix4fv(mvLoc, false, mvBuffer);
        // Set color uniform to white.
        int colorLoc = GL20.glGetUniformLocation(shader.getProgramId(), "uColor");
        GL20.glUniform4f(colorLoc, 1.0f, 1.0f, 1.0f, 1.0f);

        GL30.glBindVertexArray(vaoId);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);
        GL30.glBindVertexArray(0);
        shader.unbind();
    }

    public void cleanup() {
        GL15.glDeleteBuffers(vboId);
        GL30.glDeleteVertexArrays(vaoId);
        shader.cleanup();
    }
}
