package com.mpp.integratedscripting.core.language;

/**
 * The editing conventions of TypeScript.
 *
 * <p>Indentation, comment style and identifier syntax are identical to
 * JavaScript, so they are inherited rather than copied; the only difference is
 * the extension a new file gets when TypeScript is the default language.</p>
 *
 * @author rubensworks
 */
public final class TypeScriptEditSupport extends JavaScriptEditSupport {

    @Override
    public String getDefaultFileExtension() {
        return "ts";
    }

    @Override
    public String toString() {
        return "TypeScriptEditSupport";
    }

}
