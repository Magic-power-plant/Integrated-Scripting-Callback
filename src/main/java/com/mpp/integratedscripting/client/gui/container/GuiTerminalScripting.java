package com.mpp.integratedscripting.client.gui.container;

import com.google.common.collect.Maps;
import com.mpp.GeneralConfig;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.language.edit.IIdentifierSyntax;
import com.mpp.integratedscripting.api.language.service.ILanguageServices;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.client.gui.component.TerminalBackground;
import com.mpp.integratedscripting.client.gui.component.TerminalHeaderLayout;
import com.mpp.integratedscripting.client.gui.component.TerminalLayout;
import com.mpp.integratedscripting.client.gui.component.input.GuiWidgetDialog;
import com.mpp.integratedscripting.client.gui.component.input.GuiWidgetTextArea;
import com.mpp.integratedscripting.client.language.ClientLanguageIcons;
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
import net.minecraft.inventory.Slot;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The scripting terminal GUI.
 *
 * <p>1.12.2 port of the 1.20 {@code ContainerScreenTerminalScripting}
 * (docs/PORTING-1.12.2.md §7.9.2). The 1.12 {@code GuiMultipart} already
 * provides the background texture, the error component and the settings button,
 * so only the disk selector, the script file list, the text editor, the removal
 * dialog and the script sync have to be added here. The part name is drawn by
 * this screen instead of {@code GuiMultipart}, because the base class puts it at
 * (8, 6) where the disk selector lives (see {@link TerminalHeaderLayout}).</p>
 *
 * <p>The screen draws its own widgets in the background layer because 1.12 has
 * no renderable-widget system; the foreground layer is translated to
 * {@code guiLeft/guiTop} by {@code GuiContainer} and is therefore only used for
 * the part name, the disk label and the error tooltip.</p>
 *
 * <p>The background is resizable: {@link TerminalLayout} derives the GUI size
 * from the player's scaled screen, and {@link TerminalBackground} slices the
 * 256x240 texture so that the frame, header boxes and slot cells keep their
 * pixel size while the editor and file list grow. The client side container
 * slots are re-anchored to the expanded, bottom-anchored band in
 * {@link #initGui()}; click routing uses slot indices, so the server side
 * coordinates are unaffected. At 256x240 the screen is unchanged.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public class GuiTerminalScripting
        extends GuiMultipart<PartTypeTerminalScripting, PartStateEmpty<PartTypeTerminalScripting>> {

    public static final int PATHS_X = TerminalLayout.LIST_X;
    public static final int PATHS_Y = TerminalLayout.LIST_Y;
    public static final int PATHS_WIDTH = TerminalLayout.LIST_WIDTH;
    /** Legacy file list height of the 256x240 layout; see {@link TerminalLayout#getListHeight()}. */
    public static final int PATHS_HEIGHT = 214;
    public static final int PATHS_ROW_HEIGHT = TerminalLayout.LIST_ROW_HEIGHT;
    /** Legacy row count of the 256x240 layout; see {@link TerminalLayout#getVisibleRows()}. */
    public static final int PATHS_MAX_ROWS = PATHS_HEIGHT / PATHS_ROW_HEIGHT;
    /** Legacy editor constants of the 256x240 layout; see {@link TerminalLayout}. */
    public static final int SCRIPT_X = 80;
    public static final int SCRIPT_Y = 18;
    public static final int SCRIPT_WIDTH = 168;
    public static final int SCRIPT_HEIGHT = 115;
    private static final int BUTTON_CREATE_FILE = 0;
    /** Width of the file icon area inside one file row. */
    private static final int ICON_SIZE = 7;

    private GuiArrowedListField<Integer> fieldDisk;
    private GuiScrollBar scrollBar;
    private GuiWidgetTextArea textArea;
    private GuiButtonText buttonCreateFile;
    @Nullable
    private GuiWidgetDialog pendingScriptRemovalDialog;

    /** Current GUI geometry; never null, so the constructors keep the base size. */
    private TerminalLayout layout = new TerminalLayout(TerminalLayout.BASE_WIDTH, TerminalLayout.BASE_HEIGHT);
    /**
     * Slot positions as constructed by the container, captured once per screen.
     * The layout is applied as an offset from these, so repeated {@code initGui}
     * calls (window resizes) never accumulate a shift.
     */
    @Nullable
    private List<int[]> baseSlotPositions;

    private int firstRow;
    private int lastClientSyncTick;
    private List<Integer> disksShown = Collections.emptyList();

    private final Map<Path, Integer> lastScriptCursorPos = Maps.newHashMap();
    private final Map<Path, Integer> lastScriptSelectionPos = Maps.newHashMap();
    private final Int2ObjectMap<Path> lastDiskScript = new Int2ObjectAVLTreeMap<>();
    /** Resolved once per selected file; the drawing path never looks a language up. */
    private ILanguageServices services = LanguageHandlers.getServices(null);
    /** File-type icons by path, because the icon depends only on the extension. */
    private final Map<Path, IImage> scriptIcons = Maps.newHashMap();

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
        // GuiContainerExtended#initGui reads this before it centers the screen;
        // the layout for the current scaled window is chosen in initGui().
        return this.layout == null ? TerminalLayout.BASE_WIDTH : this.layout.getWidth();
    }

    @Override
    protected int getBaseYSize() {
        return this.layout == null ? TerminalLayout.BASE_HEIGHT : this.layout.getHeight();
    }

    public ContainerTerminalScripting getTerminalContainer() {
        return (ContainerTerminalScripting) this.container;
    }

    public TerminalLayout getTerminalLayout() {
        return this.layout;
    }

    /**
     * @return Top of the create-file button, relative to the GUI. The development
     *         harnesses use it so their simulated clicks follow the resized layout.
     */
    public int getCreateFileButtonY() {
        return this.layout.getCreateButtonY();
    }

    /** @return Left edge of the removal dialog, relative to the GUI. */
    public int getDialogLeft() {
        return (this.layout.getWidth() - GuiWidgetDialog.WIDTH) / 2;
    }

    /** @return Top edge of the removal dialog, relative to the GUI. */
    public int getDialogTop() {
        return (this.layout.getHeight() - GuiWidgetDialog.HEIGHT) / 2;
    }

    /**
     * @return The script editor widget; used by the GUI acceptance harness to
     *         inspect completion state without reaching into private fields.
     */
    public GuiWidgetTextArea getTextArea() {
        return this.textArea;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void initGui() {
        // Pick the expanded size before the base class centers the screen. The
        // screen's scaled size already accounts for the player's GUI scale.
        this.layout = TerminalLayout.forScreen(this.width, this.height);
        super.initGui();

        // The client-side slot objects are only used for drawing and hit testing.
        // Moving them keeps the slots on the stretched bottom band; clicks are
        // routed by slot index, so the server container keeps its own coordinates.
        applySlotLayout();
        positionSettingsButton();

        this.fieldDisk = createDiskField();
        this.disksShown = getTerminalContainer().getAvailableDiskList();

        this.scrollBar = new GuiScrollBar(getGuiLeftTotal() + TerminalLayout.SCROLL_BAR_X,
                getGuiTopTotal() + PATHS_Y, this.layout.getScrollBarHeight(),
                this::setFirstRow, this.layout.getVisibleRows()) {
            @Override
            public int getTotalRows() {
                Map<Path, String> scripts = getActiveScripts();
                return scripts == null ? 0 : scripts.size();
            }
        };

        int editorX = getGuiLeftTotal() + this.layout.getEditorX();
        int editorY = getGuiTopTotal() + this.layout.getEditorY();
        int editorWidth = this.layout.getEditorWidth();
        int editorHeight = this.layout.getEditorHeight();
        if (this.textArea == null) {
            // First open: the editor owns the document, the listeners and the
            // undo history.
            this.textArea = new GuiWidgetTextArea(this.fontRenderer, editorX, editorY,
                    editorWidth, editorHeight, true, true);
            this.textArea.setListener(this::onActiveScriptModified);
            this.textArea.setListenerSelection(this::onSelectionModified);
            this.textArea.setListenerCursor(this::onSelectionModified);
            // The language services are resolved once for the selected file
            // (docs/LANGUAGE-API-REFACTOR.md §4.5); highlighting, editing rules,
            // identifiers and completion all come from that bundle.
            this.services = LanguageHandlers.getServices(getTerminalContainer().getActiveScriptPath());
            this.textArea.setLanguageServices(this.services);
            this.textArea.setValue(getActiveScriptOrEmpty());
        } else {
            // Window resize: keep the document, caret, selection, undo history and
            // scroll row; only the bounds and the wrapped layout change.
            this.textArea.setBounds(editorX, editorY, editorWidth, editorHeight);
        }

        this.buttonCreateFile = new GuiButtonText(BUTTON_CREATE_FILE,
                getGuiLeftTotal() + PATHS_X, getGuiTopTotal() + this.layout.getCreateButtonY(),
                PATHS_WIDTH, 10,
                L10NHelpers.localize("gui.integratedscripting.create_file"), true);
        this.buttonList.add(this.buttonCreateFile);

        this.pendingScriptRemovalDialog = null;
        clampFirstRow();
        syncScrollBarToFirstRow();
    }

    /**
     * Re-anchor the client-side slot widgets on the expanded bottom band.
     *
     * <p>All terminal and player slots sit in the bottom band, so every slot moves
     * by the same vertical offset. Horizontally the whole cluster moves by half of
     * the extra width, which centers it under the editor. The original positions
     * are captured once and the layout is always applied as an offset from them,
     * so resizing the window does not accumulate a shift.</p>
     */
    private void applySlotLayout() {
        if (this.inventorySlots == null) {
            return;
        }
        List<Slot> slots = this.inventorySlots.inventorySlots;
        if (this.baseSlotPositions == null || this.baseSlotPositions.size() != slots.size()) {
            this.baseSlotPositions = new ArrayList<>(slots.size());
            for (Slot slot : slots) {
                this.baseSlotPositions.add(new int[]{slot.xPos, slot.yPos});
            }
        }
        int shiftX = this.layout.getContentShiftX();
        int shiftY = this.layout.getExtraHeight();
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            int[] base = this.baseSlotPositions.get(i);
            slot.xPos = base[0] + shiftX;
            slot.yPos = base[1] + shiftY;
        }
    }

    /** Keep the settings button of {@code GuiMultipart} in the top-right corner. */
    private void positionSettingsButton() {
        int buttonX = getGuiLeftTotal() + this.layout.getWidth() - 82;
        int buttonY = getGuiTopTotal() + 4;
        for (GuiButton button : this.buttonList) {
            if (button.id == GuiMultipart.BUTTON_SETTINGS) {
                button.x = buttonX;
                button.y = buttonY;
            }
        }
    }

    private void clampFirstRow() {
        Map<Path, String> scripts = getActiveScripts();
        int total = scripts == null ? 0 : scripts.size();
        this.firstRow = Math.max(0, Math.min(this.firstRow,
                Math.max(0, total - this.layout.getVisibleRows())));
    }

    /** Restore the scroll bar handle after a resize kept a scrolled list. */
    private void syncScrollBarToFirstRow() {
        if (this.firstRow <= 0 || this.scrollBar == null) {
            return;
        }
        Map<Path, String> scripts = getActiveScripts();
        int total = scripts == null ? 0 : scripts.size();
        // GuiScrollBar#getScrollStep is total - visible + 1, and scrollTo maps the
        // scroll fraction back with the same step count.
        int steps = total - this.layout.getVisibleRows() + 1;
        if (steps > 0) {
            this.scrollBar.scrollTo(Math.min(1F, (float) this.firstRow / (float) steps));
        }
    }

    private GuiArrowedListField<Integer> createDiskField() {
        List<Integer> disks = getTerminalContainer().getAvailableDiskList();
        GuiArrowedListField<Integer> field = new GuiArrowedListField<>(0, this.fontRenderer,
                getGuiLeftTotal() + TerminalHeaderLayout.DISK_FIELD_X,
                getGuiTopTotal() + TerminalHeaderLayout.DISK_FIELD_Y,
                TerminalHeaderLayout.DISK_FIELD_WIDTH, TerminalHeaderLayout.DISK_FIELD_HEIGHT,
                true, true, disks);
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
            // Keep the caret where it was: an external reload must not jump the
            // player to the top of the file (docs/AUDIT.md R3).
            this.textArea.setValuePassive(script);
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
                int to = Math.min(paths.size(), from + this.layout.getVisibleRows());
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
        // A click anywhere dismisses the completion popup before it is routed,
        // except a click on the popup itself, which picks that row.
        if (!this.textArea.isCompletionPopupAt(mouseX, mouseY)) {
            this.textArea.dismissCompletion();
        }
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
                getGuiLeftTotal() + getDialogLeft(), getGuiTopTotal() + getDialogTop(),
                this.width, this.height,
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
        this.services = LanguageHandlers.getServices(getTerminalContainer().getActiveScriptPath());
        this.textArea.setLanguageServices(this.services);
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
            // If nothing was selected, derive the member around the current cursor
            // position. The language decides what an identifier is, so JavaScript
            // identifiers such as `$value` or Unicode names work too.
            int cursorPos = this.textArea.getCursorPos();
            if (cursorPos >= 0) {
                IIdentifierSyntax syntax = this.services.get(LanguageServiceKeys.EDIT)
                        .getIdentifierSyntax();
                selected = syntax.memberAt(this.textArea.getDocument(), cursorPos);
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
        // GuiMultipart#func_146976_a blits the background *and* the part name at
        // (8, 6), which collides with the disk selector of this screen. Only the
        // sliced background is drawn here; the part name is placed at
        // TerminalHeaderLayout.TITLE_X/Y in the foreground layer instead.
        TerminalBackground.draw(this, this.texture, this.layout,
                getGuiLeftTotal(), getGuiTopTotal());

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
                    getGuiTopTotal() + PATHS_Y + this.layout.getListHeight(), 0x64282828);
        }

        this.textArea.draw(mouseX, mouseY);
        if (container.getActiveScript() == null) {
            // Gray-out the editor when no script is selected.
            drawRect(getGuiLeftTotal() + this.layout.getEditorX(),
                    getGuiTopTotal() + this.layout.getEditorY(),
                    getGuiLeftTotal() + this.layout.getEditorX() + this.layout.getEditorWidth(),
                    getGuiTopTotal() + this.layout.getEditorY() + this.layout.getEditorHeight(),
                    0x64282828);
        }

        this.displayErrors.drawBackground(container.getReadErrors(),
                this.layout.getErrorX(), this.layout.getErrorY(),
                this.layout.getErrorX(), this.layout.getErrorY(),
                this, getGuiLeftTotal(), getGuiTopTotal(), container.canWriteScriptToVariable());

        if (this.pendingScriptRemovalDialog != null) {
            this.pendingScriptRemovalDialog.draw(mouseX, mouseY);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Still translated to guiLeft/guiTop by GuiContainer.
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);

        // Part name, in the wide header box above the editor (1.20 titleLabelX/Y).
        // Drawing it here instead of letting GuiMultipart draw it at (8, 6) keeps it
        // clear of the disk label and the disk selector below.
        this.fontRenderer.drawString(L10NHelpers.localize(getPartType().getTranslationKey()),
                TerminalHeaderLayout.TITLE_X, TerminalHeaderLayout.TITLE_Y,
                TerminalHeaderLayout.TEXT_COLOR, false);

        String diskLabel = L10NHelpers.localize("gui.integratedscripting.disk") + ":";
        this.fontRenderer.drawString(diskLabel,
                TerminalHeaderLayout.diskLabelX(this.fontRenderer.getStringWidth(diskLabel)),
                TerminalHeaderLayout.DISK_LABEL_Y, TerminalHeaderLayout.TEXT_COLOR, false);

        // Cursor position in the wide header box, like the status line of an IDE.
        // The compact "line:column" form stays clear of the localised part name.
        String position = getCursorLine() + ":" + getCursorColumn();
        int positionX = Math.max(TerminalHeaderLayout.TITLE_X,
                this.layout.getWidth() - 8 - this.fontRenderer.getStringWidth(position));
        this.fontRenderer.drawString(position, positionX, TerminalHeaderLayout.TITLE_Y,
                TerminalHeaderLayout.TEXT_COLOR, false);

        this.displayErrors.drawForeground(getTerminalContainer().getReadErrors(),
                this.layout.getErrorX(), this.layout.getErrorY(),
                mouseX, mouseY, this, getGuiLeftTotal(), getGuiTopTotal());
    }

    /** One-based logical line of the editor caret. */
    private int getCursorLine() {
        DocumentText document = this.textArea.getDocument();
        int cursor = Math.max(0, Math.min(document.getLength(), this.textArea.getCursorPos()));
        return document.getLineOfOffset(cursor) + 1;
    }

    /** One-based column of the editor caret within its line. */
    private int getCursorColumn() {
        DocumentText document = this.textArea.getDocument();
        int cursor = Math.max(0, Math.min(document.getLength(), this.textArea.getCursorPos()));
        return document.getColumnOfOffset(cursor) + 1;
    }

    /**
     * Resolve the icon of a script file, caching it per path: the icon only
     * depends on the file's language, which never changes for a path.
     */
    private IImage scriptIconFor(Path path) {
        IImage cached = this.scriptIcons.get(path);
        if (cached != null) {
            return cached;
        }
        ILanguageServices fileServices = LanguageHandlers.getServices(path);
        IImage icon = ClientLanguageIcons.getRegistry().getIcon(fileServices.getLanguageId());
        this.scriptIcons.put(path, icon);
        return icon;
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

            // File type icon, cached per path: the icon depends only on the file's
            // language, so the drawing path does no registry lookup after the first
            // frame of a file.
            IImage icon = scriptIconFor(path);
            GlStateManager.pushMatrix();
            GlStateManager.translate(
                    getGuiLeftTotal() + PATHS_X + 1, getGuiTopTotal() + PATHS_Y + i * PATHS_ROW_HEIGHT + 1, 0);
            GlStateManager.scale(0.5F, 0.5F, 1F);
            icon.draw(this, 0, 0);
            GlStateManager.popMatrix();

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
                GlStateManager.pushMatrix();
                GlStateManager.translate(iconX, iconY, 0);
                GlStateManager.scale(0.4F, 0.4F, 1F);
                Images.ERROR.draw(this, 0, 0);
                GlStateManager.popMatrix();
            }

            i++;
        }
    }

    private static String truncate(String text, int length) {
        return text.length() <= length ? text : StringUtils.abbreviate(text, length);
    }

}
