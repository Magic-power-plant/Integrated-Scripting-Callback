package com.mpp.integratedscripting.core.language;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.language.service.IServerStartTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the TypeScript typings of the scripting API into the world's scripting
 * data directory when the server starts.
 *
 * <p>This was a side effect of {@code LanguageHandlerJavaScript#onServerStarted()}.
 * It is a contributed {@link IServerStartTask} now, so the language handler stays
 * a thin description of the language, the task can be tested against a temporary
 * directory, and a mod can disable or replace it by not registering it.</p>
 *
 * @author rubensworks
 */
public final class TypeScriptTypingsTask implements IServerStartTask {

    /** The file name inside the scripting data root. */
    public static final String FILE_NAME = Reference.MOD_ID + ".d.ts";

    @Override
    public void onServerStarted() {
        IntegratedScriptingCallback instance = IntegratedScriptingCallback._instance;
        if (instance == null || instance.scriptingData == null) {
            IntegratedScriptingCallback.clog(
                    "Skipping TypeScript typings generation: scripting data is not available.");
            return;
        }
        Path root = instance.scriptingData.getRootPath();
        try {
            Path path = write(root);
            IntegratedScriptingCallback.clog("Wrote TypeScript typings to " + path + ".");
        } catch (IOException e) {
            IntegratedScriptingCallback.clog("Failed to write TypeScript typings to " + root
                    + ": " + e.getMessage());
        }
    }

    /**
     * Generate the typings and write them into a directory.
     * @param rootPath The directory that receives the {@code .d.ts} file; created
     *                 when it does not exist.
     * @return The path of the written file.
     * @throws IOException When the file cannot be written.
     */
    public Path write(Path rootPath) throws IOException {
        return write(rootPath, new TypeScriptTypingsGenerator().generate());
    }

    /**
     * Write already generated typings into a directory. Separated from the
     * generator so the file contract can be tested without the game's registries.
     * @param rootPath The directory that receives the {@code .d.ts} file; created
     *                 when it does not exist.
     * @param content The typings content, in UTF-8.
     * @return The path of the written file.
     * @throws IOException When the file cannot be written.
     */
    public Path write(Path rootPath, String content) throws IOException {
        Path path = rootPath.resolve(FILE_NAME);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(path, (content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        return path;
    }

    @Override
    public String toString() {
        return "TypeScriptTypingsTask(" + FILE_NAME + ")";
    }

}
