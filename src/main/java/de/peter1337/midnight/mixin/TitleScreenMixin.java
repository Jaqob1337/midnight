package de.peter1337.midnight.mixin;

import de.peter1337.midnight.render.gui.alt.AltManagerButton;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    // A matching constructor is required.
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        int x = 10;
        int y = this.height - 30; // 30 pixels from the bottom
        // Debug: Log the addition of the Alt Manager button.
        System.out.println("[DEBUG] TitleScreenMixin: Adding Alt Manager button at x=" + x + " y=" + y);
        this.addDrawableChild(AltManagerButton.create(x, y, 100, 20));
    }
}
