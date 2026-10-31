package com.mpp.integratedscripting.core.network;

import com.mpp.GeneralConfig;
import com.mpp.integratedscripting.api.network.IScriptingData;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the 1.12.2 {@link ScriptingData} storage layer.
 *
 * <p>Ported from the 1.20 reference test suite (docs/PORTING-1.12.2.md §11.1),
 * adapted to the {@code File worldDirectory} constructor and to the C8 rule
 * that external disk changes are applied by {@link ScriptingData#tick()}.</p>
 */
public class ScriptingDataTest {

    private static final int AWAIT_TIMEOUT_MS = 20000;
    private static final int POLLING_INTERVAL_MS = 25;

    private Path worldPath;
    private int maxLogLinesBackup;

    @Before
    public void setUp() throws IOException {
        this.worldPath = Files.createTempDirectory("integratedscriptingcallback-test");
        this.maxLogLinesBackup = GeneralConfig.maxLogLines;
    }

    @After
    public void tearDown() throws IOException {
        GeneralConfig.maxLogLines = this.maxLogLinesBackup;
        deleteRecursively(this.worldPath);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        List<Path> paths;
        try (java.util.stream.Stream<Path> stream = Files.walk(path)) {
            paths = stream.sorted(java.util.Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList());
        }
        for (Path toDelete : paths) {
            Files.deleteIfExists(toDelete);
        }
    }

    private ScriptingData newData() {
        return new ScriptingData(this.worldPath.toFile());
    }

    private Path diskPath(ScriptingData data, int disk) {
        return data.getRootPath().resolve("scripting-disks").resolve(String.valueOf(disk));
    }

    @Test
    public void testRootPathUsesModNamespace() {
        ScriptingData data = newData();
        assertEquals("integratedscriptingcallback", data.getRootPath().getFileName().toString());
    }

    @Test
    public void testIsScriptPathSafeAcceptsSimpleName() {
        assertTrue(newData().isScriptPathSafe(1, java.nio.file.Paths.get("main.js")));
    }

    @Test
    public void testIsScriptPathSafeAcceptsSubdirectory() {
        assertTrue(newData().isScriptPathSafe(1, java.nio.file.Paths.get("subdir", "main.js")));
    }

    @Test
    public void testIsScriptPathSafeRejectsNull() {
        assertFalse(newData().isScriptPathSafe(1, null));
    }

    @Test
    public void testIsScriptPathSafeRejectsAbsolute() {
        assertFalse(newData().isScriptPathSafe(1, java.nio.file.Paths.get("/etc/passwd")));
    }

    @Test
    public void testIsScriptPathSafeRejectsParentTraversal() {
        assertFalse(newData().isScriptPathSafe(1, java.nio.file.Paths.get("../escape.txt")));
    }

    @Test
    public void testIsScriptPathSafeRejectsNestedTraversal() {
        assertFalse(newData().isScriptPathSafe(1, java.nio.file.Paths.get("subdir", "..", "..", "escape.txt")));
    }

    @Test
    public void testSetScriptIgnoresTraversalPath() {
        ScriptingData data = newData();
        data.setScript(1, java.nio.file.Paths.get("../escape.txt"), "bad content", IScriptingData.ChangeLocation.MEMORY);
        assertNull(data.getScripts(1).get(java.nio.file.Paths.get("../escape.txt")));
    }

    @Test
    public void testSetScriptIgnoresAbsolutePath() throws IOException {
        ScriptingData data = newData();
        Path outsidePath = this.worldPath.resolve("outside.txt").toAbsolutePath();
        data.setScript(1, outsidePath, "bad content", IScriptingData.ChangeLocation.MEMORY);
        assertFalse(Files.exists(outsidePath));
    }

    @Test
    public void testSetScriptAcceptsValidPath() {
        ScriptingData data = newData();
        data.initialize();
        try {
            Path scriptPath = java.nio.file.Paths.get("main.js");
            data.setScript(1, scriptPath, "export const x = 1;", IScriptingData.ChangeLocation.MEMORY);
            assertEquals("export const x = 1;", data.getScripts(1).get(scriptPath));
        } finally {
            data.close();
        }
    }

    @Test
    public void testMemoryChangeIsFlushedOnTick() throws IOException {
        ScriptingData data = newData();
        data.initialize();
        try {
            Path scriptPath = java.nio.file.Paths.get("main.js");
            data.setScript(1, scriptPath, "export const x = 1;", IScriptingData.ChangeLocation.MEMORY);
            assertFalse("Script must not be written before the next tick",
                    Files.exists(diskPath(data, 1).resolve(scriptPath)));

            data.tick();

            Path absolute = diskPath(data, 1).resolve(scriptPath);
            assertTrue(Files.exists(absolute));
            assertEquals("export const x = 1;", new String(Files.readAllBytes(absolute), StandardCharsets.UTF_8));
            assertFalse("Temporary write file must be removed",
                    Files.exists(diskPath(data, 1).resolve("main.js.tmp")));
        } finally {
            data.close();
        }
    }

    @Test
    public void testCloseFlushesPendingChanges() throws IOException {
        ScriptingData data = newData();
        data.initialize();
        Path scriptPath = java.nio.file.Paths.get("main.js");
        data.setScript(2, scriptPath, "close me", IScriptingData.ChangeLocation.MEMORY);
        data.close();

        Path absolute = diskPath(data, 2).resolve(scriptPath);
        assertTrue(Files.exists(absolute));
        assertEquals("close me", new String(Files.readAllBytes(absolute), StandardCharsets.UTF_8));
    }

    @Test
    public void testDeletedScriptIsRemovedFromDisk() throws IOException {
        ScriptingData data = newData();
        data.initialize();
        try {
            Path scriptPath = java.nio.file.Paths.get("main.js");
            data.setScript(3, scriptPath, "to be removed", IScriptingData.ChangeLocation.MEMORY);
            data.tick();
            assertTrue(Files.exists(diskPath(data, 3).resolve(scriptPath)));

            data.setScript(3, scriptPath, null, IScriptingData.ChangeLocation.MEMORY);
            data.tick();
            assertFalse(Files.exists(diskPath(data, 3).resolve(scriptPath)));
        } finally {
            data.close();
        }
    }

    @Test
    public void testLogFilesAreTruncatedOnDiskReload() throws IOException {
        GeneralConfig.maxLogLines = 10;
        ScriptingData data = newData();
        Path diskDir = diskPath(data, 4);
        Files.createDirectories(diskDir);
        StringBuilder log = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            log.append("line ").append(i).append('\n');
        }
        Path logPath = diskDir.resolve("main.js.stdout");
        Files.write(logPath, log.toString().getBytes(StandardCharsets.UTF_8));

        data.initialize();
        try {
            String truncated = new String(Files.readAllBytes(logPath), StandardCharsets.UTF_8);
            String[] lines = truncated.split("\n");
            assertEquals(GeneralConfig.maxLogLines, lines.length);
            assertTrue("The newest log lines must be kept", truncated.contains("line 299"));
            assertFalse("The oldest log lines must be dropped", truncated.contains("line 0\n"));
        } finally {
            data.close();
        }
    }

    @Test
    public void testExternalDiskCreationIsSyncedOnTick() throws IOException, InterruptedException {
        ScriptingData data = newData();
        data.initialize();
        try {
            int disk = 123;
            Path diskDir = diskPath(data, disk);
            Files.createDirectories(diskDir);

            awaitCondition(() -> {
                data.tick();
                return data.getDisks().contains(disk);
            });
            assertTrue(data.getDisks().contains(disk));

            Path scriptPath = java.nio.file.Paths.get("main.js");
            String scriptValue = "export const value = 1;";
            Files.write(diskDir.resolve(scriptPath), scriptValue.getBytes(StandardCharsets.UTF_8));

            awaitCondition(() -> {
                data.tick();
                return scriptValue.equals(data.getScripts(disk).get(scriptPath));
            });
            assertEquals(scriptValue, data.getScripts(disk).get(scriptPath));
        } finally {
            data.close();
        }
    }

    @Test
    public void testExternalDiskUpdatesAreSyncedOnTick() throws IOException, InterruptedException {
        ScriptingData data = newData();
        data.initialize();
        try {
            int disk = 456;
            Path scriptPath = java.nio.file.Paths.get("main.js");
            data.setScript(disk, scriptPath, "export const value = 1;", IScriptingData.ChangeLocation.MEMORY);
            data.tick();
            Path absolute = diskPath(data, disk).resolve(scriptPath);
            assertTrue(Files.exists(absolute));

            String updated = "export const value = 2;";
            for (int attempt = 0; attempt < 20; attempt++) {
                Files.write(absolute, updated.getBytes(StandardCharsets.UTF_8));
                final String expected = updated;
                if (awaitCondition(() -> {
                    data.tick();
                    return expected.equals(data.getScripts(disk).get(scriptPath));
                }, 1000)) {
                    break;
                }
                updated = "export const value = " + (2 + attempt) + ";";
            }
            assertEquals(updated, data.getScripts(disk).get(scriptPath));
        } finally {
            data.close();
        }
    }

    @Test
    public void testListenerIsInvokedForMemoryChanges() {
        ScriptingData data = newData();
        data.initialize();
        try {
            final int[] invoked = new int[]{0};
            IScriptingData.IDiskScriptsChangeListener listener = path -> invoked[0]++;
            data.addListener(5, listener);
            data.setScript(5, java.nio.file.Paths.get("main.js"), "x", IScriptingData.ChangeLocation.MEMORY);
            assertEquals(1, invoked[0]);

            data.removeListener(5, listener);
            data.setScript(5, java.nio.file.Paths.get("main.js"), "y", IScriptingData.ChangeLocation.MEMORY);
            assertEquals(1, invoked[0]);
        } finally {
            data.close();
        }
    }

    @Test
    public void testListenerMayRemoveItselfDuringDispatch() {
        // M7 regression: a script variable removes its own disk listener when it is
        // invalidated, which used to throw a ConcurrentModificationException in the
        // middle of a script write and abort the change notification.
        ScriptingData data = newData();
        data.initialize();
        try {
            final int[] first = new int[]{0};
            final int[] second = new int[]{0};
            final IScriptingData.IDiskScriptsChangeListener[] selfRemoving =
                    new IScriptingData.IDiskScriptsChangeListener[1];
            selfRemoving[0] = path -> {
                first[0]++;
                data.removeListener(5, selfRemoving[0]);
            };
            IScriptingData.IDiskScriptsChangeListener other = path -> second[0]++;
            data.addListener(5, selfRemoving[0]);
            data.addListener(5, other);

            data.setScript(5, java.nio.file.Paths.get("main.js"), "x", IScriptingData.ChangeLocation.MEMORY);
            assertEquals(1, first[0]);
            assertEquals(1, second[0]);

            // The self-removing listener is gone, the other one still receives changes.
            data.setScript(5, java.nio.file.Paths.get("main.js"), "y", IScriptingData.ChangeLocation.MEMORY);
            assertEquals(1, first[0]);
            assertEquals(2, second[0]);
        } finally {
            data.close();
        }
    }

    @Test
    public void testDirtyPathIsRegistered() {
        ScriptingData data = newData();
        data.markDirty(7, java.nio.file.Paths.get("main.js"));
        assertTrue(data.getDirtyPaths().hasNext());
    }

    private static void awaitCondition(Condition condition) throws InterruptedException {
        assertTrue("Timed out waiting for condition", awaitCondition(condition, AWAIT_TIMEOUT_MS));
    }

    private static boolean awaitCondition(Condition condition, int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.matches()) {
                return true;
            }
            Thread.sleep(POLLING_INTERVAL_MS);
        }
        return condition.matches();
    }

    @FunctionalInterface
    private interface Condition {
        boolean matches();
    }

}

