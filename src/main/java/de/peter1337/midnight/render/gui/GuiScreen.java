package de.peter1337.midnight.render.gui;

import de.peter1337.midnight.render.Render2D;
import de.peter1337.midnight.utils.GuiRenderUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public abstract class GuiScreen extends Screen {
    protected final Render2D render2D;

    protected GuiScreen(Text title) {
        super(title);
        this.render2D = new Render2D();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        GuiRenderUtils.setupBlending();
        render2D.renderShapes();
        super.render(context, mouseX, mouseY, delta);
        GuiRenderUtils.cleanupBlending();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return render2D.handleMouseClicked(mouseX, mouseY, button) ||
                super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return render2D.handleMouseDragged(mouseX, mouseY) ||
                super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        render2D.handleMouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void init() {
        super.init();
        render2D.init(width, height);
    }

    @Override
    public void close() {
        super.close();
        render2D.cleanup();
    }
}