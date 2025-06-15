package de.peter1337.midnight.render.screens.accountmanager;

import com.google.common.collect.ImmutableList;
import de.peter1337.midnight.manager.alt.AltAccount;
import de.peter1337.midnight.manager.alt.AltManager;
import de.peter1337.midnight.utils.MicrosoftAuthenticator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ElementListWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AltManagerScreen extends Screen {
    private final Screen parent;
    private AltListWidget altListWidget;
    private TextFieldWidget usernameField;
    private ButtonWidget microsoftLoginButton;

    private volatile String status = "";

    public AltManagerScreen(Screen parent) {
        super(Text.literal("Alt Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // The list of accounts
        altListWidget = new AltListWidget(this.client, this.width, this.height, 32, this.height - 64, 25);
        AltManager.getAltAccounts().forEach(altListWidget::addAccount);
        this.addSelectableChild(altListWidget);

        // The text field for cracked usernames
        usernameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 154, this.height - 52, 100, 20, Text.literal("Username"));
        this.addSelectableChild(usernameField);

        // All buttons are now added as drawable children, which is the correct way to handle them.
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Add Cracked"), button -> {
            String user = usernameField.getText();
            if (!user.isEmpty()) {
                AltAccount account = new AltAccount(user);
                AltManager.addAlt(account);
                altListWidget.addAccount(account);
                usernameField.setText("");
            }
        }).dimensions(this.width / 2 - 50, this.height - 52, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Login"), button -> {
            AltListWidget.Entry selected = altListWidget.getSelectedOrNull();
            if (selected != null) {
                status = "Logging in as " + selected.getAccount().getUsername() + "...";
                AltManager.login(selected.getAccount());
                this.client.setScreen(this.parent);
            }
        }).dimensions(this.width / 2 - 154, this.height - 28, 100, 20).build());

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), button -> {
            AltListWidget.Entry selected = altListWidget.getSelectedOrNull();
            if (selected != null) {
                altListWidget.removeAccount(selected);
            }
        }).dimensions(this.width / 2 - 50, this.height - 28, 100, 20).build());

        microsoftLoginButton = ButtonWidget.builder(Text.literal("Login with Microsoft"), button -> {
            loginWithMicrosoft();
        }).dimensions(this.width / 2 + 54, this.height - 52, 100, 20).build();
        this.addDrawableChild(microsoftLoginButton);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> this.client.setScreen(parent))
                .dimensions(this.width / 2 + 54, this.height - 28, 100, 20).build());
    }

    private void loginWithMicrosoft() {
        microsoftLoginButton.active = false;
        microsoftLoginButton.setMessage(Text.literal("..."));
        CompletableFuture.runAsync(() -> {
            try {
                MicrosoftAuthenticator.DeviceCode deviceCode = MicrosoftAuthenticator.getDeviceCode();
                this.status = "Go to " + deviceCode.verification_uri + " and enter code: " + deviceCode.user_code;

                long startTime = System.currentTimeMillis();
                long timeout = deviceCode.expires_in * 1000L;

                MicrosoftAuthenticator.AuthResult authResult = null;
                while (System.currentTimeMillis() - startTime < timeout) {
                    Thread.sleep(deviceCode.interval * 1000L);
                    try {
                        authResult = MicrosoftAuthenticator.pollForToken(deviceCode);
                        if (authResult != null) break;
                    } catch (Exception e) {
                        if (e.getMessage() == null || !e.getMessage().contains("authorization_pending")) {
                            this.status = "Error: " + e.getMessage();
                            return;
                        }
                    }
                }

                if(authResult != null){
                    this.status = "Logged in as " + authResult.session.getUsername();
                    AltAccount msAccount = new AltAccount(authResult.session.getUsername(), authResult.refreshToken);
                    AltManager.addAlt(msAccount);
                    client.execute(() -> altListWidget.addAccount(msAccount));
                } else {
                    this.status = "Login timed out.";
                }

            } catch (Exception e) {
                e.printStackTrace();
                this.status = "An error occurred during login.";
            } finally {
                client.execute(() -> {
                    microsoftLoginButton.active = true;
                    microsoftLoginButton.setMessage(Text.literal("Login with Microsoft"));
                });
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Renders the background gradient
        this.renderBackground(context, mouseX, mouseY, delta);

        // Let the superclass handle rendering all the widgets that were added.
        // This is crucial for button clicks to be registered correctly.
        super.render(context, mouseX, mouseY, delta);

        // Render our text overlays on top of everything else.
        context.drawCenteredTextWithShadow(this.textRenderer, this.getTitle(), this.width / 2, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, 20, 0xFFFFFF);
    }

    private class AltListWidget extends ElementListWidget<AltListWidget.Entry> {
        public AltListWidget(MinecraftClient minecraftClient, int width, int height, int y, int y2, int itemHeight) {
            super(minecraftClient, width, height, y, itemHeight);
        }

        public void addAccount(AltAccount account) {
            this.addEntry(new Entry(account));
        }

        public void removeAccount(Entry entry) {
            AltManager.getAltAccounts().remove(entry.getAccount());
            this.removeEntry(entry);
        }

        @Override
        public int getRowWidth() {
            return this.width - 50;
        }

        public class Entry extends ElementListWidget.Entry<Entry> {
            private final AltAccount account;

            public Entry(AltAccount account) {
                this.account = account;
            }

            public AltAccount getAccount() {
                return account;
            }

            @Override
            public void render(DrawContext context, int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
                String text = this.account.getUsername() + " §7[" + this.account.getType().name() + "]";
                context.drawTextWithShadow(client.textRenderer, text, x + 5, y + (entryHeight - 8) / 2, 0xFFFFFF);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    setSelected(this);
                    return true;
                }
                return false;
            }

            @Override
            public List<? extends Selectable> selectableChildren() {
                return ImmutableList.of();
            }

            @Override
            public List<? extends Element> children() {
                return ImmutableList.of();
            }

            public Text getNarration() {
                return Text.literal(this.account.getUsername());
            }
        }
    }
}
