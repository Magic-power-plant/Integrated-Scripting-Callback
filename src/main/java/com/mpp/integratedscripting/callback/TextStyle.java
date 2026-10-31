package com.mpp.integratedscripting.callback;

import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 1.12.2-only: maps the reference implementation's RGB syntax-highlighting
 * styles to the 16 colours available in {@link TextFormatting}
 * (docs/PORTING-1.12.2.md §7.9.3).
 *
 * <p>The {@link TextFormatting} constants are the semantic <em>tags</em> that
 * {@code ILanguageHandler#markupLine} returns, so consumers outside the terminal
 * editor (chat text, error messages) can keep using formatting codes.</p>
 *
 * <p>The editor itself does not draw those codes: the terminal background
 * texture paints the editor panel in {@link #EDITOR_BACKGROUND_RGB}, a light
 * grey. The bright colour codes that {@code TextFormatting} resolves to there
 * ({@code GRAY} → 0xAAAAAA, {@code GREEN} → 0x55FF55, {@code AQUA} → 0x55FFFF)
 * only reach 1.8–2.4:1 contrast on that panel, and the previous light default
 * (0xE0E0E0) reached 1.01:1 — the same colour as the panel, i.e. invisible.
 * The editor therefore draws the tuned {@code *_RGB} colours below, which keep
 * the reference hue and reach at least 4.5:1 (WCAG AA for body text);
 * {@code TextStyleContrastTest} guards that.</p>
 *
 * <p>{@link #DEFAULT} is {@code null}, which means "use the GUI's default text
 * colour" and is what 1.12's {@code Style.EMPTY} corresponds to.</p>
 *
 * @author rubensworks
 */
public final class TextStyle {

    /** The light editor panel of {@code textures/gui/terminal_scripting.png}. */
    public static final int EDITOR_BACKGROUND_RGB = 0xE1E1E1;

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

    // Editor palette: the reference hue, darkened for the light editor panel.
    // The contrast ratio against EDITOR_BACKGROUND_RGB is noted per colour.
    /** Purple, 5.9:1. */
    public static final int ATTRIBUTE_RGB = 0x5E4880;
    /** Gray, 4.9:1. */
    public static final int COMMENT_RGB = 0x5F5F5F;
    /** Green, 4.9:1. */
    public static final int SYMBOL_RGB = 0x2E6B32;
    /** Blue, 5.4:1. */
    public static final int CONSTANT_RGB = 0x00607F;
    /** Red-purple, 5.4:1. */
    public static final int KEYWORD_RGB = 0xA71D5D;
    /** Plain text, 12.5:1. */
    public static final int DEFAULT_RGB = 0x202020;

    private static final Map<TextFormatting, Integer> RGB_BY_STYLE = createRgbByStyle();

    private static Map<TextFormatting, Integer> createRgbByStyle() {
        Map<TextFormatting, Integer> map = new EnumMap<>(TextFormatting.class);
        map.put(ATTRIBUTE, ATTRIBUTE_RGB);
        map.put(COMMENT, COMMENT_RGB);
        map.put(SYMBOL, SYMBOL_RGB);
        map.put(CONSTANT, CONSTANT_RGB);
        map.put(KEYWORD, KEYWORD_RGB);
        return Collections.unmodifiableMap(map);
    }

    private TextStyle() {
    }

    /**
     * Resolve the colour a GUI should draw one token style with.
     * @param style A token style, or {@code null} for plain text.
     * @param fallback The colour to use for a style that has no tuned RGB
     *                 (any other {@link TextFormatting} value).
     * @return {@link #DEFAULT_RGB} for {@code null}, the tuned RGB for the
     *         semantic styles, otherwise {@code fallback}.
     */
    public static int rgbOf(@Nullable TextFormatting style, int fallback) {
        if (style == null) {
            return DEFAULT_RGB;
        }
        Integer rgb = RGB_BY_STYLE.get(style);
        return rgb == null ? fallback : rgb;
    }

}
