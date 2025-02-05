package de.peter1337.midnight.render.gui.clickgui;

import de.peter1337.midnight.render.shape.ShapeRenderer;
import de.peter1337.midnight.render.shape.ShapeUtils;
import de.peter1337.midnight.render.shader.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class ClickGuiScreen extends Screen {

    private ShapeRenderer shapeRenderer;

    public ClickGuiScreen() {
        super(Text.literal("ClickGUI"));
        try {
            // Simple vertex shader source.
            String vertexSource =
                    "#version 330 core\n" +
                            "layout(location = 0) in vec3 aPos;\n" +
                            "uniform mat4 uProjection;\n" +
                            "uniform mat4 uModelView;\n" +
                            "void main() {\n" +
                            "    gl_Position = uProjection * uModelView * vec4(aPos, 1.0);\n" +
                            "}";
            // Simple fragment shader source.
            String fragmentSource =
                    "#version 330 core\n" +
                            "out vec4 FragColor;\n" +
                            "uniform vec4 uColor;\n" +
                            "void main() {\n" +
                            "    FragColor = uColor;\n" +
                            "}";
            ShaderProgram shader = new ShaderProgram(vertexSource, fragmentSource);
            // Generate vertex data for a rounded rectangle.
            // For example: top-left at (50,50), width 300, height 200, corner radius 50, with 60 segments per corner.
            float[] vertices = ShapeUtils.generateRoundedRectVertices(50, 50, 300, 200, 50, 60);
            shapeRenderer = new ShapeRenderer(vertices, shader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Do not pause the game when this screen is open.
    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw a semi-transparent background so that the game is visible.
        context.fill(0, 0, this.width, this.height, 0xAA000000);
        if (shapeRenderer != null) {
            shapeRenderer.render(this.width, this.height);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
