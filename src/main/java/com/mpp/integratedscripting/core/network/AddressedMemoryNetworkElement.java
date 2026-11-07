package com.mpp.integratedscripting.core.network;

import com.mpp.GeneralConfig;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import net.minecraft.util.ResourceLocation;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.network.IEventListenableNetworkElement;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.core.network.TileNetworkElement;

/**
 * The network element of an addressed memory.
 *
 * <p>Besides making the block part of the Integrated Dynamics network (which is
 * what makes its address cards resolvable, through the tile's
 * {@code IVariableContainer}), it announces the memory to the scripting network
 * of that network. Scripts compiled in the network then receive the memory
 * object as a binding, so {@code 内存器变量卡.get("a")} works without any extra
 * wiring.</p>
 *
 * @author rubensworks
 */
public class AddressedMemoryNetworkElement extends TileNetworkElement<TileAddressedMemory>
        implements IEventListenableNetworkElement<TileAddressedMemory> {

    public static final ResourceLocation GROUP = new ResourceLocation(Reference.MOD_ID, "addressed_memory");

    public AddressedMemoryNetworkElement(DimPos pos) {
        super(pos);
    }

    @Override
    public boolean onNetworkAddition(INetwork network) {
        if (super.onNetworkAddition(network)) {
            IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(network);
            if (scriptingNetwork != null) {
                scriptingNetwork.addMemory(TileAddressedMemory.getMemoryName(getPos().getBlockPos()), getPos());
            }
            return true;
        }
        return false;
    }

    @Override
    public void onNetworkRemoval(INetwork network) {
        super.onNetworkRemoval(network);
        IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(network);
        if (scriptingNetwork != null) {
            scriptingNetwork.removeMemory(TileAddressedMemory.getMemoryName(getPos().getBlockPos()));
        }
    }

    @Override
    public TileAddressedMemory getNetworkEventListener() {
        return getTile();
    }

    @Override
    public void setPriorityAndChannel(INetwork network, int priority, int channel) {
        // Memories have no priority.
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
        return GeneralConfig.addressedMemoryBaseConsumption;
    }

    @Override
    protected Class<TileAddressedMemory> getTileClass() {
        return TileAddressedMemory.class;
    }

}
