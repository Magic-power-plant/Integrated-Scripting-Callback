package com.mpp.integratedscripting.command;

import com.mpp.integratedscripting.api.evaluate.translation.IEvaluationExceptionFactory;
import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import org.cyclops.cyclopscore.init.ModBase;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeBlock;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueObjectTypeItemStack;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeBoolean;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeInteger;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeList;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypeNbt;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import java.io.IOException;

/**
 * The {@code /integratedscriptingcallback testscript} command: evaluates a small
 * script through the real translator pipeline so that the runtime can be
 * verified in a running game (docs/PORTING-1.12.2.md §7.6).
 *
 * @author rubensworks
 */
public class CommandTestScript extends CommandBase {

    private final ModBase mod;

    public CommandTestScript(ModBase mod) {
        this.mod = mod;
    }

    @Override
    public String getName() {
        return "testscript";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/" + mod.getModId() + " testscript";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        IEvaluationExceptionFactory exceptionFactory =
                ScriptHelpers.getDummyEvaluationExceptionFactory();
        try {
            Source source = Source.newBuilder("js",
                    "function testFunction(a, b, c, d, e, f) {\n"
                            + "    console.log('args: ' + a + ' ' + b + ' ' + c.length);\n"
                            + "    console.log('operators: ' + Object.keys(idContext.ops).length);\n"
                            + "    console.log('stackable: ' + idContext.ops.itemstackStackable(e));\n"
                            + "    console.log('item size method: ' + e.size());\n"
                            + "    console.log('item has block: ' + (e.block() !== null));\n"
                            + "    console.log('block plantable method: ' + f.isplantable());\n"
                            + "    console.log('nbt size: ' + idContext.ops.nbtSize(d));\n"
                            + "    console.log('nbt member a: ' + d.a);\n"
                            + "    console.log('withsize roundtrip: ' + idContext.ops.itemstackSize(\n"
                            + "        idContext.ops.itemstackWithsize(e, 5)));\n"
                            + "    console.log('item literal: ' + idContext.ops.itemstackSize(\n"
                            + "        { id_item: { id: 'minecraft:arrow', Count: 1 } }));\n"
                            + "    console.log('nbt literal: ' + idContext.ops.nbtSize({ a: 1, b: 2 }));\n"
                            + "    console.log('block literal: ' + idContext.ops.blockIsplantable(\n"
                            + "        { id_block: { name: 'minecraft:sapling', meta: 0 } }));\n"
                            + "    return 10;\n"
                            + "}", "src.js").build();

            try (Context context = ScriptHelpers.createPopulatedContext(null)) {
                context.eval(source);
                Value function = context.getBindings("js").getMember("testFunction");

                NBTTagCompound nbt = new NBTTagCompound();
                nbt.setInteger("a", 1);
                nbt.setInteger("b", 2);

                Value ret = function.execute(
                        ValueTranslators.REGISTRY.translateToGraal(context,
                                ValueTypeInteger.ValueInteger.of(10), exceptionFactory),
                        ValueTranslators.REGISTRY.translateToGraal(context,
                                ValueTypeBoolean.ValueBoolean.of(true), exceptionFactory),
                        ValueTranslators.REGISTRY.translateToGraal(context,
                                ValueTypeList.ValueList.ofAll(ValueTypes.CATEGORY_ANY,
                                        ValueTypeInteger.ValueInteger.of(10),
                                        ValueTypeBoolean.ValueBoolean.of(true)), exceptionFactory),
                        ValueTranslators.REGISTRY.translateToGraal(context,
                                ValueTypeNbt.ValueNbt.of(nbt), exceptionFactory),
                        ValueTranslators.REGISTRY.translateToGraal(context,
                                ValueObjectTypeItemStack.ValueItemStack.of(
                                        new net.minecraft.item.ItemStack(
                                                net.minecraft.init.Blocks.STONE)), exceptionFactory),
                        ValueTranslators.REGISTRY.translateToGraal(context,
                                ValueObjectTypeBlock.ValueBlock.of(
                                        net.minecraft.init.Blocks.SAPLING.getDefaultState()),
                                exceptionFactory)
                );
                IValue value = ValueTranslators.REGISTRY.translateFromGraal(
                        context, ret, exceptionFactory);
                sender.sendMessage(new TextComponentString("testscript ok, result "
                        + value.getType().toCompactString(value)));
            }
        } catch (IOException | EvaluationException | RuntimeException e) {
            throw new CommandException(e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    @Override
    public int compareTo(ICommand o) {
        return getName().compareTo(o.getName());
    }

}
