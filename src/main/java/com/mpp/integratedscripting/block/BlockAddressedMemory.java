package com.mpp.integratedscripting.block;

import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import com.mpp.integratedscripting.client.gui.container.GuiAddressedMemory;
import com.mpp.integratedscripting.inventory.container.ContainerAddressedMemory;
import net.minecraft.block.SoundType;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.util.EnumFacing;
import org.cyclops.cyclopscore.block.property.BlockProperty;
import org.cyclops.cyclopscore.config.extendedconfig.BlockConfig;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;
import org.cyclops.integrateddynamics.core.block.BlockContainerGuiCabled;

/**
 * The addressed memory block: sixteen addressable variables that live directly
 * in an Integrated Dynamics network.
 *
 * <p>Like the scripting drive it is a cabled container block, so it connects to
 * cables and gets a facing property for its model. Its tile entity provides the
 * network element and the variable container.</p>
 *
 * @author rubensworks
 */
public class BlockAddressedMemory extends BlockContainerGuiCabled {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static BlockAddressedMemory _instance;

    @BlockProperty
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockAddressedMemory(ExtendedConfig<BlockConfig> eConfig) {
        super(eConfig, TileAddressedMemory.class);

        setHardness(2.5F);
        setResistance(6.0F);
        setSoundType(SoundType.METAL);
    }

    @Override
    public Class<? extends Container> getContainer() {
        return ContainerAddressedMemory.class;
    }

    /**
     * Required by Cyclops' {@code ExtendedConfig#getSubInstance()}.
     * @return The registered instance, may be null before registration.
     */
    public static BlockAddressedMemory getInstance() {
        return _instance;
    }

    @Override
    public Class<? extends GuiScreen> getGui() {
        return GuiAddressedMemory.class;
    }

}
