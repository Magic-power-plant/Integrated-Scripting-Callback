package com.mpp.integratedscripting.client.language;

import com.mpp.integratedscripting.api.language.ILanguageHandler;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.client.gui.image.ScriptImages;
import com.mpp.integratedscripting.core.language.LanguageHandlers;
import com.mpp.integratedscripting.core.language.service.LegacyLanguageServices;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.client.gui.image.IImage;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The client-side language icon registry and its default registrations.
 *
 * <p>The common language API used to expose {@code ILanguageHandler#getIcon()},
 * which returns the client-only {@code IImage} type and therefore could not be
 * loaded on a dedicated server. Icons now live here, and
 * {@link #registerDefaults()} bridges the legacy method of any handler that still
 * overrides it.</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public final class ClientLanguageIcons {

    private static final ILanguageIconRegistry REGISTRY = new Registry();

    private ClientLanguageIcons() {
    }

    /**
     * @return The client language icon registry; never {@code null}.
     */
    public static ILanguageIconRegistry getRegistry() {
        return REGISTRY;
    }

    /**
     * Register this mod's icons and bridge legacy handler icons. Called once from
     * the client proxy; safe to call more than once.
     */
    public static void registerDefaults() {
        REGISTRY.registerIcon(LanguageId.JAVASCRIPT, ScriptImages.FILE_JS);
        REGISTRY.registerIcon(LanguageId.TYPESCRIPT, ScriptImages.FILE_TS);
    }

    private static final class Registry implements ILanguageIconRegistry {

        private final Map<LanguageId, IImage> icons = new HashMap<>();

        @Override
        public void registerIcon(LanguageId language, IImage icon) {
            if (language == null || icon == null) {
                return;
            }
            this.icons.put(language, icon);
        }

        @Override
        public IImage getIcon(LanguageId language) {
            IImage icon = language == null ? null : this.icons.get(language);
            if (icon != null) {
                return icon;
            }
            icon = legacyIcon(language);
            return icon == null ? ScriptImages.FILE_OTHER : icon;
        }

        @SuppressWarnings("deprecation")
        @Nullable
        private IImage legacyIcon(@Nullable LanguageId language) {
            if (language == null) {
                return null;
            }
            ILanguageHandler handler = LanguageHandlers.REGISTRY.getHandler(language);
            if (handler == null || !LegacyLanguageServices.overrides(handler, "getIcon")) {
                return null;
            }
            return handler.getIcon();
        }

    }

}
