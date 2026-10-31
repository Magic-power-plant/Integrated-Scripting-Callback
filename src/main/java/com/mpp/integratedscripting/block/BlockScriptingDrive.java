package com.mpp.integratedscripting.block;

import net.minecraft.block.SoundType;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.inventory.Container;
import net.minecraft.util.EnumFacing;
import org.cyclops.cyclopscore.block.property.BlockProperty;
import org.cyclops.cyclopscore.config.extendedconfig.BlockConfig;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;
import org.cyclops.integrateddynamics.core.block.BlockContainerGuiCabled;
import com.mpp.integratedscripting.blockentity.TileScriptingDrive;
import com.mpp.integratedscripting.client.gui.container.GuiScriptingDrive;
import com.mpp.integratedscripting.inventory.container.ContainerScriptingDrive;

/**
 * A block that can hold scripting disks so that they can be referred to
 * elsewhere in the network.
 *
 * <p>1.12.2 difference (docs/PORTING-1.12.2.md §6.6): the block extends
 * {@code BlockContainerGuiCabled} and implements {@code IGuiContainerProvider};
 * the facing property is picked up by Cyclops' block property manager and the
 * inherited {@code getStateForPlacement} rotates the block towards the placer.</p>
 *
 * @author rubensworks
 */
public class BlockScriptingDrive extends BlockContainerGuiCabled {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static BlockScriptingDrive _instance;

    @BlockProperty
    public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);

    public BlockScriptingDrive(ExtendedConfig<BlockConfig> eConfig) {
        super(eConfig, TileScriptingDrive.class);

        setHardness(2.0F);
        setResistance(5.0F);
        setSoundType(SoundType.METAL);
    }

    @Override
    public Class<? extends Container> getContainer() {
        return ContainerScriptingDrive.class;
    }

    /**
     * Required by Cyclops' {@code ExtendedConfig#getSubInstance()}.
     * @return The registered instance, may be null before registration.
     */
    public static BlockScriptingDrive getInstance() {
        return _instance;
    }

    @Override
    public Class<? extends GuiScreen> getGui() {
        return GuiScriptingDrive.class;
    }

}
