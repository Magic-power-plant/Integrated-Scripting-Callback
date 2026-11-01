package com.mpp.integratedscripting.client.gui.component.input;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guard for the editor's render path (docs/LANGUAGE-API-REFACTOR.md §7.5:
 * "编辑器绘制路径零语言注册表查找").
 *
 * <p>Drawing and key handling must read the cached {@code ILanguageServices}
 * bundle only. This is a code-review guard, as the plan allows: it scans the
 * sources so a future change that reintroduces a per-frame registry lookup fails
 * the build.</p>
 */
public class EditorRenderPathTest {

    private static final String WIDGET =
            "src/main/java/com/mpp/integratedscripting/client/gui/component/input/GuiWidgetTextArea.java";
    private static final String TERMINAL =
            "src/main/java/com/mpp/integratedscripting/client/gui/container/GuiTerminalScripting.java";

    @Test
    public void testEditorWidgetNeverLooksUpALanguage() throws IOException {
        String source = read(WIDGET);
        assertFalse("GuiWidgetTextArea must not touch the language registries",
                source.contains("LanguageHandlers"));
        assertFalse("GuiWidgetTextArea must not touch the language registries",
                source.contains("LanguageHandlerRegistry"));
        assertFalse("GuiWidgetTextArea must not call the legacy handler API",
                source.contains("ILanguageHandler"));
        assertFalse("GuiWidgetTextArea must not call the legacy handler API",
                source.contains("markupLine"));
        assertFalse("GuiWidgetTextArea must not call the legacy handler API",
                source.contains("getHighlighter"));
        assertFalse("GuiWidgetTextArea must not look files up in the registry",
                source.contains("getProvider("));
        // It receives the resolved bundle instead.
        assertTrue(source, source.contains("setLanguageServices"));
    }

    @Test
    public void testTerminalGuiOnlyResolvesLanguagesWhenTheFileChanges() throws IOException {
        String source = read(TERMINAL);
        assertFalse("The terminal must not query the handler registry directly",
                source.contains("LanguageHandlers.REGISTRY"));
        assertFalse("The terminal must not look files up in the registry",
                source.contains("getProvider("));
        assertFalse("The terminal must not call the legacy handler API",
                source.contains("markupLine"));
        assertFalse("The terminal must not call the legacy handler API",
                source.contains("getHighlighter"));
        assertFalse("The terminal must not call the legacy handler API",
                source.contains("getLineCommentPrefix"));
        assertTrue(source, source.contains("LanguageHandlers.getServices("));
    }

    @Test
    public void testContainerTakesTheMemberRuleAndExtensionFromTheLanguage() throws IOException {
        String source = read("src/main/java/com/mpp/integratedscripting/inventory/container/"
                + "ContainerTerminalScripting.java");
        assertFalse("The member name rule moved to IIdentifierSyntax",
                source.contains("INVALID_MEMBER_NAME"));
        assertFalse("The new-file extension moved to IEditSupport",
                source.contains("script\" + i++ + \".js\""));
        assertTrue(source, source.contains("isValidMemberName"));
        assertTrue(source, source.contains("getDefaultFileExtension"));
    }

    private static String read(String source) throws IOException {
        Path path = Paths.get(source);
        assertTrue("Missing source file: " + path.toAbsolutePath(), Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

}
