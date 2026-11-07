package com.mpp.integratedscripting.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.config.configurable.ConfigurableItem;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;
import org.cyclops.cyclopscore.config.extendedconfig.ItemConfig;
import org.cyclops.cyclopscore.helper.L10NHelpers;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The network refresher: a handheld debugging tool for Integrated Dynamics
 * networks.
 *
 * <p>Right-clicking a Display Panel or a cable refreshes the whole Integrated
 * Dynamics network it belongs to: cached variables are re-validated, all
 * panels are recomputed, and the network's crashed state is cleared. See
 * {@link RefresherActions} and docs/NETWORK-REFRESHER.md.</p>
 *
 * <p>1.12.2 difference: the item is created reflectively by Cyclops'
 * {@code ItemConfig}, so it extends {@code ConfigurableItem} and exposes a
 * static {@code getInstance()} (same pattern as the scripting disk).</p>
 *
 * @author rubensworks
 */
public class ItemRefresher extends ConfigurableItem {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static ItemRefresher _instance;

    public ItemRefresher(ExtendedConfig<ItemConfig> eConfig) {
        super(eConfig);
        setMaxStackSize(1);
    }

    /**
     * Required by Cyclops' {@code ExtendedConfig#getSubInstance()}.
     * @return The registered instance, may be null before registration.
     */
    public static ItemRefresher getInstance() {
        return _instance;
    }

    @Override
    public EnumActionResult onItemUseFirst(EntityPlayer player, World world, BlockPos pos, EnumFacing side,
                                           float hitX, float hitY, float hitZ, EnumHand hand) {
        if (world.isRemote) {
            // The click is authoritative on the server; returning PASS keeps the
            // client from predicting a different interaction.
            return EnumActionResult.PASS;
        }
        RefreshResult result = RefresherActions.use(world, pos);
        if (result == null) {
            // Not an Integrated Dynamics cable: let the block/item interaction run.
            return EnumActionResult.PASS;
        }
        player.sendStatusMessage(buildMessage(result), true);
        return EnumActionResult.SUCCESS;
    }

    private static ITextComponent buildMessage(RefreshResult result) {
        if (result.getKind() == RefreshResult.Kind.NETWORK) {
            return new TextComponentTranslation(result.getMessageKey(),
                    result.getNetworkPartCount(), result.getNetworkDisplayCount());
        }
        return new TextComponentTranslation(result.getMessageKey());
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, @Nullable World world, List<String> list, ITooltipFlag flag) {
        list.add(TextFormatting.GRAY + L10NHelpers.localize(
                "item.integratedscripting.refresher.tooltip.network"));
        list.add(TextFormatting.DARK_GRAY + L10NHelpers.localize(
                "item.integratedscripting.refresher.tooltip.detail"));
        super.addInformation(itemStack, world, list, flag);
    }

}
