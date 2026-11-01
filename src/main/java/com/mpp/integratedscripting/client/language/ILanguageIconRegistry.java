package com.mpp.integratedscripting.client.language;

import com.mpp.integratedscripting.api.language.LanguageId;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.cyclops.cyclopscore.client.gui.image.IImage;
import org.cyclops.cyclopscore.init.IRegistry;

/**
 * Client-only registry of the icon shown for a language in the terminal's file
 * list.
 *
 * <p>Icons are a client presentation detail, so they deliberately do not live in
 * the common language service keys: the common API must not reference client
 * classes such as {@code IImage}, because FML strips client-only types on a
 * dedicated server. A language registers its icon from client initialisation;
 * when it registers nothing, the fallback file icon is used.</p>
 *
 * <p>This interface is part of the stable API (docs/LANGUAGE-API.md).</p>
 *
 * @author rubensworks
 */
@SideOnly(Side.CLIENT)
public interface ILanguageIconRegistry extends IRegistry {

    /**
     * Register (or replace) the icon of one language.
     * @param language The language.
     * @param icon The icon.
     */
    void registerIcon(LanguageId language, IImage icon);

    /**
     * @param language The language.
     * @return The registered icon, the handler's legacy icon, or the fallback file
     *         icon; never {@code null}.
     */
    IImage getIcon(LanguageId language);

}
