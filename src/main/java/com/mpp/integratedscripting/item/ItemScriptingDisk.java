package com.mpp.integratedscripting.item;

import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.config.configurable.ConfigurableItem;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;
import org.cyclops.cyclopscore.config.extendedconfig.ItemConfig;
import org.cyclops.cyclopscore.helper.ItemStackHelpers;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import com.mpp.Reference;

import java.util.List;

/**
 * Item for storing scripts.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §6.21 / §7.4): the item is
 * created reflectively by Cyclops' {@code ItemConfig} and must therefore extend
 * {@code ConfigurableItem} (which fulfils {@code IConfigurableItem}) and expose a
 * static {@code getInstance()}; tooltips are plain strings; the disk id NBT key
 * uses the mod namespace (decision A5/§10.2).</p>
 *
 * @author rubensworks
 */
public class ItemScriptingDisk extends ConfigurableItem {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static ItemScriptingDisk _instance;

    private static final String NBT_KEY_ID = Reference.MOD_ID + ":disk-key";

    public ItemScriptingDisk(ExtendedConfig<ItemConfig> eConfig) {
        super(eConfig);
        // Stack size is intentionally left at 64: disks with an id differ in NBT and do not stack (decision D13).
    }

    /**
     * Required by Cyclops' {@code ExtendedConfig#getSubInstance()}.
     * @return The registered instance, may be null before registration.
     */
    public static ItemScriptingDisk getInstance() {
        return _instance;
    }

    public static int generateScriptingId() {
        return IntegratedDynamics.globalCounters.getNext(Reference.MOD_ID + ":scripting-ids");
    }

    public int getDiskId(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemScriptingDisk
                && itemStack.hasTagCompound()
                && itemStack.getTagCompound().hasKey(NBT_KEY_ID, Constants.NBT.TAG_INT)) {
            return itemStack.getTagCompound().getInteger(NBT_KEY_ID);
        }
        return -1;
    }

    public int getOrCreateDiskId(ItemStack itemStack) {
        int id = getDiskId(itemStack);

        // Initialize an ID if none has been set yet
        if (id < 0) {
            id = generateScriptingId();
            ItemStackHelpers.getSafeTagCompound(itemStack).setInteger(NBT_KEY_ID, id);
        }

        return id;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(ItemStack itemStack, World world, List<String> list, ITooltipFlag flag) {
        int id = getDiskId(itemStack);
        if (id >= 0) {
            list.add(TextFormatting.GRAY + L10NHelpers.localize(
                    "item.integratedscripting.scripting_disk.id", id));
        }
        if (id >= 0 && net.minecraft.client.Minecraft.getMinecraft().player != null
                && net.minecraft.client.Minecraft.getMinecraft().player.isCreative()) {
            list.add(L10NHelpers.localize("item.items.integrateddynamics.variable.warning"));
        }
        super.addInformation(itemStack, world, list, flag);
    }

}
