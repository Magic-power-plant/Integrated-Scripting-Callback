package com.mpp.integratedscripting.api.language;

import com.mpp.integratedscripting.api.network.IScriptFactory;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.Pair;
import org.cyclops.cyclopscore.client.gui.image.IImage;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;

import java.util.Collections;
import java.util.List;

/**
 * Identity and runtime of a scripting language.
 *
 * <p>Since the language service layer was introduced
 * (docs/LANGUAGE-API-REFACTOR.md) this interface is deliberately thin: a
 * language provides its stable id, its file extensions and its script factory.
 * Everything else - highlighting, editing rules, completion, diagnostics, icons,
 * server-start side effects - is a service or contributor registered against the
 * language id, so adding a capability never changes this interface and never
 * touches the editor.</p>
 *
 * <p>A minimal new language therefore only has to implement
 * {@link #getName()}, {@link #getExtensions()} and
 * {@link #getScriptFactory()}; the default methods below provide everything
 * else. Override {@link #getLanguageId()} when the derived id
 * ({@code integratedscriptingcallback:<slug of getName()>}) is not what you want.</p>
 *
 * <p>The legacy members at the bottom of this file are a one-minor-version
 * compatibility bridge: the registry converts an overriding legacy handler into
 * the corresponding service, so old handlers keep working unchanged. New code
 * must not call them; they are removed in 2.0.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public interface ILanguageHandler {

    /**
     * @return The mod id that owns this language; override this (and
     *         {@link #getLanguageId()}) in a third-party language.
     */
    default String getLanguageNamespace() {
        return LanguageId.DEFAULT_NAMESPACE;
    }

    /**
     * The stable id of this language, used to attach services and contributors
     * and to cache the resolved service bundle.
     * @return The language id; derived from the namespace and {@link #getName()}
     *         by default.
     */
    default LanguageId getLanguageId() {
        return LanguageId.of(getLanguageNamespace(), LanguageId.slug(getName()));
    }

    /**
     * @return The human readable language name.
     */
    String getName();

    /**
     * @return The file extensions (without dot) this language handles. Extensions
     *         are matched case insensitively.
     */
    List<String> getExtensions();

    /**
     * @return The factory that creates runnable scripts of this language.
     * @throws EvaluationException When the runtime of this language is not available.
     */
    IScriptFactory getScriptFactory() throws EvaluationException;

    // ------------------------------------------------------------------
    // Legacy compatibility bridge (deprecated, removed in 2.0).
    // The registry reads these methods through LegacyLanguageServices and
    // converts them into the corresponding language service.
    // ------------------------------------------------------------------

    /**
     * @param line One line of the document.
     * @return The styled segments of that line.
     * @deprecated Implement {@code IHighlightService} instead; this method is only
     *             read by the legacy bridge.
     */
    @Deprecated
    default List<Pair<TextFormatting, String>> markupLine(String line) {
        return Collections.singletonList(Pair.of((TextFormatting) null, line));
    }

    /**
     * A document-wide highlighter, or {@code null} when this language can only
     * highlight one line at a time.
     * @return The highlighter, or {@code null}.
     * @deprecated Implement {@code IHighlightService} instead; this method is only
     *             read by the legacy bridge.
     */
    @Deprecated
    @SideOnly(Side.CLIENT)
    default ILanguageHighlighter getHighlighter() {
        return null;
    }

    /**
     * The lexical context of a document position.
     * @param value The complete document.
     * @param position A character index in {@code value}.
     * @return One of the {@code LexicalContext} constants.
     * @deprecated Implement {@code IHighlightService#lexicalContextAt} instead;
     *             this method is only read by the legacy bridge.
     */
    @Deprecated
    default int getLexicalContext(String value, int position) {
        return LexicalContext.CODE;
    }

    /**
     * @return The prefix that starts a line comment.
     * @deprecated Implement {@code IEditSupport#getCommentStyle()} instead; this
     *             method is only read by the legacy bridge.
     */
    @Deprecated
    default String getLineCommentPrefix() {
        return "//";
    }

    /**
     * @return The icon shown in the terminal's file list, or {@code null} for the
     *         fallback icon.
     * @deprecated Register the icon through the client-side language icon registry
     *             ({@code ILanguageIconRegistry}) instead; this method is only read
     *             by the client bridge.
     */
    @Deprecated
    @SideOnly(Side.CLIENT)
    default IImage getIcon() {
        return null;
    }

    /**
     * Called once when the integrated server has started.
     * @deprecated Contribute an {@code IServerStartTask} instead; this method is
     *             still called once during the transition period.
     */
    @Deprecated
    default void onServerStarted() {
    }

}
