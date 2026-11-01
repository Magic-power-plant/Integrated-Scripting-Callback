package com.mpp.integratedscripting.core.language.service;

import com.mpp.integratedscripting.api.language.completion.CompletionContext;
import com.mpp.integratedscripting.api.language.completion.CompletionItem;
import com.mpp.integratedscripting.api.language.completion.ICompletionService;
import com.mpp.integratedscripting.api.language.diagnostics.Diagnostic;
import com.mpp.integratedscripting.api.language.diagnostics.IDiagnosticService;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.HighlightState;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.highlight.LineTokens;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.text.DocumentText;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * The default (null-object) implementations behind the built-in service keys.
 *
 * <p>These make the "nothing implemented" case work: a language that only
 * declares its identity and its script factory still gets plain highlighting,
 * sane editing rules, an empty completion list and no diagnostics, so the
 * editor never has to check whether a service exists.</p>
 *
 * @author rubensworks
 */
public final class DefaultServices {

    /** Highlights every line as one plain token. */
    public static final IHighlightService PLAIN_HIGHLIGHT = new PlainHighlightService();

    /** Four-space indentation, {@code //} comments, ASCII identifiers, {@code txt} files. */
    public static final IEditSupport PLAIN_EDIT = new PlainEditSupport();

    /** Offers no candidates. */
    public static final ICompletionService EMPTY_COMPLETION = new EmptyCompletionService();

    /** Reports no problems. */
    public static final IDiagnosticService EMPTY_DIAGNOSTICS = new EmptyDiagnosticService();

    private DefaultServices() {
    }

    /**
     * A highlighter that emits one {@link TokenType#DEFAULT} token per non-empty
     * line and always reports {@code LexicalContext.CODE}.
     */
    public static final class PlainHighlightService implements IHighlightService {

        private static final ILineTokenizer TOKENIZER = new ILineTokenizer() {
            @Override
            public HighlightState initialState() {
                return null;
            }

            @Override
            public LineTokens tokenizeLine(@Nullable String line, @Nullable HighlightState startState) {
                String text = line == null ? "" : line;
                if (text.isEmpty()) {
                    return LineTokens.of(Collections.<Token>emptyList(), null);
                }
                return LineTokens.of(Collections.singletonList(
                        new Token(0, text.length(), TokenType.DEFAULT)), null);
            }
        };

        @Override
        public ILineTokenizer getTokenizer() {
            return TOKENIZER;
        }

        @Override
        public List<List<Token>> highlight(@Nullable DocumentText document) {
            return IHighlightService.super.highlight(document);
        }

    }

    /**
     * The neutral editing rules: interface defaults, plus the {@code txt}
     * extension for a language that does not say otherwise.
     */
    public static final class PlainEditSupport implements IEditSupport {

        @Override
        public String getIndentUnit() {
            return "    ";
        }

        @Override
        public String getDefaultFileExtension() {
            return "txt";
        }

    }

    /**
     * A completion service without candidates.
     */
    public static final class EmptyCompletionService implements ICompletionService {

        @Override
        public List<CompletionItem> complete(CompletionContext context) {
            return Collections.emptyList();
        }

    }

    /**
     * A diagnostic service without findings.
     */
    public static final class EmptyDiagnosticService implements IDiagnosticService {

        @Override
        public List<Diagnostic> diagnose(DocumentText document) {
            return Collections.emptyList();
        }

    }

}
