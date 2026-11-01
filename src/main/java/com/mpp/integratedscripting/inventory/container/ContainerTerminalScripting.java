package com.mpp.integratedscripting.inventory.container;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.cyclopscore.helper.ValueNotifierHelpers;
import org.cyclops.cyclopscore.inventory.SimpleInventory;
import org.cyclops.cyclopscore.persist.IDirtyMarkListener;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integrateddynamics.core.inventory.container.ContainerMultipart;
import org.cyclops.integrateddynamics.core.inventory.container.slot.SlotVariable;
import org.cyclops.integrateddynamics.core.part.PartStateEmpty;
import com.mpp.IntegratedScriptingCallback;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.api.language.edit.IIdentifierSyntax;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.core.evaluate.ScriptVariableFacadeHandler;
import com.mpp.integratedscripting.core.item.ScriptVariableFacade;
import com.mpp.integratedscripting.core.language.LanguageHandlers;
import com.mpp.integratedscripting.core.network.DiskListCodec;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;
import com.mpp.integratedscripting.network.packet.TerminalScriptingCreateNewScriptPacket;
import com.mpp.integratedscripting.network.packet.TerminalScriptingDeleteScriptPacket;
import com.mpp.integratedscripting.network.packet.TerminalScriptingModifiedScriptPacket;
import com.mpp.integratedscripting.part.PartTypeTerminalScripting;
import com.mpp.integratedscripting.part.PartTypes;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Container for the scripting terminal GUI.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation
 * (docs/PORTING-1.12.2.md §6.21 / §7.8.2):</p>
 * <ul>
 *   <li>The container extends Integrated Dynamics' {@code ContainerMultipart},
 *       whose constructor is
 *       {@code (EntityPlayer, PartTarget, IPartContainer, PartType)}, matching
 *       the reflective {@code ExtendedGuiHandler.PART} constructor.</li>
 *   <li>{@code SimpleInventory} only has the
 *       {@code (int size, String name, int stackSize)} constructor, and its
 *       inventory methods are only visible through {@link IInventory}.</li>
 *   <li>1.12 has no {@code InitData} payload in the open-container packet, and
 *       the client cannot compute the disk list (the scripting data only exists
 *       on the server). The list is therefore pushed through a value notifier on
 *       the first server tick after the container was opened, see
 *       {@link #detectAndSendChanges()}.</li>
 *   <li>{@code DisplayErrorsComponent} takes
 *       {@link L10NHelpers.UnlocalizedString} entries, so {@link #getReadErrors}
 *       returns those instead of components.</li>
 * </ul>
 *
 * @author rubensworks
 */
public class ContainerTerminalScripting
        extends ContainerMultipart<PartTypeTerminalScripting, PartStateEmpty<PartTypeTerminalScripting>>
        implements IDirtyMarkListener {

    private final SimpleInventory inventory;
    private final IScriptingNetwork scriptingNetwork;
    private final int activeDiskId;
    private final int availableDisksId;
    private final int activeScriptPathId;
    private final int selectionId;
    private final Set<Pair<Integer, Path>> clientScriptsDirty = Sets.newHashSet();
    private final Int2ObjectMap<Map<Path, String>> lastScripts = new Int2ObjectAVLTreeMap<>();

    private IntList availableDisks;
    private boolean availableDisksSynced;

    /** The active file's identifier rule, cached because it is read while drawing. */
    @Nullable
    private Path cachedIdentifierSyntaxPath;
    @Nullable
    private IIdentifierSyntax cachedIdentifierSyntax;

    public ContainerTerminalScripting(EntityPlayer player, PartTarget target,
                                      IPartContainer partContainer, PartTypeTerminalScripting partType) {
        super(player, target, partContainer, partType);

        this.inventory = new SimpleInventory(1, "terminal_scripting", 1);
        this.inventory.addDirtyMarkListener(this);
        addInventory(this.inventory, 0, 232, 137, 1, 1);
        addPlayerInventory(player.inventory, 88, 158);

        this.scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(
                NetworkHelpers.getNetwork(getTarget().getCenter()));

        this.availableDisks = new IntArrayList();
        if (this.scriptingNetwork != null) {
            this.scriptingNetwork.getDisks().stream().sorted()
                    .forEach(disk -> this.availableDisks.add(disk.intValue()));
        }

        // The value ids must be allocated in the same order on both sides.
        this.activeDiskId = getNextValueId();
        this.availableDisksId = getNextValueId();
        this.activeScriptPathId = getNextValueId();
        this.selectionId = getNextValueId();
        setActiveDisk(this.availableDisks.isEmpty() ? -1 : this.availableDisks.getInt(0));
    }

    @Override
    protected int getSizeInventory() {
        return 1;
    }

    @Override
    protected Slot createNewSlot(IInventory inventory, int index, int x, int y) {
        if (inventory instanceof SimpleInventory) {
            return new SlotVariable(inventory, index, x, y);
        }
        return super.createNewSlot(inventory, index, x, y);
    }

    /**
     * 1.12.2: {@code ContainerMultipart} only declares the SRG-named variant of this
     * method, so the MCP-named abstract method has to be implemented explicitly. It
     * delegates to the same helper the base class uses.
     */
    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return PartHelpers.canInteractWith(getTarget(), playerIn, getPartContainer());
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        if (!getWorld().isRemote) {
            // SimpleInventory's inventory methods are only visible through IInventory (see M2 notes).
            ItemStack itemStack = ((IInventory) this.inventory).getStackInSlot(0);
            if (!itemStack.isEmpty()) {
                ((IInventory) this.inventory).setInventorySlotContents(0, ItemStack.EMPTY);
                player.dropItem(itemStack, false);
            }
        }
    }

    /**
     * Push the disk list to the client and script changes to/from the serverside
     * data.
     *
     * <p>1.12.2: this is the equivalent of the reference implementation's
     * {@code broadcastChanges}. Value ids allocated in the constructor cannot be
     * sent while the container is being created (the client GUI does not exist
     * yet), so the disk list is pushed on the first tick instead.</p>
     */
    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (getWorld().isRemote) {
            return;
        }

        if (!this.availableDisksSynced) {
            this.availableDisksSynced = true;
            ValueNotifierHelpers.setValue(this, this.availableDisksId, DiskListCodec.encode(this.availableDisks));
        }

        if (this.scriptingNetwork == null || !(getPlayer() instanceof EntityPlayerMP)) {
            return;
        }
        IScriptingData scriptingData = ScriptingNetworkHelpers.getScriptingData();
        if (scriptingData == null) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) getPlayer();
        for (int i = 0; i < this.availableDisks.size(); i++) {
            int disk = this.availableDisks.getInt(i);
            Map<Path, String> scriptsNew = scriptingData.getScripts(disk);
            Map<Path, String> scriptsOld = this.lastScripts.get(disk);

            // Determine files (union of new and old)
            Set<Path> files;
            if (scriptsOld == null) {
                scriptsOld = Maps.newHashMap();
                this.lastScripts.put(disk, scriptsOld);
                files = Sets.newHashSet(scriptsNew.keySet());
            } else {
                files = Sets.newHashSet(scriptsNew.keySet());
                files.addAll(scriptsOld.keySet());
            }

            // Determine and send changed files
            for (Path file : files) {
                String scriptNew = scriptsNew.get(file);
                String scriptOld = scriptsOld.get(file);
                if (!Objects.equals(scriptNew, scriptOld)) {
                    if (scriptNew != null) {
                        IntegratedScriptingCallback._instance.getPacketHandler().sendToPlayer(
                                new TerminalScriptingModifiedScriptPacket(disk, file, scriptNew), player);
                        scriptsOld.put(file, scriptNew);
                    } else {
                        IntegratedScriptingCallback._instance.getPacketHandler().sendToPlayer(
                                new TerminalScriptingDeleteScriptPacket(disk, file), player);
                        scriptsOld.remove(file);
                    }

                    // Cleanup if we have no scripts anymore for a certain disk
                    if (scriptsOld.isEmpty()) {
                        this.lastScripts.remove(disk);
                    }
                }
            }
        }
    }

    @Nullable
    public IScriptingNetwork getScriptingNetwork() {
        return scriptingNetwork;
    }

    public IntList getAvailableDisks() {
        return availableDisks;
    }

    /**
     * The available disk ids as a boxed list, for the GUI's disk selector.
     */
    public List<Integer> getAvailableDiskList() {
        List<Integer> disks = Lists.newArrayList();
        for (int i = 0; i < this.availableDisks.size(); i++) {
            disks.add(this.availableDisks.getInt(i));
        }
        return disks;
    }

    public int getActiveDisk() {
        return ValueNotifierHelpers.getValueInt(this, activeDiskId);
    }

    public void setActiveDisk(int activeDisk) {
        ValueNotifierHelpers.setValue(this, activeDiskId, activeDisk);
    }

    public Set<Pair<Integer, Path>> getClientScriptsDirty() {
        return clientScriptsDirty;
    }

    public Int2ObjectMap<Map<Path, String>> getLastScripts() {
        return lastScripts;
    }

    public void setLastScript(int disk, Path path, @Nullable String script) {
        Map<Path, String> lastScriptsDisk = lastScripts.get(disk);
        if (lastScriptsDisk == null) {
            lastScriptsDisk = Maps.newHashMap();
            lastScripts.put(disk, lastScriptsDisk);
        }

        // Store or delete script
        if (script != null) {
            lastScriptsDisk.put(path, script);
        } else {
            lastScriptsDisk.remove(path);
        }

        // Cleanup if we have no scripts anymore for a certain disk
        if (lastScriptsDisk.isEmpty()) {
            lastScripts.remove(disk);
        }
    }

    /**
     * @param disk A disk id supplied by a client packet.
     * @return If this terminal may currently write to that disk.
     *
     * <p>1.12.2 port fix (docs/AUDIT.md D2): the disk id arrives from the client,
     * so it is checked against the terminal's own scripting network instead of
     * being trusted. Without this, any client could create or rewrite script
     * directories on any disk id of the world.</p>
     */
    public boolean isDiskAvailable(int disk) {
        if (disk < 0) {
            return false;
        }
        if (this.scriptingNetwork != null && this.scriptingNetwork.getDisks().contains(disk)) {
            return true;
        }
        // Fall back to the list that was synced when the terminal was opened, so a
        // disk that was just removed from the network can still be cleaned up.
        return this.availableDisks.contains(disk);
    }

    public void setServerScript(int disk, Path path, @Nullable String script) {
        IScriptingData scriptingData = ScriptingNetworkHelpers.getScriptingData();
        if (scriptingData == null) {
            return;
        }
        if (!isDiskAvailable(disk) || !scriptingData.isScriptPathSafe(disk, path)) {
            IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.WARN,
                    "Ignoring a script write for disk " + disk + " path " + path
                            + ": the disk is not exposed by this terminal, or the path is not safe.");
            return;
        }
        scriptingData.setScript(disk, path, script, IScriptingData.ChangeLocation.MEMORY);
    }

    public void createNewServerScript(int disk) {
        IScriptingData scriptingData = ScriptingNetworkHelpers.getScriptingData();
        if (scriptingData == null) {
            return;
        }
        if (!isDiskAvailable(disk)) {
            IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.WARN,
                    "Ignoring a create-file request for disk " + disk
                            + ": the disk is not exposed by this terminal.");
            return;
        }

        // Determine unique script name. The extension comes from the default
        // language's edit support instead of being hard-coded to `.js`.
        String extension = LanguageHandlers.getDefaultFileExtension();
        Set<Path> existingScripts = scriptingData.getScripts(disk).keySet();
        Path path;
        int i = 0;
        do {
            path = Paths.get("script" + i++ + "." + extension);
        } while (existingScripts.contains(path));

        // Create empty script
        scriptingData.setScript(disk, path, "", IScriptingData.ChangeLocation.MEMORY);
    }

    /**
     * Ask the server to create a new empty script file on the active disk.
     */
    public void createNewFile() {
        int disk = getActiveDisk();
        if (disk >= 0) {
            IntegratedScriptingCallback._instance.getPacketHandler()
                    .sendToServer(new TerminalScriptingCreateNewScriptPacket(disk));
        }
    }

    @Nullable
    public Path getActiveScriptPath() {
        String str = ValueNotifierHelpers.getValueString(this, activeScriptPathId);
        return str == null || str.isEmpty() ? null : Paths.get(str);
    }

    public void setActiveScriptPath(@Nullable Path activeScriptPath) {
        ValueNotifierHelpers.setValue(this, activeScriptPathId,
                activeScriptPath == null ? "" : activeScriptPath.toString());
    }

    public String getSelectedMember() {
        String str = ValueNotifierHelpers.getValueString(this, selectionId);

        if (str != null) {
            // If "func(abc, ...)" is selected, splice of everything after "("
            int openBracketsPos = str.indexOf("(");
            if (openBracketsPos >= 0) {
                str = str.substring(0, openBracketsPos);
            }
        }

        return str == null ? "" : str;
    }

    public void setSelection(String selection) {
        ValueNotifierHelpers.setValue(this, selectionId, selection);
    }

    public boolean isMemberSelected() {
        // What counts as a valid member name is language data now: JavaScript for
        // example accepts `$` and Unicode identifiers, which the old
        // `[0-9a-zA-Z_]+` pattern rejected (docs/LANGUAGE-API-REFACTOR.md §1).
        // The rule is cached per active file: this is called on the render path.
        return identifierSyntax().isValidMemberName(getSelectedMember());
    }

    /**
     * @return The identifier rule of the active script's language, resolved once
     *         per selected file.
     */
    private IIdentifierSyntax identifierSyntax() {
        Path path = getActiveScriptPath();
        if (this.cachedIdentifierSyntax == null
                || !Objects.equals(this.cachedIdentifierSyntaxPath, path)) {
            this.cachedIdentifierSyntaxPath = path;
            this.cachedIdentifierSyntax = LanguageHandlers.getServices(path)
                    .get(LanguageServiceKeys.EDIT)
                    .getIdentifierSyntax();
        }
        return this.cachedIdentifierSyntax;
    }

    @Nullable
    public String getActiveScript() {
        Path path = getActiveScriptPath();
        int disk = getActiveDisk();
        if (path != null && disk >= 0) {
            Map<Path, String> diskScripts = lastScripts.get(disk);
            if (diskScripts != null) {
                return diskScripts.get(path);
            }
        }
        return null;
    }

    public void setActiveScript(String scriptNew) {
        Path path = getActiveScriptPath();
        int disk = getActiveDisk();
        if (path != null && disk >= 0) {
            Map<Path, String> diskScripts = lastScripts.get(disk);
            if (diskScripts != null) {
                String scriptOld = diskScripts.put(path, scriptNew);
                if (!Objects.equals(scriptOld, scriptNew)) {
                    this.clientScriptsDirty.add(Pair.of(disk, path));
                }
            }
        }
    }

    public List<L10NHelpers.UnlocalizedString> getReadErrors() {
        List<L10NHelpers.UnlocalizedString> list = Lists.newArrayList();
        if (canWriteScriptToVariable()) {
            if (!isMemberSelected()) {
                list.add(new L10NHelpers.UnlocalizedString("gui.integratedscripting.error.invalid_member"));
                list.add(new L10NHelpers.UnlocalizedString(
                        "gui.integratedscripting.error.invalid_member.current", getSelectedMember()));
            }
        }
        return list;
    }

    public boolean canWriteScriptToVariable() {
        return !((IInventory) this.inventory).getStackInSlot(0).isEmpty()
                && getActiveDisk() >= 0
                && getActiveScriptPath() != null;
    }

    @Override
    public void onDirty() {
        ItemStack itemStack = ((IInventory) this.inventory).getStackInSlot(0);
        if (canWriteScriptToVariable() && !itemStack.isEmpty() && isMemberSelected()) {
            ItemStack outputStack = writeScriptVariable(!getWorld().isRemote, itemStack.copy(),
                    getActiveDisk(), getActiveScriptPath(), getSelectedMember());
            this.inventory.removeDirtyMarkListener(this);
            ((IInventory) this.inventory).setInventorySlotContents(0, outputStack);
            this.inventory.addDirtyMarkListener(this);
        }
    }

    public ItemStack writeScriptVariable(boolean generateId, ItemStack itemStack,
                                         final int disk, final Path path, final String member) {
        IVariableFacadeHandlerRegistry registry = IntegratedDynamics._instance
                .getRegistryManager().getRegistry(IVariableFacadeHandlerRegistry.class);
        return registry.writeVariableFacadeItem(generateId, itemStack,
                ScriptVariableFacadeHandler.getInstance(),
                new IVariableFacadeHandlerRegistry.IVariableFacadeFactory<IScriptVariableFacade>() {
                    @Override
                    public IScriptVariableFacade create(boolean generateId) {
                        return new ScriptVariableFacade(generateId, disk, path, member);
                    }

                    @Override
                    public IScriptVariableFacade create(int id) {
                        return new ScriptVariableFacade(id, disk, path, member);
                    }
                },
                getPlayer(), PartTypes.TERMINAL_SCRIPTING.getBlock());
    }

    @Override
    public void onUpdate(int valueId, NBTTagCompound value) {
        // Decode the disk list before the GUI is notified, so that it can render
        // the new disks in the same tick.
        if (valueId == this.availableDisksId && value != null
                && value.hasKey(ValueNotifierHelpers.KEY)) {
            this.availableDisks = DiskListCodec.decode(value.getString(ValueNotifierHelpers.KEY));
            int activeDisk = getActiveDisk();
            if (this.availableDisks.isEmpty()) {
                if (activeDisk >= 0) {
                    setActiveDisk(-1);
                }
            } else if (!this.availableDisks.contains(activeDisk)) {
                setActiveDisk(this.availableDisks.getInt(0));
            }
        }

        super.onUpdate(valueId, value);

        // Potentially trigger a variable write when a new member was selected
        if (valueId == selectionId) {
            this.onDirty();
        }
    }

    // ---------------------------------------------------------------- disk list encoding

}
