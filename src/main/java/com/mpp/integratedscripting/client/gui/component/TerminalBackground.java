package com.mpp.integratedscripting.client.gui.component;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Draws the resizable scripting terminal background by slicing
 * {@code textures/gui/terminal_scripting.png}.
 *
 * <p>Only frame edges, header boxes and the two slot blocks are copied 1:1; all
 * growing bands are stretched from a solid part of the texture. Stretching a
 * solid column (or the vertically uniform file list strip) cannot smear the 3D
 * bevel, and every fixed pixel keeps its original position relative to the
 * nearest border. At 256x240 the result is identical to blitting the whole
 * texture.</p>
 *
 * <p>The rectangles below refer to the texture, whose useful area is 256x240
 * although the PNG is 256x256. The left panel is 80px wide and does not grow
 * horizontally; the main panel grows to the right, and its bottom band is
 * anchored to the bottom with the slot cluster centered horizontally (see
 * {@link TerminalLayout}).</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public final class TerminalBackground {

    private static final float TEXTURE_SIZE = TerminalLayout.TEXTURE_WIDTH;

    private TerminalBackground() {
    }

    /**
     * Draw the complete terminal background.
     *
     * @param gui The screen to draw on.
     * @param texture The terminal texture.
     * @param layout The current layout.
     * @param guiLeft Absolute left edge of the GUI.
     * @param guiTop Absolute top edge of the GUI.
     */
    public static void draw(Gui gui, ResourceLocation texture, TerminalLayout layout,
                            int guiLeft, int guiTop) {
        GlStateManager.color(1F, 1F, 1F, 1F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);

        int width = layout.getWidth();
        int height = layout.getHeight();
        int bottomTop = layout.getBottomTop();
        // Interior rows 18..bottomTop-1; the last row is the editor's white border.
        int editorInteriorHeight = bottomTop - 19;
        int listHeight = layout.getListHeight();

        // --- left panel: fixed header, vertically stretched file list, fixed bottom.
        blit(gui, guiLeft, guiTop, 0, 0, TerminalLayout.LEFT_PANEL_WIDTH,
                TerminalLayout.HEADER_HEIGHT);
        stretch(gui, guiLeft, guiTop + TerminalLayout.HEADER_HEIGHT,
                0, 18, TerminalLayout.LEFT_PANEL_WIDTH, 214,
                TerminalLayout.LEFT_PANEL_WIDTH, listHeight);
        blit(gui, guiLeft, guiTop + layout.getLeftBottomTop(),
                0, 232, TerminalLayout.LEFT_PANEL_WIDTH, TerminalLayout.LEFT_BOTTOM_HEIGHT);

        // --- main panel header: fixed corners, stretched wide header box.
        blit(gui, guiLeft + 79, guiTop, 79, 0, 5, TerminalLayout.HEADER_HEIGHT);
        stretch(gui, guiLeft + 84, guiTop,
                84, 0, 164, TerminalLayout.HEADER_HEIGHT,
                width - 92, TerminalLayout.HEADER_HEIGHT);
        blit(gui, guiLeft + width - 8, guiTop, 248, 0, 8, TerminalLayout.HEADER_HEIGHT);

        // --- main panel editor: gutter and frame stay fixed, the text area grows.
        stretch(gui, guiLeft + 79, guiTop + 18,
                79, 18, 15, 115, 15, editorInteriorHeight);
        stretch(gui, guiLeft + 94, guiTop + 18,
                94, 18, 1, 115, width - 102, editorInteriorHeight);
        stretch(gui, guiLeft + width - 8, guiTop + 18,
                248, 18, 1, 115, 1, editorInteriorHeight);
        stretch(gui, guiLeft + width - TerminalLayout.RIGHT_FRAME_WIDTH, guiTop + 18,
                249, 18, TerminalLayout.RIGHT_FRAME_WIDTH, 115,
                TerminalLayout.RIGHT_FRAME_WIDTH, editorInteriorHeight);

        // White line below the editor (texture row 133), split at the frame edges.
        int editorLineY = guiTop + bottomTop - 1;
        blit(gui, guiLeft + 79, editorLineY, 79, 133, 2, 1);
        stretch(gui, guiLeft + 81, editorLineY, 81, 133, 168, 1, width - 88, 1);
        blit(gui, guiLeft + width - TerminalLayout.RIGHT_FRAME_WIDTH, editorLineY,
                249, 133, TerminalLayout.RIGHT_FRAME_WIDTH, 1);

        // --- main panel bottom band: anchored to the bottom, slot cluster centered.
        // Grey background, stretched from one solid C6 pixel.
        stretch(gui, guiLeft + 84, guiTop + bottomTop,
                84, 135, 1, 1, width - 91, TerminalLayout.BOTTOM_HEIGHT);
        // Fixed frame columns.
        blit(gui, guiLeft + 79, guiTop + bottomTop,
                79, 134, 5, TerminalLayout.BOTTOM_HEIGHT);
        blit(gui, guiLeft + width - TerminalLayout.RIGHT_FRAME_WIDTH, guiTop + bottomTop,
                249, 134, TerminalLayout.RIGHT_FRAME_WIDTH, TerminalLayout.BOTTOM_HEIGHT);

        // Slot cells are copied as whole blocks, so the inner 8/W bevel of every
        // cell stays intact while the blocks follow the centered slot cluster.
        blit(gui, guiLeft + layout.getPlayerGridX(), guiTop + layout.getPlayerGridY(),
                87, 157, 162, 76);
        blit(gui, guiLeft + layout.getVariableSlotX(), guiTop + layout.getVariableSlotY(),
                231, 136, 18, 18);

        // Bottom bevel (texture rows 236..239), only the middle stretches.
        int bevelY = guiTop + height - 4;
        blit(gui, guiLeft + 79, bevelY, 79, 236, 5, 4);
        stretch(gui, guiLeft + 84, bevelY, 84, 236, 165, 4, width - 91, 4);
        blit(gui, guiLeft + width - TerminalLayout.RIGHT_FRAME_WIDTH, bevelY,
                249, 236, TerminalLayout.RIGHT_FRAME_WIDTH, 4);
    }

    private static void blit(Gui gui, int x, int y, int u, int v, int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        gui.drawTexturedModalRect(x, y, u, v, width, height);
    }

    private static void stretch(Gui gui, int x, int y, int u, int v,
                                int sourceWidth, int sourceHeight,
                                int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        Gui.drawScaledCustomSizeModalRect(x, y, u, v, sourceWidth, sourceHeight,
                width, height, TEXTURE_SIZE, TEXTURE_SIZE);
    }

}
