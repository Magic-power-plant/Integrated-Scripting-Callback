package com.mpp.integratedscripting.core.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValue;
import org.cyclops.integrateddynamics.api.evaluate.variable.IVariable;
import org.cyclops.integrateddynamics.api.network.INetwork;
import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.network.IScript;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.api.network.IScriptingNetwork;
import com.mpp.integratedscripting.core.evaluate.ScriptVariable;
import com.mpp.integratedscripting.core.language.LanguageHandlers;
import com.mpp.integratedscripting.evaluate.ScriptRuntime;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Network capability holding the disks that are available within one Integrated
 * Dynamics network, plus the per-network script and variable caches.
 *
 * <p>1.12.2 differences (docs/PORTING-1.12.2.md §6.24): localized
 * {@link EvaluationException} messages, and a readable error instead of a
 * {@code NullPointerException} when the server-side scripting data is absent
 * (decision C6).</p>
 *
 * @author rubensworks
 */
public class ScriptingNetwork implements IScriptingNetwork {

    private final Set<Integer> disks = Sets.newHashSet();
    private final Map<String, DimPos> memories = Maps.newLinkedHashMap();
    private final Map<Pair<Integer, Path>, IScript> scriptCache = Maps.newHashMap();
    private final Map<Triple<Integer, Path, String>, ScriptVariable> variableCache = Maps.newHashMap();
    @Nullable
    private final INetwork network;

    public ScriptingNetwork() {
        this(null);
    }

    /**
     * @param network The Integrated Dynamics network that owns this capability, if known.
     */
    public ScriptingNetwork(@Nullable INetwork network) {
        this.network = network;
    }

    @Override
    public void addDisk(int disk) {
        disks.add(disk);
    }

    @Override
    public void removeDisk(int disk) {
        disks.remove(disk);
    }

    @Override
    public Set<Integer> getDisks() {
        return Collections.unmodifiableSet(this.disks);
    }

    @Override
    public void addMemory(String name, DimPos position) {
        if (name == null || position == null) {
            return;
        }
        if (!position.equals(this.memories.put(name, position))) {
            // A new memory changes what a script can see, so drop compiled scripts:
            // their sandbox was built with the previous set of memory bindings.
            invalidateScripts();
        }
    }

    @Override
    public void removeMemory(String name) {
        if (name != null && this.memories.remove(name) != null) {
            invalidateScripts();
        }
    }

    @Override
    public Map<String, DimPos> getMemories() {
        return Collections.unmodifiableMap(this.memories);
    }

    /**
     * Drop every compiled script of this network, because its bindings changed.
     */
    protected void invalidateScripts() {
        List<IScript> scripts = Lists.newArrayList(this.scriptCache.values());
        this.scriptCache.clear();
        for (IScript script : scripts) {
            try {
                script.release();
            } catch (RuntimeException e) {
                // Never let cache invalidation break joining a network.
                com.mpp.IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.WARN,
                        "Could not release a compiled script: " + e);
            }
        }
        postVariableContentsUpdated();
    }

    @Nullable
    @Override
    public IScript getScript(int disk, Path path) throws EvaluationException {
        // Client-side (and very early server-side) there is no script data: fail readably (C6).
        if (ScriptingNetworkHelpers.getScriptingData() == null) {
            throw new EvaluationException(L10NHelpers.localize(
                    "script.integratedscripting.error.no_scripting_data"));
        }

        Pair<Integer, Path> cacheKey = Pair.of(disk, path);
        IScript script = scriptCache.get(cacheKey);
        if (script == null) {
            // A missing companion library must not surface as a LinkageError deep
            // inside GraalVM (docs/AUDIT.md D6).
            if (!ScriptRuntime.isAvailable()) {
                throw new EvaluationException(L10NHelpers.localize(
                        "script.integratedscripting.error.runtime_missing",
                        String.valueOf(ScriptRuntime.getUnavailableReason())));
            }
            ILanguageHandler languageHandler = LanguageHandlers.REGISTRY.getProvider(path);
            if (languageHandler == null) {
                throw new EvaluationException(L10NHelpers.localize(
                        "script.integratedscripting.error.unsupported_language", path.toString()));
            }
            IScriptFactory scriptFactory = languageHandler.getScriptFactory();
            // The memories of this network are part of the script's bindings, so a
            // script compiled here can reach the memory cards of its network.
            script = scriptFactory.getScript(disk, path, Collections.unmodifiableMap(this.memories));
            if (script != null) {
                final IScript cachedScript = script;
                script.addInvalidationListener(() -> {
                    // Only drop this cache entry, and only when it still holds the
                    // released instance: a newer script for the same path must not be
                    // evicted by a stale notification (docs/AUDIT.md D1).
                    scriptCache.remove(cacheKey, cachedScript);
                    // A changed script is a changed variable: tell the network, exactly
                    // like ID does when the variable of a part changes. Parts that cache
                    // the variable (Display Panel, value interface, ...) then clear
                    // their stale validation errors and re-read the value; without this
                    // a panel stays in its error state after a script is fixed (§11.2
                    // item 18, M7 finding).
                    postVariableContentsUpdated();
                });
                scriptCache.put(cacheKey, script);
            }
        }
        return script;
    }

    /**
     * Post ID's {@code VariableContentsUpdatedEvent} on the owning network, if any.
     * This is the same notification Integrated Dynamics posts when the variable in
     * a part's inventory changes.
     */
    protected void postVariableContentsUpdated() {
        if (this.network == null) {
            return;
        }
        try {
            this.network.getEventBus().post(
                    new org.cyclops.integrateddynamics.core.network.event.VariableContentsUpdatedEvent(
                            this.network));
        } catch (RuntimeException e) {
            // Never let a notification failure break a script write.
            com.mpp.IntegratedScriptingCallback.clog(org.apache.logging.log4j.Level.WARN,
                    "Could not post VariableContentsUpdatedEvent: " + e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V extends IValue> IVariable<V> getVariable(int disk, Path path, String member) {
        Triple<Integer, Path, String> cacheKey = Triple.of(disk, path, member);
        ScriptVariable variable = variableCache.get(cacheKey);
        if (variable == null) {
            variable = new ScriptVariable(disk, path, member, this);
            variable.addInvalidationListener(() -> variableCache.remove(cacheKey));
            variableCache.put(cacheKey, variable);
        }
        return (IVariable<V>) variable;
    }

}
