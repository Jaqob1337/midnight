package de.peter1337.midnight.mixins;

import de.peter1337.midnight.render.screens.accountmanager.buttons.AltManagerButton;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.Element;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ButtonWidget realmsButton = null;
        for (Element element : this.children()) {
            // In some versions, the element might not be a ButtonWidget, so we check
            if (element instanceof ButtonWidget) {
                ButtonWidget button = (ButtonWidget) element;
                // Using string comparison which is less ideal than translatable keys, but more robust across versions
                if (button.getMessage().getString().contains("Realms")) {
                    realmsButton = button;
                    break;
                }
            }
        }

        if (realmsButton != null) {
            // Get the properties of the realms button
            int x = realmsButton.getX();
            int y = realmsButton.getY();
            int width = realmsButton.getWidth();
            int height = realmsButton.getHeight();

            // Remove the realms button
            this.remove(realmsButton);

            // Add the new alt manager button, passing `this` as the parent screen
            this.addDrawableChild(AltManagerButton.create(x, y, width, height, this));
        }
    }
}
