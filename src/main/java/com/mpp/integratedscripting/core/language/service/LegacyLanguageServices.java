package com.mpp.integratedscripting.core.language.service;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.ILanguageHighlighter;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.edit.ICommentStyle;
import com.mpp.integratedscripting.api.language.edit.IEditSupport;
import com.mpp.integratedscripting.api.language.highlight.HighlightState;
import com.mpp.integratedscripting.api.language.highlight.IHighlightService;
import com.mpp.integratedscripting.api.language.highlight.ILineTokenizer;
import com.mpp.integratedscripting.api.language.highlight.LineTokens;
import com.mpp.integratedscripting.api.language.highlight.Token;
import com.mpp.integratedscripting.api.language.highlight.TokenType;
import com.mpp.integratedscripting.api.language.service.ILanguageServiceRegistry;
import com.mpp.integratedscripting.api.language.service.LanguageServiceKeys;
import com.mpp.integratedscripting.api.language.text.DocumentText;
import com.mpp.integratedscripting.callback.TextStyle;
import com.mpp.integratedscripting.core.language.LanguageHandlerRegistry;
import net.minecraft.util.text.TextFormatting;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Adapts a legacy {@link ILanguageHandler} to the new language services.
 *
 * <p>The language API used to put highlighting, lexical context and comment rules
 * on the handler itself. A handler that still overrides any of those methods is
 * detected here and gets the corresponding service registered, so the editor can
 * drop its handler lookups while old (including third-party, binary-only)
 * handlers keep working. The detection uses reflection on the concrete class,
 * which also means a method that FML stripped for the current side is correctly
 * treated as absent.</p>
 *
 * @author rubensworks
 */
public final class LegacyLanguageServices {

    private static final String METHOD_GET_HIGHLIGHTER = "getHighlighter";
    private static final String METHOD_MARKUP_LINE = "markupLine";
    private static final String METHOD_GET_LEXICAL_CONTEXT = "getLexicalContext";
    private static final String METHOD_GET_LINE_COMMENT_PREFIX = "getLineCommentPrefix";

    private LegacyLanguageServices() {
    }

    /**
     * Bridge the legacy capabilities of one handler into services, replacing only
     * the services the handler actually implements.
     * @param services The service registry to register into.
     * @param language The handler's language id.
     * @param handler The handler.
     */
    public static void bridge(ILanguageServiceRegistry services, LanguageId language,
                              ILanguageHandler handler) {
        if (services == null || language == null || handler == null) {
            return;
        }
        if (handler instanceof IHighlightService) {
            services.registerService(language, LanguageServiceKeys.HIGHLIGHT,
                    (IHighlightService) handler);
        } else {
            boolean hasDocumentHighlighter = overrides(handler, METHOD_GET_HIGHLIGHTER);
            boolean hasLineHighlighter = overrides(handler, METHOD_MARKUP_LINE, String.class);
            boolean hasLexicalContext = overrides(handler, METHOD_GET_LEXICAL_CONTEXT,
                    String.class, int.class);
            if (hasDocumentHighlighter || hasLineHighlighter || hasLexicalContext) {
                services.registerService(language, LanguageServiceKeys.HIGHLIGHT,
                        new LegacyHighlightService(handler, hasDocumentHighlighter));
            }
        }
        if (overrides(handler, METHOD_GET_LINE_COMMENT_PREFIX)) {
            services.registerService(language, LanguageServiceKeys.EDIT,
                    new LegacyEditSupport(handler));
        }
    }

    /**
     * Whether the concrete handler class declares the given method (anywhere in
     * its class hierarchy above the interface defaults).
     * @param handler The handler.
     * @param methodName The method name.
     * @param parameterTypes The parameter types.
     * @return Whether the handler overrides the interface default.
     */
    public static boolean overrides(ILanguageHandler handler, String methodName,
                                    Class<?>... parameterTypes) {
        Class<?> type = handler.getClass();
        while (type != null && type != Object.class) {
            try {
                Method method = type.getDeclaredMethod(methodName, parameterTypes);
                return method.getDeclaringClass() != ILanguageHandler.class;
            } catch (NoSuchMethodException e) {
                type = type.getSuperclass();
            } catch (SecurityException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * Convert a legacy {@link TextFormatting} style to a semantic token type.
     * @param style The style; {@code null} means plain text.
     * @return The token type.
     */
    public static TokenType tokenTypeOf(@Nullable TextFormatting style) {
        if (style == null) {
            return TokenType.DEFAULT;
        }
        if (style == TextStyle.KEYWORD) {
            return TokenType.KEYWORD;
        }
        if (style == TextStyle.COMMENT) {
            return TokenType.COMMENT;
        }
        if (style == TextStyle.ATTRIBUTE) {
            return TokenType.PROPERTY;
        }
        if (style == TextStyle.SYMBOL) {
            return TokenType.SYMBOL;
        }
        if (style == TextStyle.CONSTANT) {
            return TokenType.STRING;
        }
        return TokenType.DEFAULT;
    }

    /**
     * Convert legacy styled segments into covering, non-overlapping tokens.
     * @param segments The segments.
     * @param length The line length the tokens must cover.
     * @return The tokens; empty for an empty line.
     */
    public static List<Token> toTokens(@Nullable List<Pair<TextFormatting, String>> segments,
                                       int length) {
        if (length <= 0) {
            return Collections.emptyList();
        }
        List<Token> tokens = new ArrayList<>();
        int offset = 0;
        if (segments != null) {
            for (Pair<TextFormatting, String> segment : segments) {
                if (segment == null || segment.getRight() == null || segment.getRight().isEmpty()) {
                    continue;
                }
                if (offset >= length) {
                    break;
                }
                int end = Math.min(length, offset + segment.getRight().length());
                add(tokens, offset, end, tokenTypeOf(segment.getLeft()));
                offset = end;
            }
        }
        if (offset < length) {
            // A legacy handler that returned fewer characters than the line would
            // leave a hole; cover it with plain text so the editor can slice it.
            add(tokens, offset, length, TokenType.DEFAULT);
        }
        return tokens;
    }

    private static void add(List<Token> tokens, int start, int end, TokenType type) {
        if (end <= start) {
            return;
        }
        if (!tokens.isEmpty()) {
            Token last = tokens.get(tokens.size() - 1);
            if (last.getType() == type && last.getEnd() == start) {
                tokens.set(tokens.size() - 1, new Token(last.getStart(), end, type));
                return;
            }
        }
        tokens.add(new Token(start, end, type));
    }

    /**
     * {@link IHighlightService} over a legacy handler.
     */
    @SuppressWarnings("deprecation")
    public static final class LegacyHighlightService implements IHighlightService {

        private final ILanguageHandler handler;
        private final boolean hasDocumentHighlighter;
        private final ILineTokenizer tokenizer;

        private LegacyHighlightService(ILanguageHandler handler, boolean hasDocumentHighlighter) {
            this.handler = handler;
            this.hasDocumentHighlighter = hasDocumentHighlighter;
            this.tokenizer = new ILineTokenizer() {
                @Override
                public HighlightState initialState() {
                    return null;
                }

                @Override
                public LineTokens tokenizeLine(@Nullable String line, @Nullable HighlightState startState) {
                    String text = line == null ? "" : line;
                    return LineTokens.of(toTokens(LegacyHighlightService.this.handler.markupLine(text),
                            text.length()), null);
                }
            };
        }

        @Override
        public ILineTokenizer getTokenizer() {
            return tokenizer;
        }

        @Override
        public int lexicalContextAt(DocumentText document, int offset) {
            return handler.getLexicalContext(document.getText(), offset);
        }

        @Override
        public List<List<Token>> highlight(@Nullable DocumentText document) {
            if (document == null) {
                return Collections.emptyList();
            }
            if (!hasDocumentHighlighter) {
                return IHighlightService.super.highlight(document);
            }
            try {
                ILanguageHighlighter highlighter = handler.getHighlighter();
                if (highlighter == null) {
                    return IHighlightService.super.highlight(document);
                }
                List<List<Pair<TextFormatting, String>>> legacy =
                        highlighter.highlightDocument(document.getText());
                if (legacy == null) {
                    return IHighlightService.super.highlight(document);
                }
                List<List<Token>> lines = new ArrayList<>(legacy.size());
                for (int line = 0; line < legacy.size(); line++) {
                    int length = line < document.getLineCount() ? document.getLine(line).length() : 0;
                    lines.add(toTokens(legacy.get(line), length));
                }
                // The document highlighter contract is one entry per line; keep the
                // editor's slicing safe if a legacy implementation returned fewer.
                while (lines.size() < document.getLineCount()) {
                    lines.add(toTokens(null, document.getLine(lines.size()).length()));
                }
                return lines;
            } catch (RuntimeException e) {
                // A broken legacy highlighter must not break drawing.
                return IHighlightService.super.highlight(document);
            } catch (LinkageError e) {
                // The @SideOnly method was stripped on this side.
                return IHighlightService.super.highlight(document);
            }
        }

        @Override
        public String toString() {
            return "LegacyHighlightService(" + handler.getName() + ")";
        }

    }

    /**
     * {@link IEditSupport} over a legacy handler.
     */
    public static final class LegacyEditSupport implements IEditSupport {

        private final ILanguageHandler handler;

        private LegacyEditSupport(ILanguageHandler handler) {
            this.handler = handler;
        }

        @Override
        @SuppressWarnings("deprecation")
        public ICommentStyle getCommentStyle() {
            final String prefix = handler.getLineCommentPrefix();
            if (prefix == null || prefix.isEmpty()) {
                return IEditSupport.super.getCommentStyle();
            }
            final boolean cStyle = "//".equals(prefix);
            return new ICommentStyle() {
                @Override
                public String getLineComment() {
                    return prefix;
                }

                @Override
                public String getBlockCommentOpen() {
                    return cStyle ? "/*" : null;
                }

                @Override
                public String getBlockCommentClose() {
                    return cStyle ? "*/" : null;
                }
            };
        }

        @Override
        public String getDefaultFileExtension() {
            List<String> extensions = handler.getExtensions();
            if (extensions != null && !extensions.isEmpty()) {
                String extension = LanguageHandlerRegistry.normalizeExtension(extensions.get(0));
                if (extension != null) {
                    return extension;
                }
            }
            return IEditSupport.super.getDefaultFileExtension();
        }

        @Override
        public String toString() {
            return "LegacyEditSupport(" + handler.getName() + ")";
        }

    }

}
