package com.mpp.integratedscripting.core.network;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.apache.commons.lang3.tuple.Pair;
import com.mpp.GeneralConfig;
import com.mpp.integratedscripting.api.network.IScriptingData;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Server-side scripting data, persisted in {@code <world>/integratedscriptingcallback}
 * (decision A6 in docs/PORTING-1.12.2.md §1.6).
 *
 * <p>1.12.2 differences from the 1.20 reference implementation, all decided in
 * docs/PORTING-1.12.2.md:</p>
 * <ul>
 *   <li>No {@code LevelResource}; the world directory is passed in, and the
 *       mod-specific root folder is created below it (§7.12, §10.1).</li>
 *   <li>The file-system watcher never mutates script state from its own thread
 *       (decision C8): it only queues disk ids, and {@link #tick()} performs the
 *       reload on the server thread. This keeps the ID variable/network caches
 *       single-threaded and avoids {@code ConcurrentModificationException}.</li>
 *   <li>Writes go through a temporary file plus a move, so a crash never leaves
 *       a half written script (§10.5).</li>
 * </ul>
 *
 * @author rubensworks
 */
public class ScriptingData implements IScriptingData {

    public static final String LEVEL_RESOURCE = "integratedscriptingcallback";

    private final Int2ObjectMap<Map<Path, String>> diskScripts = new Int2ObjectAVLTreeMap<>();
    private final Path rootPath;
    private final Set<Pair<Integer, Path>> dirtyPaths = Sets.newHashSet();
    private final Int2ObjectMap<List<IDiskScriptsChangeListener>> scriptChangeListeners = new Int2ObjectAVLTreeMap<>();
    private final Map<Path, WatchKey> pathWatchers = Maps.newConcurrentMap();
    private final Map<WatchKey, Path> pathWatchersReverse = Maps.newConcurrentMap();
    /** Disk ids whose directory changed on disk; drained by {@link #tick()} on the server thread (C8). */
    private final Queue<Integer> pendingDiskReloads = new ConcurrentLinkedQueue<>();

    private boolean initialized = false;
    private volatile boolean closed = false;
    private WatchService watchService;
    private Thread watchThread;

    /**
     * @param worldDirectory The world save directory; the mod data lives in a
     *                       sub directory named {@link #LEVEL_RESOURCE}.
     */
    public ScriptingData(File worldDirectory) {
        this.rootPath = new File(worldDirectory, LEVEL_RESOURCE).toPath();
    }

    public Path getRootPath() {
        return rootPath;
    }

    public void tick() {
        // Initialize once
        if (!this.initialized) {
            this.initialized = true;
            this.initialize();
        }
        if (this.closed) {
            return;
        }

        // Flush changes to disk first: a watch event that was queued by one of our
        // own writes must never resurrect a pending in-memory edit (including a delete).
        if (!dirtyPaths.isEmpty()) {
            for (Pair<Integer, Path> entry : dirtyPaths) {
                this.flushScript(entry.getLeft(), entry.getRight());
            }
            dirtyPaths.clear();
        }

        // Apply external disk changes on the server thread only (C8)
        Integer pendingDiskId;
        while ((pendingDiskId = this.pendingDiskReloads.poll()) != null) {
            this.initializeDiskScripts(pendingDiskId);
        }
    }

    public void close() {
        // Flush pending changes before shutting down, so no edit is lost (§10.5).
        if (!dirtyPaths.isEmpty()) {
            for (Pair<Integer, Path> entry : dirtyPaths) {
                this.flushScript(entry.getLeft(), entry.getRight());
            }
            dirtyPaths.clear();
        }

        this.closed = true;
        WatchService service = this.watchService;
        this.watchService = null;
        if (service != null) {
            try {
                service.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Thread thread = this.watchThread;
        this.watchThread = null;
        if (thread != null) {
            try {
                thread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    protected Path getDisksPath() {
        return rootPath.resolve("scripting-disks");
    }

    protected Path getDiskPath(int id) {
        return getDisksPath().resolve(String.valueOf(id));
    }

    public void initialize() {
        Path disksPath = getDisksPath();
        try {
            Files.createDirectories(disksPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize watcher
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            // Without a watcher the game still works, external edits are just not picked up.
            e.printStackTrace();
        }

        if (this.watchService != null) {
            // Register watcher for all disk directories
            this.registerAbsolutePathWatcher(disksPath);
        }

        // Collect all disk ids (independent of the watcher: existing disks must always load)
        List<Integer> diskIds = Lists.newArrayList();
        try (Stream<Path> stream = Files.walk(disksPath, 1)) {
            stream.filter(Files::isDirectory)
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        try {
                            diskIds.add(Integer.parseInt(name));
                        } catch (NumberFormatException e) {
                            // Ignore invalid disk directories
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Read scripts for all disk ids
        for (Integer diskId : diskIds) {
            this.initializeDiskScripts(diskId);
        }

        if (this.watchService != null) {
            // Listen to changes
            Thread watchThread = new Thread(this::watchFiles, "integratedscriptingcallback-script-watcher");
            watchThread.setDaemon(true);
            watchThread.start();
            this.watchThread = watchThread;
        }
    }

    protected void initializeDiskScripts(int diskId) {
        Map<Path, String> scripts = Maps.newHashMap();
        Path diskDir = getDiskPath(diskId);
        if (Files.isDirectory(diskDir)) {
            try (Stream<Path> stream = Files.walk(diskDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(file -> !isTemporaryFile(file))
                        .forEach(file -> {
                            Path filePathRelative = diskDir.relativize(file);
                            try {
                                scripts.put(filePathRelative, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        this.setScripts(diskId, scripts, ChangeLocation.DISK);
    }

    /**
     * @param file A file inside a disk directory.
     * @return If the file is a temporary artifact of {@link #flushScript(int, Path)}.
     */
    protected static boolean isTemporaryFile(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".tmp") || name.startsWith(".");
    }

    protected void watchFiles() {
        WatchService watchService = this.watchService;
        if (watchService == null) {
            return;
        }
        // Listen to changes
        while (true) {
            try {
                boolean poll = true;
                while (poll) {
                    WatchKey key = watchService.take();
                    for (WatchEvent<?> event : key.pollEvents()) {

                        // Check if the file is part of a disk directory
                        Path changedPathRelative = (Path) event.context();
                        Path pathDirectory = pathWatchersReverse.get(key);
                        if (pathDirectory != null) {
                            Path changedPathAbsolute = pathDirectory.resolve(changedPathRelative);
                            Path disksPath = getDisksPath();
                            if (changedPathAbsolute.startsWith(disksPath)) {
                                try {
                                    int diskId = Integer.parseInt(changedPathAbsolute.getName(disksPath.getNameCount()).toString());
                                    // Schedule a full directory reload; the actual reload happens in tick() (C8).
                                    this.pendingDiskReloads.add(diskId);
                                } catch (NumberFormatException e) {
                                    // Ignore non-disk directories
                                }
                            }
                        }
                    }
                    poll = key.reset();
                    Thread.yield();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                // Exit the infinite loop
                break;
            }
        }
    }

    @Override
    public Collection<Integer> getDisks() {
        return diskScripts.keySet();
    }

    @Override
    public Map<Path, String> getScripts(int disk) {
        return diskScripts.getOrDefault(disk, Maps.newHashMap());
    }

    @Override
    public void setScripts(int disk, Map<Path, String> scripts, ChangeLocation changeLocation) {
        // Update script data
        Map<Path, String> oldScripts = diskScripts.put(disk, scripts);

        // Determine scripts that actually changed
        List<Path> modifiedScripts = Lists.newLinkedList();
        Set<Path> keys = Sets.newHashSet();
        keys.addAll(scripts.keySet());
        if (oldScripts != null) {
            keys.addAll(oldScripts.keySet());
        }
        for (Path path : keys) {
            if (oldScripts == null || !Objects.equals(oldScripts.get(path), scripts.get(path))) {
                modifiedScripts.add(path);
            }
        }

        // Propagate changes
        if (changeLocation == ChangeLocation.MEMORY) {
            // Mark dirty
            for (Path path : modifiedScripts) {
                this.markDirty(disk, path);
            }
        }
        if (changeLocation == ChangeLocation.DISK) {
            // Limit length of log files
            if (GeneralConfig.maxLogLines != -1) {
                for (Path modifiedScript : modifiedScripts) {
                    String modifiedScriptStr = modifiedScript.toString();
                    if (modifiedScriptStr.endsWith(".stdout") || modifiedScriptStr.endsWith(".stderr")) {
                        String data = scripts.get(modifiedScript);
                        if (data != null) {
                            String[] lines = data.split("\n");
                            if (lines.length > GeneralConfig.maxLogLines + 100) {
                                String dataNew = Arrays.stream(lines).skip(lines.length - GeneralConfig.maxLogLines)
                                        .collect(Collectors.joining("\n")) + "\n";
                                scripts.put(modifiedScript, dataNew);
                                this.flushScript(disk, modifiedScript);
                            }
                        }
                    }
                }
            }

            // Unregister old watchers
            if (oldScripts != null) {
                for (Path path : oldScripts.keySet()) {
                    this.unregisterPathWatcher(disk, path.getParent());
                }
            }

            // Register watchers for all directories
            this.registerPathWatcher(disk, null);
            for (Path path : scripts.keySet()) {
                this.registerPathWatcher(disk, path.getParent());
            }
        }

        // Invoke listeners
        List<IDiskScriptsChangeListener> listeners = scriptChangeListeners.get(disk);
        if (listeners != null) {
            for (IDiskScriptsChangeListener listener : Lists.newArrayList(listeners.listIterator())) {
                for (Path scriptPathRelative : modifiedScripts) {
                    listener.onChange(scriptPathRelative);
                }
            }
        }
    }

    /**
     * Check if the given path is relative, normalizes inside the disk and does not escape it.
     * @param disk A disk id.
     * @param supplied The supplied relative script path.
     * @return If the path may be used for this disk.
     */
    @Override
    public boolean isScriptPathSafe(int disk, @Nullable Path supplied) {
        if (supplied == null || supplied.isAbsolute()) {
            return false;
        }

        Path relative = supplied.normalize();

        if (relative.getNameCount() == 0 || relative.startsWith("..")) {
            return false;
        }

        Path diskRoot = getDiskPath(disk).toAbsolutePath().normalize();
        Path resolved = diskRoot.resolve(relative).normalize();

        return resolved.startsWith(diskRoot);
    }

    @Override
    public void setScript(int disk, Path scriptPathRelative, @Nullable String script, ChangeLocation changeLocation) {
        // Validate that the path is safe and contained within the disk root
        if (!isScriptPathSafe(disk, scriptPathRelative)) {
            return;
        }

        // Update script data
        Map<Path, String> scripts = diskScripts.get(disk);
        if (scripts == null) {
            scripts = Maps.newHashMap();
            diskScripts.put(disk, scripts);
        }
        if (script == null) {
            scripts.remove(scriptPathRelative);
        } else {
            scripts.put(scriptPathRelative, script);
        }

        // Propagate changes
        if (changeLocation == ChangeLocation.MEMORY) {
            this.markDirty(disk, scriptPathRelative);
        }
        if (changeLocation == ChangeLocation.DISK) {
            // Register watcher
            this.registerPathWatcher(disk, scriptPathRelative.getParent());
        }

        // Invoke listeners. Iterate over a copy: a listener may invalidate itself or
        // another listener (a script variable removes its disk listener when it is
        // invalidated), which would otherwise throw a ConcurrentModificationException
        // in the middle of a script write.
        List<IDiskScriptsChangeListener> listeners = scriptChangeListeners.get(disk);
        if (listeners != null) {
            for (IDiskScriptsChangeListener listener : Lists.newArrayList(listeners)) {
                listener.onChange(scriptPathRelative);
            }
        }
    }

    protected void registerPathWatcher(int diskId, @Nullable Path pathRelative) {
        Path diskPath = getDiskPath(diskId);
        Path pathAbsolute = pathRelative == null ? diskPath : diskPath.resolve(pathRelative);
        this.registerAbsolutePathWatcher(pathAbsolute);
    }

    protected void registerAbsolutePathWatcher(Path pathAbsolute) {
        WatchService watchService = this.watchService;
        if (watchService == null || this.closed || Files.notExists(pathAbsolute)) {
            return;
        }
        if (!pathWatchers.containsKey(pathAbsolute)) {
            try {
                WatchKey watchKey = pathAbsolute.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                pathWatchers.put(pathAbsolute, watchKey);
                pathWatchersReverse.put(watchKey, pathAbsolute);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    protected void unregisterPathWatcher(int diskId, @Nullable Path pathRelative) {
        Path diskPath = getDiskPath(diskId);
        Path pathAbsolute = pathRelative == null ? diskPath : diskPath.resolve(pathRelative);
        WatchKey watchKey = pathWatchers.remove(pathAbsolute);
        if (watchKey != null) {
            pathWatchersReverse.remove(watchKey);
            watchKey.cancel();
        }
    }

    @Override
    public void markDirty(int disk, Path scriptPathRelative) {
        dirtyPaths.add(Pair.of(disk, scriptPathRelative));
    }

    @Override
    public void addListener(int disk, IDiskScriptsChangeListener listener) {
        List<IDiskScriptsChangeListener> listeners = scriptChangeListeners.get(disk);
        if (listeners == null) {
            listeners = Lists.newArrayList();
            scriptChangeListeners.put(disk, listeners);
        }
        listeners.add(listener);
    }

    @Override
    public void removeListener(int disk, IDiskScriptsChangeListener listener) {
        List<IDiskScriptsChangeListener> listeners = scriptChangeListeners.get(disk);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                scriptChangeListeners.remove(disk);
            }
        }
    }

    @Override
    public Pair<OutputStream, OutputStream> getOutputStreams(int disk, Path scriptPathRelative) {
        Path scriptPathAbsolute = getDiskPath(disk).resolve(scriptPathRelative);
        return Pair.of(
                new LazyOutputStream(() -> new FileOutputStream(scriptPathAbsolute + ".stdout", true)),
                new LazyOutputStream(() -> new FileOutputStream(scriptPathAbsolute + ".stderr", true))
        );
    }

    protected void flushScript(int disk, Path scriptPathRelative) {
        Map<Path, String> scripts = getScripts(disk);
        String script = scripts.get(scriptPathRelative);
        Path scriptPathAbsolute = getDiskPath(disk).resolve(scriptPathRelative);
        try {
            if (script == null) {
                Files.deleteIfExists(scriptPathAbsolute);
            } else {
                Path parent = scriptPathAbsolute.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                // Atomic-ish replacement: write a temporary file first, then move it over the script (§10.5).
                Path tempPath = scriptPathAbsolute.resolveSibling(scriptPathAbsolute.getFileName() + ".tmp");
                Files.write(tempPath, script.getBytes(StandardCharsets.UTF_8));
                Files.move(tempPath, scriptPathAbsolute, StandardCopyOption.REPLACE_EXISTING);
                this.registerPathWatcher(disk, null);
                this.registerPathWatcher(disk, scriptPathRelative.getParent());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @return The pending external disk reloads that will be applied on the next tick.
     * Only used for diagnostics and tests.
     */
    public int getPendingDiskReloadCount() {
        return this.pendingDiskReloads.size();
    }

    /**
     * @return All changed script paths that still have to be flushed to disk.
     * Only used for diagnostics and tests.
     */
    public Iterator<Pair<Integer, Path>> getDirtyPaths() {
        return this.dirtyPaths.iterator();
    }

}
