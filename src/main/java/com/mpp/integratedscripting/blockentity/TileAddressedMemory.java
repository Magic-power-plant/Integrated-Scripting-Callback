package com.mpp.integratedscripting.blockentity;

import com.google.common.collect.Sets;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.block.BlockAddressedMemory;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.core.evaluate.value.ValueAddressedMemory;
import com.mpp.integratedscripting.core.network.AddressedMemoryNetworkElement;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.persist.IDirtyMarkListener;
import org.cyclops.cyclopscore.persist.nbt.NBTPersist;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.block.IVariableContainer;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.item.IVariableFacade;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHolder;
import org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.INetworkElement;
import org.cyclops.integrateddynamics.api.network.INetworkEventListener;
import org.cyclops.integrateddynamics.api.network.event.INetworkEvent;
import org.cyclops.integrateddynamics.capability.networkelementprovider.NetworkElementProviderConfig;
import org.cyclops.integrateddynamics.capability.networkelementprovider.NetworkElementProviderSingleton;
import org.cyclops.integrateddynamics.capability.variablecontainer.VariableContainerConfig;
import org.cyclops.integrateddynamics.capability.variablecontainer.VariableContainerDefault;
import org.cyclops.integrateddynamics.capability.variablefacade.VariableFacadeHolderConfig;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.cyclops.integrateddynamics.core.item.ValueTypeVariableFacade;
import org.cyclops.integrateddynamics.core.network.event.VariableContentsUpdatedEvent;
import org.cyclops.integrateddynamics.core.tileentity.TileCableConnectableInventory;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * The tile entity of the addressed memory.
 *
 * <p>The memory <b>stores</b> sixteen variables itself, addressed by the letters
 * {@code a} to {@code p}, and {@code get}/{@code set} only ever read and write
 * that own storage ({@link #storedValues}). The variable cards are the memory's
 * <b>outputs</b>, not its inputs:</p>
 *
 * <ul>
 *   <li>the top-left slot holds the memory's own card, into which the block
 *       writes the {@code addressed_memory} object (the accessor with
 *       {@code get}/{@code set});</li>
 *   <li>an address slot holds a card into which the block writes the stored
 *       value of that address (as a static variable), so the value can be used
 *       elsewhere in the network. Inserting a card into an address that already
 *       stores a value fills it; {@code set} updates it.</li>
 * </ul>
 *
 * <p>Because the address cards are ordinary static value cards, the
 * {@link IVariableContainer} capability still makes them resolvable by the
 * network, which is what "directly connected to the network" means.</p>
 *
 * <p>Values are persisted as a small JSON object per letter, so the block never
 * depends on a custom NBT serialization of {@link IValue}.</p>
 *
 * <p>Addresses are the letters {@code a} to {@code p}, in slot order: slot 0 is
 * the memory card, slots 1 to 16 are the addresses.</p>
 *
 * @author rubensworks
 */
public class TileAddressedMemory extends TileCableConnectableInventory
        implements IDirtyMarkListener, INetworkEventListener<AddressedMemoryNetworkElement> {

    public static final int ADDRESS_COUNT = 16;
    public static final String ADDRESS_LETTERS = "abcdefghijklmnop";
    public static final int SLOT_MEMORY_CARD = 0;
    public static final int INVENTORY_SIZE = 1 + ADDRESS_COUNT;

    private static final String KEY_TYPE = "type";
    private static final String KEY_VALUE = "value";
    /** Upper bound of remembered card ids per address (a card can be replaced). */
    public static final int MAX_CARD_IDS_PER_ADDRESS = 8;

    /**
     * Per-address values written by {@code set(...)}, as JSON keyed by address letter.
     */
    @NBTPersist
    private String storedValues = "";

    /**
     * Per-address variable ids of the cards this memory has written, as JSON keyed by
     * address letter.
     *
     * <p>Keeping the id after the card is taken out lets the network keep resolving
     * references to that card (operator cards, proxy cards, ...) to the memory's
     * current stored value, forever: the card is only the carrier of the id.</p>
     */
    @NBTPersist
    private String cardIds = "";

    private final IVariableContainer variableContainer;
    private boolean shouldSendUpdateEvent;
    private boolean writingCard;

    public TileAddressedMemory() {
        super(INVENTORY_SIZE, "addressed_memory", 1);
        getInventory().addDirtyMarkListener(this);

        addCapabilityInternal(NetworkElementProviderConfig.CAPABILITY, new NetworkElementProviderSingleton() {
            @Override
            public INetworkElement createNetworkElement(World world, BlockPos blockPos) {
                return new AddressedMemoryNetworkElement(DimPos.of(world, blockPos));
            }
        });

        this.variableContainer = new VariableContainerDefault();
        addCapabilityInternal(VariableContainerConfig.CAPABILITY, this.variableContainer);
    }

    // ---------------------------------------------------------------- addresses

    /**
     * @param address An address string, for example {@code "a"}.
     * @return The zero-based address index, or -1 when the address is invalid.
     */
    public static int getAddressIndex(@Nullable String address) {
        if (address == null || address.length() != 1) {
            return -1;
        }
        return ADDRESS_LETTERS.indexOf(Character.toLowerCase(address.charAt(0)));
    }

    /**
     * @param index A zero-based address index.
     * @return The address letter, for example {@code "a"}.
     */
    public static String getAddressLetter(int index) {
        return String.valueOf(ADDRESS_LETTERS.charAt(index));
    }

    /**
     * @param index A zero-based address index.
     * @return The inventory slot of that address card.
     */
    public static int getSlotForAddress(int index) {
        return SLOT_MEMORY_CARD + 1 + index;
    }

    /**
     * @param pos A memory block position.
     * @return A stable name for a memory at that position, used inside scripts.
     */
    public static String getMemoryName(BlockPos pos) {
        return "addressed_memory_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
    }

    /**
     * @return A stable name for this memory, used to expose it inside scripts.
     */
    public String getMemoryName() {
        return getMemoryName(getPos());
    }

    // ---------------------------------------------------------------- inventory

    public IInventory getInventoryAccess() {
        return getInventory();
    }

    @Override
    public boolean isItemValidForSlot(int index, ItemStack stack) {
        return stack.isEmpty() || stack.hasCapability(VariableFacadeHolderConfig.CAPABILITY, null);
    }

    @Override
    public ITextComponent getDisplayName() {
        return new TextComponentTranslation("tile.blocks.integratedscriptingcallback.addressed_memory.name");
    }

    @Override
    public void onDirty() {
        if (getWorld() == null || getWorld().isRemote) {
            return;
        }
        if (this.writingCard) {
            // Our own card write: the caller already handled the network refresh.
            return;
        }
        writeMemoryCard();
        syncAddressCards();
        refreshVariables(true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getWorld() != null && !getWorld().isRemote) {
            this.shouldSendUpdateEvent = true;
            writeMemoryCard();
            // Cards mirror the stored values: make them match again after a reload,
            // in case the world was saved between a value change and its card write.
            syncAddressCards();
        }
    }

    /**
     * Ensure the top-left card describes this memory block, so it can be taken
     * out and used as the memory's object anywhere in the network.
     *
     * <p>The card is a plain static value card of the {@code addressed_memory}
     * value type. That is what makes it render like any other typed variable card
     * (the value type's texture over the variable card texture) and what lets
     * Integrated Dynamics evaluate it without any custom facade.</p>
     */
    protected void writeMemoryCard() {
        if (this.writingCard) {
            return;
        }
        ItemStack stack = ((IInventory) getInventory()).getStackInSlot(SLOT_MEMORY_CARD);
        if (stack.isEmpty()) {
            return;
        }
        IVariableFacadeHolder holder = stack.getCapability(VariableFacadeHolderConfig.CAPABILITY, null);
        if (holder == null) {
            // Not a variable card: nothing we could write a facade into.
            return;
        }
        IVariableFacade current = holder.getVariableFacade();
        if (isMemoryCardOfThisBlock(current)) {
            return;
        }
        if (current != null && current.isValid() && !isMemoryCard(current)) {
            // Leave value, operator and script cards alone: this slot only claims
            // empty cards and memory cards, so a valuable card is never lost.
            return;
        }

        this.writingCard = true;
        try {
            final ValueAddressedMemory memoryValue = AddressedMemoryValues.of(DimPos.of(getWorld(), getPos()));
            IVariableFacadeHandlerRegistry registry = IntegratedDynamics._instance.getRegistryManager()
                    .getRegistry(IVariableFacadeHandlerRegistry.class);
            ItemStack written = registry.writeVariableFacadeItem(true, stack.copy(), ValueTypes.REGISTRY,
                    new IVariableFacadeHandlerRegistry.IVariableFacadeFactory<IValueTypeVariableFacade>() {
                        @Override
                        public IValueTypeVariableFacade create(boolean generateId) {
                            return new ValueTypeVariableFacade<ValueAddressedMemory>(generateId,
                                    AddressedMemoryValues.getType(), memoryValue);
                        }

                        @Override
                        public IValueTypeVariableFacade create(int id) {
                            return new ValueTypeVariableFacade<ValueAddressedMemory>(id,
                                    AddressedMemoryValues.getType(), memoryValue);
                        }
                    },
                    null, BlockAddressedMemory.getInstance());
            ((IInventory) getInventory()).setInventorySlotContents(SLOT_MEMORY_CARD, written);
        } finally {
            this.writingCard = false;
        }
    }

    /**
     * @param facade A variable facade.
     * @return If the facade is a static card of the addressed memory value type.
     */
    private static boolean isMemoryCard(@Nullable IVariableFacade facade) {
        if (!(facade instanceof IValueTypeVariableFacade)) {
            return false;
        }
        IValue value = ((IValueTypeVariableFacade<?>) facade).getValue();
        return value != null && value.getType() == AddressedMemoryValues.getTypeOrNull();
    }

    /**
     * @param facade A variable facade.
     * @return If the facade is a memory card that refers to this block.
     */
    private boolean isMemoryCardOfThisBlock(@Nullable IVariableFacade facade) {
        if (!isMemoryCard(facade)) {
            return false;
        }
        IValue value = ((IValueTypeVariableFacade<?>) facade).getValue();
        if (!(value instanceof ValueAddressedMemory)) {
            return false;
        }
        DimPos position = ((ValueAddressedMemory) value).getPosition();
        return position != null
                && position.getDimensionId() == getWorld().provider.getDimension()
                && position.getBlockPos().equals(getPos());
    }

    // ---------------------------------------------------------------- values

    /**
     * @param index A zero-based address index.
     * @return The variable card in that address, or null when the slot is empty.
     */
    @Nullable
    public IVariableFacade getCardFacade(int index) {
        ItemStack stack = ((IInventory) getInventory()).getStackInSlot(getSlotForAddress(index));
        if (stack.isEmpty()) {
            return null;
        }
        IVariableFacadeHolder holder = stack.getCapability(VariableFacadeHolderConfig.CAPABILITY, null);
        return holder == null ? null : holder.getVariableFacade();
    }

    /**
     * @param index A zero-based address index.
     * @return The value the memory stores at that address. Addresses that were
     *         never written are initialised to integer {@code 0}; null is only
     *         returned when a stored value became unreadable.
     */
    @Nullable
    public IValue getStoredValue(int index) {
        String letter = getAddressLetter(index);
        JsonObject root = parseStoredValues();
        if (!root.has(letter) || !root.get(letter).isJsonObject()) {
            // Every address starts as integer 0, so a fresh memory is usable
            // without writing all sixteen addresses first.
            return ValueTypeInteger.ValueInteger.of(0);
        }
        JsonObject entry = root.getAsJsonObject(letter);
        IValueType<?> valueType = ValueTypes.REGISTRY.getValueType(
                entry.has(KEY_TYPE) ? entry.get(KEY_TYPE).getAsString() : "");
        if (valueType == null) {
            return null;
        }
        try {
            return ValueHelpers.deserializeRaw(valueType, entry.has(KEY_VALUE) ? entry.get(KEY_VALUE).getAsString() : "");
        } catch (RuntimeException e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "Dropping unreadable addressed memory value at " + getMemoryName() + "/" + letter + ": " + e);
            return null;
        }
    }

    /**
     * Read the value the memory stores at one address.
     *
     * <p>The value comes from the memory's own storage and is integer {@code 0}
     * until it is written. A variable card in the address slot does not define
     * the value: the memory writes its stored value into that card so the value
     * can be used elsewhere in the network.</p>
     *
     * @param index A zero-based address index.
     * @return The value of that address.
     * @throws EvaluationException When the stored value became unreadable.
     */
    public IValue readValue(int index) throws EvaluationException {
        String letter = getAddressLetter(index);
        IValue stored = getStoredValue(index);
        if (stored == null) {
            throw new EvaluationException(L10NHelpers.localize(
                    "addressedmemory.integratedscripting.error.address_empty", letter));
        }
        return stored;
    }

    /**
     * Store a value at an address and mirror it into the variable card of that
     * slot, when one is present.
     *
     * @param index A zero-based address index.
     * @param value The value to store.
     * @return The stored value.
     * @throws EvaluationException When the value can not be persisted.
     */
    public IValue writeValue(int index, IValue value) throws EvaluationException {
        // A memory value can only be written by the logical server, on its own thread:
        // writing from a client-side evaluation used to reach the server tile through
        // the dimension fallback, which raced with the server and could lose the card
        // update entirely.
        if (getWorld() == null || getWorld().isRemote) {
            throw new EvaluationException(L10NHelpers.localize(
                    "addressedmemory.integratedscripting.error.client_side"));
        }
        net.minecraft.server.MinecraftServer server = getWorld().getMinecraftServer();
        if (server != null && !server.isCallingFromMinecraftThread()) {
            throw new EvaluationException(L10NHelpers.localize(
                    "addressedmemory.integratedscripting.error.wrong_thread"));
        }
        String letter = getAddressLetter(index);
        String serialized;
        try {
            serialized = ValueHelpers.serializeRaw(value);
        } catch (RuntimeException e) {
            throw new EvaluationException(L10NHelpers.localize(
                    "addressedmemory.integratedscripting.error.unserializable_value", letter,
                    L10NHelpers.localize(value.getType().getTranslationKey())));
        }
        JsonObject entry = new JsonObject();
        entry.addProperty(KEY_TYPE, value.getType().getTranslationKey());
        entry.addProperty(KEY_VALUE, serialized);
        JsonObject root = parseStoredValues();
        String previous = root.has(letter) ? root.get(letter).toString() : null;
        boolean changed = !entry.toString().equals(previous);
        if (changed) {
            root.add(letter, entry);
            this.storedValues = root.toString();
            markDirty();
            this.shouldSendUpdateEvent = true;
        }
        // The card in the address slot is the memory's output: write the stored
        // value into it so the network (and the player) can use it as a variable.
        boolean cardWritten = syncAddressCard(index, value);
        if (changed || cardWritten) {
            this.shouldSendUpdateEvent = true;
            // Publish the new value in the same tick, so variables that resolve this
            // address (Display Panels, cards referencing its id, ...) see it right away.
            refreshVariables(true);
        }
        return value;
    }

    /**
     * Write the given value into the variable card of an address slot, replacing
     * whatever variable the card referenced: the card mirrors the memory.
     *
     * @param index A zero-based address index.
     * @param value The stored value of that address.
     * @return If the card was (re)written.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected boolean syncAddressCard(int index, IValue value) {
        if (this.writingCard || value == null) {
            return false;
        }
        ItemStack stack = ((IInventory) getInventory()).getStackInSlot(getSlotForAddress(index));
        if (stack.isEmpty()) {
            return false;
        }
        IVariableFacadeHolder holder = stack.getCapability(VariableFacadeHolderConfig.CAPABILITY, null);
        if (holder == null) {
            return false;
        }
        IVariableFacade current = holder.getVariableFacade();
        if (current instanceof IValueTypeVariableFacade
                && ValueHelpers.areValuesEqual(((IValueTypeVariableFacade<?>) current).getValue(), value)) {
            // The card already mirrors the value, but its id may be new (a card that
            // the player just inserted): remember it anyway.
            recordCardId(index, current.getId());
            return false;
        }

        this.writingCard = true;
        try {
            IVariableFacadeHandlerRegistry registry = IntegratedDynamics._instance.getRegistryManager()
                    .getRegistry(IVariableFacadeHandlerRegistry.class);
            ItemStack written = registry.writeVariableFacadeItem(true, stack.copy(), ValueTypes.REGISTRY,
                    new IVariableFacadeHandlerRegistry.IVariableFacadeFactory<IValueTypeVariableFacade>() {
                        @Override
                        public IValueTypeVariableFacade create(boolean generateId) {
                            return new ValueTypeVariableFacade(generateId, value.getType(), value);
                        }

                        @Override
                        public IValueTypeVariableFacade create(int id) {
                            return new ValueTypeVariableFacade(id, value.getType(), value);
                        }
                    },
                    null, BlockAddressedMemory.getInstance());
            ((IInventory) getInventory()).setInventorySlotContents(getSlotForAddress(index), written);
            // Remember the card's variable id: the memory keeps resolving it even
            // after the card itself is taken out of the slot.
            NBTTagCompound writtenTag = written.getTagCompound();
            if (writtenTag != null) {
                recordCardId(index, writtenTag.getInteger("_id"));
            }
            // Read the card back: a write that does not stick would otherwise look
            // like "the memory changed but its card did not".
            ItemStack after = ((IInventory) getInventory()).getStackInSlot(getSlotForAddress(index));
            NBTTagCompound afterTag = after.isEmpty() ? null : after.getTagCompound();
            String expectedValue = ValueHelpers.serializeRaw(value);
            if (afterTag == null
                    || !"valuetype".equals(afterTag.getString("_type"))
                    || !value.getType().getTranslationKey().equals(afterTag.getString("typeName"))
                    || !expectedValue.equals(afterTag.getString("value"))) {
                IntegratedScriptingCallback.clog(Level.WARN, String.format(
                        "The card of addressed memory %s at address %s did not take value %s (card=%s).",
                        getMemoryName(), getAddressLetter(index), value, afterTag));
            }
            return true;
        } finally {
            this.writingCard = false;
        }
    }

    /**
     * Mirror every stored value into its address card (used when a card is
     * inserted into an address that already stores a value).
     */
    protected void syncAddressCards() {
        for (int index = 0; index < ADDRESS_COUNT; index++) {
            IValue stored = getStoredValue(index);
            if (stored != null) {
                syncAddressCard(index, stored);
            }
        }
    }

    private JsonObject parseStoredValues() {
        String raw = this.storedValues;
        if (raw == null || raw.isEmpty()) {
            return new JsonObject();
        }
        try {
            com.google.gson.JsonElement element = new JsonParser().parse(raw);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "Ignoring unreadable addressed memory data at " + getMemoryName() + ": " + e);
            return new JsonObject();
        }
    }

    // ---------------------------------------------------------------- card ids

    /**
     * Remember the variable id of a card that carries an address, so references to
     * that id keep resolving to this memory's stored value even after the card is
     * taken out of the slot (or replaced by another card).
     *
     * @param index A zero-based address index.
     * @param id The card's variable id.
     */
    protected void recordCardId(int index, int id) {
        if (id < 0) {
            return;
        }
        JsonObject root = parseCardIds();
        String letter = getAddressLetter(index);
        com.google.gson.JsonArray ids = root.has(letter) && root.get(letter).isJsonArray()
                ? root.getAsJsonArray(letter) : new com.google.gson.JsonArray();
        for (com.google.gson.JsonElement element : ids) {
            if (element.getAsInt() == id) {
                return;
            }
        }
        ids.add(id);
        while (ids.size() > MAX_CARD_IDS_PER_ADDRESS) {
            ids.remove(0);
        }
        root.add(letter, ids);
        this.cardIds = root.toString();
        markDirty();
    }

    /**
     * @param index A zero-based address index.
     * @return The remembered card ids of that address, oldest first.
     */
    public java.util.List<Integer> getCardIds(int index) {
        java.util.List<Integer> result = com.google.common.collect.Lists.newArrayList();
        JsonObject root = parseCardIds();
        String letter = getAddressLetter(index);
        if (root.has(letter) && root.get(letter).isJsonArray()) {
            for (com.google.gson.JsonElement element : root.getAsJsonArray(letter)) {
                result.add(element.getAsInt());
            }
        }
        return result;
    }

    /**
     * @param index A zero-based address index.
     * @return The most recently remembered card id of that address, or -1.
     */
    public int getCardId(int index) {
        java.util.List<Integer> ids = getCardIds(index);
        return ids.isEmpty() ? -1 : ids.get(ids.size() - 1);
    }

    private JsonObject parseCardIds() {
        String raw = this.cardIds;
        if (raw == null || raw.isEmpty()) {
            return new JsonObject();
        }
        try {
            com.google.gson.JsonElement element = new JsonParser().parse(raw);
            return element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            IntegratedScriptingCallback.clog(Level.WARN,
                    "Ignoring unreadable addressed memory card ids at " + getMemoryName() + ": " + e);
            return new JsonObject();
        }
    }

    /**
     * Publish the remembered cards into the network's variable container.
     *
     * <p>The container itself is rebuilt from the inventory, so it only knows the
     * cards that are currently in a slot. This adds the remembered ids back and
     * resolves each of them to the memory's <b>current</b> stored value: the id stays
     * owned by the memory, which is what keeps cards that were taken out (or replaced)
     * usable in the network.</p>
     */
    protected void registerRememberedCards() {
        JsonObject ids = parseCardIds();
        if (ids.entrySet().isEmpty()) {
            return;
        }
        java.util.Map<Integer, IVariableFacade> cache = this.variableContainer.getVariableCache();
        for (int index = 0; index < ADDRESS_COUNT; index++) {
            String letter = getAddressLetter(index);
            if (!ids.has(letter) || !ids.get(letter).isJsonArray()) {
                continue;
            }
            IValue stored = getStoredValue(index);
            if (stored == null) {
                continue;
            }
            for (com.google.gson.JsonElement element : ids.getAsJsonArray(letter)) {
                int id = element.getAsInt();
                if (id >= 0 && !cache.containsKey(id)) {
                    cache.put(id, new ValueTypeVariableFacade(id, stored.getType(), stored));
                }
            }
        }
    }

    // ---------------------------------------------------------------- network

    protected void refreshVariables(boolean sendEvent) {
        INetwork network = getNetwork();
        if (network == null) {
            return;
        }
        this.variableContainer.refreshVariables(network, getInventory(), sendEvent);
        registerRememberedCards();
    }

    @Override
    protected void updateTileEntity() {
        super.updateTileEntity();
        if (this.shouldSendUpdateEvent && getNetwork() != null) {
            this.shouldSendUpdateEvent = false;
            refreshVariables(true);
        }
    }

    @Override
    public boolean hasEventSubscriptions() {
        return true;
    }

    @Override
    public Set<Class<? extends INetworkEvent>> getSubscribedEvents() {
        return Sets.newHashSet(VariableContentsUpdatedEvent.class);
    }

    @Override
    public void onEvent(INetworkEvent event, AddressedMemoryNetworkElement element) {
        if (event instanceof VariableContentsUpdatedEvent) {
            refreshVariables(false);
        }
    }

    /**
     * @return The variable container that exposes the address cards to the network.
     */
    public IVariableContainer getVariableContainer() {
        return this.variableContainer;
    }

    /**
     * Test/diagnostic helper: the address slots as an array.
     * @return A copy of the sixteen address card stacks.
     */
    public ItemStack[] getAddressCards() {
        ItemStack[] cards = new ItemStack[ADDRESS_COUNT];
        for (int i = 0; i < ADDRESS_COUNT; i++) {
            cards[i] = ((IInventory) getInventory()).getStackInSlot(getSlotForAddress(i));
        }
        return cards;
    }

    /**
     * @return The number of addresses, for diagnostics and the GUI.
     */
    public int getAddressCount() {
        return ADDRESS_COUNT;
    }

    /**
     * @return The persisted JSON for diagnostics.
     */
    public String getStoredValuesData() {
        return this.storedValues == null ? "" : this.storedValues;
    }

}


