package com.mpp.integratedscripting.infobook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.infobook.GuiInfoBook;
import org.cyclops.cyclopscore.infobook.IInfoBook;
import org.cyclops.cyclopscore.infobook.InfoSection;
import org.cyclops.cyclopscore.infobook.pageelement.SectionAppendix;
import org.cyclops.cyclopscore.helper.MinecraftHelpers;
import org.w3c.dom.Element;

/**
 * A code-block appendix for the "On the Dynamics of Integration" manual.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §9.8.4): CyclopsCore 1.6.7 does
 * not ship the 1.20 {@code textfield} appendix type, so it is implemented and
 * registered here. The appendix is only drawn client-side; on a dedicated
 * server it merely participates in parsing so the whole section is not
 * discarded (missing appendix types throw and hide the section).</p>
 *
 * @author rubensworks
 */
public class TextfieldAppendix extends SectionAppendix {

    private final String text;
    private final float scale;
    private int height;

    public TextfieldAppendix(IInfoBook infoBook, Element node) {
        super(infoBook);
        this.text = node.getTextContent();
        this.scale = node.hasAttribute("scale") ? Float.parseFloat(node.getAttribute("scale")) : 1.0F;
    }

    @Override
    protected int getOffsetY() {
        return 0;
    }

    @Override
    protected int getWidth() {
        return 100;
    }

    @Override
    protected int getHeight() {
        ensureHeight();
        return this.height;
    }

    /**
     * InfoSection#bakeSection asks for the full height <em>before</em> it calls
     * {@link #preBakeElement(InfoSection)}, so the height must be computable on
     * demand. Otherwise the appendix would be allocated zero lines and its text
     * would overlap the following page content.
     */
    @Override
    public int getFullHeight() {
        ensureHeight();
        return super.getFullHeight();
    }

    private void ensureHeight() {
        if (this.height <= 0 && MinecraftHelpers.isClientSide()) {
            FontRenderer fontRenderer = Minecraft.getMinecraft().fontRenderer;
            if (fontRenderer != null) {
                int wrapped = fontRenderer.getWordWrappedHeight(this.text,
                        (int) (getWidth() / this.scale));
                // The element itself is drawn scaled, so its layout height must be
                // scaled as well; otherwise it reserves an extra (empty) page.
                this.height = Math.max(fontRenderer.FONT_HEIGHT,
                        (int) Math.ceil(wrapped * this.scale));
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void drawElement(GuiInfoBook gui, int x, int y, int width, int height,
                               int page, int mx, int my) {
        GlStateManager.pushMatrix();
        GlStateManager.scale(this.scale, this.scale, this.scale);
        FontRenderer fontRenderer = gui.getFontRenderer();
        fontRenderer.drawSplitString(this.text, (int) (x / this.scale), (int) (y / this.scale),
                (int) (getWidth() / this.scale), 0x000000);
        GlStateManager.popMatrix();
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected void postDrawElement(GuiInfoBook gui, int x, int y, int width, int height,
                                   int page, int mx, int my) {
        // Nothing to draw on top of the code block.
    }

    @Override
    public void preBakeElement(InfoSection infoSection) {
        // Only the client can measure the font. Server-side parsing still creates
        // this appendix, but never draws it (see the class javadoc).
        ensureHeight();
    }

    @Override
    public void bakeElement(InfoSection infoSection) {
        // No additional state to bake.
    }

}
