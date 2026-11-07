package com.mpp.integratedscripting.core.evaluate.value;

import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueBase;

import javax.annotation.Nullable;

/**
 * The value of an {@code addressed_memory} object: a reference to one placed
 * addressed memory block.
 *
 * <p>The reference is a {@link DimPos} and nothing else, so it survives
 * re-evaluations of the variable that produced it and never keeps a tile entity
 * alive. The tile is looked up on every evaluation, which is exactly what makes
 * the object reflect the current contents of its addresses.</p>
 *
 * @author rubensworks
 */
public class ValueAddressedMemory extends ValueBase {

    @Nullable
    private final DimPos position;

    protected ValueAddressedMemory(IValueType type, @Nullable DimPos position) {
        super(type);
        this.position = position;
    }

    /**
     * @param type The addressed memory value type.
     * @param position The position of the memory block, or null for the default value.
     * @return A value referencing that memory.
     */
    public static ValueAddressedMemory of(IValueType type, @Nullable DimPos position) {
        return new ValueAddressedMemory(type, position);
    }

    /**
     * @return The position of the referenced memory block, or null if this is the
     *         default (empty) value.
     */
    @Nullable
    public DimPos getPosition() {
        return this.position;
    }

    /**
     * Resolve the tile entity of the referenced memory.
     *
     * @return The tile entity, or null when the position is empty, the dimension is
     *         not loaded, or the block at the position is no longer a memory.
     */
    @Nullable
    public TileAddressedMemory getMemory() {
        if (this.position == null) {
            return null;
        }
        World world = this.position.getWorld();
        if (world == null) {
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) {
                return null;
            }
            world = server.getWorld(this.position.getDimensionId());
        }
        if (world == null) {
            return null;
        }
        TileEntity tile = world.getTileEntity(this.position.getBlockPos());
        return tile instanceof TileAddressedMemory ? (TileAddressedMemory) tile : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ValueAddressedMemory)) {
            return false;
        }
        ValueAddressedMemory that = (ValueAddressedMemory) o;
        return java.util.Objects.equals(this.position, that.position);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(this.position);
    }

    @Override
    public String toString() {
        return "ValueAddressedMemory(" + this.position + ")";
    }

}
