package com.mpp.integratedscripting.client.gui.container;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.InventoryPlayer;
import org.cyclops.cyclopscore.client.gui.container.GuiContainerConfigurable;
import com.mpp.integratedscripting.blockentity.TileScriptingDrive;
import com.mpp.integratedscripting.inventory.container.ContainerScriptingDrive;

/**
 * GUI for the scripting drive.
 *
 * <p>The texture path is derived from the Cyclops GUI texture reference and the
 * block's named id, so it resolves to
 * {@code assets/integratedscriptingcallback/textures/gui/scripting_drive.png}.
 * The image is 176x128, hence the {@link #getBaseYSize()} override
 * (docs/PORTING-1.12.2.md §7.9.1).</p>
 *
 * @author rubensworks
 */
public class GuiScriptingDrive extends GuiContainerConfigurable<ContainerScriptingDrive> {

    public GuiScriptingDrive(InventoryPlayer inventory, TileScriptingDrive tile) {
        super(new ContainerScriptingDrive(inventory, tile));
    }

    @Override
    protected int getBaseYSize() {
        return 128;
    }

    // The 1.12.2 Cyclops base class only declares the SRG-named variant of this
    // method, so the MCP-named abstract method has to be implemented explicitly.
    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(this.texture);
        this.drawTexturedModalRect(this.guiLeft + this.offsetX, this.guiTop + this.offsetY,
                0, 0, this.xSize, this.getBaseYSize());
    }

}
