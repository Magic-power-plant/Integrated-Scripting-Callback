package com.mpp.integratedscripting.client.gui.container;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.inventory.container.ContainerTerminalScripting;
import com.mpp.integratedscripting.part.PartTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.integrateddynamics.core.client.gui.ExtendedGuiHandler;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Temporary M5 acceptance harness. Enabled with {@code -Disc.guiSmoke=true} and
 * deleted after the milestone is verified (docs/MILESTONES.md M5 records the
 * run). It drives a real singleplayer world, places a scripting drive/cable/
 * terminal, opens the terminal through the same code path as a right click, and
 * then simulates mouse/keyboard input while asserting the container state.
 */
@SideOnly(Side.CLIENT)
public class GuiSmokeHarness {

    private static final String TAG = "[SMOKE]";
    private static final String WORLD_NAME = "isc-m5-smoke";
    private static final int GLOBAL_TIMEOUT_TICKS = 6000;

    private static final String SCRIPT_LINE_1 = "const foo = 42;";
    private static final String SCRIPT_LINE_2 = "function f(item) { return item.stackable(); }";
    private static final String SCRIPT = SCRIPT_LINE_1 + "\n" + SCRIPT_LINE_2;

    private int phase;
    private int phaseTicks;
    private int totalTicks;
    private int failures;
    private boolean finished;

    private volatile boolean setupDone;
    private BlockPos drivePos;
    private BlockPos cablePos;
    private int diskId = -1;

    public static boolean isEnabled() {
        return Boolean.getBoolean("isc.guiSmoke");
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
                scheduleSetup(mc);
                return;
            case 2:
                if (!this.setupDone) {
                    timeout(600, "server setup");
                    return;
                }
                nextPhase();
                log("server setup done; opening terminal GUI");
                scheduleOpenGui(mc);
                return;
            case 3:
                if (!(mc.currentScreen instanceof GuiTerminalScripting)) {
                    timeout(300, "terminal screen");
                    return;
                }
                // Let the client render the screen at least once, so screenshots
                // capture the GUI instead of the world.
                if (this.phaseTicks < 10) {
                    return;
                }
                nextPhase();
                log("terminal screen is open");
                drawSmoke(mc);
                return;
            case 4:
                if (container(mc).getAvailableDisks().isEmpty()) {
                    timeout(400, "disk list sync");
                    return;
                }
                this.diskId = container(mc).getAvailableDisks().getInt(0);
                log("disk list synced: " + container(mc).getAvailableDisks() + " (active "
                        + container(mc).getActiveDisk() + ")");
                check(container(mc).getActiveDisk() == this.diskId, "active disk is the first disk");
                // §11.2 item 14: an empty disk has no active script, so the editor is greyed out.
                check(container(mc).getActiveScript() == null, "empty disk has no active script");
                drawSmoke(mc);
                screenshot(mc, "m5-terminal-open.png");
                nextPhase();
                // create the first file by clicking the "+" button
                clickCreateFile(mc);
                return;
            case 5:
                if (activeScriptCount(mc) < 1) {
                    timeout(400, "first script creation");
                    return;
                }
                log("first script created: " + container(mc).getLastScripts());
                nextPhase();
                clickScriptRow(mc, 0);
                return;
            case 6:
                if (container(mc).getActiveScriptPath() == null) {
                    timeout(200, "script selection");
                    return;
                }
                log("script selected: " + container(mc).getActiveScriptPath());
                nextPhase();
                typeIntoEditor(mc);
                return;
            case 7:
                if (!SCRIPT.equals(container(mc).getActiveScript())) {
                    timeout(600, "typed script sync (client)");
                    return;
                }
                log("typed script reached the client container");
                nextPhase();
                return;
            case 8:
                if (this.phaseTicks == 20) {
                    drawSmoke(mc);
                    screenshot(mc, "m5-terminal-edited.png");
                }
                // Give the client -> server -> client round trip a few ticks, then
                // verify the server-side scripting data by creating a second file and
                // deleting it again (which forces a script diff round trip).
                if (this.phaseTicks < 40) {
                    return;
                }
                nextPhase();
                clickCreateFile(mc);
                return;
            case 9:
                if (activeScriptCount(mc) < 2) {
                    timeout(400, "second script creation");
                    return;
                }
                log("second script created; opening the removal dialog");
                nextPhase();
                clickScriptRow(mc, 1);
                return;
            case 10:
                if (this.phaseTicks < 10) {
                    return;
                }
                nextPhase();
                clickRemoveIcon(mc, 1);
                return;
            case 11:
                if (this.phaseTicks < 10) {
                    return;
                }
                nextPhase();
                drawSmoke(mc);
                screenshot(mc, "m5-terminal-dialog.png");
                log("removal dialog drawn; clicking 'Keep'");
                clickDialogCancel(mc);
                return;
            case 12:
                if (this.phaseTicks < 10) {
                    return;
                }
                check(activeScriptCount(mc) == 2, "cancel keeps the script (count=" + activeScriptCount(mc) + ")");
                nextPhase();
                clickRemoveIcon(mc, 1);
                return;
            case 13:
                if (this.phaseTicks < 10) {
                    return;
                }
                nextPhase();
                log("clicking 'Delete'");
                clickDialogConfirm(mc);
                return;
            case 14:
                if (activeScriptCount(mc) != 1) {
                    timeout(400, "script deletion");
                    return;
                }
                check(container(mc).getLastScripts().values().stream()
                        .flatMap(m -> m.keySet().stream())
                        .noneMatch(p -> p.toString().equals("script1.js")), "deleted script is gone");
                log("deletion confirmed; closing the GUI");
                nextPhase();
                mc.displayGuiScreen(null);
                return;
            case 15:
                if (this.phaseTicks < 60) {
                    return;
                }
                verifyPersistedScript();
                nextPhase();
                scheduleOpenDrive(mc);
                return;
            case 16:
                if (!(mc.currentScreen instanceof GuiScriptingDrive)) {
                    timeout(200, "drive screen");
                    return;
                }
                log("drive screen is open");
                check(((GuiScriptingDrive) mc.currentScreen).inventorySlots.getSlot(0).getHasStack(),
                        "drive GUI slot holds the scripting disk");
                drawSmoke(mc);
                nextPhase();
                return;
            case 17:
                if (this.phaseTicks == 10) {
                    drawSmoke(mc);
                    screenshot(mc, "m5-drive.png");
                }
                if (this.phaseTicks > 12) {
                    finish();
                }
                return;
            default:
                finish();
        }
    }

    private void scheduleSetup(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                World world = server.getWorld(0);
                BlockPos spawn = world.getSpawnPoint();
                BlockPos base = world.getTopSolidOrLiquidBlock(spawn);
                this.drivePos = base;
                this.cablePos = base.add(1, 0, 0);

                run(server, String.format("setblock %d %d %d minecraft:air",
                        base.getX(), base.getY(), base.getZ()));
                run(server, String.format("setblock %d %d %d minecraft:air",
                        this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ()));
                run(server, String.format("setblock %d %d %d integratedscriptingcallback:scripting_drive",
                        base.getX(), base.getY(), base.getZ()));
                run(server, String.format("setblock %d %d %d integrateddynamics:cable",
                        this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ()));
                run(server, String.format(
                        "replaceitem block %d %d %d slot.container.0 integratedscriptingcallback:scripting_disk",
                        base.getX(), base.getY(), base.getZ()));

                // Wait for the cable's network so the terminal part can be added.
                boolean added = PartHelpers.addPart(world, this.cablePos, EnumFacing.UP,
                        PartTypes.TERMINAL_SCRIPTING,
                        new net.minecraft.item.ItemStack(PartTypes.TERMINAL_SCRIPTING.getItem()));
                if (!added) {
                    failOnServer("could not add the terminal part");
                } else {
                    log("terminal part added at " + this.cablePos);
                }
                this.setupDone = true;
            } catch (Throwable t) {
                failOnServer("setup exception: " + t);
                t.printStackTrace();
                this.setupDone = true;
            }
        });
    }

    private void scheduleOpenGui(Minecraft mc) {
        // A real right click runs on both sides, and Cyclops keeps one temporary
        // data map per side. The client needs the part side before the open packet
        // arrives, the server before the container is constructed.
        PartTypes.TERMINAL_SCRIPTING.getModGui().getGuiHandler()
                .setTemporaryData(ExtendedGuiHandler.PART, EnumFacing.UP);

        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    failOnServer("no players on the server");
                    return;
                }
                EntityPlayerMP player = players.get(0);
                World world = server.getWorld(0);

                // Exactly what PartTypeBase#onPartActivated does for a right click.
                PartTypes.TERMINAL_SCRIPTING.getModGui().getGuiHandler()
                        .setTemporaryData(ExtendedGuiHandler.PART, EnumFacing.UP);
                player.openGui(PartTypes.TERMINAL_SCRIPTING.getModGui(),
                        PartTypes.TERMINAL_SCRIPTING.getGuiID(), world,
                        this.cablePos.getX(), this.cablePos.getY(), this.cablePos.getZ());
                log(players.size() + " player(s); terminal GUI open requested");
            } catch (Throwable t) {
                failOnServer("open exception: " + t);
                t.printStackTrace();
            }
        });
    }

    // ---------------------------------------------------------------- input simulation

    private void scheduleOpenDrive(Minecraft mc) {
        MinecraftServer server = mc.getIntegratedServer();
        server.addScheduledTask(() -> {
            try {
                List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
                if (players.isEmpty()) {
                    failOnServer("no players on the server");
                    return;
                }
                org.cyclops.cyclopscore.inventory.IGuiContainerProvider provider =
                        (org.cyclops.cyclopscore.inventory.IGuiContainerProvider)
                                com.mpp.RegistryEntries.blockScriptingDrive();
                players.get(0).openGui(provider.getModGui(), provider.getGuiID(),
                        server.getWorld(0), this.drivePos.getX(), this.drivePos.getY(), this.drivePos.getZ());
                log("drive GUI open requested");
            } catch (Throwable t) {
                failOnServer("drive open exception: " + t);
                t.printStackTrace();
            }
        });
    }

    private void clickCreateFile(Minecraft mc) throws java.io.IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        screen.mouseClicked(screen.getGuiLeftTotal() + GuiTerminalScripting.PATHS_X + 5,
                screen.getGuiTopTotal() + 226, 0);
    }

    private void clickScriptRow(Minecraft mc, int row) throws java.io.IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        screen.mouseClicked(screen.getGuiLeftTotal() + GuiTerminalScripting.PATHS_X + 20,
                screen.getGuiTopTotal() + GuiTerminalScripting.PATHS_Y + row * GuiTerminalScripting.PATHS_ROW_HEIGHT + 4, 0);
    }

    private void clickRemoveIcon(Minecraft mc, int row) throws java.io.IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        screen.mouseClicked(screen.getGuiLeftTotal() + GuiTerminalScripting.PATHS_X
                        + GuiTerminalScripting.PATHS_WIDTH - 3,
                screen.getGuiTopTotal() + GuiTerminalScripting.PATHS_Y + row * GuiTerminalScripting.PATHS_ROW_HEIGHT + 4, 0);
    }

    private void clickDialogConfirm(Minecraft mc) throws java.io.IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        // Dialog x = guiLeft + 128 - 216/2 = guiLeft + 20, y = guiTop + 50, buttons at +51.
        screen.mouseClicked(screen.getGuiLeftTotal() + 80, screen.getGuiTopTotal() + 50 + 55, 0);
    }

    private void clickDialogCancel(Minecraft mc) throws java.io.IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        screen.mouseClicked(screen.getGuiLeftTotal() + 150, screen.getGuiTopTotal() + 50 + 55, 0);
    }

    private void typeIntoEditor(Minecraft mc) throws java.io.IOException {
        GuiTerminalScripting screen = (GuiTerminalScripting) mc.currentScreen;
        // Focus the editor.
        screen.mouseClicked(screen.getGuiLeftTotal() + 120, screen.getGuiTopTotal() + 40, 0);
        for (String line : new String[]{SCRIPT_LINE_1, SCRIPT_LINE_2}) {
            for (char c : line.toCharArray()) {
                screen.keyTyped(c, 0);
            }
            if (!line.equals(SCRIPT_LINE_2)) {
                screen.keyTyped('\r', 28);
            }
        }
        log("typed script into the editor");
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

    /**
     * Save the last rendered frame, so the milestone record can show the GUI.
     */
    private void screenshot(Minecraft mc, String name) {
        try {
            net.minecraft.util.ScreenShotHelper.saveScreenshot(mc.mcDataDir, name,
                    mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
            log("screenshot saved: screenshots/" + name);
        } catch (Throwable t) {
            fail("screenshot failed: " + t);
        }
    }

    // ---------------------------------------------------------------- checks

    private ContainerTerminalScripting container(Minecraft mc) {
        return ((GuiTerminalScripting) mc.currentScreen).getTerminalContainer();
    }

    private int activeScriptCount(Minecraft mc) {
        Map<java.nio.file.Path, String> scripts = container(mc).getLastScripts()
                .get(container(mc).getActiveDisk());
        return scripts == null ? 0 : scripts.size();
    }

    private void verifyPersistedScript() {
        if (this.diskId < 0) {
            fail("no disk id for the persistence check");
            return;
        }
        File dir = new File(Minecraft.getMinecraft().mcDataDir,
                "saves/" + WORLD_NAME + "/integratedscriptingcallback/scripting-disks/" + this.diskId);
        Path script = new File(dir, "script0.js").toPath();
        try {
            String content = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
            check(SCRIPT.equals(content), "script0.js persisted with the typed content: " + content.replace("\n", "\\n"));
        } catch (Exception e) {
            fail("could not read " + script + ": " + e);
        }
    }

    private void run(MinecraftServer server, String command) {
        log("command: " + command + " -> "
                + server.getCommandManager().executeCommand(server, command));
    }

    private void check(boolean condition, String message) {
        if (condition) {
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

    private void failOnServer(String message) {
        this.failures++;
        IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.ERROR, TAG + " FAIL (server): " + message);
    }

}
