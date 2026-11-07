package com.mpp.integratedscripting.core.evaluate.value;

import com.mpp.Reference;
import net.minecraft.util.math.BlockPos;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBase;

import java.util.List;

/**
 * The {@code addressed_memory} object value type: a handle to one placed
 * addressed memory block.
 *
 * <p>Scripts (and the Logic Programmer) can call the {@code get} and
 * {@code set} operators on such a value, which are bound as methods on the
 * object: {@code memory.get("a")} and {@code memory.set("a", 42)}. The
 * operators themselves live in
 * {@link com.mpp.integratedscripting.core.evaluate.operator.AddressedMemoryOperators}.</p>
 *
 * <p>The value is serialized as {@code dimension:x:y:z}, so a memory reference
 * can also travel through a static variable card. The block position is the
 * identity of the object, which is why equality only compares that.</p>
 *
 * @author rubensworks
 */
public class ValueTypeAddressedMemory extends ValueObjectTypeBase<ValueAddressedMemory> {

    public static final String TYPE_NAME = "addressed_memory";

    public ValueTypeAddressedMemory() {
        super(TYPE_NAME, 0x00AAAA, "§3", ValueAddressedMemory.class);
    }

    @Override
    protected String getModId() {
        return Reference.MOD_ID;
    }

    @Override
    public ValueAddressedMemory getDefault() {
        return ValueAddressedMemory.of(this, null);
    }

    @Override
    public String toCompactString(ValueAddressedMemory value) {
        DimPos position = value.getPosition();
        if (position == null) {
            return "null";
        }
        BlockPos pos = position.getBlockPos();
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " (" + position.getDimensionId() + ")";
    }

    @Override
    public String serialize(ValueAddressedMemory value) {
        DimPos position = value.getPosition();
        if (position == null) {
            return "";
        }
        BlockPos pos = position.getBlockPos();
        return position.getDimensionId() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    @Override
    public ValueAddressedMemory deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return getDefault();
        }
        String[] parts = raw.split(":");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid addressed memory value: " + raw);
        }
        return ValueAddressedMemory.of(this, DimPos.of(Integer.parseInt(parts[0]),
                new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]))));
    }

    @Override
    public void loadTooltip(List<String> list, boolean advanced, ValueAddressedMemory value) {
        super.loadTooltip(list, advanced, value);
        // The Logic Programmer draws a value type's tooltip through its string
        // input element, which has no concrete value to pass on:
        // GuiElementValueTypeString.loadTooltip calls
        // getValueType().loadTooltip(list, true, null). ValueTypeBase tolerates
        // that by ignoring the value, so the value must be null-checked here too,
        // otherwise hovering the type crashes the client while rendering the GUI.
        if (value == null) {
            return;
        }
        DimPos position = value.getPosition();
        if (position != null) {
            list.add(L10NHelpers.localize("gui.integratedscripting.addressed_memory.position",
                    position.getBlockPos().getX(), position.getBlockPos().getY(),
                    position.getBlockPos().getZ(), position.getDimensionId()));
        }
    }

    /**
     * @param value A memory value.
     * @return A human readable name, used by diagnostics and the memory card.
     */
    public String getName(ValueAddressedMemory value) {
        return toCompactString(value);
    }

}
