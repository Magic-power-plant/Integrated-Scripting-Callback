package com.mpp.integratedscripting.client.gui.container;

import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.Reference;
import com.mpp.RegistryEntries;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.core.evaluate.ScriptVariableFacadeHandler;
import com.mpp.integratedscripting.core.item.ScriptVariableFacade;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;
import com.mpp.integratedscripting.inventory.container.ContainerTerminalScripting;
import com.mpp.integratedscripting.part.PartTypes;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.item.IVariableFacade;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.IPartState;
import org.cyclops.integrateddynamics.api.part.IPartType;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.client.gui.GuiOnTheDynamicsOfIntegration;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integrateddynamics.core.part.PartStateActiveVariableBase;
import org.cyclops.integrateddynamics.infobook.OnTheDynamicsOfIntegrationBook;
import org.cyclops.integrateddynamics.part.PartTypePanelDisplay;
import org.lwjgl.input.Keyboard;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Temporary M7 client acceptance harness. Enabled with {@code -Disc.m7Gui=true}
 * ({@code ./gradlew runClient -Pm7Gui}) and removed after the milestone is
 * verified (docs/MILESTONES.md M7 records the run).
 *
 * <p>It covers the GUI-driven part of §11.2 in a real client: selecting a script
 * member in the terminal editor, inserting an empty variable card through a real
 * slot click, watching the card become a scripting facade, a real Display Panel
 * part on the same network showing 42/43 and the error state, the info book being
 * opened through the real item, the drive being placed by a real
 * {@code ItemBlock#onItemUse} (facing + drops), two terminals editing the same
 * disk, and the terminal opening with one hundred scripts.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class ClientAcceptanceHarness {

    private static final String TAG = "[M7-GUI]";
    private static final String WORLD_NAME = "isc-m7-gui";
    private static final int GLOBAL_TIMEOUT_TICKS = 9000;

    private static final String SCRIPT_42 = "const foo = 42;\nfunction f(item) { return item.stackable(); }";
    private static final String SCRIPT_43 = "const foo = 43;\nfunction f(item) { return item.stackable(); }";
    private static final String SCRIPT_BROKEN = "const foo = ;";
    private static final String SCRIPT_SECOND_TERMINAL = "const foo = 100;";

    private int phase;
    private int phaseTicks;
    private int totalTicks;
    private int failures;
    private boolean finished;

    private volatile boolean serverBusy;
    private BlockPos drivePos;
    private BlockPos cablePos;
    private int diskId = -1;
    private String activeScriptPath = "script0.js";
    private long scriptFlushMillis = -1;
    private int scriptSyncStep;
    private volatile String serverScript = "";

    /** Result of the last scheduled server check. */
    private volatile boolean serverOk = true;
    private volatile String serverMessage = "";
    private volatile String panelValue = "";
    private volatile String panelErrors = "";

    private long terminalOpenStartNanos = -1;
    private long terminalOpenMillis = -1;
    private boolean secondTerminalWritten;

    public static boolean isEnabled() {
        return Boolean.getBoolean("isc.m7Gui");
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

    // ---------------------------------------------------------------- state machine

    private void tick() throws IOException {
        this.phaseTicks++;
        this.totalTicks++;
        if (this.totalTicks > GLOBAL_TIMEOUT_TICKS) {
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
                mc.launchIntegratedServer(WORLD_NAME, WORLD_NAME,
                        new WorldSettings(1234L, GameType.CREATIVE, false, false, WorldType.DEFAULT));
                return;
            case 1:
                if (mc.player == null || mc.world == null || mc.getIntegratedServer() == null) {
                    timeout(600, "world join");
                    return;
                }
                nextPhase();
                log("joined world at " + mc.player.getPosition());
                mc.displayGuiScreen(null);
                scheduleSetup(mc);
                return;
            case 2:
                if (!waitServer("world setup")) {
                    return;
                }
                nextPhase();
                log("setup done; opening the terminal");
                openTerminal(mc);
                return;
            case 3:
                if (!(mc.currentScreen instanceof GuiTerminalScripting)) {
                    timeout(400, "terminal screen");
                    return;
                }
                if (container(mc).getAvailableDisks().isEmpty()) {
                    timeout(400, "disk list sync");
                    return;
                }
                this.diskId = container(mc).getAvailableDisks().getInt(0);
                nextPhase();
                log("terminal open; disk " + this.diskId);
                clickCreateFile(mc);
                return;
            case 4:
                if (activeScriptCount(mc) < 1) {
                    timeout(400, "script creation");
                    return;
                }
                nextPhase();
                clickScriptRow(mc, 0);
                return;
            case 5:
                if (container(mc).getActiveScriptPath() == null) {
                    timeout(300, "script selection");
                    return;
                }
                nextPhase();
                this.activeScriptPath = container(mc).getActiveScriptPath().toString();
                log("editing script " + this.activeScriptPath);
                typeScript(mc, SCRIPT_42, true);
                return;
            case 6:
                if (!SCRIPT_42.equals(container(mc).getActiveScript())) {
                    timeout(600, "typed script in the client container");
                    return;
                }
                nextPhase();
                // Move the cursor into the "foo" member of the first line. The editor
                // wraps long lines, so move up until the first visual line, then to its
                // start and six characters to the right ("const " prefix).
                for (int i = 0; i < 10; i++) {
                    key(mc, Keyboard.KEY_UP);
                }
                key(mc, Keyboard.KEY_HOME);
                for (int i = 0; i < 6; i++) {
                    key(mc, Keyboard.KEY_RIGHT);
                }
                return;
            case 7: {
                if (this.phaseTicks < 5) {
                    return;
                }
                if (!waitServer("script sync / card setup")) {
                    return;
                }
                if (this.scriptSyncStep == 0) {
                    this.scriptSyncStep = 1;
                    check("15a", "foo".equals(container(mc).getSelectedMember()),
                            "selecting inside the editor resolves the member: '"
                                    + container(mc).getSelectedMember() + "'");
                    scheduleScriptCheck(mc);
                    return;
                }
                if (this.scriptSyncStep == 1) {
                    if (!SCRIPT_42.equals(this.serverScript)) {
                        if (this.phaseTicks % 10 == 0) {
                            scheduleScriptCheck(mc);
                        }
                        timeout(300, "script content on the server (" + this.serverScript + ")");
                        return;
                    }
                    this.scriptSyncStep = 2;
                    scheduleCardInsertion(mc);
                    return;
                }
                jumpToPhase(8);
                clickVariableSlot(mc);
                return;
            }
            case 8: {
                ItemStack stack = variableSlotStack(mc);
                if (stack.isEmpty() || stack.getTagCompound() == null
                        || !stack.getTagCompound().hasKey("_type")) {
                    timeout(300, "variable card written by the container");
                    return;
                }
                jumpToPhase(10);
                check("15b", Reference.MOD_ID.concat(":script")
                                .equals(stack.getTagCompound().getString("_type")),
                        "the inserted variable card became a scripting facade (_type="
                                + stack.getTagCompound().getString("_type") + ")");
                scheduleCardAndPanelChecks(mc);
                return;
            }
            case 10:
                if (!waitServer("facade + display panel setup")) {
                    return;
                }
                nextPhase();
                return;
            case 11:
                // Give the network a few ticks to evaluate the Display Panel.
                if (this.phaseTicks < 40) {
                    return;
                }
                nextPhase();
                schedulePanelRead(mc);
                return;
            case 12:
                if (!waitServer("display panel value 42")) {
                    return;
                }
                nextPhase();
                check("16", this.panelValue.contains("42"),
                        "the Display Panel shows 42 (value=" + this.panelValue + ")");
                mc.displayGuiScreen(null);
                return;
            case 13:
                if (this.phaseTicks < 20) {
                    return;
                }
                nextPhase();
                lookAtPanel(mc);
                return;
            case 14:
                if (this.phaseTicks == 20) {
                    screenshot(mc, "m7-display-panel.png");
                }
                if (this.phaseTicks < 25) {
                    return;
                }
                nextPhase();
                openTerminal(mc);
                return;
            case 15:
                if (!(mc.currentScreen instanceof GuiTerminalScripting)) {
                    timeout(400, "terminal reopen");
                    return;
                }
                if (container(mc).getActiveScriptPath() == null) {
                    timeout(300, "terminal reopen script selection");
                    return;
                }
                nextPhase();
                typeScript(mc, SCRIPT_43, true);
                return;
            case 16:
                if (!SCRIPT_43.equals(container(mc).getActiveScript())) {
                    timeout(600, "changed script in the client container");
                    return;
                }
                if (this.phaseTicks < 60) {
                    return;
                }
                nextPhase();
                schedulePanelRead(mc);
                return;
            case 17:
                if (!waitServer("display panel value 43")) {
                    return;
                }
                nextPhase();
                check("17", this.panelValue.contains("43"),
                        "the Display Panel follows the edited script to 43 (value=" + this.panelValue + ")");
                typeScript(mc, SCRIPT_BROKEN, true);
                return;
            case 18:
                if (!SCRIPT_BROKEN.equals(container(mc).getActiveScript())) {
                    timeout(600, "broken script in the client container");
                    return;
                }
                if (this.phaseTicks < 60) {
                    return;
                }
                nextPhase();
                schedulePanelRead(mc);
                return;
            case 19:
                if (!waitServer("display panel error state")) {
                    return;
                }
                nextPhase();
                check("18", !this.panelValue.contains("43") && !this.panelErrors.isEmpty(),
                        "a syntax error shows an error instead of a value (value=" + this.panelValue
                                + ", errors=" + this.panelErrors + ")");
                typeScript(mc, SCRIPT_43, true);
                return;
            case 20:
                if (!SCRIPT_43.equals(container(mc).getActiveScript())) {
                    timeout(600, "fixed script in the client container");
                    return;
                }
                if (this.phaseTicks < 60) {
                    return;
                }
                nextPhase();
                schedulePanelRead(mc);
                return;
            case 21:
                if (!waitServer("display panel recovery")) {
                    return;
                }
                nextPhase();
                check("18b", this.panelValue.contains("43"),
                        "fixing the script recovers the value (value=" + this.panelValue + ")");
                scheduleSecondTerminalWrite(mc);
                return;
            case 22:
                if (!waitServer("second terminal write")) {
                    return;
                }
                nextPhase();
                check("27a", this.secondTerminalWritten,
                        "a second terminal can write the same disk: " + this.serverMessage);
                typeScript(mc, SCRIPT_OK_LAST, true);
                return;
            case 23:
                if (!SCRIPT_OK_LAST.equals(container(mc).getActiveScript())) {
                    timeout(600, "last writer content");
                    return;
                }
                if (this.phaseTicks < 60) {
                    return;
                }
                nextPhase();
                scheduleFinalDiskCheck(mc);
                return;
            case 24:
                if (!waitServer("last writer wins")) {
                    return;
                }
                nextPhase();
                check("27b", SCRIPT_OK_LAST.equals(this.serverMessage),
                        "the last writer wins without corruption: " + this.serverMessage);
                mc.displayGuiScreen(null);
                return;
            case 25:
                if (this.phaseTicks < 10) {
                    return;
                }
                nextPhase();
                scheduleInfoBook(mc);
                return;
            case 26:
                if (!waitServer("info book in hand")) {
                    return;
                }
                nextPhase();
                // The real item use path: the client right-clicks with the book in hand.
                mc.playerController.processRightClick(mc.player, mc.world, EnumHand.MAIN_HAND);
                log("used the info book item");
                return;
            case 27:
                if (!(mc.currentScreen instanceof GuiOnTheDynamicsOfIntegration)) {
                    timeout(400, "info book screen via the real item");
                    return;
                }
                nextPhase();
                check("24a", true, "the info book opens through the real item use");
                drawSmoke(mc);
                screenshot(mc, "m7-infobook.png");
                mc.displayGuiScreen(null);
                return;
            case 28:
                if (this.phaseTicks < 10) {
                    return;
                }
                nextPhase();
                scheduleDrivePlacement(mc);
                return;
            case 29:
                if (!waitServer("drive placement and drops")) {
                    return;
                }
                nextPhase();
                check("3a", this.serverOk, "the drive is placed by a real item use with "
                        + "player-relative facing and drops itself and its disk: " + this.serverMessage);
                scheduleHundredScripts(mc);
                return;
            case 30:
                if (!waitServer("hundred scripts written")) {
                    return;
                }
                nextPhase();
                this.terminalOpenStartNanos = System.nanoTime();
                openTerminal(mc);
                return;
            case 31:
                if (!(mc.currentScreen instanceof GuiTerminalScripting)) {
                    timeout(400, "terminal screen with 100 scripts");
                    return;
                }
                {
                    Map<Path, String> scripts = container(mc).getLastScripts().get(this.diskId);
                    boolean ready = scripts != null && scripts.size() >= 100;
                    if (!ready) {
                        timeout(400, "100 scripts on the client");
                        return;
                    }
                }
                nextPhase();
                this.terminalOpenMillis = (System.nanoTime() - this.terminalOpenStartNanos) / 1_000_000L;
                check("28a", this.terminalOpenMillis < 1000,
                        "the terminal opens with 100 scripts in " + this.terminalOpenMillis + " ms");
                drawSmoke(mc);
                screenshot(mc, "m7-terminal-many-scripts.png");
                return;
            case 32:
                if (this.phaseTicks < 20) {
                    return;
                }
                finish();
                return;
            default:
                finish();
        }
    }

    private static final String SCRIPT_OK_LAST = "const foo = 7;";

    // ---------------------------------------------------------------- setup

    private void scheduleSetup(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "setup", () -> {
            World world = server.getWorld(0);
            BlockPos spawn = world.getSpawnPoint();
            BlockPos base = world.getTopSolidOrLiquidBlock(spawn);
            this.drivePos = base.add(4, 0, 4);
            this.cablePos = this.drivePos.east();

            run(server, String.format("setblock %d %d %d minecraft:air",
                    this.drivePos.getX(), this.drivePos.getY(), this.drivePos.getZ()));
            run(server, String.format("setblock %d %d %d minecraft:air",
                    this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ()));
            run(server, String.format("setblock %d %d %d minecraft:air",
                    this.drivePos.getX(), this.drivePos.getY() + 1, this.drivePos.getZ()));
            run(server, String.format("setblock %d %d %d minecraft:air",
                    this.cablePos.getX(), this.cablePos.getY() + 1, this.cablePos.getZ()));
            run(server, String.format("setblock %d %d %d integratedscriptingcallback:scripting_drive",
                    this.drivePos.getX(), this.drivePos.getY(), this.drivePos.getZ()));
            run(server, String.format("setblock %d %d %d integrateddynamics:cable",
                    this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ()));
            run(server, String.format(
                    "replaceitem block %d %d %d slot.container.0 integratedscriptingcallback:scripting_disk",
                    this.drivePos.getX(), this.drivePos.getY(), this.drivePos.getZ()));

            boolean added = PartHelpers.addPart(world, this.cablePos, EnumFacing.UP,
                    PartTypes.TERMINAL_SCRIPTING,
                    new ItemStack(PartTypes.TERMINAL_SCRIPTING.getItem()));
            serverOk = added || PartPos.getPartData(PartPos.of(world, this.cablePos, EnumFacing.UP)) != null;
            serverMessage = added ? "terminal part added" : "terminal part already present";
            log("terminal part: " + serverMessage + " at " + this.cablePos);
        });
    }

    private void openTerminal(Minecraft mc) {
        // A real right click sets the temporary part side on both sides.
        PartTypes.TERMINAL_SCRIPTING.getModGui().getGuiHandler()
                .setTemporaryData(org.cyclops.integrateddynamics.core.client.gui.ExtendedGuiHandler.PART,
                        EnumFacing.UP);
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "open terminal", () -> {
            List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) {
                throw new IllegalStateException("no players on the server");
            }
            EntityPlayerMP player = players.get(0);
            World world = server.getWorld(0);
            PartTypes.TERMINAL_SCRIPTING.getModGui().getGuiHandler()
                    .setTemporaryData(org.cyclops.integrateddynamics.core.client.gui.ExtendedGuiHandler.PART,
                            EnumFacing.UP);
            player.openGui(PartTypes.TERMINAL_SCRIPTING.getModGui(),
                    PartTypes.TERMINAL_SCRIPTING.getGuiID(), world,
                    this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ());
        });
    }

    private void lookAtPanel(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "look at panel", () -> {
            EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
            // The panel is on the north face of the cable; stand in front of it and
            // look slightly up so the panel face is visible in the screenshot.
            player.connection.setPlayerLocation(this.cablePos.getX() + 0.5D, this.cablePos.getY() - 1.0D,
                    this.cablePos.getZ() - 3.5D, 0.0F, -15.0F);
        });
    }

    // ---------------------------------------------------------------- server checks

    private void scheduleScriptCheck(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "script sync", () -> {
            Map<Path, String> scripts = ScriptingNetworkHelpers.getScriptingData()
                    .getScripts(this.diskId);
            String content = scripts.get(Paths.get(this.activeScriptPath));
            this.serverScript = content == null ? "" : content;
        });
    }

    private void scheduleCardInsertion(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "give variable card", () -> {
            EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
            if (player.openContainer instanceof ContainerTerminalScripting) {
                ContainerTerminalScripting container = (ContainerTerminalScripting) player.openContainer;
                checkServer("15c", "foo".equals(container.getSelectedMember()),
                        "the server container received the selection ("
                                + container.getSelectedMember() + ")");
            } else {
                fail("15c: the server container is not a terminal container");
            }
            ItemStack card = new ItemStack(Item.getByNameOrId(
                    Reference.MOD_INTEGRATEDDYNAMICS + ":variable"));
            player.inventory.currentItem = 0;
            player.inventory.setInventorySlotContents(0, card);
            player.inventoryContainer.detectAndSendChanges();
        });
    }

    private void scheduleCardAndPanelChecks(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "card + panel", () -> {
            World world = server.getWorld(0);
            EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
            ContainerTerminalScripting container = (ContainerTerminalScripting) player.openContainer;
            ItemStack written = container.getSlot(0).getStack();
            if (written.isEmpty() || written.getTagCompound() == null) {
                throw new IllegalStateException("the terminal slot does not hold a written card");
            }
            NBTTagCompound tag = written.getTagCompound();
            checkServer("15d", Reference.MOD_ID.concat(":script").equals(tag.getString("_type")),
                    "the card written by the container uses the script handler");
            IVariableFacade facade = IntegratedDynamics._instance.getRegistryManager()
                    .getRegistry(IVariableFacadeHandlerRegistry.class).handle(written);
            checkServer("15e", facade instanceof IScriptVariableFacade,
                    "the card reads back as a scripting facade");
            if (facade instanceof IScriptVariableFacade) {
                IScriptVariableFacade scriptFacade = (IScriptVariableFacade) facade;
                checkServer("15f", scriptFacade.getDisk() == this.diskId
                                && "foo".equals(scriptFacade.getMember()),
                        "the facade points at disk " + scriptFacade.getDisk()
                                + " member " + scriptFacade.getMember());
                INetwork network = NetworkHelpers.getNetwork(
                        PartPos.of(world, this.cablePos, EnumFacing.UP));
                IPartNetwork partNetwork = NetworkHelpers.getPartNetwork(network);
                IValue value;
                try {
                    value = scriptFacade.getVariable(partNetwork).getValue();
                } catch (EvaluationException e) {
                    throw new IllegalStateException("the card failed to evaluate: " + e.getMessage(), e);
                }
                checkServer("15g", value instanceof ValueTypeInteger.ValueInteger
                                && ((ValueTypeInteger.ValueInteger) value).getRawValue() == 42,
                        "the card evaluates to 42 through the network (" + compact(value) + ")");
            }

            // Put a copy of the card into a real Display Panel part.
            PartTypePanelDisplay displayPanel =
                    org.cyclops.integrateddynamics.core.part.PartTypes.DISPLAY_PANEL;
            boolean added = PartHelpers.addPart(world, this.cablePos, EnumFacing.NORTH,
                    displayPanel, new ItemStack(displayPanel.getItem()));
            IPartState state = displayPanelState(world);
            checkServer("16a", added || state != null, "a Display Panel part was added to the cable");
            if (state instanceof PartStateActiveVariableBase) {
                ((net.minecraft.inventory.IInventory) ((PartStateActiveVariableBase) state).getInventory())
                        .setInventorySlotContents(0, written.copy());
            }
        });
    }

    private void schedulePanelRead(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "panel read", () -> {
            World world = server.getWorld(0);
            IPartState state = displayPanelState(world);
            if (!(state instanceof PartTypePanelDisplay.State)) {
                throw new IllegalStateException("no Display Panel state");
            }
            IValue value = ((PartTypePanelDisplay.State) state).getDisplayValue();
            this.panelValue = value == null ? "null" : compact(value);
            List<?> errors = ((PartStateActiveVariableBase) state).getGlobalErrors();
            this.panelErrors = describeErrors(errors);
            log("panel: value=" + this.panelValue + " errors=" + this.panelErrors);
        });
    }

    private void scheduleSecondTerminalWrite(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "second terminal", () -> {
            World world = server.getWorld(0);
            EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
            IPartContainer partContainer = PartHelpers.getPartContainer(world, this.cablePos, EnumFacing.UP);
            PartTarget target = PartTarget.fromCenter(world, this.cablePos, EnumFacing.UP);
            // A second terminal on the same part: same sync code as a second client.
            ContainerTerminalScripting second = new ContainerTerminalScripting(
                    player, target, partContainer, PartTypes.TERMINAL_SCRIPTING);
            Path path = Paths.get(this.activeScriptPath);
            second.setServerScript(this.diskId, path, SCRIPT_SECOND_TERMINAL);
            second.detectAndSendChanges();
            String onDisk = ScriptingNetworkHelpers.getScriptingData()
                    .getScripts(this.diskId).get(path);
            this.secondTerminalWritten = SCRIPT_SECOND_TERMINAL.equals(onDisk);
            this.serverMessage = "disk content after the second write: " + onDisk;
        });
    }

    private void scheduleFinalDiskCheck(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "last writer", () -> {
            Path path = Paths.get(this.activeScriptPath);
            String onDisk = ScriptingNetworkHelpers.getScriptingData()
                    .getScripts(this.diskId).get(path);
            this.serverMessage = onDisk;
            checkServer("27c", SCRIPT_OK_LAST.equals(onDisk),
                    "the client's edit is what ends up on the disk");
            ContainerTerminalScripting clientContainer;
            if (server.getPlayerList().getPlayers().get(0).openContainer
                    instanceof ContainerTerminalScripting) {
                clientContainer = (ContainerTerminalScripting) server.getPlayerList().getPlayers().get(0)
                        .openContainer;
            } else {
                return;
            }
            Map<Path, String> synced = clientContainer.getLastScripts().get(this.diskId);
            checkServer("27d", synced != null && SCRIPT_OK_LAST.equals(synced.get(path)),
                    "the server pushes the final content back to the open terminal");
        });
    }

    private void scheduleInfoBook(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "give info book", () -> {
            EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
            Item book = Item.getByNameOrId(
                    Reference.MOD_INTEGRATEDDYNAMICS + ":on_the_dynamics_of_integration");
            if (book == null) {
                throw new IllegalStateException("the info book item is not registered");
            }
            player.inventory.currentItem = 0;
            player.inventory.setInventorySlotContents(0, new ItemStack(book));
            player.inventoryContainer.detectAndSendChanges();
        });
    }

    private void scheduleDrivePlacement(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "drive placement", () -> {
            World world = server.getWorld(0);
            EntityPlayerMP player = server.getPlayerList().getPlayers().get(0);
            BlockPos spawn = world.getSpawnPoint();
            BlockPos base = world.getTopSolidOrLiquidBlock(spawn);
            BlockPos side = base.add(-4, 0, 0);
            run(server, String.format("setblock %d %d %d minecraft:air",
                    side.getX(), side.getY(), side.getZ()));
            player.setPositionAndRotation(side.getX() + 0.5D, side.getY() + 1.0D,
                    side.getZ() + 2.5D, 180.0F, 0.0F);
            ItemStack driveStack = new ItemStack(
                    Item.getItemFromBlock(RegistryEntries.blockScriptingDrive()));
            player.inventory.currentItem = 0;
            player.inventory.setInventorySlotContents(0, driveStack);
            net.minecraft.util.EnumActionResult actionResult =
                    ((ItemBlock) driveStack.getItem()).onItemUse(player, world, side.down(),
                            EnumHand.MAIN_HAND, EnumFacing.UP, 0.5F, 1.0F, 0.5F);
            boolean placed = actionResult == net.minecraft.util.EnumActionResult.SUCCESS;
            Block placedBlock = world.getBlockState(side).getBlock();
            checkServer("3b", placed && placedBlock == RegistryEntries.blockScriptingDrive(),
                    "the drive is placed by a real item use");
            EnumFacing facing = world.getBlockState(side)
                    .getValue(com.mpp.integratedscripting.block.BlockScriptingDrive.FACING);
            EnumFacing expected = player.getHorizontalFacing();
            checkServer("3c", facing == expected,
                    "the drive faces the player (facing=" + facing + ", expected=" + expected + ")");

            // Insert the disk, then break the block and inspect the drops.
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(side);
            if (te instanceof com.mpp.integratedscripting.blockentity.TileScriptingDrive) {
                ((net.minecraft.inventory.IInventory)
                        ((com.mpp.integratedscripting.blockentity.TileScriptingDrive) te).getInventory())
                        .setInventorySlotContents(0, new ItemStack(RegistryEntries.itemScriptingDisk()));
            }
            List<EntityItem> before = world.getEntitiesWithinAABB(EntityItem.class,
                    new AxisAlignedBB(side).grow(4));
            for (EntityItem item : before) {
                item.setDead();
            }
            world.destroyBlock(side, true);
            List<EntityItem> after = world.getEntitiesWithinAABB(EntityItem.class,
                    new AxisAlignedBB(side).grow(4));
            boolean driveDrop = false;
            boolean diskDrop = false;
            for (EntityItem item : after) {
                if (item.getItem().getItem() == Item.getItemFromBlock(
                        RegistryEntries.blockScriptingDrive())) {
                    driveDrop = true;
                }
                if (item.getItem().getItem() == RegistryEntries.itemScriptingDisk()) {
                    diskDrop = true;
                }
            }
            checkServer("3d", driveDrop, "the placed drive drops itself");
            checkServer("3e", diskDrop, "the placed drive drops its disk");
            for (EntityItem item : after) {
                item.setDead();
            }
            this.serverMessage = "facing=" + facing + ", driveDrop=" + driveDrop
                    + ", diskDrop=" + diskDrop;
        });
    }

    private void scheduleHundredScripts(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        runOnServer(server, "hundred scripts", () -> {
            String body = "const value = %d;\nconst twice = (x) => x * 2;\n";
            for (int i = 0; i < 100; i++) {
                ScriptingNetworkHelpers.getScriptingData().setScript(this.diskId,
                        Paths.get("bench" + i + ".js"), String.format(body, i),
                        com.mpp.integratedscripting.api.network.IScriptingData.ChangeLocation.MEMORY);
            }
            // Flush now and measure it separately: the §11.2 item 28 budget is about
            // opening a terminal, not about writing 100 files in one server tick.
            long flushStart = System.nanoTime();
            IntegratedScriptingCallback._instance.scriptingData.tick();
            this.scriptFlushMillis = (System.nanoTime() - flushStart) / 1_000_000L;
            log("wrote 100 scripts to disk " + this.diskId + " (flush " + this.scriptFlushMillis + " ms)");
        });
    }

    // ---------------------------------------------------------------- input helpers

    private void clickCreateFile(Minecraft mc) throws IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        screen.mouseClicked(screen.getGuiLeftTotal() + GuiTerminalScripting.PATHS_X + 5,
                screen.getGuiTopTotal() + 226, 0);
    }

    private void clickScriptRow(Minecraft mc, int row) throws IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        screen.mouseClicked(screen.getGuiLeftTotal() + GuiTerminalScripting.PATHS_X + 20,
                screen.getGuiTopTotal() + GuiTerminalScripting.PATHS_Y
                        + row * GuiTerminalScripting.PATHS_ROW_HEIGHT + 4, 0);
    }

    /**
     * Replace the whole editor content: right click selects everything in the
     * editor (the reference behaviour), typing replaces the selection.
     */
    private void typeScript(Minecraft mc, String script, boolean replaceAll) throws IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        if (replaceAll) {
            screen.mouseClicked(screen.getGuiLeftTotal() + 120, screen.getGuiTopTotal() + 40, 1);
        } else {
            screen.mouseClicked(screen.getGuiLeftTotal() + 120, screen.getGuiTopTotal() + 40, 0);
        }
        for (int i = 0; i < script.length(); i++) {
            char c = script.charAt(i);
            if (c == '\n') {
                screen.keyTyped('\r', Keyboard.KEY_RETURN);
            } else {
                screen.keyTyped(c, 0);
            }
        }
        log("typed script (" + script.length() + " chars)");
    }

    private void key(Minecraft mc, int keyCode) throws IOException {
        ((GuiTerminalScripting) mc.currentScreen).keyTyped((char) 0, keyCode);
    }

    /**
     * Insert the card from the player's hotbar with a real shift click
     * ({@code QUICK_MOVE}), which is the shortest genuine inventory interaction:
     * the server moves the stack from the player slot into the terminal slot.
     */
    private void clickVariableSlot(Minecraft mc) {
        ContainerTerminalScripting container = container(mc);
        int playerSlot = findPlayerHotbarSlot(container, 0);
        if (playerSlot < 0) {
            fail("could not find the player hotbar slot in the terminal container");
            finish();
            return;
        }
        mc.playerController.windowClick(container.windowId, playerSlot, 0, ClickType.QUICK_MOVE, mc.player);
        log("shift-clicked the variable card into the terminal slot");
    }

    private int findPlayerHotbarSlot(net.minecraft.inventory.Container container, int hotbarIndex) {
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            net.minecraft.inventory.Slot slot = container.inventorySlots.get(i);
            if (slot.inventory == Minecraft.getMinecraft().player.inventory
                    && slot.getSlotIndex() == hotbarIndex) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack variableSlotStack(Minecraft mc) {
        return ((GuiTerminalScripting) mc.currentScreen).inventorySlots.getSlot(0).getStack();
    }

    // ---------------------------------------------------------------- helpers

    private void runOnServer(MinecraftServer server, String name, Runnable task) {
        this.serverBusy = true;
        this.serverOk = true;
        server.addScheduledTask(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                this.serverOk = false;
                fail("server task '" + name + "' threw: " + t);
                t.printStackTrace();
            } finally {
                this.serverBusy = false;
            }
        });
    }

    private boolean waitServer(String what) {
        if (this.serverBusy) {
            timeout(400, what);
            return false;
        }
        return true;
    }

    private ContainerTerminalScripting container(Minecraft mc) {
        return ((GuiTerminalScripting) mc.currentScreen).getTerminalContainer();
    }

    private int activeScriptCount(Minecraft mc) {
        Map<Path, String> scripts = container(mc).getLastScripts()
                .get(container(mc).getActiveDisk());
        return scripts == null ? 0 : scripts.size();
    }

    @Nullable
    private IPartState displayPanelState(World world) {
        Pair<IPartType, IPartState> data =
                PartPos.getPartData(PartPos.of(world, this.cablePos, EnumFacing.NORTH));
        return data == null ? null : data.getRight();
    }

    private void drawSmoke(Minecraft mc) {
        GuiScreen screen = mc.currentScreen;
        try {
            screen.drawScreen(60, 60, 0F);
            log("drawScreen ok");
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

    private static String compact(IValue value) {
        return value.getType().toCompactString(value);
    }

    private static String describeErrors(List<?> errors) {
        StringBuilder builder = new StringBuilder();
        for (Object error : errors) {
            if (error instanceof org.cyclops.cyclopscore.helper.L10NHelpers.UnlocalizedString) {
                builder.append(((org.cyclops.cyclopscore.helper.L10NHelpers.UnlocalizedString) error)
                        .localize()).append(' ');
            } else {
                builder.append(error).append(' ');
            }
        }
        return builder.toString().trim();
    }

    private void run(MinecraftServer server, String command) {
        log("command: " + command + " -> " + server.getCommandManager().executeCommand(server, command));
    }

    // ---------------------------------------------------------------- reporting

    private void check(String id, boolean condition, String message) {
        if (condition) {
            log("PASS [" + id + "] " + message);
        } else {
            fail("[" + id + "] " + message);
        }
    }

    private void checkServer(String id, boolean condition, String message) {
        if (condition) {
            log("PASS [" + id + "] " + message);
        } else {
            fail("[server: " + id + "] " + message);
            this.serverOk = false;
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

    private void jumpToPhase(int phase) {
        this.phase = phase;
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
