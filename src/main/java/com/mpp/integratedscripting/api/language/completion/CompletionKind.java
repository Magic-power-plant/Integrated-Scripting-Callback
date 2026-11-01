package com.mpp.integratedscripting.api.language.completion;

/**
 * The semantic class of a completion candidate, used by the popup to pick an
 * icon or a colour.
 *
 * <p>This enum is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public enum CompletionKind {

    /** A reserved word of the language. */
    KEYWORD,
    /** A value declared in the document. */
    VARIABLE,
    /** A function or method. */
    FUNCTION,
    /** A property of an object. */
    PROPERTY,
    /** An operator. */
    OPERATOR,
    /** A module or namespace. */
    MODULE

}
