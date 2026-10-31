package com.mpp.integratedscripting.core.network;

import net.minecraft.util.ResourceLocation;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.network.IIdentifiableNetworkElement;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.core.network.TileNetworkElement;
import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.blockentity.TileScriptingDrive;

import java.util.function.Supplier;

/**
 * The network element of a scripting drive: it exposes the id of the disk that
 * is currently inserted into the {@link IScriptingNetwork} of the network.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §5.8.4): the element extends
 * {@code TileNetworkElement<TileScriptingDrive>}; capability lookups are
 * nullable instead of {@code LazyOptional}.</p>
 *
 * @author rubensworks
 */
public class ScriptingDriveNetworkElement extends TileNetworkElement<TileScriptingDrive> implements
        IIdentifiableNetworkElement {

    public static final ResourceLocation GROUP = new ResourceLocation(Reference.MOD_ID, "scripting_drive");

    private final Supplier<Integer> idGetter;

    public ScriptingDriveNetworkElement(DimPos pos, Supplier<Integer> idGetter) {
        super(pos);
        this.idGetter = idGetter;
    }

    @Override
    public int getId() {
        return this.idGetter.get();
    }

    @Override
    public ResourceLocation getGroup() {
        return ScriptingDriveNetworkElement.GROUP;
    }

    @Override
    public boolean onNetworkAddition(INetwork network) {
        if (super.onNetworkAddition(network)) {
            IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(network);
            if (scriptingNetwork != null) {
                // Decision §10.3: an empty drive (id -1) is not part of the scripting network.
                int diskId = getId();
                if (diskId >= 0) {
                    scriptingNetwork.addDisk(diskId);
                    // M2 diagnostic, downgraded to DEBUG in M7: only useful when
                    // debugging network membership, too noisy for normal play.
                    IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.DEBUG, String.format(
                            "Scripting drive at %s registered disk id %s in its scripting network.",
                            getPos(), diskId));
                }
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public void onNetworkRemoval(INetwork network) {
        super.onNetworkRemoval(network);
        IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(network);
        if (scriptingNetwork != null) {
            int diskId = getId();
            if (diskId >= 0) {
                scriptingNetwork.removeDisk(diskId);
            }
        }
    }

    @Override
    public void setPriorityAndChannel(INetwork network, int priority, int channel) {

    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getChannel() {
        return IPositionedAddonsNetwork.DEFAULT_CHANNEL;
    }

    @Override
    public int getConsumptionRate() {
        return GeneralConfig.scriptingDriveBaseConsumption;
    }

    @Override
    protected Class<TileScriptingDrive> getTileClass() {
        return TileScriptingDrive.class;
    }

}
