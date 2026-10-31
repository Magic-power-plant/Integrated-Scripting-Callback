package com.mpp.integratedscripting.client.gui.component.input;

import com.mpp.Reference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.client.gui.component.button.GuiButtonText;

/**
 * A modal confirm/keep dialog, ported from the 1.20 {@code WidgetDialog}
 * (docs/PORTING-1.12.2.md §7.9.4).
 *
 * <p>1.12.2 has no renderable widget system, so the owning screen draws this
 * object and forwards mouse clicks to it while it is open. The two buttons are
 * plain Cyclops {@link GuiButtonText}s, so no {@code buttonList} bookkeeping is
 * required.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class GuiWidgetDialog extends Gui {

    public static final int WIDTH = 216;
    public static final int HEIGHT = 71;

    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Reference.MOD_ID, "textures/gui/dialog.png");
    private static final int BUTTON_HEIGHT = 15;
    private static final int BUTTON_WIDTH = 50;

    private final FontRenderer fontRenderer;
    private final int x;
    private final int y;
    private final int screenWidth;
    private final int screenHeight;
    private final String title;
    private final String message;
    private final GuiButtonText buttonConfirm;
    private final GuiButtonText buttonCancel;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public GuiWidgetDialog(FontRenderer fontRenderer, int x, int y, int screenWidth, int screenHeight,
                           String title, String message, String confirm, String cancel,
                           Runnable onConfirm, Runnable onCancel) {
        this.fontRenderer = fontRenderer;
        this.x = x;
        this.y = y;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.title = title;
        this.message = message;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;

        int buttonY = y + HEIGHT - BUTTON_HEIGHT - 5;
        this.buttonConfirm = new GuiButtonText(0, x + 50, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                confirm, true);
        this.buttonCancel = new GuiButtonText(1, x + WIDTH - BUTTON_WIDTH - 50, buttonY,
                BUTTON_WIDTH, BUTTON_HEIGHT, cancel, true);
    }

    /**
     * Draw the dialog and its buttons.
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     */
    public void draw(int mouseX, int mouseY) {
        // Gray-out background.
        drawRect(0, 0, this.screenWidth, this.screenHeight, 0x64000000);

        // Dialog texture.
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(this.x, this.y, 0, 0, WIDTH, HEIGHT);

        this.buttonConfirm.drawButton(minecraft, mouseX, mouseY, 0);
        this.buttonCancel.drawButton(minecraft, mouseX, mouseY, 0);

        drawCenteredString(this.fontRenderer, this.title, this.x + WIDTH / 2, this.y + 4, 0xFFFFFF);
        drawCenteredString(this.fontRenderer, this.message, this.x + WIDTH / 2, this.y + 25, 0x404040);
    }

    /**
     * Forward a click to the dialog buttons.
     * @param mouseX The mouse x position.
     * @param mouseY The mouse y position.
     * @param mouseButton The clicked button, only left clicks are handled.
     * @return Whether the click was consumed (always true while the dialog is open).
     */
    public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (isPointInButton(this.buttonConfirm, mouseX, mouseY)
                && this.buttonConfirm.mousePressed(minecraft, mouseX, mouseY)) {
            this.buttonConfirm.playPressSound(minecraft.getSoundHandler());
            this.onConfirm.run();
            return true;
        }
        if (isPointInButton(this.buttonCancel, mouseX, mouseY)
                && this.buttonCancel.mousePressed(minecraft, mouseX, mouseY)) {
            this.buttonCancel.playPressSound(minecraft.getSoundHandler());
            this.onCancel.run();
            return true;
        }
        // A modal dialog consumes all clicks while it is open.
        return true;
    }

    /**
     * Whether a point is inside the dialog bounds, used to decide whether the GUI
     * should offer the dialog's own hovertip.
     */
    public boolean isInside(int mouseX, int mouseY) {
        return mouseX >= this.x && mouseX < this.x + WIDTH && mouseY >= this.y && mouseY < this.y + HEIGHT;
    }

    private static boolean isPointInButton(GuiButton button, int mouseX, int mouseY) {
        return mouseX >= button.x && mouseX < button.x + button.width
                && mouseY >= button.y && mouseY < button.y + button.height;
    }

}
