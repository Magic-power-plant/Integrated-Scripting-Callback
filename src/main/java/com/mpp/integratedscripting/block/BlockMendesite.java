package com.mpp.integratedscripting.block;

import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.config.configurable.ConfigurableBlock;
import org.cyclops.cyclopscore.config.extendedconfig.BlockConfig;
import org.cyclops.cyclopscore.config.extendedconfig.ExtendedConfig;

/**
 * A translucent mineral used as the basic crafting material for scripting items.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation
 * (docs/PORTING-1.12.2.md §9.4): the 1.20 {@code noOcclusion} block property has
 * no direct 1.12 equivalent, so the block is registered as translucent through
 * {@link #getBlockLayer()}, {@link #isOpaqueCube(IBlockState)} and
 * {@link #isFullCube(IBlockState)} instead. The texture is 50% alpha, so the
 * translucency is visible in game.</p>
 *
 * <p>The block is created reflectively by Cyclops' {@code BlockConfig}, so it
 * must extend {@link ConfigurableBlock}, take an {@link ExtendedConfig} and
 * expose a static {@code getInstance()} (see the M2 notes in
 * docs/MILESTONES.md).</p>
 *
 * @author rubensworks
 */
public class BlockMendesite extends ConfigurableBlock {

    /**
     * Set reflectively by Cyclops' {@code ExtendedConfig#save()}; do not remove.
     */
    public static BlockMendesite _instance;

    public BlockMendesite(ExtendedConfig<BlockConfig> eConfig) {
        super(eConfig, Material.ROCK);

        setHardness(1.5F);
        setResistance(6.0F);
        setSoundType(SoundType.SNOW);
        // Translucent model: do not shade neighbours as if this were an opaque cube.
        setLightOpacity(0);
    }

    /**
     * Required by Cyclops' {@code ExtendedConfig#getSubInstance()}.
     * @return The registered instance, may be null before registration.
     */
    public static BlockMendesite getInstance() {
        return _instance;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        // §11.2 item 2: the texture has an alpha channel, so it must render in the
        // translucent pass. Missing this is a classic 1.12 "texture is black/opaque" bug.
        return BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        // 1.12 has no JSON loot table in this project (docs/PORTING-1.12.2.md §9.7),
        // so the block drops itself explicitly.
        drops.add(new ItemStack(this));
    }

}
