package com.mpp.integratedscripting.client.gui.container;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.client.gui.component.input.GuiWidgetDialog;
import com.mpp.integratedscripting.client.gui.component.input.GuiWidgetTextArea;
import com.mpp.integratedscripting.client.gui.image.ScriptImages;
import com.mpp.integratedscripting.core.language.LanguageHandlers;
import com.mpp.integratedscripting.inventory.container.ContainerTerminalScripting;
import com.mpp.integratedscripting.network.packet.TerminalScriptingDeleteScriptPacket;
import com.mpp.integratedscripting.network.packet.TerminalScriptingModifiedScriptPacket;
import com.mpp.integratedscripting.part.PartTypeTerminalScripting;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.gui.component.GuiScrollBar;
import org.cyclops.cyclopscore.client.gui.component.button.GuiButtonText;
import org.cyclops.cyclopscore.client.gui.component.input.GuiArrowedListField;
import org.cyclops.cyclopscore.client.gui.image.IImage;
import org.cyclops.cyclopscore.client.gui.image.Images;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.helper.RenderHelpers;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.core.client.gui.container.GuiMultipart;
import org.cyclops.integrateddynamics.core.part.PartStateEmpty;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

/**
 * The scripting terminal GUI.
 *
 * <p>1.12.2 port of the 1.20 {@code ContainerScreenTerminalScripting}
 * (docs/PORTING-1.12.2.md §7.9.2). The 1.12 {@code GuiMultipart} already
 * provides the background texture, the part name and the error component, so
 * only the disk selector, the script file list, the text editor, the removal
 * dialog and the script sync have to be added here.</p>
 *
 * <p>The screen draws its own widgets in the background layer because 1.12 has
 * no renderable-widget system; the foreground layer is translated to
 * {@code guiLeft/guiTop} by {@code GuiContainer} and is therefore only used for
 * the disk label and the error tooltip.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class GuiTerminalScripting
        extends GuiMultipart<PartTypeTerminalScripting, PartStateEmpty<PartTypeTerminalScripting>> {

    public static final int PATHS_X = 19;
    public static final int PATHS_Y = 18;
    public static final int PATHS_WIDTH = 56;
    public static final int PATHS_HEIGHT = 214;
    public static final int PATHS_ROW_HEIGHT = 8;
    public static final int PATHS_MAX_ROWS = PATHS_HEIGHT / PATHS_ROW_HEIGHT;
    public static final int SCRIPT_X = 80;
    public static final int SCRIPT_Y = 18;
    public static final int SCRIPT_WIDTH = 168;
    public static final int SCRIPT_HEIGHT = 115;
    private static final int ERROR_X = 212;
    private static final int ERROR_Y = 139;
    private static final int BUTTON_CREATE_FILE = 0;
    /** Width of the file icon area inside one file row. */
    private static final int ICON_SIZE = 7;

    private GuiArrowedListField<Integer> fieldDisk;
    private GuiScrollBar scrollBar;
    private GuiWidgetTextArea textArea;
    private GuiButtonText buttonCreateFile;
    @Nullable
    private GuiWidgetDialog pendingScriptRemovalDialog;

    private int firstRow;
    private int lastClientSyncTick;
    private List<Integer> disksShown = Collections.emptyList();

    private final Map<Path, Integer> lastScriptCursorPos = Maps.newHashMap();
    private final Map<Path, Integer> lastScriptSelectionPos = Maps.newHashMap();
    private final Int2ObjectMap<Path> lastDiskScript = new Int2ObjectAVLTreeMap<>();

    public GuiTerminalScripting(ContainerTerminalScripting container) {
        super(container);
    }

    /**
     * Constructor used reflectively by Integrated Dynamics'
     * {@code ExtendedGuiHandler.PART}: it looks up
     * {@code (EntityPlayer, PartTarget, IPartContainer, <part type class>)}.
     */
    public GuiTerminalScripting(EntityPlayer player, PartTarget target,
                                IPartContainer partContainer, PartTypeTerminalScripting partType) {
        this(new ContainerTerminalScripting(player, target, partContainer, partType));
    }

    @Override
    protected String getNameId() {
        return "terminal_scripting";
    }

    @Override
    protected int getBaseXSize() {
        return 256;
    }

    @Override
    protected int getBaseYSize() {
        return 240;
    }

    public ContainerTerminalScripting getTerminalContainer() {
        return (ContainerTerminalScripting) this.container;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void initGui() {
        super.initGui();

        this.fieldDisk = createDiskField();
        this.disksShown = getTerminalContainer().getAvailableDiskList();

        this.scrollBar = new GuiScrollBar(getGuiLeftTotal() + 5, getGuiTopTotal() + PATHS_Y, 162,
                this::setFirstRow, PATHS_MAX_ROWS) {
            @Override
            public int getTotalRows() {
                Map<Path, String> scripts = getActiveScripts();
                return scripts == null ? 0 : scripts.size();
            }
        };

        this.textArea = new GuiWidgetTextArea(this.fontRenderer,
                getGuiLeftTotal() + SCRIPT_X + 1, getGuiTopTotal() + SCRIPT_Y + 1,
                SCRIPT_WIDTH, SCRIPT_HEIGHT, true, true);
        this.textArea.setListener(this::onActiveScriptModified);
        this.textArea.setListenerSelection(this::onSelectionModified);
        this.textArea.setListenerCursor(this::onSelectionModified);
        this.textArea.setMarkupProvider(line -> {
            Path path = getTerminalContainer().getActiveScriptPath();
            ILanguageHandler handler = path == null ? null : LanguageHandlers.REGISTRY.getProvider(path);
            if (handler == null) {
                List<Pair<TextFormatting, String>> segments = Lists.newArrayList();
                segments.add(Pair.of((TextFormatting) null, line));
                return segments;
            }
            return handler.markupLine(line);
        });
        this.textArea.setValue(getActiveScriptOrEmpty());

        this.buttonCreateFile = new GuiButtonText(BUTTON_CREATE_FILE,
                getGuiLeftTotal() + PATHS_X, getGuiTopTotal() + 222, PATHS_WIDTH, 10,
                L10NHelpers.localize("gui.integratedscripting.create_file"), true);
        this.buttonList.add(this.buttonCreateFile);

        this.pendingScriptRemovalDialog = null;
        this.firstRow = 0;
    }

    private GuiArrowedListField<Integer> createDiskField() {
        List<Integer> disks = getTerminalContainer().getAvailableDiskList();
        GuiArrowedListField<Integer> field = new GuiArrowedListField<>(0, this.fontRenderer,
                getGuiLeftTotal() + 30, getGuiTopTotal() + 4, 42, 15, true, true, disks);
        field.setMaxStringLength(5);
        field.setTextColor(0xFFFFFF);
        field.setCanLoseFocus(true);
        field.setVisible(!disks.isEmpty());
        if (!disks.isEmpty()) {
            int active = getTerminalContainer().getActiveDisk();
            int index = disks.indexOf(active);
            if (index >= 0 && index < disks.size()) {
                field.setActiveElement(index);
            }
        }
        return field;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();

        // The disk list arrives from the server on the first tick after opening.
        refreshDiskFieldIfChanged();
        // Select the first script of the active disk when nothing is selected yet.
        autoSelectFirstScript();
        // Pick up script contents that arrived from the server (or changed externally).
        syncEditorContent();

        this.textArea.tick();
        if (this.lastClientSyncTick == 0) {
            syncDirtyScripts();
        }
        this.lastClientSyncTick = (this.lastClientSyncTick + 1)
                % Math.max(1, GeneralConfig.terminalScriptingClientSyncTickInterval);
    }

    @Override
    public void onGuiClosed() {
        syncDirtyScripts();
        super.onGuiClosed();
    }

    private void refreshDiskFieldIfChanged() {
        List<Integer> disks = getTerminalContainer().getAvailableDiskList();
        if (disks.equals(this.disksShown)) {
            return;
        }
        String previousText = this.fieldDisk.getText();
        this.disksShown = disks;
        this.fieldDisk = createDiskField();
        if (!previousText.isEmpty()) {
            this.fieldDisk.setText(previousText);
        }
    }

    private void autoSelectFirstScript() {
        ContainerTerminalScripting container = getTerminalContainer();
        if (container.getActiveScriptPath() != null) {
            return;
        }
        Map<Path, String> scripts = getActiveScripts();
        if (scripts == null || scripts.isEmpty()) {
            return;
        }
        Path first = scripts.keySet().stream().sorted().findFirst().orElse(null);
        if (first != null) {
            container.setActiveScriptPath(first);
            onActiveScriptSelected();
        }
    }

    /**
     * Keep the editor in sync with the script that the server sent for the active
     * file, as long as the player is not typing in it.
     */
    private void syncEditorContent() {
        ContainerTerminalScripting container = getTerminalContainer();
        if (container.getActiveScriptPath() == null) {
            if (!this.textArea.getValue().isEmpty()) {
                this.textArea.setValue("");
            }
            return;
        }
        String script = container.getActiveScript();
        if (script != null && !this.textArea.isFocused() && !script.equals(this.textArea.getValue())) {
            this.textArea.setValue(script);
        }
    }

    // ---------------------------------------------------------------- script sync

    private void syncDirtyScripts() {
        ContainerTerminalScripting container = getTerminalContainer();
        for (Pair<Integer, Path> entry : container.getClientScriptsDirty()) {
            Map<Path, String> diskScripts = container.getLastScripts().get(entry.getLeft().intValue());
            if (diskScripts != null) {
                String script = diskScripts.get(entry.getRight());
                if (script != null) {
                    IntegratedScriptingCallback._instance.getPacketHandler().sendToServer(
                            new TerminalScriptingModifiedScriptPacket(entry.getLeft(),
                                    entry.getRight(), script));
                }
            }
        }
        container.getClientScriptsDirty().clear();
    }

    private void removeScript(Path path) {
        IntegratedScriptingCallback._instance.getPacketHandler().sendToServer(
                new TerminalScriptingDeleteScriptPacket(getTerminalContainer().getActiveDisk(), path));
    }

    // ---------------------------------------------------------------- layout helpers

    protected void setFirstRow(int firstRow) {
        this.firstRow = Math.max(0, firstRow);
    }

    @Nullable
    protected Map<Path, String> getActiveScripts() {
        return getTerminalContainer().getLastScripts().get(getTerminalContainer().getActiveDisk());
    }

    protected List<Path> getVisibleScriptPaths() {
        Map<Path, String> scripts = getActiveScripts();
        if (scripts != null) {
            List<Path> paths = scripts.keySet().stream().sorted().collect(Collectors.toList());
            if (!paths.isEmpty()) {
                int from = Math.min(this.firstRow, paths.size());
                int to = Math.min(paths.size(), from + PATHS_MAX_ROWS);
                return paths.subList(from, to);
            }
        }
        return Collections.emptyList();
    }

    public int getActiveDisk() {
        if (this.fieldDisk == null) {
            return getTerminalContainer().getActiveDisk();
        }
        // Prefer the text, so that typing a disk id also works.
        String text = this.fieldDisk.getText();
        if (text != null && !text.isEmpty()) {
            try {
                int parsed = Integer.parseInt(text);
                if (this.disksShown.contains(parsed)) {
                    return parsed;
                }
            } catch (NumberFormatException e) {
                // Fall through to the list element.
            }
        }
        try {
            Integer element = this.fieldDisk.getActiveElement();
            return element == null ? -1 : element;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    protected Path getHoveredScriptPath(int mouseX, int mouseY) {
        List<Path> paths = getVisibleScriptPaths();
        for (int i = 0; i < paths.size(); i++) {
            if (isPointInRegion(PATHS_X, PATHS_Y + i * PATHS_ROW_HEIGHT,
                    PATHS_WIDTH, PATHS_ROW_HEIGHT, mouseX, mouseY)) {
                return paths.get(i);
            }
        }
        return null;
    }

    private boolean isRemoveIconHovered(int mouseX) {
        int iconWidth = (int) (Images.ERROR.getWidth() * 0.4F);
        return mouseX >= getGuiLeftTotal() + PATHS_X + PATHS_WIDTH - iconWidth - 1;
    }

    // ---------------------------------------------------------------- input

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // A dialog consumes all input while it is open.
        if (this.pendingScriptRemovalDialog != null) {
            this.pendingScriptRemovalDialog.mouseClicked(mouseX, mouseY, mouseButton);
            return;
        }

        Path hoveredScriptPath = getHoveredScriptPath(mouseX, mouseY);
        if (hoveredScriptPath != null) {
            if (mouseButton == 0 && isRemoveIconHovered(mouseX)) {
                showRemovalDialog(hoveredScriptPath);
                return;
            }
            if (mouseButton == 0) {
                saveCursorPos();
                getTerminalContainer().setActiveScriptPath(hoveredScriptPath);
                onActiveScriptSelected();
                restoreCursorPos();
                playButtonClickSound();
                return;
            }
        }

        int previousDisk = getActiveDisk();
        if (!this.disksShown.isEmpty() && this.fieldDisk.mouseClicked(mouseX, mouseY, mouseButton)) {
            saveCursorPos();
            saveDiskScript(previousDisk);
            getTerminalContainer().setActiveDisk(getActiveDisk());
            this.scrollBar.scrollTo(0);
            restoreDiskScript();
            restoreCursorPos();
            playButtonClickSound();
            return;
        }

        this.textArea.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void showRemovalDialog(Path path) {
        playButtonClickSound();
        ContainerTerminalScripting container = getTerminalContainer();
        this.pendingScriptRemovalDialog = new GuiWidgetDialog(this.fontRenderer,
                getGuiLeftTotal() + getBaseXSize() / 2 - GuiWidgetDialog.WIDTH / 2,
                getGuiTopTotal() + 50, this.width, this.height,
                L10NHelpers.localize("gui.integratedscripting.removal_dialog.title"),
                L10NHelpers.localize("gui.integratedscripting.removal_dialog.message", path.toString()),
                L10NHelpers.localize("gui.integratedscripting.removal_dialog.delete"),
                L10NHelpers.localize("gui.integratedscripting.removal_dialog.keep"),
                () -> {
                    this.pendingScriptRemovalDialog = null;
                    removeScript(path);
                    if (path.equals(container.getActiveScriptPath())) {
                        container.setActiveScriptPath(null);
                        this.textArea.setValue("");
                    }
                },
                () -> this.pendingScriptRemovalDialog = null);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (this.pendingScriptRemovalDialog != null) {
            return;
        }
        this.textArea.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (this.pendingScriptRemovalDialog != null) {
            return;
        }
        double mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        double mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        if (this.textArea.isInside((int) mouseX, (int) mouseY)) {
            this.textArea.handleMouseInput();
        } else {
            this.scrollBar.handleMouseInput();
        }
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (this.pendingScriptRemovalDialog != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.pendingScriptRemovalDialog = null;
            }
            return;
        }
        if (this.textArea.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_CREATE_FILE) {
            getTerminalContainer().createNewFile();
            playButtonClickSound();
            return;
        }
        super.actionPerformed(button);
    }

    private void playButtonClickSound() {
        this.mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(
                SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---------------------------------------------------------------- script state

    private void onActiveScriptSelected() {
        this.textArea.setValue(getActiveScriptOrEmpty());
    }

    private String getActiveScriptOrEmpty() {
        String script = getTerminalContainer().getActiveScript();
        return script == null ? "" : script;
    }

    private void onActiveScriptModified() {
        getTerminalContainer().setActiveScript(this.textArea.getValue());
    }

    private void onSelectionModified() {
        String selected = this.textArea.getSelected();
        if (StringUtils.isEmpty(selected)) {
            // If nothing was selected, derive the member around the current cursor position.
            int cursorPos = this.textArea.getCursorPos();
            if (cursorPos >= 0) {
                String value = this.textArea.getValue();
                Matcher matcherToEnd = ContainerTerminalScripting.INVALID_MEMBER_NAME.matcher(value);
                int endPos = value.length();
                if (matcherToEnd.find(cursorPos)) {
                    endPos = matcherToEnd.end() - 1;
                }
                Matcher matcherToStart = ContainerTerminalScripting.INVALID_MEMBER_NAME
                        .matcher(new StringBuilder(value).reverse().toString());
                int startPos = 0;
                if (matcherToStart.find(value.length() - cursorPos)) {
                    startPos = value.length() - matcherToStart.end() + 1;
                }
                selected = value.substring(startPos, endPos);
            }
        }
        getTerminalContainer().setSelection(selected);
    }

    private void saveCursorPos() {
        Path currentScriptPath = getTerminalContainer().getActiveScriptPath();
        if (currentScriptPath != null) {
            this.lastScriptCursorPos.put(currentScriptPath, this.textArea.getCursorPos());
            this.lastScriptSelectionPos.put(currentScriptPath, this.textArea.getSelectionPos());
        }
    }

    private void restoreCursorPos() {
        Path currentScriptPath = getTerminalContainer().getActiveScriptPath();

        Integer scriptCursorPos = this.lastScriptCursorPos.get(currentScriptPath);
        if (scriptCursorPos != null) {
            this.textArea.setCursorPos(scriptCursorPos);
        } else {
            this.textArea.setCursorPos(0);
            if (this.textArea.isFocused()) {
                this.textArea.setFocused(false);
            }
        }

        Integer scriptSelectionPos = this.lastScriptSelectionPos.get(currentScriptPath);
        if (scriptSelectionPos != null) {
            this.textArea.setSelectionPos(scriptSelectionPos);
        } else {
            this.textArea.setSelectionPos(0);
        }
    }

    private void saveDiskScript(int disk) {
        Path currentScriptPath = getTerminalContainer().getActiveScriptPath();
        if (disk >= 0 && currentScriptPath != null) {
            this.lastDiskScript.put(disk, currentScriptPath);
        }
    }

    private void restoreDiskScript() {
        int disk = getActiveDisk();
        if (disk >= 0) {
            Path scriptPath = this.lastDiskScript.get(disk);
            getTerminalContainer().setActiveScriptPath(scriptPath);
            onActiveScriptSelected();
        }
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // GuiMultipart only declares the SRG-named variant; delegate to it (it draws
        // the background texture and the part name).
        super.func_146976_a(partialTicks, mouseX, mouseY);

        ContainerTerminalScripting container = getTerminalContainer();
        RenderHelpers.bindTexture(this.texture);

        if (this.fieldDisk.getVisible()) {
            this.fieldDisk.drawTextBox(this.mc, mouseX, mouseY);
        }
        this.scrollBar.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);

        if (!container.getAvailableDisks().isEmpty()) {
            renderScriptPaths(mouseX, mouseY);
        } else {
            // Gray-out the file list when the network exposes no disks.
            drawRect(getGuiLeftTotal() + PATHS_X, getGuiTopTotal() + PATHS_Y,
                    getGuiLeftTotal() + PATHS_X + PATHS_WIDTH,
                    getGuiTopTotal() + PATHS_Y + PATHS_HEIGHT, 0x64282828);
        }

        this.textArea.draw(mouseX, mouseY);
        if (container.getActiveScript() == null) {
            // Gray-out the editor when no script is selected.
            drawRect(getGuiLeftTotal() + SCRIPT_X + 1, getGuiTopTotal() + SCRIPT_Y + 1,
                    getGuiLeftTotal() + SCRIPT_X + 1 + SCRIPT_WIDTH,
                    getGuiTopTotal() + SCRIPT_Y + 1 + SCRIPT_HEIGHT, 0x64282828);
        }

        this.displayErrors.drawBackground(container.getReadErrors(), ERROR_X, ERROR_Y, ERROR_X, ERROR_Y,
                this, getGuiLeftTotal(), getGuiTopTotal(), container.canWriteScriptToVariable());

        if (this.pendingScriptRemovalDialog != null) {
            this.pendingScriptRemovalDialog.draw(mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Still translated to guiLeft/guiTop by GuiContainer.
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);

        this.fontRenderer.drawString(L10NHelpers.localize("gui.integratedscripting.disk") + ":", 8, 6, 0xFFFFFF);
        this.displayErrors.drawForeground(getTerminalContainer().getReadErrors(), ERROR_X, ERROR_Y,
                mouseX, mouseY, this, getGuiLeftTotal(), getGuiTopTotal());
    }

    protected void renderScriptPaths(int mouseX, int mouseY) {
        List<Path> paths = getVisibleScriptPaths();
        int i = 0;
        for (Path path : paths) {
            boolean hovering = isPointInRegion(PATHS_X, PATHS_Y + i * PATHS_ROW_HEIGHT,
                    PATHS_WIDTH, PATHS_ROW_HEIGHT, mouseX, mouseY);
            boolean active = path.equals(getTerminalContainer().getActiveScriptPath());

            // Highlight the active path.
            if (active) {
                drawRect(getGuiLeftTotal() + PATHS_X, getGuiTopTotal() + PATHS_Y + i * PATHS_ROW_HEIGHT,
                        getGuiLeftTotal() + PATHS_X + PATHS_WIDTH,
                        getGuiTopTotal() + PATHS_Y + (i + 1) * PATHS_ROW_HEIGHT, 0xFF6E82F0);
            }

            // File type icon.
            ILanguageHandler languageHandler = LanguageHandlers.REGISTRY.getProvider(path);
            IImage icon = languageHandler == null ? ScriptImages.FILE_OTHER : languageHandler.getIcon();
            net.minecraft.client.renderer.GlStateManager.pushMatrix();
            net.minecraft.client.renderer.GlStateManager.translate(
                    getGuiLeftTotal() + PATHS_X + 1, getGuiTopTotal() + PATHS_Y + i * PATHS_ROW_HEIGHT + 1, 0);
            net.minecraft.client.renderer.GlStateManager.scale(0.5F, 0.5F, 1F);
            icon.draw(this, 0, 0);
            net.minecraft.client.renderer.GlStateManager.popMatrix();

            // File name.
            RenderHelpers.drawScaledString(this.fontRenderer, truncate(path.toString(), 20),
                    getGuiLeftTotal() + PATHS_X + 1 + ICON_SIZE,
                    getGuiTopTotal() + PATHS_Y + i * PATHS_ROW_HEIGHT + 2, 0.5F,
                    hovering && !active ? 0x3232FA : 0x000000, false);

            // Removal button on hover.
            if (hovering) {
                int iconWidth = (int) (Images.ERROR.getWidth() * 0.4F);
                int iconX = getGuiLeftTotal() + PATHS_X + PATHS_WIDTH - iconWidth - 1;
                int iconY = getGuiTopTotal() + PATHS_Y + i * PATHS_ROW_HEIGHT + 1;
                net.minecraft.client.renderer.GlStateManager.pushMatrix();
                net.minecraft.client.renderer.GlStateManager.translate(iconX, iconY, 0);
                net.minecraft.client.renderer.GlStateManager.scale(0.4F, 0.4F, 1F);
                Images.ERROR.draw(this, 0, 0);
                net.minecraft.client.renderer.GlStateManager.popMatrix();
            }

            i++;
        }
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : StringUtils.abbreviate(text, length);
    }

}
