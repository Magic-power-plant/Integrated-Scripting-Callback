package com.mpp.integratedscripting.inventory.container;

import com.google.common.collect.Lists;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.nbt.NBTTagCompound;
import org.cyclops.cyclopscore.helper.ValueNotifierHelpers;
import org.cyclops.cyclopscore.inventory.container.TileInventoryContainerConfigurable;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.inventory.container.slot.SlotVariable;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Container for the addressed memory.
 *
 * <p>The slot grid follows {@code textures/gui/memory.png} exactly: the
 * memory card in the top-left corner, then two rows of eight address slots, each
 * with a type box above it, and the player inventory at the bottom.</p>
 *
 * <p>The type and value of every address are pushed to the client through one
 * value notifier entry, encoded as a JSON array. That is the same mechanism the
 * terminal uses for its disk list; nothing in the GUI has to evaluate variables
 * on the client, where the scripting data does not exist.</p>
 *
 * @author rubensworks
 */
public class ContainerAddressedMemory extends TileInventoryContainerConfigurable<TileAddressedMemory> {

    public static final int ADDRESS_COUNT = TileAddressedMemory.ADDRESS_COUNT;

    // Slots, as measured from the texture (slot-interior coordinates).
    public static final int MEMORY_CARD_SLOT_X = 9;
    public static final int MEMORY_CARD_SLOT_Y = 9;
    public static final int ADDRESS_ROW_ONE_X = 45;
    public static final int ADDRESS_ROW_ONE_Y = 34;
    public static final int ADDRESS_ROW_TWO_X = 27;
    public static final int ADDRESS_ROW_TWO_Y = 91;
    /** One address cell (slot + type box): 36 pixels wide, as in the texture. */
    public static final int ADDRESS_CELL_WIDTH = 36;
    public static final int PLAYER_INVENTORY_X = 89;
    public static final int PLAYER_INVENTORY_Y = 121;

    // Type boxes, as measured from the texture.
    public static final int TEXT_BOX_ROW_ONE_X = 37;
    public static final int TEXT_BOX_ROW_ONE_Y = 22;
    public static final int TEXT_BOX_ROW_TWO_X = 19;
    public static final int TEXT_BOX_ROW_TWO_Y = 79;
    public static final int TEXT_BOX_WIDTH = 32;
    public static final int TEXT_BOX_HEIGHT = 7;
    /** Distance between a type box and the address text above it. */
    public static final int ADDRESS_LABEL_GAP = 4;
    /** Ticks between two address data refreshes (server side). */
    public static final int ADDRESS_DATA_INTERVAL = 10;

    private final int addressDataId;
    private final List<AddressData> clientAddressData = Lists.newArrayList();
    private String lastSentAddressData = "";
    private int addressDataTicks;

    public ContainerAddressedMemory(InventoryPlayer inventory, TileAddressedMemory tile) {
        super(inventory, tile);
        this.addressDataId = getNextValueId();

        addInventory(tile.getInventory(), TileAddressedMemory.SLOT_MEMORY_CARD,
                MEMORY_CARD_SLOT_X, MEMORY_CARD_SLOT_Y, 1, 1);
        // Each address slot is added on its own: neighbouring addresses sit 36
        // pixels apart (the type box sits between them), while addInventory lays
        // consecutive slots out 18 pixels apart.
        for (int index = 0; index < ADDRESS_COUNT; index++) {
            addInventory(tile.getInventory(), TileAddressedMemory.getSlotForAddress(index),
                    getAddressSlotX(index), getAddressSlotY(index), 1, 1);
        }
        addPlayerInventory(inventory, PLAYER_INVENTORY_X, PLAYER_INVENTORY_Y);
    }

    @Override
    protected Slot createNewSlot(IInventory inventory, int index, int x, int y) {
        if (inventory instanceof InventoryPlayer) {
            return super.createNewSlot(inventory, index, x, y);
        }
        return new SlotVariable(inventory, index, x, y);
    }

    /**
     * @param index A zero-based address index.
     * @return The x coordinate of the address slot, relative to the GUI.
     */
    public static int getAddressSlotX(int index) {
        if (index < ADDRESS_COUNT / 2) {
            return ADDRESS_ROW_ONE_X + index * ADDRESS_CELL_WIDTH;
        }
        return ADDRESS_ROW_TWO_X + (index - ADDRESS_COUNT / 2) * ADDRESS_CELL_WIDTH;
    }

    /**
     * @param index A zero-based address index.
     * @return The y coordinate of the address slot, relative to the GUI.
     */
    public static int getAddressSlotY(int index) {
        return index < ADDRESS_COUNT / 2 ? ADDRESS_ROW_ONE_Y : ADDRESS_ROW_TWO_Y;
    }

    /**
     * @param index A zero-based address index.
     * @return The x coordinate of the type box, relative to the GUI.
     */
    public static int getTextBoxX(int index) {
        if (index < ADDRESS_COUNT / 2) {
            return TEXT_BOX_ROW_ONE_X + index * ADDRESS_CELL_WIDTH;
        }
        return TEXT_BOX_ROW_TWO_X + (index - ADDRESS_COUNT / 2) * ADDRESS_CELL_WIDTH;
    }

    /**
     * @param index A zero-based address index.
     * @return The y coordinate of the type box, relative to the GUI.
     */
    public static int getTextBoxY(int index) {
        return index < ADDRESS_COUNT / 2 ? TEXT_BOX_ROW_ONE_Y : TEXT_BOX_ROW_TWO_Y;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (this.tile.getWorld() == null || this.tile.getWorld().isRemote) {
            return;
        }
        // Reading a dynamic variable card can evaluate an operator, so the address
        // data is recomputed twice per second instead of on every tick. The GUI
        // shows the type immediately and the value within half a second.
        if (++this.addressDataTicks % ADDRESS_DATA_INTERVAL != 0) {
            return;
        }
        String encoded = AddressDataCodec.encode(collectAddressData());
        if (!encoded.equals(this.lastSentAddressData)) {
            this.lastSentAddressData = encoded;
            ValueNotifierHelpers.setValue(this, this.addressDataId, encoded);
        }
    }

    @Override
    public void onUpdate(int valueId, NBTTagCompound value) {
        if (valueId == this.addressDataId && value != null && value.hasKey(ValueNotifierHelpers.KEY)) {
            this.clientAddressData.clear();
            this.clientAddressData.addAll(AddressDataCodec.decode(value.getString(ValueNotifierHelpers.KEY)));
        }
        super.onUpdate(valueId, value);
    }

    /**
     * @return The address information the client last received, one entry per address.
     */
    public List<AddressData> getAddressData() {
        return this.clientAddressData;
    }

    private List<AddressData> collectAddressData() {
        List<AddressData> data = Lists.newArrayListWithCapacity(ADDRESS_COUNT);
        for (int i = 0; i < ADDRESS_COUNT; i++) {
            data.add(describeAddress(i));
        }
        return data;
    }

    /**
     * Describe one address for the GUI.
     *
     * <p>The value is the memory's own stored value: a variable card in the slot
     * is an output of the memory and never defines what the address holds.</p>
     */
    private AddressData describeAddress(int index) {
        IValue stored = this.tile.getStoredValue(index);
        if (stored == null) {
            return AddressData.empty();
        }
        return AddressData.value(stored.getType().getTranslationKey(), compact(stored));
    }

    @SuppressWarnings("unchecked")
    private static String compact(IValue value) {
        return value.getType().toCompactString(value);
    }

    /**
     * What the GUI knows about one address: its variable type, its value, or the
     * error that reading it produced.
     */
    public static final class AddressData {

        private static final AddressData EMPTY = new AddressData("", "", "");

        private final String typeKey;
        private final String valueText;
        private final String errorText;

        private AddressData(String typeKey, String valueText, String errorText) {
            this.typeKey = typeKey == null ? "" : typeKey;
            this.valueText = valueText == null ? "" : valueText;
            this.errorText = errorText == null ? "" : errorText;
        }

        public static AddressData empty() {
            return EMPTY;
        }

        public static AddressData value(String typeKey, String valueText) {
            return new AddressData(typeKey, valueText, "");
        }

        public static AddressData error(String typeKey, String errorText) {
            return new AddressData(typeKey, "", errorText);
        }

        public String getTypeKey() {
            return this.typeKey;
        }

        @Nullable
        public String getValueText() {
            return this.valueText.isEmpty() ? null : this.valueText;
        }

        @Nullable
        public String getErrorText() {
            return this.errorText.isEmpty() ? null : this.errorText;
        }

        public boolean isEmpty() {
            return this.typeKey.isEmpty() && this.valueText.isEmpty() && this.errorText.isEmpty();
        }
    }

    /**
     * JSON encoding of the address list, transported through one value notifier.
     */
    static final class AddressDataCodec {

        private static final String KEY_TYPE = "type";
        private static final String KEY_VALUE = "value";
        private static final String KEY_ERROR = "error";

        private AddressDataCodec() {
        }

        static String encode(List<AddressData> data) {
            JsonArray array = new JsonArray();
            for (AddressData entry : data) {
                JsonObject object = new JsonObject();
                object.addProperty(KEY_TYPE, entry.getTypeKey());
                object.addProperty(KEY_VALUE, entry.getValueText());
                object.addProperty(KEY_ERROR, entry.getErrorText());
                array.add(object);
            }
            return array.toString();
        }

        static List<AddressData> decode(String raw) {
            List<AddressData> data = Lists.newArrayList();
            if (raw == null || raw.isEmpty()) {
                return data;
            }
            try {
                JsonElement root = new JsonParser().parse(raw);
                if (!root.isJsonArray()) {
                    return data;
                }
                for (JsonElement element : root.getAsJsonArray()) {
                    if (!element.isJsonObject()) {
                        data.add(AddressData.empty());
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    data.add(new AddressData(text(object, KEY_TYPE), text(object, KEY_VALUE),
                            text(object, KEY_ERROR)));
                }
            } catch (RuntimeException e) {
                data.clear();
            }
            return data;
        }

        private static String text(JsonObject object, String key) {
            return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
        }
    }

}
