package com.mpp.integratedscripting.blockentity;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.persist.IDirtyMarkListener;
import org.cyclops.cyclopscore.persist.nbt.NBTPersist;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.capability.networkelementprovider.NetworkElementProviderConfig;
import org.cyclops.integrateddynamics.capability.networkelementprovider.NetworkElementProviderSingleton;
import org.cyclops.integrateddynamics.core.tileentity.TileCableConnectableInventory;
import org.apache.logging.log4j.Level;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.core.network.ScriptingDriveNetworkElement;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;
import com.mpp.integratedscripting.item.ItemScriptingDisk;

/**
 * The tile entity of the scripting drive: one slot for a scripting disk, and
 * the id of that disk exposed to the network.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §6.7 / §7.5): no
 * {@code BlockEntityConfig} (the tile entity is registered automatically by
 * Cyclops' {@code BlockAction}), and {@code exposedDiskId} is persisted through
 * Cyclops' {@code @NBTPersist}, which writes the field name as NBT key and thus
 * keeps the {@code exposedDiskId} key documented in §10.2.</p>
 *
 * @author rubensworks
 */
public class TileScriptingDrive extends TileCableConnectableInventory implements IDirtyMarkListener {

    public static final int ROWS = 1;
    public static final int COLS = 1;
    public static final int INVENTORY_SIZE = ROWS * COLS;

    @NBTPersist
    private int exposedDiskId = -1;

    public TileScriptingDrive() {
        super(INVENTORY_SIZE, "scripting_drive", 1);
        getInventory().addDirtyMarkListener(this);

        addCapabilityInternal(NetworkElementProviderConfig.CAPABILITY, new NetworkElementProviderSingleton() {
            @Override
            public INetworkElement createNetworkElement(World world, BlockPos blockPos) {
                return new ScriptingDriveNetworkElement(
                        DimPos.of(world, blockPos),
                        TileScriptingDrive.this::getExposedDiskId);
            }
        });
    }

    public int getExposedDiskId() {
        return exposedDiskId;
    }

    public void setExposedDiskId(int exposedDiskId) {
        int oldExposedDiskId = this.exposedDiskId;
        this.exposedDiskId = exposedDiskId;

        if (oldExposedDiskId != exposedDiskId) {
            this.markDirty();
            // M2 diagnostic, downgraded to DEBUG in M7 (see docs/MILESTONES.md).
            IntegratedScriptingCallback.clog(Level.DEBUG, String.format(
                    "Scripting drive at %s now exposes disk id %s.", getPos(), exposedDiskId));
            IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(getNetwork());
            if (scriptingNetwork != null) {
                if (oldExposedDiskId >= 0) {
                    scriptingNetwork.removeDisk(oldExposedDiskId);
                }
                if (exposedDiskId >= 0) {
                    scriptingNetwork.addDisk(exposedDiskId);
                }
            }
        }
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return stack.isEmpty() || stack.getItem() instanceof ItemScriptingDisk;
    }

    @Override
    public void onDirty() {
        // Cast to IInventory because the 1.12.2 CyclopsCore jar keeps the SRG method names.
        ItemStack stack = ((IInventory) getInventory()).getStackInSlot(0);
        int id = -1;
        if (stack.getItem() instanceof ItemScriptingDisk) {
            id = ((ItemScriptingDisk) stack.getItem()).getOrCreateDiskId(stack);
        }
        setExposedDiskId(id);
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation("tile.blocks.integratedscriptingcallback.scripting_drive.name");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // M2 diagnostic ("loaded with disk id"), downgraded to DEBUG in M7: the
        // persisted exposedDiskId is restored by Cyclops' @NBTPersist before onLoad.
        if (this.exposedDiskId >= 0 && getWorld() != null && !getWorld().isRemote) {
            IntegratedScriptingCallback.clog(Level.DEBUG, String.format(
                    "Scripting drive at %s loaded with disk id %s.", getPos(), exposedDiskId));
        }
    }

}
