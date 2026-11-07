package com.mpp.integratedscripting.item;

import com.mpp.IntegratedScriptingCallback;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.PartStateException;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetworkElement;
import org.cyclops.integrateddynamics.api.part.IPartState;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.block.BlockCable;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.network.event.VariableContentsUpdatedEvent;
import org.cyclops.integrateddynamics.core.part.PartStateActiveVariableBase;
import org.cyclops.integrateddynamics.core.part.PartStateBase;
import org.cyclops.integrateddynamics.core.part.panel.PartTypePanelVariableDriven;

import javax.annotation.Nullable;

/**
 * Server-side behaviour of the network refresher item.
 *
 * <p>1.12.2's Integrated Dynamics caches things that can go stale without any
 * visible error: the variable facade validation of a part, the value a Display
 * Panel last pushed to its clients, and a network-wide {@code crashed} state
 * that stops all of its elements from ticking. The refresher repairs the whole
 * network at once (docs/NETWORK-REFRESHER.md §3):</p>
 *
 * <ul>
 *   <li>post ID's own {@link VariableContentsUpdatedEvent}, so every subscribed
 *       element re-validates its variable facades and clears stale errors;</li>
 *   <li>re-validate every part of the network and recompute every Display
 *       Panel immediately;</li>
 *   <li>force a block/tile sync for each part, so clients re-render the
 *       panels;</li>
 *   <li>clear the network's crashed flag if it was set.</li>
 * </ul>
 *
 * <p>The tool is network-only: right-clicking a Display Panel or a cable both
 * refresh the entire network. Blocks that are not Integrated Dynamics cables
 * are not handled at all.</p>
 *
 * @author rubensworks
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class RefresherActions {

    private RefresherActions() {
    }

    /**
     * Handle one right-click on a block.
     *
     * <p>Must only be called server-side. Returning {@code null} means "not
     * handled", so the caller should let the vanilla/part activation run.</p>
     *
     * @param world The world.
     * @param pos The clicked block position.
     * @return The refresh result, or null when the click is not for this tool.
     */
    @Nullable
    public static RefreshResult use(World world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        if (!(block instanceof BlockCable)) {
            return null;
        }
        return refreshNetwork(world, pos);
    }

    /**
     * @param partType A part type, possibly null.
     * @return If the part is a variable-driven panel, i.e. a Display Panel
     *         (or the dynamic light panel that shares its state class).
     */
    public static boolean isDisplayPanel(@Nullable IPartType partType) {
        return partType instanceof PartTypePanelVariableDriven;
    }

    private static RefreshResult refreshNetwork(World world, BlockPos pos) {
        INetwork network = NetworkHelpers.getNetwork(world, pos, null);
        if (network == null) {
            return RefreshResult.failure(RefreshResult.KEY_ERROR_NO_NETWORK);
        }

        try {
            boolean crashCleared = network.isCrashed();
            if (crashCleared) {
                // A crashed network is skipped by ID's tick handler until this flag
                // is cleared; refreshing it is the point of this tool.
                network.setCrashed(false);
                IntegratedScriptingCallback.clog(Level.WARN,
                        "The network refresher cleared the crashed state of the Integrated Dynamics network at "
                                + pos + ".");
            }
            IPartNetwork partNetwork = NetworkHelpers.getPartNetwork(network);

            // ID's own "variables changed" notification: every subscribed
            // element (parts, variable stores, the addressed memory, ...) drops
            // its cached variable facades and re-validates them.
            postVariableContentsUpdated(network);

            int partCount = 0;
            int displayCount = 0;
            for (INetworkElement element : network.getElements()) {
                if (!(element instanceof IPartNetworkElement)) {
                    continue;
                }
                IPartNetworkElement<?, ?> partElement = (IPartNetworkElement<?, ?>) element;
                if (!partElement.isLoaded()) {
                    continue;
                }
                try {
                    IPartType partType = partElement.getPart();
                    IPartState partState = partElement.getPartState();
                    PartTarget target = partElement.getTarget();
                    if (partType == null || partState == null || target == null) {
                        continue;
                    }
                    partCount++;
                    resetVariables(partType, partState, target);
                    if (isDisplayPanel(partType)) {
                        displayCount++;
                        forceRecomputeDisplay(partType, partState, network, partNetwork, target);
                    }
                    syncPart(partState);
                    markTargetForUpdate(target);
                } catch (PartStateException e) {
                    // The element was invalidated between getElements() and the read;
                    // skip it instead of failing the whole refresh.
                } catch (RuntimeException e) {
                    IntegratedScriptingCallback.clog(Level.WARN,
                            "The network refresher skipped a part at " + pos + ": " + e);
                }
            }

            playClick(world, pos, crashCleared ? 0.9F : 1.15F);
            return RefreshResult.network(partCount, displayCount, crashCleared);
        } catch (RuntimeException e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "The network refresher failed to refresh the network at " + pos + ": " + e);
            return RefreshResult.failure(RefreshResult.KEY_ERROR_FAILED);
        }
    }

    /**
     * Drop the cached variable facade and validation errors of a part, then
     * re-read the variable cards it contains. This is the same reset a part
     * performs when its variable inventory changes.
     */
    private static void resetVariables(IPartType partType, IPartState partState, PartTarget target) {
        if (partState instanceof PartStateActiveVariableBase) {
            ((PartStateActiveVariableBase) partState).onVariableContentsUpdated(partType, target);
        }
    }

    /**
     * Forget the last displayed value and evaluate the part once more, so the
     * current value is computed immediately instead of on the next scheduled
     * part tick. Clearing the value also forces ID to treat the next
     * evaluation as a change, which is what re-sends the panel to clients.
     */
    private static void forceRecomputeDisplay(IPartType partType, IPartState partState, @Nullable INetwork network,
                                              @Nullable IPartNetwork partNetwork, PartTarget target) {
        if (partState instanceof PartTypePanelVariableDriven.State) {
            ((PartTypePanelVariableDriven.State) partState).setDisplayValue(null);
        }
        if (network == null || partNetwork == null) {
            return;
        }
        try {
            partType.update(network, partNetwork, target, partState);
        } catch (RuntimeException e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "The network refresher could not re-evaluate part '" + partType.getName() + "': " + e);
        }
    }

    /**
     * Mark the part state dirty and request a tile/packet update. The part
     * container consumes both flags on the next tile tick and sends the state
     * NBT to the clients, which is how a desynced panel is repaired.
     */
    private static void syncPart(IPartState partState) {
        partState.forceBlockRenderUpdate();
        if (partState instanceof PartStateBase) {
            PartStateBase partStateBase = (PartStateBase) partState;
            partStateBase.onDirty();
            partStateBase.sendUpdate();
        }
    }

    private static void markTargetForUpdate(PartTarget target) {
        DimPos center = target.getCenter().getPos();
        if (!center.isLoaded()) {
            return;
        }
        World world = center.getWorld();
        if (world != null) {
            markBlockForUpdate(world, center.getBlockPos());
        }
    }

    private static void markBlockForUpdate(World world, BlockPos pos) {
        IBlockState blockState = world.getBlockState(pos);
        world.notifyBlockUpdate(pos, blockState, blockState, 3);
        world.markBlockRangeForRenderUpdate(pos, pos);
    }

    private static void postVariableContentsUpdated(INetwork network) {
        try {
            network.getEventBus().post(new VariableContentsUpdatedEvent(network));
        } catch (RuntimeException e) {
            // A broken third-party listener must not abort the physical refresh.
            IntegratedScriptingCallback.clog(Level.WARN,
                    "The network refresher could not post VariableContentsUpdatedEvent: " + e);
        }
    }

    private static void playClick(World world, BlockPos pos, float pitch) {
        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.6F, pitch);
    }

}
