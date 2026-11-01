package com.mpp.integratedscripting.core.language;

import com.mpp.Reference;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests the server-start typings task (docs/LANGUAGE-API-REFACTOR.md Phase 3).
 */
public class TypeScriptTypingsTaskTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testFileName() {
        assertEquals(Reference.MOD_ID + ".d.ts", TypeScriptTypingsTask.FILE_NAME);
    }

    @Test
    public void testWriteCreatesTheFileAndItsDirectory() throws IOException {
        Path root = temporaryFolder.getRoot().toPath().resolve("nested").resolve("scripts");
        TypeScriptTypingsTask task = new TypeScriptTypingsTask();
        Path written = task.write(root, "declare global {}\n");

        assertEquals(root.resolve(TypeScriptTypingsTask.FILE_NAME), written);
        assertTrue(Files.isRegularFile(written));
        assertEquals("declare global {}\n",
                new String(Files.readAllBytes(written), StandardCharsets.UTF_8));
    }

    @Test
    public void testWriteOverwritesAnExistingFile() throws IOException {
        Path root = temporaryFolder.getRoot().toPath();
        TypeScriptTypingsTask task = new TypeScriptTypingsTask();
        task.write(root, "old");
        Path written = task.write(root, "new");
        assertEquals("new", new String(Files.readAllBytes(written), StandardCharsets.UTF_8));
    }

    @Test
    public void testOnServerStartedIsSafeWithoutServerData() {
        // In unit tests there is no mod instance, so the task must only log and
        // return instead of throwing.
        Path root = temporaryFolder.getRoot().toPath();
        try {
            new TypeScriptTypingsTask().onServerStarted();
            assertFalse(Files.exists(root.resolve(TypeScriptTypingsTask.FILE_NAME)));
        } catch (RuntimeException e) {
            throw new AssertionError("The server-start task must never throw", e);
        }
    }

}
