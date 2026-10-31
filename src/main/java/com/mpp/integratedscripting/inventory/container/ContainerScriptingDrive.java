package com.mpp.integratedscripting.inventory.container;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import org.cyclops.cyclopscore.inventory.container.TileInventoryContainerConfigurable;
import org.cyclops.cyclopscore.inventory.slot.SlotSingleItem;
import com.mpp.RegistryEntries;
import com.mpp.integratedscripting.blockentity.TileScriptingDrive;

/**
 * Container for the scripting drive: exactly one slot that only accepts a
 * scripting disk.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation
 * (docs/PORTING-1.12.2.md §6.20): the container extends
 * {@code TileInventoryContainerConfigurable} and is resolved through
 * {@code GuiHandler} (no {@code MenuType} / container config).</p>
 *
 * @author rubensworks
 */
public class ContainerScriptingDrive extends TileInventoryContainerConfigurable<TileScriptingDrive> {

    public ContainerScriptingDrive(InventoryPlayer inventory, TileScriptingDrive tile) {
        super(inventory, tile);

        addInventory(tile.getInventory(), 0,
                offsetX + 80, offsetY + 16, TileScriptingDrive.ROWS, TileScriptingDrive.COLS);
        addPlayerInventory(inventory, offsetX + 8, offsetY + 46);
    }

    @Override
    protected Slot createNewSlot(IInventory inventory, int index, int x, int y) {
        if (inventory instanceof InventoryPlayer) {
            return super.createNewSlot(inventory, index, x, y);
        }
        return new SlotSingleItem(inventory, index, x, y, RegistryEntries.itemScriptingDisk());
    }

}
