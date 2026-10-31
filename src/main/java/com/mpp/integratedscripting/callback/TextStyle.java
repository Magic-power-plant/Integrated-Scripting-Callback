package com.mpp.integratedscripting.callback;

import net.minecraft.util.text.TextFormatting;

/**
 * 1.12.2-only: maps the reference implementation's RGB syntax-highlighting
 * styles to the 16 colours available in {@link TextFormatting}
 * (docs/PORTING-1.12.2.md §7.9.3).
 *
 * <p>{@link #DEFAULT} is {@code null}, which means "use the GUI's default text
 * colour" and is what 1.12's {@code Style.EMPTY} corresponds to.</p>
 *
 * @author rubensworks
 */
public final class TextStyle {

    /** Reference RGB (121, 93, 163), purple. */
    public static final TextFormatting ATTRIBUTE = TextFormatting.DARK_PURPLE;
    /** Reference RGB (150, 152, 150), gray. */
    public static final TextFormatting COMMENT = TextFormatting.GRAY;
    /** Reference RGB (99, 163, 92), green. */
    public static final TextFormatting SYMBOL = TextFormatting.GREEN;
    /** Reference RGB (0, 134, 179), blue. */
    public static final TextFormatting CONSTANT = TextFormatting.AQUA;
    /** Reference RGB (167, 29, 93), red-purple. */
    public static final TextFormatting KEYWORD = TextFormatting.LIGHT_PURPLE;
    /** Plain text: no colour code. */
    public static final TextFormatting DEFAULT = null;

    private TextStyle() {
    }

}
