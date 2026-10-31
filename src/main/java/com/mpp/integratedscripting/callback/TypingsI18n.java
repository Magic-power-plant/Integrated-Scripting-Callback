package com.mpp.integratedscripting.callback;

import org.cyclops.cyclopscore.helper.MinecraftHelpers;

/**
 * 1.12.2-only: localization that is safe on a dedicated server.
 *
 * <p>The 1.20 reference implementation uses {@code DistExecutor.callWhenOn},
 * which does not exist in 1.12.2. Client classes are isolated in a nested class
 * so a dedicated server never loads them; the server falls back to the
 * (deprecated, but server-safe) vanilla {@code I18n} translator
 * (docs/PORTING-1.12.2.md §6.16, §6.27).</p>
 *
 * @author rubensworks
 */
public final class TypingsI18n {

    private TypingsI18n() {
    }

    public static boolean canTranslate(String translationKey) {
        return MinecraftHelpers.isClientSide()
                ? Client.canTranslate(translationKey)
                : Server.canTranslate(translationKey);
    }

    public static String translate(String translationKey) {
        return MinecraftHelpers.isClientSide()
                ? Client.translate(translationKey)
                : Server.translate(translationKey);
    }

    /**
     * Client-only localization; loaded only when {@code isClientSide()} is true.
     */
    private static final class Client {

        private static boolean canTranslate(String translationKey) {
            return net.minecraft.client.resources.I18n.hasKey(translationKey);
        }

        private static String translate(String translationKey) {
            return net.minecraft.client.resources.I18n.format(translationKey);
        }

    }

    /**
     * Server-safe localization (reads the bundled en_us language file).
     */
    private static final class Server {

        @SuppressWarnings("deprecation")
        private static boolean canTranslate(String translationKey) {
            return net.minecraft.util.text.translation.I18n.canTranslate(translationKey);
        }

        @SuppressWarnings("deprecation")
        private static String translate(String translationKey) {
            return net.minecraft.util.text.translation.I18n.translateToLocal(translationKey);
        }

    }

}
