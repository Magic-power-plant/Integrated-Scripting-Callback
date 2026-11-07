package com.mpp.integratedscripting.api.network;

import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;

/**
 * Instantiates scripts for paths in disks.
 *
 * @author rubensworks
 */
public interface IScriptFactory {

    /**
     * Get the script by the given path in the given disk.
     * @param disk A disk id.
     * @param path A script path.
     * @return The script or null.
     * @throws EvaluationException If the script could not be created.
     */
    @Nullable
    public IScript getScript(int disk, Path path) throws EvaluationException;

    /**
     * Get the script by the given path in the given disk, with the addressed
     * memories of the network it is compiled for.
     *
     * <p>This is the variant the scripting network calls: a script belongs to the
     * network that compiled it, and that is what makes the network's memory
     * blocks available inside the script sandbox. The default keeps the simple
     * signature working for third-party implementations.</p>
     *
     * @param disk A disk id.
     * @param path A script path.
     * @param memories The addressed memories of the network, keyed by stable name.
     * @return The script or null.
     * @throws EvaluationException If the script could not be created.
     */
    @Nullable
    public default IScript getScript(int disk, Path path, Map<String, DimPos> memories) throws EvaluationException {
        return getScript(disk, path);
    }

    /**
     * @return No memories, for implementations that call the simple variant.
     */
    public static Map<String, DimPos> noMemories() {
        return Collections.emptyMap();
    }

}
