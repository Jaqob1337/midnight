package de.peter1337.midnight.render.shader;

import org.lwjgl.opengl.GL20;

public class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertexSource, String fragmentSource) throws Exception {
        int vertexShaderId = compileShader(vertexSource, GL20.GL_VERTEX_SHADER);
        int fragmentShaderId = compileShader(fragmentSource, GL20.GL_FRAGMENT_SHADER);
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShaderId);
        GL20.glAttachShader(programId, fragmentShaderId);
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == 0) {
            throw new Exception("Error linking shader program: " + GL20.glGetProgramInfoLog(programId, 1024));
        }
        GL20.glDetachShader(programId, vertexShaderId);
        GL20.glDetachShader(programId, fragmentShaderId);
        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
    }

    private int compileShader(String source, int type) throws Exception {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == 0) {
            throw new Exception("Error compiling shader: " + GL20.glGetShaderInfoLog(shaderId, 1024));
        }
        return shaderId;
    }

    public void bind() {
        GL20.glUseProgram(programId);
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    public int getProgramId() {
        return programId;
    }

    public void cleanup() {
        GL20.glDeleteProgram(programId);
    }
}
