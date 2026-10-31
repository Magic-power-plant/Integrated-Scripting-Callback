package com.mpp.integratedscripting.command;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.item.IVariableFacade;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.api.network.IScriptingData;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.core.evaluate.ScriptVariableFacadeHandler;
import com.mpp.integratedscripting.core.item.ScriptVariableFacade;
import com.mpp.integratedscripting.core.network.ScriptingNetwork;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;
import com.mpp.integratedscripting.part.PartTypes;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * The {@code /integratedscriptingcallback testfacade} command: exercises the M4
 * script variable facade without a GUI.
 *
 * <p>It performs the same steps a Display Panel and the terminal's
 * "bind member to variable card" action perform:</p>
 * <ol>
 *   <li>read the script member through {@code ScriptingNetwork#getVariable},
 *       which is exactly the variable a Display Panel evaluates;</li>
 *   <li>write a {@link ScriptVariableFacade} onto an Integrated Dynamics
 *       variable card through the real
 *       {@link IVariableFacadeHandlerRegistry} and read it back from NBT,
 *       proving that the {@code integratedscriptingcallback:script} handler is
 *       registered and round-trips {@code disk/path/member}.</li>
 * </ol>
 *
 * <p>With the optional {@code [x y z]} arguments it additionally resolves the
 * facade through a real Integrated Dynamics part network, i.e. the full
 * {@code facade.getVariable(partNetwork) -> PartNetworkBridge -> INetwork ->
 * ScriptingNetwork} path that a Display Panel uses.</p>
 *
 * <p>It finally removes the {@code member} tag and verifies that the handler
 * falls back to an invalid facade instead of throwing.</p>
 *
 * @author rubensworks
 */
public class CommandTestFacade extends CommandBase {

    private final ModBase mod;

    public CommandTestFacade(ModBase mod) {
        this.mod = mod;
    }

    @Override
    public String getName() {
        return "testfacade";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + mod.getModId() + " testfacade <disk> <path> <member> [x y z]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        if (args.length != 3 && args.length != 6) {
            throw new CommandException("Usage: " + getUsage(sender));
        }
        IScriptingData scriptingData = ScriptingNetworkHelpers.getScriptingData();
        if (scriptingData == null) {
            throw new CommandException("No scripting data available; is a world loaded?");
        }

        int disk = parseInt(args[0]);
        Path path = Paths.get(args[1]);
        String member = args[2];
        if (!scriptingData.getScripts(disk).containsKey(path)) {
            throw new CommandException("Script " + path + " does not exist on disk " + disk);
        }

        // 1) Read the member exactly like a Display Panel does.
        ScriptingNetwork scriptingNetwork = new ScriptingNetwork();
        scriptingNetwork.addDisk(disk);
        IValue value;
        try {
            value = scriptingNetwork.getVariable(disk, path, member).getValue();
        } catch (EvaluationException e) {
            throw new CommandException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        sender.sendMessage(new TextComponentString("testfacade value: " + compact(value)));

        // 2) Optionally resolve the same member through a real Integrated Dynamics
        //    part network, which is the exact path a Display Panel uses:
        //    facade.getVariable(partNetwork) -> PartNetworkBridge -> INetwork ->
        //    ScriptingNetwork capability -> ScriptVariable.
        if (args.length == 6) {
            World world = sender.getEntityWorld();
            BlockPos pos = parseBlockPos(sender, args, 3, false);
            INetwork idNetwork = NetworkHelpers.getNetwork(PartPos.of(world, pos, null));
            if (idNetwork == null) {
                throw new CommandException("No Integrated Dynamics network at " + pos);
            }
            IPartNetwork partNetwork = NetworkHelpers.getPartNetwork(idNetwork);
            if (partNetwork == null) {
                throw new CommandException("The network at " + pos + " has no part network");
            }
            IScriptingNetwork idScriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(idNetwork);
            if (idScriptingNetwork == null || !idScriptingNetwork.getDisks().contains(disk)) {
                throw new CommandException("Disk " + disk + " is not exposed in the network at " + pos);
            }

            ScriptVariableFacade probe = new ScriptVariableFacade(0, disk, path, member);
            IVariable<?> variable = probe.getVariable(partNetwork);
            if (variable == null) {
                throw new CommandException("The facade did not resolve a variable through the part network.");
            }
            IValue networkValue;
            try {
                networkValue = variable.getValue();
            } catch (EvaluationException e) {
                throw new CommandException(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            if (!compact(networkValue).equals(compact(value))) {
                throw new CommandException("The part network value differs from the direct value.");
            }
            sender.sendMessage(new TextComponentString("testfacade part-network value: "
                    + compact(networkValue)));
        }

        // 3) Write the facade onto a variable card and read it back from NBT.
        Item variableItem = Item.getByNameOrId(Reference.MOD_INTEGRATEDDYNAMICS + ":variable");
        if (variableItem == null) {
            throw new CommandException("The Integrated Dynamics variable card item is not registered.");
        }
        IVariableFacadeHandlerRegistry registry = IntegratedDynamics._instance
                .getRegistryManager().getRegistry(IVariableFacadeHandlerRegistry.class);

        final int facadeDisk = disk;
        final Path facadePath = path;
        final String facadeMember = member;
        ItemStack written = registry.writeVariableFacadeItem(true, new ItemStack(variableItem),
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

        NBTTagCompound tag = written.getTagCompound();
        if (tag == null) {
            throw new CommandException("The variable facade was not written to the item.");
        }
        IVariableFacade read = registry.handle(written);
        if (!(read instanceof IScriptVariableFacade)) {
            throw new CommandException("The written variable card did not resolve to a script facade.");
        }
        IScriptVariableFacade facade = (IScriptVariableFacade) read;
        sender.sendMessage(new TextComponentString("testfacade card: _type=" + tag.getString("_type")
                + " _id=" + facade.getId()
                + " disk=" + facade.getDisk()
                + " path=" + facade.getPath()
                + " member=" + facade.getMember()));

        if (facade.getDisk() != disk
                || !facade.getPath().equals(path)
                || !facade.getMember().equals(member)) {
            throw new CommandException("Facade round-trip mismatch.");
        }

        // 4) A malformed facade must degrade to an invalid facade instead of throwing.
        NBTTagCompound broken = tag.copy();
        broken.removeTag("member");
        IVariableFacade invalid = registry.handle(broken);
        sender.sendMessage(new TextComponentString("testfacade invalid facade: valid="
                + invalid.isValid()));
        if (invalid.isValid()) {
            throw new CommandException("A facade without a member tag must be invalid.");
        }

        sender.sendMessage(new TextComponentString("testfacade ok: " + facade));
    }

    /**
     * 1.12.2's {@code IValue#getType()} returns a raw {@code IValueType}, so the
     * member call is unchecked by definition of the ID API.
     * @param value A value.
     * @return Its compact string representation.
     */
    @SuppressWarnings("unchecked")
    private static String compact(IValue value) {
        return value.getType().toCompactString(value);
    }

    @Override
    public int compareTo(ICommand o) {
        return getName().compareTo(o.getName());
    }

}
