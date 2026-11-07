package com.mpp.integratedscripting.client.gui.container;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.blockentity.TileAddressedMemory;
import com.mpp.integratedscripting.blockentity.TileScriptingDrive;
import com.mpp.integratedscripting.core.evaluate.operator.AddressedMemoryOperators;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;
import com.mpp.integratedscripting.inventory.container.ContainerAddressedMemory;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeString;
import org.cyclops.integrateddynamics.api.network.INetwork;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Development-only acceptance harness for the addressed memory (block, network
 * element, operators, script bindings and GUI).
 *
 * <p>Enabled with {@code -Disc.memorySmoke=true} (Gradle: {@code -PmemorySmoke}).
 * It builds a small network in a real singleplayer world - a cable, a scripting
 * drive with a disk and an addressed memory - then checks:</p>
 *
 * <ol>
 *   <li>the block writes the memory facade into the variable card of its
 *       top-left slot,</li>
 *   <li>{@code set} and {@code get} work through the operators,</li>
 *   <li>the memory is announced to the scripting network, and a script can read
 *       an address through the {@code 内存器变量卡} global,</li>
 *   <li>the GUI opens, shows the type and value of an address, and draws its
 *       hover tooltip.</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public class AddressedMemorySmokeHarness {

    private static final String TAG = "[MEMORY]";
    private static final String WORLD_NAME = "isc-memory-smoke";

    private int phase;
    private int phaseTicks;
    private int totalTicks;
    private int failures;
    private boolean finished;
    private boolean setupDone;
    private boolean checksDone;

    private BlockPos memoryPos;
    private BlockPos drivePos;
    private BlockPos cablePos;
    private int diskId = -1;
    private final List<String> results = new ArrayList<>();

    public static boolean isEnabled() {
        return Boolean.getBoolean("isc.memorySmoke");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        try {
            tick();
        } catch (Throwable t) {
            fail("unhandled exception: " + t);
            t.printStackTrace();
            finish();
        }
    }

    private void tick() throws java.io.IOException {
        this.phaseTicks++;
        this.totalTicks++;
        if (this.totalTicks > 4000) {
            fail("global timeout in phase " + this.phase);
            finish();
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();

        switch (this.phase) {
            case 0:
                if (this.phaseTicks < 40) {
                    return;
                }
                nextPhase();
                log("creating singleplayer world");
                // A leftover world would contain the memory of the previous run, so the
                // network could hold two memories and the single-memory global would
                // (correctly) be absent. Every run therefore starts from a fresh world.
                deleteWorld(mc);
                mc.launchIntegratedServer(WORLD_NAME, WORLD_NAME,
                        new WorldSettings(1234L, GameType.CREATIVE, false, false, WorldType.DEFAULT));
                return;
            case 1:
                if (mc.player == null || mc.world == null || mc.getIntegratedServer() == null) {
                    timeout(800, "world join");
                    return;
                }
                nextPhase();
                log("joined world at " + mc.player.getPosition());
                scheduleSetup(mc);
                return;
            case 2:
                if (!this.setupDone) {
                    timeout(600, "server setup");
                    return;
                }
                nextPhase();
                scheduleChecks(mc);
                return;
            case 3:
                if (!this.checksDone) {
                    timeout(600, "server checks");
                    return;
                }
                nextPhase();
                scheduleOpenGui(mc);
                return;
            case 4:
                if (!(mc.currentScreen instanceof GuiAddressedMemory)) {
                    timeout(300, "memory screen");
                    // The client may not have the tile entity yet right after the block
                    // was placed by a command (Cyclops' GUI handler then NPEs). Retry
                    // the open request instead of failing the whole run on that race.
                    if (this.phaseTicks % 20 == 0) {
                        scheduleOpenGui(mc);
                    }
                    return;
                }
                if (this.phaseTicks < 10) {
                    return;
                }
                verifyScreen(mc);
                nextPhase();
                return;
            case 5:
                if (this.phaseTicks < 10) {
                    return;
                }
                drawHoveredTooltip(mc);
                nextPhase();
                return;
            case 6:
                // The cursor was moved onto the type box; let the normal render loop
                // draw one frame so the screenshot contains the tooltip itself.
                if (this.phaseTicks < 15) {
                    return;
                }
                screenshot(mc, "memory-gui-hover.png");
                nextPhase();
                return;
            case 7:
                // Reproduce a live edit with the GUI open: write address a (which has
                // no card yet), then put a blank card into its slot. The memory must
                // fill that card, and the client must see it without reopening the GUI.
                scheduleLiveUpdate(mc);
                nextPhase();
                return;
            case 8:
                if (this.phaseTicks < 15) {
                    return;
                }
                verifyLiveUpdate(mc);
                screenshot(mc, "memory-gui-updated.png");
                nextPhase();
                // Second write to the same address, and to address b whose card already
                // holds a value: both cards must follow the memory.
                scheduleServerWrite(mc, 0, ValueTypeInteger.ValueInteger.of(11), 1, ValueTypeInteger.ValueInteger.of(43));
                return;
            case 9:
                if (this.phaseTicks < 15) {
                    return;
                }
                checkClientCard(mc, 1, "a", "integer", "11");
                checkClientCard(mc, 2, "b", "integer", "43");
                checkNetworkCache(mc, 0, "11");
                checkNetworkCache(mc, 1, "43");
                nextPhase();
                // Type change: the card has to become a string card.
                scheduleServerWrite(mc, 0, ValueTypeString.ValueString.of("text"), -1, null);
                return;
            case 10:
                if (this.phaseTicks < 15) {
                    return;
                }
                checkClientCard(mc, 1, "a", "string", "text");
                screenshot(mc, "memory-gui-retyped.png");
                nextPhase();
                return;
            case 11:
                // Diagnose a client-side evaluation (as the Logic Programmer preview
                // does): the operator must not mutate the server-side tile from the
                // client thread.
                if (this.phaseTicks == 5) {
                    try {
                        ValueHelpers.evaluateOperator(AddressedMemoryOperators.SET,
                                AddressedMemoryValues.of(org.cyclops.cyclopscore.datastructure.DimPos.of(
                                        mc.world.provider.getDimension(), this.memoryPos)),
                                ValueTypeString.ValueString.of("d"), ValueTypeInteger.ValueInteger.of(77));
                        log("client-side set(d, 77) did not throw");
                    } catch (Throwable t) {
                        log("client-side set(d, 77) threw: " + t);
                    }
                    return;
                }
                if (this.phaseTicks < 15) {
                    return;
                }
                checkServerValue(mc, 3, "0");
                nextPhase();
                return;
            case 12:
                if (this.phaseTicks > 10) {
                    finish();
                }
                return;
            default:
                finish();
        }
    }

    private void checkServerValue(Minecraft mc, int addressIndex, String expected) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                TileAddressedMemory memory = tile(server.getWorld(0), this.memoryPos);
                IValue value = memory == null ? null : memory.getStoredValue(addressIndex);
                String text = value == null ? "<null>" : value.getType().toCompactString(value);
                check(expected.equals(text), "address " + TileAddressedMemory.getAddressLetter(addressIndex)
                        + " on the server is " + expected + " (" + text.replace("\n", " ") + ")");
            } catch (Throwable t) {
                fail("server value check exception: " + t);
            }
        });
    }

    // ---------------------------------------------------------------- setup

    private void scheduleSetup(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                World world = server.getWorld(0);
                BlockPos spawn = world.getSpawnPoint();
                BlockPos base = world.getTopSolidOrLiquidBlock(spawn);
                this.cablePos = base;
                this.memoryPos = base.add(1, 0, 0);
                this.drivePos = base.add(2, 0, 0);

                run(server, String.format("setblock %d %d %d integrateddynamics:cable",
                        this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ()));
                run(server, String.format("setblock %d %d %d integratedscriptingcallback:addressed_memory",
                        this.memoryPos.getX(), this.memoryPos.getY(), this.memoryPos.getZ()));
                run(server, String.format("setblock %d %d %d integratedscriptingcallback:scripting_drive",
                        this.drivePos.getX(), this.drivePos.getY(), this.drivePos.getZ()));
                run(server, String.format(
                        "replaceitem block %d %d %d slot.container.0 integratedscriptingcallback:scripting_disk",
                        this.drivePos.getX(), this.drivePos.getY(), this.drivePos.getZ()));
                run(server, String.format(
                        "replaceitem block %d %d %d slot.container.0 integrateddynamics:variable",
                        this.memoryPos.getX(), this.memoryPos.getY(), this.memoryPos.getZ()));
                // An empty card in the slot of address b: the memory must fill it with
                // the value it stores there, because address cards are outputs.
                run(server, String.format(
                        "replaceitem block %d %d %d slot.container.2 integrateddynamics:variable",
                        this.memoryPos.getX(), this.memoryPos.getY(), this.memoryPos.getZ()));

                TileScriptingDrive drive = tile(world, this.drivePos);
                if (drive != null) {
                    this.diskId = drive.getExposedDiskId();
                }
                this.setupDone = true;
            } catch (Throwable t) {
                fail("setup exception: " + t);
                t.printStackTrace();
                this.setupDone = true;
            }
        });
    }

    /**
     * @return If the network's variable container resolves the given card id to the
     *         given integer value.
     */
    private static boolean rememberedValue(TileAddressedMemory memory, int cardId, int expected) {
        org.cyclops.integrateddynamics.api.item.IVariableFacade facade =
                memory.getVariableContainer().getVariableCache().get(cardId);
        if (!(facade instanceof org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade)) {
            return false;
        }
        IValue value = ((org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?>) facade).getValue();
        return value instanceof ValueTypeInteger.ValueInteger
                && ((ValueTypeInteger.ValueInteger) value).getRawValue() == expected;
    }

    private void scheduleChecks(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        // Give the network a moment to form and the block time to write its card.
        server.addScheduledTask(() -> {
            try {
                World world = server.getWorld(0);
                TileAddressedMemory memory = tile(world, this.memoryPos);
                check(memory != null, "the addressed memory tile entity exists");
                if (memory == null) {
                    this.checksDone = true;
                    return;
                }

                ItemStack card = ((IInventory) memory.getInventory()).getStackInSlot(0);
                String type = card.isEmpty() || card.getTagCompound() == null
                        ? "" : card.getTagCompound().getString("_type");
                org.cyclops.integrateddynamics.api.item.IVariableFacade memoryFacade = card.isEmpty()
                        ? null : org.cyclops.integrateddynamics.IntegratedDynamics._instance
                        .getRegistryManager()
                        .getRegistry(org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry.class)
                        .handle(card);
                boolean memoryCardIsTyped = false;
                if (memoryFacade instanceof org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade) {
                    org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?> valueFacade =
                            (org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?>) memoryFacade;
                    memoryCardIsTyped = valueFacade.getValueType() == AddressedMemoryValues.getType()
                            && valueFacade.getValue() instanceof com.mpp.integratedscripting.core.evaluate.value.ValueAddressedMemory
                            && this.memoryPos.equals(((com.mpp.integratedscripting.core.evaluate.value.ValueAddressedMemory)
                            valueFacade.getValue()).getPosition().getBlockPos());
                }
                check("valuetype".equals(type) && memoryCardIsTyped,
                        "the memory card slot holds a typed static card of the memory object (_type=" + type + ")");

                INetwork network = memory.getNetwork();
                check(network != null, "the memory joined an Integrated Dynamics network");
                if (network != null) {
                    check(ScriptingNetworkHelpers.getScriptingNetwork(network) != null
                                    && !ScriptingNetworkHelpers.getScriptingNetwork(network)
                                    .getMemories().isEmpty(),
                            "the memory was announced to the scripting network");
                }

                // Untouched addresses are initialised to integer 0.
                check(((ValueTypeInteger.ValueInteger) memory.readValue(2)).getRawValue() == 0,
                        "an untouched address (c) starts at integer 0");
                IValue defaultRead = ValueHelpers.evaluateOperator(AddressedMemoryOperators.GET,
                        AddressedMemoryValues.of(org.cyclops.cyclopscore.datastructure.DimPos.of(world, this.memoryPos)),
                        ValueTypeString.ValueString.of("c"));
                check(((ValueTypeInteger.ValueInteger) defaultRead).getRawValue() == 0,
                        "get(c) returns 0 without any write");

                // Write through the operator, read back through the tile and the operator.
                IValue written = ValueHelpers.evaluateOperator(AddressedMemoryOperators.SET,
                        AddressedMemoryValues.of(org.cyclops.cyclopscore.datastructure.DimPos.of(world, this.memoryPos)),
                        ValueTypeString.ValueString.of("b"), ValueTypeInteger.ValueInteger.of(42));
                check(written instanceof ValueTypeInteger.ValueInteger
                                && ((ValueTypeInteger.ValueInteger) written).getRawValue() == 42,
                        "set(b, 42) returns 42");
                check(memory.readValue(1) instanceof ValueTypeInteger.ValueInteger,
                        "address b reads back an integer");
                IValue read = ValueHelpers.evaluateOperator(AddressedMemoryOperators.GET,
                        AddressedMemoryValues.of(org.cyclops.cyclopscore.datastructure.DimPos.of(world, this.memoryPos)),
                        ValueTypeString.ValueString.of("b"));
                check(((ValueTypeInteger.ValueInteger) read).getRawValue() == 42, "get(b) returns 42");

                // The address card is the memory's output: set() must have written the
                // stored value into it as a static value card.
                ItemStack addressCard = ((IInventory) memory.getInventory()).getStackInSlot(2);
                String addressType = addressCard.isEmpty() || addressCard.getTagCompound() == null
                        ? "" : addressCard.getTagCompound().getString("_type");
                org.cyclops.integrateddynamics.api.item.IVariableFacade addressFacade = addressCard.isEmpty()
                        ? null : org.cyclops.integrateddynamics.IntegratedDynamics._instance
                        .getRegistryManager()
                        .getRegistry(org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry.class)
                        .handle(addressCard);
                boolean cardMirrorsValue = false;
                if (addressFacade instanceof org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade) {
                    IValue cardValue = ((org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?>)
                            addressFacade).getValue();
                    cardMirrorsValue = cardValue instanceof ValueTypeInteger.ValueInteger
                            && ((ValueTypeInteger.ValueInteger) cardValue).getRawValue() == 42;
                }
                check("valuetype".equals(addressType) && cardMirrorsValue,
                        "the card of address b became a static value card holding 42 (_type=" + addressType + ")");

                // And the value really lives in the memory: removing the card must not
                // remove it (the card is only an output).
                ((IInventory) memory.getInventory()).setInventorySlotContents(2, ItemStack.EMPTY);
                check(((ValueTypeInteger.ValueInteger) memory.readValue(1)).getRawValue() == 42,
                        "address b keeps its value after its card was removed");
                IValue afterRemoval = ValueHelpers.evaluateOperator(AddressedMemoryOperators.GET,
                        AddressedMemoryValues.of(org.cyclops.cyclopscore.datastructure.DimPos.of(world, this.memoryPos)),
                        ValueTypeString.ValueString.of("b"));
                check(((ValueTypeInteger.ValueInteger) afterRemoval).getRawValue() == 42,
                        "get(b) still returns 42 without a variable card");

                // The memory remembers the card id, so the network keeps resolving it
                // even though the card is no longer in the slot.
                int cardId = memory.getCardId(1);
                check(cardId >= 0, "the memory remembered the card id of address b (" + cardId + ")");
                check(rememberedValue(memory, cardId, 42),
                        "the network resolves the removed card id to the current value 42");

                // The script binding: a script compiled in this network can read the memory.
                if (this.diskId >= 0) {
                    com.mpp.integratedscripting.api.network.IScriptingData data =
                            ScriptingNetworkHelpers.getScriptingData();
                    data.setScript(this.diskId, Paths.get("memory.js"),
                            "const memoryB = 内存器变量卡.get('b');\n"
                                    + "const memoryCount = Object.keys(idContext.memories).length;\n",
                            com.mpp.integratedscripting.api.network.IScriptingData.ChangeLocation.MEMORY);
                    com.mpp.integratedscripting.api.network.IScriptingNetwork scriptingNetwork =
                            ScriptingNetworkHelpers.getScriptingNetwork(network);
                    IValue scriptValue = scriptingNetwork.getVariable(this.diskId,
                            Paths.get("memory.js"), "memoryB").getValue();
                    check(((ValueTypeInteger.ValueInteger) scriptValue).getRawValue() == 42,
                            "a script reads 内存器变量卡.get('b') as 42");
                    IValue count = scriptingNetwork.getVariable(this.diskId,
                            Paths.get("memory.js"), "memoryCount").getValue();
                    check(((ValueTypeInteger.ValueInteger) count).getRawValue() > 0,
                            "idContext.memories is populated in the script sandbox");
                } else {
                    fail("the scripting drive did not expose a disk id");
                }

                // The remembered id follows the memory: change b without any card.
                memory.writeValue(1, ValueTypeInteger.ValueInteger.of(43));
                check(((IInventory) memory.getInventory()).getStackInSlot(2).isEmpty(),
                        "address b still has no card");
                check(rememberedValue(memory, cardId, 43),
                        "the remembered card id now resolves to 43");
                this.checksDone = true;
            } catch (Throwable t) {
                fail("check exception: " + t);
                t.printStackTrace();
                this.checksDone = true;
            }
        });
    }

    private void scheduleLiveUpdate(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                World world = server.getWorld(0);
                TileAddressedMemory memory = tile(world, this.memoryPos);
                if (memory == null) {
                    fail("the memory disappeared before the live update");
                    return;
                }
                // Address a has no card at this point; storing a value must work anyway.
                memory.writeValue(0, ValueTypeInteger.ValueInteger.of(9));
                check(((ValueTypeInteger.ValueInteger) memory.readValue(0)).getRawValue() == 9,
                        "set(a, 9) stores the value in the memory");
                check(((IInventory) memory.getInventory()).getStackInSlot(1).isEmpty(),
                        "address a had no card when the value was stored");

                // Now insert an empty card: the memory must fill it with the stored 9.
                ((IInventory) memory.getInventory()).setInventorySlotContents(1,
                        new ItemStack(net.minecraft.item.Item.getByNameOrId("integrateddynamics:variable")));
                ItemStack filled = ((IInventory) memory.getInventory()).getStackInSlot(1);
                String type = filled.isEmpty() || filled.getTagCompound() == null
                        ? "" : filled.getTagCompound().getString("_type");
                log("server card of address a after insertion: _type=" + type
                        + " nbt=" + (filled.isEmpty() ? "<empty>" : filled.getTagCompound()));
            } catch (Throwable t) {
                fail("live update exception: " + t);
                t.printStackTrace();
            }
        });
    }

    /**
     * Check that the client sees the card the memory filled while the GUI was open.
     */
    private void verifyLiveUpdate(Minecraft mc) {
        GuiAddressedMemory screen = (GuiAddressedMemory) mc.currentScreen;
        ItemStack clientCard = screen.inventorySlots.getSlot(1).getStack();
        String type = clientCard.isEmpty() || clientCard.getTagCompound() == null
                ? "" : clientCard.getTagCompound().getString("_type");
        log("client card of address a: _type=" + type
                + " nbt=" + (clientCard.isEmpty() ? "<empty>" : clientCard.getTagCompound()));
        check("valuetype".equals(type),
                "the client received the card the memory filled (_type=" + type + ")");

        boolean valueOk = false;
        if (!clientCard.isEmpty()) {
            org.cyclops.integrateddynamics.api.item.IVariableFacade facade =
                    org.cyclops.integrateddynamics.IntegratedDynamics._instance.getRegistryManager()
                            .getRegistry(org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry.class)
                            .handle(clientCard);
            if (facade instanceof org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade) {
                IValue value = ((org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?>) facade).getValue();
                valueOk = value instanceof ValueTypeInteger.ValueInteger
                        && ((ValueTypeInteger.ValueInteger) value).getRawValue() == 9;
            }
        }
        check(valueOk, "the client card of address a holds the stored value 9");

        List<ContainerAddressedMemory.AddressData> data = screen.getMemoryContainer().getAddressData();
        check(data.size() > 0 && "9".equals(data.get(0).getValueText()),
                "address a reports the updated value 9 (" + (data.isEmpty() ? "-" : data.get(0).getValueText()) + ")");
    }

    /**
     * Write up to two addresses from the server thread, while the GUI stays open.
     */
    private void scheduleServerWrite(Minecraft mc, int index, IValue value, int secondIndex, IValue secondValue) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                TileAddressedMemory memory = tile(server.getWorld(0), this.memoryPos);
                if (memory == null) {
                    fail("the memory disappeared before a write");
                    return;
                }
                memory.writeValue(index, value);
                if (secondIndex >= 0 && secondValue != null) {
                    memory.writeValue(secondIndex, secondValue);
                }
                log("wrote " + value + " to address " + TileAddressedMemory.getAddressLetter(index)
                        + (secondIndex >= 0 ? " and " + secondValue + " to "
                        + TileAddressedMemory.getAddressLetter(secondIndex) : ""));
            } catch (Throwable t) {
                fail("server write exception: " + t);
                t.printStackTrace();
            }
        });
    }

    /**
     * Check the client's card of one address: its value type and value text.
     */
    private void checkClientCard(Minecraft mc, int slotIndex, String letter, String expectedType, String expectedValue) {
        GuiAddressedMemory screen = (GuiAddressedMemory) mc.currentScreen;
        ItemStack stack = screen.inventorySlots.getSlot(slotIndex).getStack();
        String type = stack.isEmpty() || stack.getTagCompound() == null
                ? "" : stack.getTagCompound().getString("_type");
        String typeName = stack.isEmpty() || stack.getTagCompound() == null
                ? "" : stack.getTagCompound().getString("typeName");
        String rawValue = stack.isEmpty() || stack.getTagCompound() == null
                ? "" : stack.getTagCompound().getString("value");
        log("client card of address " + letter + ": _type=" + type + " typeName=" + typeName
                + " value=" + rawValue);
        check("valuetype".equals(type) && typeName.contains(expectedType) && expectedValue.equals(rawValue),
                "the card of address " + letter + " follows the memory (" + typeName + " = " + rawValue + ")");
    }

    /**
     * Check the facade the network's variable container exposes for an address card:
     * this is what Display Panels and other variables resolve.
     */
    private void checkNetworkCache(Minecraft mc, int addressIndex, String expectedValue) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                TileAddressedMemory memory = tile(server.getWorld(0), this.memoryPos);
                if (memory == null) {
                    fail("the memory disappeared before the container check");
                    return;
                }
                org.cyclops.integrateddynamics.api.item.IVariableFacade facade = memory.getCardFacade(addressIndex);
                if (!(facade instanceof org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade)) {
                    fail("address " + TileAddressedMemory.getAddressLetter(addressIndex)
                            + " has no value card facade");
                    return;
                }
                int id = facade.getId();
                org.cyclops.integrateddynamics.api.item.IVariableFacade cached =
                        memory.getVariableContainer().getVariableCache().get(id);
                String cachedValue = cached instanceof org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade
                        ? ((org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?>) cached)
                        .getValue().getType().toCompactString(
                                ((org.cyclops.integrateddynamics.api.item.IValueTypeVariableFacade<?>) cached).getValue())
                        : "<missing>";
                check(expectedValue.equals(cachedValue),
                        "the network cache exposes " + expectedValue + " for address "
                                + TileAddressedMemory.getAddressLetter(addressIndex) + " (" + cachedValue + ")");
            } catch (Throwable t) {
                fail("network cache check exception: " + t);
                t.printStackTrace();
            }
        });
    }

    private void scheduleOpenGui(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                World world = server.getWorld(0);
                TileAddressedMemory memory = tile(world, this.memoryPos);
                if (memory != null) {
                    // Put cards back into two addresses so the screenshots show the
                    // typed cards the memory writes: b holds 42, c the default 0.
                    ((IInventory) memory.getInventory()).setInventorySlotContents(2,
                            new ItemStack(net.minecraft.item.Item.getByNameOrId("integrateddynamics:variable")));
                    ((IInventory) memory.getInventory()).setInventorySlotContents(3,
                            new ItemStack(net.minecraft.item.Item.getByNameOrId("integrateddynamics:variable")));
                }
                EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
                org.cyclops.cyclopscore.inventory.IGuiContainerProvider provider =
                        (org.cyclops.cyclopscore.inventory.IGuiContainerProvider)
                                com.mpp.RegistryEntries.blockAddressedMemory();
                player.openGui(provider.getModGui(), provider.getGuiID(), server.getWorld(0),
                        this.memoryPos.getX(), this.memoryPos.getY(), this.memoryPos.getZ());
                log("memory GUI open requested");
            } catch (Throwable t) {
                fail("open GUI exception: " + t);
                t.printStackTrace();
            }
        });
    }

    // ---------------------------------------------------------------- GUI

    /**
     * Diagnose the variable card overlay chain: the registered value type model and
     * whether the baked overlay map contains the addressed memory type.
     */
    private void logTypeOverlays(Minecraft mc) {
        try {
            net.minecraft.util.ResourceLocation model = org.cyclops.integrateddynamics.core.evaluate.variable
                    .ValueTypes.REGISTRY.getValueTypeModel(
                            com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues.getType());
            log("addressed memory value type model: " + model
                    + " (registered models: " + org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes.REGISTRY
                    .getValueTypeModels().size() + ")");

            net.minecraft.item.Item cardItem = net.minecraft.item.Item.getByNameOrId("integrateddynamics:variable");
            net.minecraft.client.renderer.block.model.IBakedModel baked =
                    mc.getRenderItem().getItemModelMesher()
                            .getItemModel(new ItemStack(cardItem));
            log("variable card baked model: " + baked.getClass().getName());
            if (baked instanceof org.cyclops.integrateddynamics.api.client.model.IVariableModelBaked) {
                java.util.Map<?, ?> overlays = ((org.cyclops.integrateddynamics.core.client.model.BakedMapVariableModelProvider<?>)
                        ((org.cyclops.integrateddynamics.api.client.model.IVariableModelBaked) baked)
                                .getSubModels(org.cyclops.integrateddynamics.core.client.model.VariableModelProviders.VALUETYPE))
                        .getBakedModels();
                log("value type overlays baked: " + overlays.size()
                        + ", has addressed_memory: "
                        + overlays.containsKey(com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues.getType())
                        + ", has integer: " + overlays.containsKey(
                        org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes.INTEGER));
                Object memoryOverlay = overlays.get(
                        com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues.getType());
                log("memory overlay model: " + (memoryOverlay == null ? "null"
                        : memoryOverlay.getClass().getSimpleName() + " quads="
                        + ((net.minecraft.client.renderer.block.model.IBakedModel) memoryOverlay)
                        .getQuads(null, null, 0L).size() + " particle="
                        + ((net.minecraft.client.renderer.block.model.IBakedModel) memoryOverlay).getParticleTexture()));
                Object integerOverlay = overlays.get(
                        org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes.INTEGER);
                log("integer overlay model: " + (integerOverlay == null ? "null"
                        : integerOverlay.getClass().getSimpleName() + " quads="
                        + ((net.minecraft.client.renderer.block.model.IBakedModel) integerOverlay)
                        .getQuads(null, null, 0L).size()));
            }

            GuiAddressedMemory screen = (GuiAddressedMemory) mc.currentScreen;
            logCardQuads(mc, "memory card", screen.inventorySlots.getSlot(0).getStack());
            logCardQuads(mc, "address b card", screen.inventorySlots.getSlot(2).getStack());
            logCardQuads(mc, "address c card", screen.inventorySlots.getSlot(3).getStack());
        } catch (Throwable t) {
            log("overlay diagnostics failed: " + t);
        }
    }

    private void logCardQuads(Minecraft mc, String name, ItemStack stack) {
        if (stack.isEmpty()) {
            log(name + ": empty");
            return;
        }
        net.minecraft.client.renderer.block.model.IBakedModel model =
                mc.getRenderItem().getItemModelWithOverrides(stack, null, mc.player);
        log(name + ": " + model.getClass().getSimpleName()
                + " quads=" + model.getQuads(null, null, 0L).size());
    }

    private void verifyScreen(Minecraft mc) {
        GuiAddressedMemory screen = (GuiAddressedMemory) mc.currentScreen;
        log("memory screen opened with " + screen.inventorySlots.inventorySlots.size() + " slots");
        check(screen.inventorySlots.inventorySlots.size() == 17 + 36,
                "the container has 17 memory slots and 36 player slots");
        boolean slotsAligned = screen.inventorySlots.getSlot(1).xPos == ContainerAddressedMemory.getAddressSlotX(0)
                && screen.inventorySlots.getSlot(1).yPos == ContainerAddressedMemory.getAddressSlotY(0)
                && screen.inventorySlots.getSlot(2).xPos == ContainerAddressedMemory.getAddressSlotX(1)
                && screen.inventorySlots.getSlot(9).xPos == ContainerAddressedMemory.getAddressSlotX(8);
        check(slotsAligned, "the address slots use the texture layout (36 pixel cells)");
        logTypeOverlays(mc);
        draw(mc, screen, 0, 0);
        screenshot(mc, "memory-gui.png");

        List<ContainerAddressedMemory.AddressData> data = screen.getMemoryContainer().getAddressData();
        check(data.size() == TileAddressedMemory.ADDRESS_COUNT,
                "the client received all sixteen address entries (" + data.size() + ")");
        if (data.size() > 2) {
            ContainerAddressedMemory.AddressData entry = data.get(1);
            check(entry.getTypeKey() != null && !entry.getTypeKey().isEmpty(),
                    "address b reports a variable type (" + entry.getTypeKey() + ")");
            check("43".equals(entry.getValueText()),
                    "address b reports the value 43 (" + entry.getValueText() + ")");
            ContainerAddressedMemory.AddressData untouched = data.get(2);
            check("0".equals(untouched.getValueText()),
                    "address c reports the initial value 0 (" + untouched.getValueText() + ")");
        }
    }

    private void drawHoveredTooltip(Minecraft mc) {
        GuiAddressedMemory screen = (GuiAddressedMemory) mc.currentScreen;
        int boxX = ContainerAddressedMemory.getTextBoxX(1);
        int boxY = ContainerAddressedMemory.getTextBoxY(1);
        int mouseX = screen.getGuiLeftTotal() + boxX + ContainerAddressedMemory.TEXT_BOX_WIDTH / 2;
        int mouseY = screen.getGuiTopTotal() + boxY + ContainerAddressedMemory.TEXT_BOX_HEIGHT / 2;

        check(screen.getHoveredTextBox(mouseX, mouseY) == 1,
                "hovering the type box of address b is detected");
        List<String> tooltip = screen.getAddressTooltip(1);
        check(tooltip.size() >= 2 && tooltip.get(tooltip.size() - 1).equals("43"),
                "the tooltip of address b renders the value 43 (" + tooltip + ")");

        draw(mc, screen, mouseX, mouseY);
        log("drew the hover tooltip of address b at " + mouseX + "," + mouseY);

        // The game renders the GUI on its own each frame with the real mouse
        // position, so move the cursor onto the box to capture the tooltip too.
        int scale = Math.max(1, mc.displayWidth / Math.max(1, mc.currentScreen.width));
        int displayX = mouseX * scale;
        int displayY = mc.displayHeight - mouseY * scale;
        try {
            org.lwjgl.input.Mouse.setCursorPosition(displayX, displayY);
            log("moved the cursor to " + displayX + "," + displayY);
        } catch (Throwable t) {
            log("could not move the cursor: " + t);
        }
    }

    private void draw(Minecraft mc, GuiAddressedMemory screen, int mouseX, int mouseY) {
        try {
            screen.drawScreen(mouseX, mouseY, 0F);
            log("drawScreen ok at " + mouseX + "," + mouseY);
        } catch (Throwable t) {
            fail("drawScreen threw: " + t);
            t.printStackTrace();
        }
    }

    private void screenshot(Minecraft mc, String name) {
        try {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, name,
                    mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            log("screenshot saved: screenshots/" + name);
        } catch (Throwable t) {
            fail("screenshot failed: " + t);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static void deleteWorld(Minecraft mc) {
        java.io.File directory = new java.io.File(mc.mcDataDir, "saves/" + WORLD_NAME);
        if (directory.exists()) {
            deleteRecursively(directory);
            log("deleted the previous world " + directory);
        }
    }

    private static void deleteRecursively(java.io.File file) {
        java.io.File[] children = file.listFiles();
        if (children != null) {
            for (java.io.File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            log("could not delete " + file);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T tile(World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity tile = world.getTileEntity(pos);
        return (T) tile;
    }

    private void run(MinecraftServer server, String command) {
        log("command: " + command + " -> "
                + server.getCommandManager().executeCommand(server, command));
    }

    private void check(boolean condition, String message) {
        if (condition) {
            this.results.add("PASS: " + message);
            log("PASS: " + message);
        } else {
            fail(message);
        }
    }

    private void timeout(int ticks, String what) {
        if (this.phaseTicks > ticks) {
            fail("timeout waiting for " + what + " (phase " + this.phase + ")");
            finish();
        }
    }

    private void nextPhase() {
        this.phase++;
        this.phaseTicks = 0;
    }

    private void finish() {
        if (this.finished) {
            return;
        }
        this.finished = true;
        log("RESULT: " + (this.failures == 0 ? "PASS" : "FAIL (" + this.failures + " failures)"));
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getIntegratedServer() != null) {
            mc.getIntegratedServer().addScheduledTask(() -> mc.getIntegratedServer().initiateShutdown());
        }
        mc.shutdown();
    }

    private static void log(String message) {
        IntegratedScriptingCallback.clog(TAG + " " + message);
    }

    private void fail(String message) {
        this.failures++;
        IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.ERROR, TAG + " FAIL: " + message);
    }

}

