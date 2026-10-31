package com.mpp.integratedscripting.part;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.integrateddynamics.core.helper.L10NValues;
import org.cyclops.integrateddynamics.core.part.PartStateEmpty;
import org.cyclops.integrateddynamics.core.part.panel.PartTypePanel;
import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.client.gui.container.GuiTerminalScripting;
import com.mpp.integratedscripting.inventory.container.ContainerTerminalScripting;

/**
 * A part that exposes a GUI using which players can manage scripts in the
 * network.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation
 * (docs/PORTING-1.12.2.md §6.24 / §7.7): the part type extends
 * {@code PartTypePanel} (the 1.12 panel base), implements
 * {@code IGuiContainerProvider} so that {@code PartTypeBase} registers an
 * {@code ExtendedGuiHandler.PART} GUI, and the activation/energy hooks use the
 * 1.12 signatures. The part item/block names are derived by
 * {@code PartTypeBase} as required by decision A9:
 * {@code integratedscriptingcallback:part_terminal_scripting_item} and
 * {@code ...:part_terminal_scripting_block}.</p>
 *
 * @author rubensworks
 */
public class PartTypeTerminalScripting
        extends PartTypePanel<PartTypeTerminalScripting, PartStateEmpty<PartTypeTerminalScripting>> {

    public PartTypeTerminalScripting(String name) {
        super(name);
    }

    @Override
    public Class<? super PartTypeTerminalScripting> getPartTypeClass() {
        return PartTypeTerminalScripting.class;
    }

    @Override
    public Class<? extends net.minecraft.inventory.Container> getContainer() {
        return ContainerTerminalScripting.class;
    }

    @Override
    public Class<? extends net.minecraft.client.gui.GuiScreen> getGui() {
        return GuiTerminalScripting.class;
    }

    @Override
    protected ModBase getMod() {
        // PartTypeBase creates the part's block, item and GUI entry from this mod.
        return IntegratedScriptingCallback._instance;
    }

    @Override
    public boolean onPartActivated(World world, BlockPos pos,
                                   PartStateEmpty<PartTypeTerminalScripting> partState,
                                   EntityPlayer player, EnumHand hand, ItemStack heldItem,
                                   EnumFacing side, float hitX, float hitY, float hitZ) {
        if (isUpdate(partState) && !partState.isEnabled()) {
            player.sendStatusMessage(new TextComponentTranslation(L10NValues.PART_ERROR_LOWENERGY), true);
            return true;
        }
        return super.onPartActivated(world, pos, partState, player, hand, heldItem,
                side, hitX, hitY, hitZ);
    }

    @Override
    public boolean isUpdate(PartStateEmpty<PartTypeTerminalScripting> state) {
        return getConsumptionRate(state) > 0
                && org.cyclops.integrateddynamics.GeneralConfig.energyConsumptionMultiplier > 0;
    }

    @Override
    public int getConsumptionRate(PartStateEmpty<PartTypeTerminalScripting> state) {
        return GeneralConfig.terminalScriptingBaseConsumption;
    }

    @Override
    protected PartStateEmpty<PartTypeTerminalScripting> constructDefaultState() {
        return new PartStateEmpty<PartTypeTerminalScripting>() {
            @Override
            public int getUpdateInterval() {
                return 1; // For enabling energy consumption
            }
        };
    }

}
