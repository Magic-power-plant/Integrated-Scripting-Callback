package com.mpp.integratedscripting.core.language;

import com.mpp.IntegratedScriptingCallback;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.network.IScriptFactory;
import com.mpp.integratedscripting.callback.TextStyle;
import com.mpp.integratedscripting.client.gui.image.ScriptImages;
import com.mpp.integratedscripting.core.network.GraalScriptFactory;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.gui.image.IImage;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * @author rubensworks
 */
public class LanguageHandlerJavaScript implements ILanguageHandler {

    private final IScriptFactory scriptFactory;

    public LanguageHandlerJavaScript() {
        this.scriptFactory = new GraalScriptFactory("js");
    }

    @Override
    public String getName() {
        return "JavaScript";
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IImage getIcon() {
        return ScriptImages.FILE_JS;
    }

    @Override
    public List<String> getExtensions() {
        return Arrays.asList("js", "cjs", "mjs");
    }

    @Override
    public List<Pair<TextFormatting, String>> markupLine(String line) {
        // 1.12.2: a single-pass tokenizer colours keywords, symbols, comments,
        // string literals and numbers (docs/PORTING-1.12.2.md §7.9.3).
        List<Pair<TextFormatting, String>> segments = JavaScriptTokenizer.tokenize(line);
        if (segments.isEmpty()) {
            segments.add(Pair.of(TextStyle.DEFAULT, line));
        }
        return segments;
    }

    @Override
    public IScriptFactory getScriptFactory() throws EvaluationException {
        return this.scriptFactory;
    }

    @Override
    public void onServerStarted() {
        this.generateTypeScriptTypes();
    }

    private void generateTypeScriptTypes() {
        IntegratedScriptingCallback instance = IntegratedScriptingCallback._instance;
        if (instance == null || instance.scriptingData == null) {
            IntegratedScriptingCallback.clog(
                    "Skipping TypeScript typings generation: scripting data is not available.");
            return;
        }
        Path path = instance.scriptingData.getRootPath().resolve(Reference.MOD_ID + ".d.ts");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, new TypeScriptTypingsGenerator().generate()
                    .getBytes(StandardCharsets.UTF_8));
            IntegratedScriptingCallback.clog("Wrote TypeScript typings to " + path + ".");
        } catch (IOException e) {
            IntegratedScriptingCallback.clog("Failed to write TypeScript typings to " + path + ": "
                    + e.getMessage());
        }
    }

}
