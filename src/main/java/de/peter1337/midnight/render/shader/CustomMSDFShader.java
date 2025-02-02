package de.peter1337.midnight.render.shader;

import org.lwjgl.opengl.GL20;

public class CustomMSDFShader {
    private static CustomMSDFShader instance;
    private final int programId;

    private CustomMSDFShader() {
        // Compile vertex and fragment shaders from source strings.
        int vertexShader = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE);
        int fragmentShader = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE);
        programId = GL20.glCreateProgram();
        GL20.glAttachShader(programId, vertexShader);
        GL20.glAttachShader(programId, fragmentShader);
        GL20.glLinkProgram(programId);
        int linkStatus = GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS);
        if (linkStatus == 0) {
            throw new RuntimeException("Error linking MSDF shader program: " + GL20.glGetProgramInfoLog(programId));
        }
        // Detach and delete shaders after linking.
        GL20.glDetachShader(programId, vertexShader);
        GL20.glDetachShader(programId, fragmentShader);
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    public static CustomMSDFShader getInstance() {
        if (instance == null) {
            instance = new CustomMSDFShader();
        }
        return instance;
    }

    public int getProgramId() {
        return programId;
    }

    public void bind() {
        GL20.glUseProgram(programId);
        // Set uniform values – typical values for a normalized MSDF atlas:
        int locColor = GL20.glGetUniformLocation(programId, "u_Color");
        int locThreshold = GL20.glGetUniformLocation(programId, "u_Threshold");
        int locSmoothing = GL20.glGetUniformLocation(programId, "u_Smoothing");
        GL20.glUniform4f(locColor, 1.0f, 1.0f, 1.0f, 1.0f);
        GL20.glUniform1f(locThreshold, 0.5f);
        GL20.glUniform1f(locSmoothing, 0.1f);
        // Note: You must set u_ModelViewProjection externally, using your current transformation matrix.
    }

    public void unbind() {
        GL20.glUseProgram(0);
    }

    private int compileShader(int type, String source) {
        int shaderId = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        int compileStatus = GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS);
        if (compileStatus == 0) {
            throw new RuntimeException("Error compiling shader: " + GL20.glGetShaderInfoLog(shaderId));
        }
        return shaderId;
    }

    // Vertex shader source (GLSL 150)
    private static final String VERTEX_SHADER_SOURCE =
            "#version 150 core\n" +
                    "in vec3 a_Position;\n" +
                    "in vec2 a_TexCoord;\n" +
                    "out vec2 v_TexCoord;\n" +
                    "uniform mat4 u_ModelViewProjection;\n" +
                    "void main() {\n" +
                    "    gl_Position = u_ModelViewProjection * vec4(a_Position, 1.0);\n" +
                    "    v_TexCoord = a_TexCoord;\n" +
                    "}\n";

    // Fragment shader source for MSDF text rendering.
    private static final String FRAGMENT_SHADER_SOURCE =
            "#version 150 core\n" +
                    "in vec2 v_TexCoord;\n" +
                    "out vec4 FragColor;\n" +
                    "uniform sampler2D u_Texture;\n" +
                    "uniform vec4 u_Color;\n" +
                    "uniform float u_Threshold;\n" +
                    "uniform float u_Smoothing;\n" +
                    "\n" +
                    "// Compute the median of three values\n" +
                    "float median(float a, float b, float c) {\n" +
                    "    return max(min(a, b), min(max(a, b), c));\n" +
                    "}\n" +
                    "\n" +
                    "void main() {\n" +
                    "    vec3 sample = texture(u_Texture, v_TexCoord).rgb;\n" +
                    "    float sigDist = median(sample.r, sample.g, sample.b) - u_Threshold;\n" +
                    "    float alpha = clamp(sigDist / u_Smoothing + 0.5, 0.0, 1.0);\n" +
                    "    FragColor = vec4(u_Color.rgb, u_Color.a * alpha);\n" +
                    "}\n";
}
