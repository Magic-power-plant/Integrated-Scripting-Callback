package com.mpp.integratedscripting.api.language.highlight;

/**
 * One highlighted span of a line: a half-open {@code [start, end)} character
 * range with a {@link TokenType}.
 *
 * <p>Tokens of a line never overlap and concatenate back to the line, which is
 * what lets the editor slice a wrapped visual line out of its logical tokens.</p>
 *
 * <p>This class is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
public final class Token {

    private final int start;
    private final int end;
    private final TokenType type;

    /**
     * @param start The inclusive start offset inside the line.
     * @param end The exclusive end offset inside the line.
     * @param type The semantic token type; {@code null} means {@link TokenType#DEFAULT}.
     */
    public Token(int start, int end, TokenType type) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid token range [" + start + ", " + end + ").");
        }
        this.start = start;
        this.end = end;
        this.type = type == null ? TokenType.DEFAULT : type;
    }

    /**
     * @return The inclusive start offset inside the line.
     */
    public int getStart() {
        return start;
    }

    /**
     * @return The exclusive end offset inside the line.
     */
    public int getEnd() {
        return end;
    }

    /**
     * @return The number of characters this token covers.
     */
    public int getLength() {
        return end - start;
    }

    /**
     * @return The semantic token type; never {@code null}.
     */
    public TokenType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + "[" + start + ", " + end + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Token)) {
            return false;
        }
        Token that = (Token) other;
        return start == that.start && end == that.end && type == that.type;
    }

    @Override
    public int hashCode() {
        return (31 * start + end) * 31 + type.hashCode();
    }

}
