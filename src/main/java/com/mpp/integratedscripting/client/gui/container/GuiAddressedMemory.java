package com.mpp.integratedscripting.client.gui.container;

import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import com.mpp.integratedscripting.inventory.container.ContainerAddressedMemory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.client.gui.container.GuiContainerConfigurable;
import org.cyclops.cyclopscore.helper.L10NHelpers;

import java.util.Collections;
import java.util.List;

/**
 * GUI of the addressed memory.
 *
 * <p>The layout is taken pixel by pixel from {@code textures/gui/memory.png}:
 * the memory card slot in the top-left corner, two rows of eight address slots,
 * and above every address slot a type box that shows the type of the variable at
 * that address. Four pixels above each type box the address letter is drawn;
 * hovering the box shows the variable itself as a tooltip.</p>
 *
 * <p>The texture is 337x203 and is drawn unscaled, so the slot and box
 * coordinates used by {@link ContainerAddressedMemory} are the texture's own
 * coordinates.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class GuiAddressedMemory extends GuiContainerConfigurable<ContainerAddressedMemory> {

    /** The name is centred in the free strip between the two address rows. */
    private static final float TITLE_SCALE = 0.7F;
    private static final int TITLE_Y = 54;
    private static final int TEXT_COLOR = 0x404040;
    private static final int ERROR_TEXT_COLOR = 0xAA0000;
    private static final float TYPE_TEXT_SCALE = 0.625F;

    /** The GUI texture is 384x256, not the 256x256 {@code drawTexturedModalRect} assumes. */
    private static final int TEXTURE_WIDTH = 384;
    private static final int TEXTURE_HEIGHT = 256;

    public GuiAddressedMemory(InventoryPlayer inventory, TileAddressedMemory tile) {
        super(new ContainerAddressedMemory(inventory, tile));
    }

    @Override
    protected int getBaseXSize() {
        return 337;
    }

    @Override
    protected int getBaseYSize() {
        return 203;
    }

    @Override
    public String getGuiTexture() {
        // The block is addressed_memory, but the texture is gui/memory.png.
        // The full path is needed: the base class only appends the file name to
        // the configured GUI texture path, which this override replaces entirely.
        return "textures/gui/memory.png";
    }

    /**
     * @return The container, for the acceptance harness and other client code
     *         that needs the address data the screen renders.
     */
    public ContainerAddressedMemory getMemoryContainer() {
        return this.getContainer();
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(this.texture);
        // The texture is wider than 256 pixels, so the size-aware blit is needed:
        // drawTexturedModalRect divides texture coordinates by a fixed 256 and
        // would stretch the panel horizontally and wrap around at u = 1.
        drawModalRectWithCustomSizedTexture(this.guiLeft + this.offsetX, this.guiTop + this.offsetY,
                0.0F, 0.0F, this.xSize, this.getBaseYSize(),
                TEXTURE_WIDTH, TEXTURE_HEIGHT);

        drawTitle();
        drawAddresses(mouseX, mouseY);
    }

    /**
     * The base class draws the block name at (8, 6), which is inside the memory
     * card slot of this GUI, so the title is drawn by {@link #drawTitle()}
     * instead. The foreground layer only draws the hover tooltip of a type box,
     * because that layer is translated to the GUI origin, which is exactly the
     * coordinate space {@code drawHoveringText} expects.
     */
    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        List<ContainerAddressedMemory.AddressData> data = this.getContainer().getAddressData();
        int hovered = getHoveredTextBox(mouseX, mouseY);
        if (hovered < 0) {
            return;
        }
        ContainerAddressedMemory.AddressData entry = hovered < data.size()
                ? data.get(hovered) : ContainerAddressedMemory.AddressData.empty();
        drawHoveringText(describe(hovered, entry), mouseX - this.guiLeft, mouseY - this.guiTop);
    }

    private void drawTitle() {
        String name = L10NHelpers.localize("tile.blocks.integratedscriptingcallback.addressed_memory.name");
        // The two address rows leave a free strip in the middle of the panel; the
        // title is centred there so it can never overlap an address label or a
        // type box (the texture has no room for a full-height title at the top).
        float width = this.fontRenderer.getStringWidth(name) * TITLE_SCALE;
        GlStateManager.pushMatrix();
        GlStateManager.translate(this.guiLeft + (this.xSize - width) / 2.0F, this.guiTop + TITLE_Y, 0.0F);
        GlStateManager.scale(TITLE_SCALE, TITLE_SCALE, 1.0F);
        this.fontRenderer.drawString(name, 0, 0, TEXT_COLOR);
        GlStateManager.popMatrix();
    }

    private void drawAddresses(int mouseX, int mouseY) {
        List<ContainerAddressedMemory.AddressData> data = this.getContainer().getAddressData();

        for (int index = 0; index < TileAddressedMemory.ADDRESS_COUNT; index++) {
            int boxX = ContainerAddressedMemory.getTextBoxX(index);
            int boxY = ContainerAddressedMemory.getTextBoxY(index);
            String address = TileAddressedMemory.getAddressLetter(index);

            // Address text, four pixels above the type box.
            int labelWidth = this.fontRenderer.getStringWidth(address);
            int labelX = this.guiLeft + boxX + (ContainerAddressedMemory.TEXT_BOX_WIDTH - labelWidth) / 2;
            int labelY = this.guiTop + boxY - ContainerAddressedMemory.ADDRESS_LABEL_GAP
                    - this.fontRenderer.FONT_HEIGHT;
            this.fontRenderer.drawString(address, labelX, labelY, TEXT_COLOR);

            ContainerAddressedMemory.AddressData entry = index < data.size()
                    ? data.get(index) : ContainerAddressedMemory.AddressData.empty();
            drawTypeText(entry, boxX, boxY);
        }
    }

    /**
     * @param mouseX The absolute mouse x.
     * @param mouseY The absolute mouse y.
     * @return The address index whose type box is hovered, or -1.
     */
    public int getHoveredTextBox(int mouseX, int mouseY) {
        for (int index = 0; index < TileAddressedMemory.ADDRESS_COUNT; index++) {
            if (isPointInTextBox(mouseX, mouseY,
                    ContainerAddressedMemory.getTextBoxX(index), ContainerAddressedMemory.getTextBoxY(index))) {
                return index;
            }
        }
        return -1;
    }

    private void drawTypeText(ContainerAddressedMemory.AddressData entry, int boxX, int boxY) {
        String typeName = typeName(entry);
        if (typeName.isEmpty()) {
            return;
        }
        String fitted = fit(typeName, ContainerAddressedMemory.TEXT_BOX_WIDTH);
        int width = Math.round(this.fontRenderer.getStringWidth(fitted) * TYPE_TEXT_SCALE);
        int x = this.guiLeft + boxX + (ContainerAddressedMemory.TEXT_BOX_WIDTH - width) / 2;
        int y = this.guiTop + boxY + Math.round((ContainerAddressedMemory.TEXT_BOX_HEIGHT
                - this.fontRenderer.FONT_HEIGHT * TYPE_TEXT_SCALE) / 2.0F);
        int color = entry.getErrorText() != null ? ERROR_TEXT_COLOR : TEXT_COLOR;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0.0F);
        GlStateManager.scale(TYPE_TEXT_SCALE, TYPE_TEXT_SCALE, 1.0F);
        this.fontRenderer.drawString(fitted, 0, 0, color);
        GlStateManager.popMatrix();
    }

    private String typeName(ContainerAddressedMemory.AddressData entry) {
        if (entry.isEmpty()) {
            return "";
        }
        String typeKey = entry.getTypeKey();
        return typeKey == null || typeKey.isEmpty() ? "" : L10NHelpers.localize(typeKey);
    }

    /**
     * @param index A zero-based address index.
     * @return The tooltip lines of that address, as the screen would render them.
     */
    public List<String> getAddressTooltip(int index) {
        List<ContainerAddressedMemory.AddressData> data = this.getContainer().getAddressData();
        ContainerAddressedMemory.AddressData entry = index >= 0 && index < data.size()
                ? data.get(index) : ContainerAddressedMemory.AddressData.empty();
        return describe(index, entry);
    }

    private List<String> describe(int index, ContainerAddressedMemory.AddressData entry) {
        String address = TileAddressedMemory.getAddressLetter(index);
        String typeName = typeName(entry);
        String header = L10NHelpers.localize("gui.integratedscripting.addressed_memory.address", address)
                + (typeName.isEmpty() ? "" : ": " + typeName);

        String value = entry.getValueText();
        String error = entry.getErrorText();
        if (error != null) {
            return java.util.Arrays.asList(header,
                    L10NHelpers.localize("gui.integratedscripting.addressed_memory.error", error));
        }
        if (value != null) {
            return java.util.Arrays.asList(header, value);
        }
        return Collections.singletonList(L10NHelpers.localize(
                "gui.integratedscripting.addressed_memory.empty"));
    }

    private String fit(String text, int width) {
        if (this.fontRenderer.getStringWidth(text) * TYPE_TEXT_SCALE <= width) {
            return text;
        }
        String ellipsis = "..";
        String result = text;
        while (!result.isEmpty()
                && this.fontRenderer.getStringWidth(result + ellipsis) * TYPE_TEXT_SCALE > width) {
            result = result.substring(0, result.length() - 1);
        }
        return result + ellipsis;
    }

    private boolean isPointInTextBox(int mouseX, int mouseY, int boxX, int boxY) {
        return mouseX >= this.guiLeft + boxX && mouseX < this.guiLeft + boxX + ContainerAddressedMemory.TEXT_BOX_WIDTH
                && mouseY >= this.guiTop + boxY && mouseY < this.guiTop + boxY + ContainerAddressedMemory.TEXT_BOX_HEIGHT;
    }

}
