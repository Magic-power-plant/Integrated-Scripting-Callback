package com.mpp.integratedscripting.api.network;

import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Server-side singleton for handling scripts inside the game.
 *
 * <p>In 1.12.2 the data lives under {@code <world>/integratedscriptingcallback}
 * (decision A6), the interface itself is a direct port of the 1.20 reference
 * API (level A in docs/PORTING-1.12.2.md §6.5).</p>
 *
 * @author rubensworks
 */
public interface IScriptingData {

    public Collection<Integer> getDisks();

    public Map<Path, String> getScripts(int disk);

    public void setScripts(int disk, Map<Path, String> scripts, ChangeLocation changeLocation);

    public void setScript(int disk, Path scriptPathRelative, @Nullable String script, ChangeLocation changeLocation);

    /**
     * Check whether a script path may be used for the given disk.
     *
     * <p>1.12.2 port addition (docs/AUDIT.md D2): client packets carry a disk id
     * and a script path, so the containment rule ("relative, normalised, inside
     * {@code scripting-disks/<id>}") is part of the API and can be checked before
     * any write happens.</p>
     *
     * @param disk A disk id.
     * @param scriptPathRelative The supplied relative script path, may be null.
     * @return If the path is relative, normalises inside the disk directory and
     * does not escape it.
     */
    public boolean isScriptPathSafe(int disk, @Nullable Path scriptPathRelative);

    public void markDirty(int disk, Path scriptPathRelative);

    public void addListener(int disk, IDiskScriptsChangeListener listener);

    public void removeListener(int disk, IDiskScriptsChangeListener listener);

    public Pair<OutputStream, OutputStream> getOutputStreams(int disk, Path scriptPathRelative);

    public static interface IDiskScriptsChangeListener {
        public void onChange(Path scriptPathRelative);
    }

    /**
     * Indicates the location from where the change originated.
     */
    public static enum ChangeLocation {
        /**
         * If the change originates in-memory from within the game.
         * This will cause changes to propagate to the disk.
         */
        MEMORY,
        /**
         * If the change originates from the disk outside of the game.
         * This will cause changes to propagate to the game.
         */
        DISK
    }

}
