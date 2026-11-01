package com.mpp.integratedscripting.api.language.highlight;

/**
 * The semantic class of one highlighted span of a document.
 *
 * <p>Token types are language independent; a language's lexer decides which one
 * applies. The client maps them to colours in {@code TokenTypePalette}, which
 * keeps colour decisions out of the common API (the old
 * {@code TextFormatting}-based contract leaked 1.12.2's colour codes into it).</p>
 *
 * <p>This enum is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public enum TokenType {

    /** Plain text: identifiers, whitespace, anything without a meaning yet. */
    DEFAULT,
    /** A reserved word of the language. */
    KEYWORD,
    /** A line or block comment. */
    COMMENT,
    /** A string, template or regular-expression literal. */
    STRING,
    /** A numeric literal. */
    NUMBER,
    /** A property or method name after a member access, for example {@code obj.name}. */
    PROPERTY,
    /** A symbol that is neither a pure operator nor plain punctuation. */
    SYMBOL,
    /** An operator, for example {@code +}, {@code ===} or {@code &&}. */
    OPERATOR,
    /** Punctuation, for example a bracket, comma, semicolon or dot. */
    PUNCTUATION

}
