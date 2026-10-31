package com.mpp.integratedscripting.core.evaluate;

import com.google.gson.JsonObject;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;
import org.cyclops.integrateddynamics.api.advancement.criterion.ValuePredicate;
import org.cyclops.integrateddynamics.api.advancement.criterion.VariableFacadePredicate;
import org.cyclops.integrateddynamics.api.advancement.criterion.VariablePredicate;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandler;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.core.item.ScriptVariableFacade;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Handler for script variable facades.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation
 * (docs/PORTING-1.12.2.md §5.7): the handler id is a string
 * ({@code integratedscriptingcallback:script}, decision A5), the NBT accessors
 * are the 1.12 ones, and the two advancement predicate overrides keep
 * {@code "type": "integratedscriptingcallback:script"} meaningful for the
 * {@code terminal/bind} and {@code terminal/display} advancements.</p>
 *
 * <p>Paths are stored with forward slashes and read back with
 * {@link Paths#get}, so worlds stay portable between Windows and Linux
 * (decision C10).</p>
 *
 * @author rubensworks
 */
public class ScriptVariableFacadeHandler implements IVariableFacadeHandler<IScriptVariableFacade> {

    private static final IScriptVariableFacade INVALID_FACADE =
            new ScriptVariableFacade(false, -1, Paths.get(""), "");
    private static ScriptVariableFacadeHandler _instance;

    private ScriptVariableFacadeHandler() {

    }

    public static ScriptVariableFacadeHandler getInstance() {
        if (_instance == null) {
            _instance = new ScriptVariableFacadeHandler();
        }
        return _instance;
    }

    @Override
    public String getTypeId() {
        return Reference.MOD_ID + ":script";
    }

    @Override
    public IScriptVariableFacade getVariableFacade(int id, NBTTagCompound tag) {
        if (!tag.hasKey("disk", Constants.NBT.TAG_INT)) {
            return INVALID_FACADE;
        }
        if (!tag.hasKey("path", Constants.NBT.TAG_STRING)) {
            return INVALID_FACADE;
        }
        if (!tag.hasKey("member", Constants.NBT.TAG_STRING)) {
            return INVALID_FACADE;
        }
        return new ScriptVariableFacade(id, tag.getInteger("disk"),
                Paths.get(tag.getString("path")), tag.getString("member"));
    }

    @Override
    public void setVariableFacade(NBTTagCompound tag, IScriptVariableFacade variableFacade) {
        tag.setInteger("disk", variableFacade.getDisk());
        // Decision C10: always store forward slashes, independent of the host OS.
        tag.setString("path", variableFacade.getPath().toString().replace('\\', '/'));
        tag.setString("member", variableFacade.getMember());
    }

    /**
     * 1.12.2 advancement predicate: only match variables of the script variable type.
     */
    @Override
    public VariablePredicate deserializeVariablePredicate(JsonObject element,
                                                          @Nullable IValueType valueType,
                                                          ValuePredicate valuePredicate) {
        return new VariablePredicate<>(ScriptVariable.class, valueType, valuePredicate);
    }

    /**
     * 1.12.2 advancement predicate: only match facades produced by this handler.
     */
    @Override
    public VariableFacadePredicate deserializeVariableFacadePredicate(JsonObject element) {
        return new VariableFacadePredicate<>(IScriptVariableFacade.class);
    }

}
