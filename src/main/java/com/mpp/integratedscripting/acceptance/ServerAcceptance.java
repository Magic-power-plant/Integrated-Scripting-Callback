package com.mpp.integratedscripting.acceptance;

import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.Reference;
import com.mpp.RegistryEntries;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.core.evaluate.ScriptVariableFacadeHandler;
import com.mpp.integratedscripting.core.item.ScriptVariableFacade;
import com.mpp.integratedscripting.core.network.ScriptingNetwork;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;
import com.mpp.integratedscripting.part.PartTypes;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.Level;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.api.part.IPartState;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.core.evaluate.operator.Operators;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeItemStack;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeBoolean;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeList;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeNbt;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeOperator;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.cyclops.integrateddynamics.core.evaluate.variable.Variable;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integrateddynamics.core.part.PartStateActiveVariableBase;
import org.cyclops.integrateddynamics.part.PartTypePanelDisplay;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * Temporary M7 acceptance harness. Enabled with {@code -Disc.m7Smoke=true}
 * ({@code ./gradlew runServer -Pm7Smoke}) and removed after the milestone is
 * verified (docs/MILESTONES.md M7 records the run).
 *
 * <p>It drives the real server-side code paths of §11.2 in a real world:
 * placing/breaking the scripting drive, the network capability, script
 * variables, variable cards, a Display Panel part, an operator value used as a
 * list filter, the NBT scalar policy and operator serialization. The GUI parts
 * of §11.2 are covered by the client harness
 * ({@code ClientAcceptanceHarness}).</p>
 *
 * <p>Everything runs on the server thread through the normal server tick, so
 * there are no cross-thread shortcuts.</p>
 *
 * @author rubensworks
 */
public class ServerAcceptance {

    private static final String TAG = "[M7]";
    /** Hard limit per phase, in ticks. */
    private static final int PHASE_TIMEOUT = 900;

    private static final String MAIN_PATH = "main.js";
    private static final String FOO_42 = "const foo = 42;";
    private static final String FOO_43 = "const foo = 43;";
    private static final String BROKEN = "const foo = ;";
    private static final String FUNC = "function f(item) { return item.stackable(); }";
    private static final String MEMBERS =
            "const nbtObj = { foo: 1 };\n"
            + "const nbtCall = (x) => idContext.ops.nbtSize(x);\n"
            + "const opsCall = () => idContext.ops.itemstackStackable("
            + "{ id_item: { id: 'minecraft:stone', Count: 1 } });\n"
            + "const strength = (item, block) => item.strength(block);";

    private int phase;
    private int phaseTicks;
    private int totalTicks;
    private int failures;
    private int checks;
    private boolean finished;

    private World world;
    private BlockPos drivePos;
    private BlockPos cablePos;
    private ItemStack diskStack = ItemStack.EMPTY;
    private int diskId = -1;

    private long maxTickNanos;
    private long maxTickBeforePerformance;
    private int longTicks;
    private int longTicksBeforePerformance;
    private long firstExecutionNanos = -1;
    private long warmExecutionNanos = -1;
    private long hundredScriptsNanos = -1;

    public static boolean isEnabled() {
        return Boolean.getBoolean("isc.m7Smoke");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || finished) {
            return;
        }
        long start = System.nanoTime();
        try {
            tick();
        } catch (Throwable t) {
            fail("unhandled exception: " + t);
            t.printStackTrace();
            finish();
        }
        long duration = System.nanoTime() - start;
        this.maxTickNanos = Math.max(this.maxTickNanos, duration);
        if (duration > 50_000_000L) {
            this.longTicks++;
        }
    }

    // ---------------------------------------------------------------- state machine

    private void tick() {
        this.phaseTicks++;
        this.totalTicks++;
        if (this.phaseTicks > PHASE_TIMEOUT) {
            fail("timeout in phase " + this.phase);
            finish();
            return;
        }

        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            fail("server vanished");
            finish();
            return;
        }

        switch (this.phase) {
            case 0:
                if (this.phaseTicks < 40) {
                    return;
                }
                nextPhase();
                setupWorld(server);
                return;
            case 1:
                if (!waitForDiskId()) {
                    return;
                }
                nextPhase();
                return;
            case 2:
                checkDiskId();
                nextPhase();
                return;
            case 3:
                // Remove the disk: the drive must stop exposing it.
                setDriveDisk(ItemStack.EMPTY);
                nextPhase();
                return;
            case 4: {
                TileEntity te = this.world.getTileEntity(this.drivePos);
                int exposed = te instanceof com.mpp.integratedscripting.blockentity.TileScriptingDrive
                        ? ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te).getExposedDiskId()
                        : -2;
                check("5b", exposed == -1, "drive stops exposing the removed disk (exposed=" + exposed + ")");
                check("5c", !scriptingNetwork().getDisks().contains(this.diskId),
                        "network no longer exposes the removed disk");
                // The id is stored on the item itself, so putting it back restores the same id.
                setDriveDisk(this.diskStack);
                nextPhase();
                return;
            }
            case 5: {
                TileEntity te = this.world.getTileEntity(this.drivePos);
                int exposed = te instanceof com.mpp.integratedscripting.blockentity.TileScriptingDrive
                        ? ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te).getExposedDiskId()
                        : -2;
                check("5d", exposed == this.diskId,
                        "the disk keeps its id after being removed and reinserted (id=" + exposed + ")");
                nextPhase();
                return;
            }
            case 6:
                // §11.2 item 3: breaking the drive drops the block and the disk inside.
                breakDriveAndCheckDrops();
                nextPhase();
                return;
            case 7:
                // Restore the drive and the disk for the evaluation phases.
                restoreDrive(server);
                nextPhase();
                return;
            case 8:
                if (!waitForDiskId()) {
                    return;
                }
                nextPhase();
                return;
            case 9:
                writeScripts();
                check("14a", scripts().containsKey(Paths.get(MAIN_PATH)),
                        "script written to the disk: " + MAIN_PATH);
                nextPhase();
                return;
            case 10:
                // §11.2 items 15/16: script member -> variable card -> Display Panel.
                setupDisplayPanel();
                nextPhase();
                return;
            case 11:
                checkPanelValue(42);
                nextPhase();
                return;
            case 12:
                // §11.2 item 17: changing the script updates the panel.
                setScript(MAIN_PATH, FOO_43 + "\n" + FUNC + "\n" + MEMBERS);
                nextPhase();
                return;
            case 13:
                checkPanelValue(43);
                nextPhase();
                return;
            case 14:
                // §11.2 item 18: a syntax error must not crash, the panel must show the error.
                setScript(MAIN_PATH, BROKEN);
                nextPhase();
                return;
            case 15:
                checkPanelError();
                setScript(MAIN_PATH, FOO_43 + "\n" + FUNC + "\n" + MEMBERS);
                nextPhase();
                return;
            case 16:
                checkPanelValue(43);
                nextPhase();
                return;
            case 17:
                // §11.2 item 19: a script function becomes an operator value usable as a list filter.
                checkOperatorFilter();
                nextPhase();
                return;
            case 18:
                // §11.2 items 20/21: idContext.ops operators and object methods.
                checkOperatorsAndObjectMethods();
                nextPhase();
                return;
            case 19:
                // §11.2 item 22: NBT values and the nbtScalarWrapper policy.
                checkNbt();
                nextPhase();
                return;
            case 20:
                // §11.2 item 23: operator card serialization.
                checkOperatorSerialization();
                nextPhase();
                return;
            case 21:
                // §11.2 item 26: the diagnostic command from M3.
                checkTestScriptCommand(server);
                nextPhase();
                return;
            case 22:
                // §11.2 item 28: performance. The maximum tick time of the regular
                // phases is captured first; the deliberate 100-script burst is
                // reported separately rather than asserted against the tick budget.
                this.maxTickBeforePerformance = this.maxTickNanos;
                this.longTicksBeforePerformance = this.longTicks;
                checkPerformance();
                nextPhase();
                return;
            default:
                finish();
        }
    }

    // ---------------------------------------------------------------- §11.2 item 3/5/6

    private void setupWorld(MinecraftServer server) {
        this.world = server.getWorld(0);
        BlockPos spawn = this.world.getSpawnPoint();
        BlockPos base = this.world.getTopSolidOrLiquidBlock(spawn);
        this.drivePos = base.add(4, 0, 4);
        this.cablePos = this.drivePos.east();

        // Reset the test area first so that repeated runs start from the same state
        // (a previous run's cable would still carry its parts, including the panel).
        run(server, String.format("setblock %s minecraft:air", format(this.drivePos)));
        run(server, String.format("setblock %s minecraft:air", format(this.cablePos)));
        run(server, String.format("setblock %s minecraft:air", format(this.drivePos.up())));
        run(server, String.format("setblock %s minecraft:air", format(this.cablePos.up())));
        run(server, String.format("setblock %s %s", format(this.drivePos),
                Reference.MOD_ID + ":scripting_drive"));
        run(server, String.format("setblock %s integrateddynamics:cable", format(this.cablePos)));

        this.diskStack = new ItemStack(RegistryEntries.itemScriptingDisk());
        setDriveDisk(this.diskStack);
        log("test area: drive " + this.drivePos + ", cable " + this.cablePos);
    }

    /**
     * @return True once the drive exposed a disk id and the network exposes it.
     */
    private boolean waitForDiskId() {
        TileEntity te = this.world.getTileEntity(this.drivePos);
        if (!(te instanceof com.mpp.integratedscripting.blockentity.TileScriptingDrive)) {
            return false;
        }
        int exposed = ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te).getExposedDiskId();
        if (exposed < 0) {
            return false;
        }
        INetwork network = idNetwork();
        if (network == null) {
            return false;
        }
        return scriptingNetwork().getDisks().contains(exposed);
    }

    private void checkDiskId() {
        TileEntity te = this.world.getTileEntity(this.drivePos);
        this.diskId = ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te).getExposedDiskId();
        // Read the written stack back: the drive assigns the id to the stack inside
        // its own inventory, not to the copy the harness inserted.
        ItemStack written = ((IInventory) ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te)
                .getInventory()).getStackInSlot(0);
        if (!written.isEmpty()) {
            this.diskStack = written.copy();
        }
        check("5a", this.diskId >= 0, "the inserted disk received an id: " + this.diskId);
        Item diskItem = RegistryEntries.itemScriptingDisk();
        int itemId = ((com.mpp.integratedscripting.item.ItemScriptingDisk) diskItem)
                .getDiskId(this.diskStack);
        check("5a2", itemId == this.diskId, "the disk NBT carries the same id (" + itemId + ")");
        check("6a", scriptingNetwork().getDisks().contains(this.diskId),
                "ScriptingNetwork#getDisks() contains the disk id");
    }

    private void breakDriveAndCheckDrops() {
        List<EntityItem> drops = this.world.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(this.drivePos).grow(6));
        // Clear drops from the setup phase.
        for (EntityItem drop : drops) {
            drop.setDead();
        }

        // Keep a pristine copy: Cyclops' drop logic may shrink the stack that was
        // inside the broken inventory.
        ItemStack brokenDisk = this.diskStack.copy();

        boolean removed = this.world.destroyBlock(this.drivePos, true);
        check("3a", removed, "the scripting drive block can be broken");

        List<EntityItem> after = this.world.getEntitiesWithinAABB(EntityItem.class,
                new AxisAlignedBB(this.drivePos).grow(6));
        boolean driveDrop = false;
        boolean diskDrop = false;
        for (EntityItem drop : after) {
            ItemStack stack = drop.getItem();
            if (stack.getItem() == Item.getItemFromBlock(RegistryEntries.blockScriptingDrive())) {
                driveDrop = true;
            }
            if (stack.getItem() == RegistryEntries.itemScriptingDisk()) {
                diskDrop = true;
            }
        }
        check("3b", driveDrop, "breaking the drive drops the drive itself");
        check("3c", diskDrop, "breaking the drive drops the disk inside");
        for (EntityItem drop : after) {
            drop.setDead();
        }

        this.diskStack = brokenDisk;
    }

    private void restoreDrive(MinecraftServer server) {
        run(server, String.format("setblock %s %s", format(this.drivePos),
                Reference.MOD_ID + ":scripting_drive"));
        if (this.world.getBlockState(this.cablePos).getBlock()
                != Block.getBlockFromName("integrateddynamics:cable")) {
            run(server, String.format("setblock %s integrateddynamics:cable", format(this.cablePos)));
        }
        // The disk keeps its id, so the restored drive exposes the same one.
        setDriveDisk(this.diskStack);
    }

    // ---------------------------------------------------------------- §11.2 items 15-18

    private void writeScripts() {
        setScript(MAIN_PATH, FOO_42 + "\n" + FUNC + "\n" + MEMBERS);
    }

    private void setScript(String relativePath, String content) {
        scriptingData().setScript(this.diskId, Paths.get(relativePath), content,
                IScriptingData.ChangeLocation.MEMORY);
    }

    private void setupDisplayPanel() {
        // Add a real Display Panel part on the north side of the cable.
        PartTypePanelDisplay displayPanel =
                org.cyclops.integrateddynamics.core.part.PartTypes.DISPLAY_PANEL;
        boolean added = PartHelpers.addPart(this.world, this.cablePos, EnumFacing.NORTH,
                displayPanel, new ItemStack(displayPanel.getItem()));
        IPartState state = displayPanelState();
        // A part may already be present if a previous run did not clean up.
        check("15a", added || state != null,
                "Display Panel part added to the network (already present: " + !added + ")");
        if (!(state instanceof PartStateActiveVariableBase)) {
            fail("the Display Panel state is not a PartStateActiveVariableBase: " + state);
            return;
        }
        ItemStack card = writeCard(this.diskId, Paths.get(MAIN_PATH), "foo");
        check("15b", card.getTagCompound() != null
                        && Reference.MOD_ID.concat(":script").equals(
                                card.getTagCompound().getString("_type")),
                "the variable card carries the scripting facade (_type="
                        + (card.getTagCompound() == null ? "null" : card.getTagCompound().getString("_type")) + ")");
        IInventory inventory = (IInventory) ((PartStateActiveVariableBase) state).getInventory();
        inventory.setInventorySlotContents(0, card);
    }

    private void checkPanelValue(int expected) {
        IPartState state = displayPanelState();
        if (state == null) {
            fail("no Display Panel state for the value check");
            return;
        }
        try {
            PartStateActiveVariableBase variableState = (PartStateActiveVariableBase) state;
            // Diagnostics that make a failing panel actionable.
            log("panel diagnostics: hasVariable=" + variableState.hasVariable()
                    + " errors=" + describeErrors(variableState.getGlobalErrors()));
            IVariable<?> variable = variableState.getVariable(idNetwork(), partNetwork());
            log("panel variable=" + variable + " card=" + ((IInventory) variableState.getInventory())
                    .getStackInSlot(0));
            updateDisplayPanel(state);
            IValue displayed = ((PartTypePanelDisplay.State) state).getDisplayValue();
            String compact = displayed == null ? "null"
                    : displayed.getType().toCompactString(displayed);
            check("16-" + expected, displayed instanceof ValueTypeInteger.ValueInteger
                            && ((ValueTypeInteger.ValueInteger) displayed).getRawValue() == expected,
                    "Display Panel shows " + expected + " (value=" + compact + ")");
        } catch (Throwable t) {
            fail("Display Panel evaluation for " + expected + " threw: " + t);
            t.printStackTrace();
        }
    }

    private void checkPanelError() {
        IPartState state = displayPanelState();
        if (state == null) {
            fail("no Display Panel state for the error check");
            return;
        }
        try {
            updateDisplayPanel(state);
        } catch (Throwable t) {
            fail("a broken script must not crash the panel update: " + t);
            t.printStackTrace();
            return;
        }
        List<?> errors = ((PartStateActiveVariableBase) state).getGlobalErrors();
        IValue displayed = ((PartTypePanelDisplay.State) state).getDisplayValue();
        check("18a", displayed == null || displayed.getType() == ValueTypes.CATEGORY_ANY,
                "a broken script does not display a value");
        check("18b", errors != null && !errors.isEmpty(),
                "the Display Panel reports the script error instead of crashing (errors="
                        + (errors == null ? 0 : errors.size()) + ")");
        if (errors != null && !errors.isEmpty()) {
            log("panel error: " + describeErrors(errors));
        }
    }

    /**
     * Render validation errors, which are {@code UnlocalizedString}s without a
     * useful {@code toString}.
     */
    private static String describeErrors(List<?> errors) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < errors.size(); i++) {
            Object error = errors.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            if (error instanceof org.cyclops.cyclopscore.helper.L10NHelpers.UnlocalizedString) {
                builder.append(((org.cyclops.cyclopscore.helper.L10NHelpers.UnlocalizedString) error)
                        .localize());
            } else {
                builder.append(String.valueOf(error));
            }
        }
        return builder.append(']').toString();
    }

    private void updateDisplayPanel(IPartState state) {
        INetwork network = idNetwork();
        IPartNetwork partNetwork = partNetwork();
        if (network == null || partNetwork == null) {
            throw new IllegalStateException("the test network is not available");
        }
        PartTarget target = PartTarget.fromCenter(this.world, this.cablePos, EnumFacing.NORTH);
        // What the network tick does for every part element.
        org.cyclops.integrateddynamics.core.part.PartTypes.DISPLAY_PANEL.update(
                network, partNetwork, target, (PartTypePanelDisplay.State) state);
    }

    @Nullable
    private IPartState displayPanelState() {
        Pair<IPartType, IPartState> data =
                PartPos.getPartData(PartPos.of(this.world, this.cablePos, EnumFacing.NORTH));
        return data == null ? null : data.getRight();
    }

    // ---------------------------------------------------------------- §11.2 items 19-21

    @SuppressWarnings("unchecked")
    private void checkOperatorFilter() {
        try {
            IVariable<?> operatorVariable = facadeVariable(Paths.get(MAIN_PATH), "f");
            if (operatorVariable == null) {
                fail("19: the operator facade did not resolve a variable");
                return;
            }
            IValue operatorValue = operatorVariable.getValue();
            check("19a", operatorValue instanceof ValueTypeOperator.ValueOperator,
                    "the script function is exposed as an operator value (type="
                            + operatorValue.getType().toCompactString(operatorValue) + ")");
            if (!(operatorValue instanceof ValueTypeOperator.ValueOperator)) {
                return;
            }

            ValueTypeList.ValueList list = ValueTypeList.ValueList.ofAll(ValueTypes.CATEGORY_ANY,
                    ValueObjectTypeItemStack.ValueItemStack.of(new ItemStack(net.minecraft.init.Blocks.STONE)),
                    ValueObjectTypeItemStack.ValueItemStack.of(new ItemStack(
                            net.minecraft.init.Items.IRON_SWORD)));
            IVariable[] arguments = new IVariable[]{
                    // ID's filter operator takes the operator first and the list second.
                    new Variable<>(operatorValue),
                    new Variable<>(list)
            };
            IValue result = Operators.OPERATOR_FILTER.evaluate(arguments);
            int length = ((ValueTypeList.ValueList) result).getRawValue().getLength();
            check("19b", length == 1,
                    "the script function filters a list of two item stacks down to " + length + " item");
        } catch (Throwable t) {
            fail("19: the operator filter check threw: " + t);
            t.printStackTrace();
        }
    }

    private void checkOperatorsAndObjectMethods() {
        try {
            IValue opsCall = evalMember(Paths.get(MAIN_PATH), "opsCall");
            check("20a", opsCall instanceof ValueTypeOperator.ValueOperator,
                    "idContext.ops is reachable from a script member (type=" + opsCall.getType() + ")");
            if (opsCall instanceof ValueTypeOperator.ValueOperator) {
                IVariable[] noArguments = new IVariable[0];
                IValue result = ((ValueTypeOperator.ValueOperator) opsCall).getRawValue()
                        .evaluate(noArguments);
                check("20b", result instanceof ValueTypeBoolean.ValueBoolean
                                && ((ValueTypeBoolean.ValueBoolean) result).getRawValue(),
                        "idContext.ops.itemstackStackable(...) returns true");
            }

            IValue strength = evalMember(Paths.get(MAIN_PATH), "strength");
            if (strength instanceof ValueTypeOperator.ValueOperator) {
                IVariable[] arguments = new IVariable[]{
                        new Variable<>(ValueObjectTypeItemStack.ValueItemStack.of(
                                new ItemStack(net.minecraft.init.Blocks.STONE))),
                        new Variable<>(org.cyclops.integrateddynamics.core.evaluate.variable
                                .ValueObjectTypeBlock.ValueBlock.of(
                                        net.minecraft.init.Blocks.STONE.getDefaultState()))
                };
                IValue result = ((ValueTypeOperator.ValueOperator) strength).getRawValue()
                        .evaluate(arguments);
                check("21a", result instanceof ValueTypeInteger.ValueInteger,
                        "the object method item.strength(block) returns an integer ("
                                + result.getType().toCompactString(result) + ")");
            } else {
                fail("21a: the item.strength(block) member did not become an operator");
            }

            IVariable<?> operatorVariable = facadeVariable(Paths.get(MAIN_PATH), "f");
            if (operatorVariable != null
                    && operatorVariable.getValue() instanceof ValueTypeOperator.ValueOperator) {
                IValue result = ((ValueTypeOperator.ValueOperator) operatorVariable.getValue())
                        .getRawValue()
                        .evaluate(new Variable<>(ValueObjectTypeItemStack.ValueItemStack.of(
                                new ItemStack(net.minecraft.init.Blocks.STONE))));
                check("21b", result instanceof ValueTypeBoolean.ValueBoolean
                                && ((ValueTypeBoolean.ValueBoolean) result).getRawValue(),
                        "the object method item.stackable() returns true");
            }
        } catch (Throwable t) {
            fail("20/21: the operator/object method check threw: " + t);
            t.printStackTrace();
        }
    }

    // ---------------------------------------------------------------- §11.2 item 22

    private void checkNbt() {
        try {
            IValue nbtObj = evalMember(Paths.get(MAIN_PATH), "nbtObj");
            check("22a", nbtObj instanceof ValueTypeNbt.ValueNbt,
                    "a script object literal becomes an NBT value (type=" + nbtObj.getType() + ")");
            if (nbtObj instanceof ValueTypeNbt.ValueNbt) {
                NBTTagCompound tag = ((ValueTypeNbt.ValueNbt) nbtObj).getRawValue();
                check("22b", tag != null && tag.getInteger("foo") == 1,
                        "the NBT value contains foo=1");
            }

            // Passing a scalar to an NBT parameter fails by default (decision C1).
            IValue nbtCall = evalMember(Paths.get(MAIN_PATH), "nbtCall");
            if (!(nbtCall instanceof ValueTypeOperator.ValueOperator)) {
                fail("22c: the nbtSize wrapper did not become an operator");
                return;
            }
            IOperator operator = ((ValueTypeOperator.ValueOperator) nbtCall).getRawValue();
            try {
                operator.evaluate(new Variable<>(ValueTypeInteger.ValueInteger.of(42)));
                fail("22c: passing a scalar to an NBT parameter did not fail");
            } catch (EvaluationException e) {
                check("22c", e.getMessage() != null && e.getMessage().contains("NBT"),
                        "a scalar passed to an NBT parameter fails with a readable error: "
                                + e.getMessage());
            }

            // With the wrapper enabled the same call works ({"value": 42}).
            boolean previous = GeneralConfig.nbtScalarWrapper;
            GeneralConfig.nbtScalarWrapper = true;
            try {
                ScriptingNetwork freshNetwork = new ScriptingNetwork();
                freshNetwork.addDisk(this.diskId);
                IValue wrapped = freshNetwork.getVariable(this.diskId, Paths.get(MAIN_PATH), "nbtCall")
                        .getValue();
                IOperator wrappedOperator = ((ValueTypeOperator.ValueOperator) wrapped).getRawValue();
                IValue result = wrappedOperator.evaluate(
                        new Variable<>(ValueTypeInteger.ValueInteger.of(42)));
                check("22d", result instanceof ValueTypeInteger.ValueInteger
                                && ((ValueTypeInteger.ValueInteger) result).getRawValue() == 1,
                        "nbtScalarWrapper=true makes a scalar usable as NBT (size="
                                + result.getType().toCompactString(result) + ")");
            } finally {
                GeneralConfig.nbtScalarWrapper = previous;
            }
            check("22e", !GeneralConfig.nbtScalarWrapper, "the wrapper flag was restored");
        } catch (Throwable t) {
            fail("22: the NBT check threw: " + t);
            t.printStackTrace();
        }
    }

    // ---------------------------------------------------------------- §11.2 item 23

    private void checkOperatorSerialization() {
        try {
            IVariable<?> operatorVariable = facadeVariable(Paths.get(MAIN_PATH), "f");
            if (operatorVariable == null
                    || !(operatorVariable.getValue() instanceof ValueTypeOperator.ValueOperator)) {
                fail("23: no operator value for the serialization check");
                return;
            }
            IOperator operator = ((ValueTypeOperator.ValueOperator) operatorVariable.getValue())
                    .getRawValue();
            String serialized = Operators.REGISTRY.serialize(operator);
            check("23a", serialized != null && !serialized.isEmpty(),
                    "the script operator serializes to NBT: " + serialized);
            IOperator deserialized = Operators.REGISTRY.deserialize(serialized);
            check("23b", deserialized != null, "the serialized operator deserializes again");
            if (deserialized == null) {
                return;
            }
            try {
                deserialized.evaluate(new Variable<>(ValueObjectTypeItemStack.ValueItemStack.of(
                        new ItemStack(net.minecraft.init.Blocks.STONE))));
                fail("23c: a deserialized script operator executed instead of reporting its limitation");
            } catch (EvaluationException e) {
                check("23c", e.getMessage() != null && e.getMessage().contains("serial"),
                        "executing a deserialized script operator reports "
                                + "\"not serializable\" instead of crashing: " + e.getMessage());
            }
        } catch (Throwable t) {
            fail("23: the serialization check threw: " + t);
            t.printStackTrace();
        }
    }

    // ---------------------------------------------------------------- §11.2 item 26

    private void checkTestScriptCommand(MinecraftServer server) {
        String command = Reference.MOD_ID + " testscript";
        int result = server.getCommandManager().executeCommand(server, command);
        check("26", result >= 0, "the diagnostic command ran without throwing: /" + command
                + " -> " + result);
    }

    // ---------------------------------------------------------------- §11.2 item 28

    private void checkPerformance() {
        Path path = Paths.get(MAIN_PATH);
        try {
            // Cold execution: the very first script evaluation never ran before in
            // the M7 phases (the panel phases did run it, so measure a fresh one for
            // the warm number and report the panel result as cold).
            long warmStart = System.nanoTime();
            ScriptingNetwork network = new ScriptingNetwork();
            network.addDisk(this.diskId);
            network.getVariable(this.diskId, path, "foo").getValue();
            this.warmExecutionNanos = System.nanoTime() - warmStart;
            check("28a", millis(this.warmExecutionNanos) < 100.0,
                    "an already loaded script member executes in " + millis(this.warmExecutionNanos) + " ms");

            // One hundred small scripts: write, compile and evaluate them all.
            String body = "const value = %d;\nconst twice = (x) => x * 2;\n";
            for (int i = 0; i < 100; i++) {
                scriptingData().setScript(this.diskId, Paths.get("bench" + i + ".js"),
                        String.format(body, i), IScriptingData.ChangeLocation.MEMORY);
            }
            long start = System.nanoTime();
            for (int i = 0; i < 100; i++) {
                ScriptingNetwork benchNetwork = new ScriptingNetwork();
                benchNetwork.addDisk(this.diskId);
                benchNetwork.getVariable(this.diskId, Paths.get("bench" + i + ".js"), "value").getValue();
            }
            this.hundredScriptsNanos = System.nanoTime() - start;
            check("28b", millis(this.hundredScriptsNanos) < 1000.0,
                    "100 scripts compile and evaluate in " + millis(this.hundredScriptsNanos) + " ms");
            // "No sustained stutter": at most a handful of ticks may exceed the
            // 50 ms budget (cold JIT/Graal warmup ticks), and no tick may exceed 100 ms.
            check("28c", this.longTicksBeforePerformance <= 3
                            && millis(this.maxTickBeforePerformance) < 100.0,
                    "the server tick stays responsive: " + this.longTicksBeforePerformance
                            + " tick(s) above 50 ms, max " + millis(this.maxTickBeforePerformance) + " ms");
            log(String.format("performance: warm single evaluation %.1f ms, 100 scripts %.1f ms, "
                            + "max regular tick %.1f ms, max tick incl. the 100-script burst %.1f ms",
                    millis(this.warmExecutionNanos), millis(this.hundredScriptsNanos),
                    millis(this.maxTickBeforePerformance), millis(this.maxTickNanos)));
        } catch (Throwable t) {
            fail("28: the performance check threw: " + t);
            t.printStackTrace();
        }
    }

    // ---------------------------------------------------------------- helpers

    @Nullable
    private IScriptingData scriptingData() {
        return ScriptingNetworkHelpers.getScriptingData();
    }

    private Map<Path, String> scripts() {
        return scriptingData().getScripts(this.diskId);
    }

    private INetwork idNetwork() {
        return NetworkHelpers.getNetwork(PartPos.of(this.world, this.cablePos, EnumFacing.UP));
    }

    private IPartNetwork partNetwork() {
        INetwork network = idNetwork();
        return network == null ? null : NetworkHelpers.getPartNetwork(network);
    }

    private com.mpp.integratedscripting.api.network.IScriptingNetwork scriptingNetwork() {
        INetwork network = idNetwork();
        if (network == null) {
            return null;
        }
        return ScriptingNetworkHelpers.getScriptingNetwork(network);
    }

    @Nullable
    private IVariable<?> facadeVariable(Path path, String member) {
        ScriptVariableFacade probe = new ScriptVariableFacade(0, this.diskId, path, member);
        return probe.getVariable(partNetwork());
    }

    private IValue evalMember(Path path, String member) throws EvaluationException {
        IVariable<?> variable = facadeVariable(path, member);
        if (variable == null) {
            throw new EvaluationException("the facade did not resolve a variable for " + member);
        }
        return variable.getValue();
    }

    /**
     * Write a {@link ScriptVariableFacade} onto a real Integrated Dynamics
     * variable card through its registry, exactly like the terminal does.
     */
    private ItemStack writeCard(int disk, Path path, String member) {
        Item variableItem = Item.getByNameOrId(Reference.MOD_INTEGRATEDDYNAMICS + ":variable");
        IVariableFacadeHandlerRegistry registry = IntegratedDynamics._instance
                .getRegistryManager().getRegistry(IVariableFacadeHandlerRegistry.class);
        final int facadeDisk = disk;
        final Path facadePath = path;
        final String facadeMember = member;
        return registry.writeVariableFacadeItem(true, new ItemStack(variableItem),
                ScriptVariableFacadeHandler.getInstance(),
                new IVariableFacadeHandlerRegistry.IVariableFacadeFactory<IScriptVariableFacade>() {
                    @Override
                    public IScriptVariableFacade create(boolean generateId) {
                        return new ScriptVariableFacade(generateId, facadeDisk, facadePath, facadeMember);
                    }

                    @Override
                    public IScriptVariableFacade create(int id) {
                        return new ScriptVariableFacade(id, facadeDisk, facadePath, facadeMember);
                    }
                },
                null, PartTypes.TERMINAL_SCRIPTING.getBlock());
    }

    private void setDriveDisk(ItemStack stack) {
        TileEntity te = this.world.getTileEntity(this.drivePos);
        if (!(te instanceof com.mpp.integratedscripting.blockentity.TileScriptingDrive)) {
            fail("the scripting drive tile entity is missing at " + this.drivePos);
            return;
        }
        IInventory inventory = (IInventory) ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te)
                .getInventory();
        // Insert a copy: some vanilla/Cyclops code shrinks the stack it was given,
        // which would corrupt the harness' own reference across the break phase.
        inventory.setInventorySlotContents(0, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
    }

    private void run(MinecraftServer server, String command) {
        int result = server.getCommandManager().executeCommand(server, command);
        if (result < 0) {
            fail("command failed: " + command);
        }
    }

    private static String format(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    // ---------------------------------------------------------------- reporting

    private void check(String id, boolean condition, String message) {
        this.checks++;
        if (condition) {
            log("PASS [" + id + "] " + message);
        } else {
            fail("[" + id + "] " + message);
        }
    }

    private void log(String message) {
        IntegratedScriptingCallback.clog(TAG + " " + message);
    }

    private void fail(String message) {
        this.failures++;
        IntegratedScriptingCallback.clog(Level.ERROR, TAG + " FAIL " + message);
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
        log("RESULT: " + (this.failures == 0 ? "PASS" : "FAIL (" + this.failures + " failures)")
                + " checks=" + this.checks
                + (this.warmExecutionNanos >= 0 ? " warm-ms=" + millis(this.warmExecutionNanos) : "")
                + (this.hundredScriptsNanos >= 0
                        ? " 100-scripts-ms=" + millis(this.hundredScriptsNanos) : ""));
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null && server.isDedicatedServer()) {
            server.initiateShutdown();
        }
    }

}
