package de.peter1337.midnight.render.screens.accountmanager;

import de.peter1337.midnight.manager.alt.AltAccount;
import de.peter1337.midnight.manager.alt.AltManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AltManagerScreen extends Screen {
    private TextFieldWidget usernameField;
    private ButtonWidget loginButton;
    private ButtonWidget backButton;

    public AltManagerScreen() {
        super(Text.literal("Alt Manager"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // Create the vanilla text field for alt username.
        usernameField = new TextFieldWidget(this.textRenderer, centerX - 100, centerY - 20, 200, 20, Text.literal("Username"));
        usernameField.setText(""); // Start empty.
        usernameField.setFocused(true); // Force focus so its box and cursor are visible.
        this.addSelectableChild(usernameField);

        // Debug: print text field dimensions.
        System.out.println("[DEBUG] TextFieldWidget at x=" + usernameField.getX() +
                ", y=" + usernameField.getY() + ", w=" + usernameField.getWidth() +
                ", h=" + usernameField.getHeight());

        // Create the "Login Alt" button with a default label.
        loginButton = ButtonWidget.builder(Text.literal("Login Alt"), button -> {
                    String username = usernameField.getText().trim();
                    if (!username.isEmpty()) {
                        AltAccount alt = new AltAccount(username);
                        AltManager.addAlt(alt);
                        AltManager.login(alt);
                        MinecraftClient.getInstance().setScreen(null); // Close the alt manager screen.
                    }
                })
                .dimensions(centerX - 50, centerY + 10, 100, 20)
                .build();
        this.addDrawableChild(loginButton);

        // Create the "Back" button.
        backButton = ButtonWidget.builder(Text.literal("Back"), button -> {
                    MinecraftClient.getInstance().setScreen(null);
                })
                .dimensions(centerX - 50, centerY + 40, 100, 20)
                .build();
        this.addDrawableChild(backButton);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render a background so the text field is visible.
        this.renderBackground(context, mouseX, mouseY, delta);
        // Render the standard widgets.
        super.render(context, mouseX, mouseY, delta);

        // For debugging: explicitly render the text field if needed.
        if (usernameField != null) {
            usernameField.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
