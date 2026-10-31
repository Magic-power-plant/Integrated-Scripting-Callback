package com.mpp.integratedscripting.core.item;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.client.model.IVariableModelBaked;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.item.IVariableFacade;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueHelpers;
import org.cyclops.integrateddynamics.core.evaluate.variable.ValueTypes;
import org.cyclops.integrateddynamics.core.helper.L10NValues;
import org.cyclops.integrateddynamics.core.item.VariableFacadeBase;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.callback.PartNetworkBridge;
import com.mpp.integratedscripting.core.client.model.ScriptingVariableModelProviders;
import com.mpp.integratedscripting.core.network.ScriptingNetworkHelpers;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Variable facade for variables determined by script members.
 *
 * <p>1.12.2 differences from the 1.20 reference implementation (see
 * docs/PORTING-1.12.2.md §5.7):</p>
 * <ul>
 *   <li>{@link IVariableFacade} only receives the {@link IPartNetwork}; the
 *       owning {@link INetwork} is resolved through {@link PartNetworkBridge}
 *       (decision C12).</li>
 *   <li>Validation errors are plain {@link L10NHelpers.UnlocalizedString}s
 *       because {@code EvaluationException} has no component payload
 *       (decision D5).</li>
 *   <li>On the client the scripting data is absent, so {@link #getVariable}
 *       returns {@code null} instead of failing with a NPE (decision C6).</li>
 * </ul>
 *
 * @author rubensworks
 */
public class ScriptVariableFacade extends VariableFacadeBase implements IScriptVariableFacade {

    private final int disk;
    private final Path path;
    private final String member;

    public ScriptVariableFacade(boolean generateId, int disk, Path path, String member) {
        super(generateId);
        this.disk = disk;
        this.path = path;
        this.member = member;
    }

    public ScriptVariableFacade(int id, int disk, Path path, String member) {
        super(id);
        this.disk = disk;
        this.path = path;
        this.member = member;
    }

    @Override
    public <V extends IValue> IVariable<V> getVariable(IPartNetwork partNetwork) {
        if (!isValid()) {
            return null;
        }
        // The scripting data only exists on the logical server (decision C6).
        if (ScriptingNetworkHelpers.getScriptingData() == null) {
            return null;
        }
        INetwork network = PartNetworkBridge.getNetwork(partNetwork);
        IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(network);
        return scriptingNetwork == null ? null : scriptingNetwork.getVariable(this.disk, this.path, this.member);
    }

    @Override
    public boolean isValid() {
        return disk >= 0 && path != null && member != null;
    }

    @Override
    public void validate(IPartNetwork partNetwork, IValidator validator, IValueType containingValueType) {
        INetwork network = PartNetworkBridge.getNetwork(partNetwork);
        IScriptingNetwork scriptingNetwork = ScriptingNetworkHelpers.getScriptingNetwork(network);
        try {
            if (!isValid()) {
                validator.addError(new L10NHelpers.UnlocalizedString(L10NValues.VARIABLE_ERROR_INVALIDITEM));
            } else if (scriptingNetwork == null) {
                validator.addError(new L10NHelpers.UnlocalizedString(
                        "script.integratedscripting.error.invalid_network"));
            } else if (!scriptingNetwork.getDisks().contains(this.disk)) {
                validator.addError(new L10NHelpers.UnlocalizedString(
                        "script.integratedscripting.error.disk_not_in_network", this.disk));
            } else if (scriptingNetwork.getScript(this.disk, this.path) == null) {
                validator.addError(new L10NHelpers.UnlocalizedString(
                        "script.integratedscripting.error.path_not_in_network", this.disk, this.path.toString()));
            } else if (scriptingNetwork.getScript(this.disk, this.path).getMember(this.member) == null) {
                validator.addError(new L10NHelpers.UnlocalizedString(
                        "script.integratedscripting.error.member_not_in_network",
                        this.disk, this.member, this.path.toString()));
            } else {
                // Check if the expected type corresponds to the actual value's type produced by this script.
                IValue value = scriptingNetwork.getScript(this.disk, this.path).getMember(this.member).getValue();
                if (!ValueHelpers.correspondsTo(containingValueType, value.getType())) {
                    validator.addError(new L10NHelpers.UnlocalizedString(
                            "script.integratedscripting.error.invalid_type",
                            this.member, this.path.toString(), this.disk,
                            new L10NHelpers.UnlocalizedString(containingValueType.getTranslationKey()),
                            new L10NHelpers.UnlocalizedString(value.getType().getTranslationKey())));
                }
            }
        } catch (EvaluationException e) {
            // EvaluationException only carries a message in 1.12.2 (decision D5).
            validator.addError(new L10NHelpers.UnlocalizedString(e.getMessage()));
        }
    }

    @Override
    public IValueType getOutputType() {
        return ValueTypes.CATEGORY_ANY;
    }

    protected List<String> getScriptTooltip() {
        return java.util.Arrays.asList(
                L10NHelpers.localize("script.integratedscripting.tooltip.disk", getDisk()),
                L10NHelpers.localize("script.integratedscripting.tooltip.path", getPath()),
                L10NHelpers.localize("script.integratedscripting.tooltip.member", getMember())
        );
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addInformation(List<String> list, World world) {
        if (isValid()) {
            list.addAll(getScriptTooltip());
        }
        super.addInformation(list, world);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void addModelOverlay(IVariableModelBaked variableModelBaked, List<BakedQuad> quads) {
        // M6: draw the script overlay on the variable card (docs/PORTING-1.12.2.md
        // §6.13, §9.4). The overlay is purely cosmetic; the card itself is fully
        // functional even if the client resource pack is missing the model.
        if (isValid()) {
            quads.addAll(variableModelBaked
                    .getSubModels(ScriptingVariableModelProviders.SCRIPT)
                    .getBakedModel()
                    .getQuads(null, null, 0L));
        }
    }

    @Override
    public int getDisk() {
        return disk;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public String getMember() {
        return member;
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) {
            return false;
        }
        ScriptVariableFacade that = (ScriptVariableFacade) o;
        return disk == that.disk
                && Objects.equals(path, that.path)
                && Objects.equals(member, that.member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), disk, path, member);
    }

    @Override
    public String toString() {
        return "ScriptVariableFacade(disk=" + disk + ", path=" + path + ", member=" + member + ")";
    }

}
