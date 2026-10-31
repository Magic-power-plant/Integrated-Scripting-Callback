package com.mpp;

/**
 * Basic static constants that are better not hard-coded: mod details, mod IDs
 * and the version-check URL.
 *
 * <p>The mod identity is fixed (see docs/PORTING-1.12.2.md, decision D1): every
 * in-game namespace is derived from {@link #MOD_ID}.</p>
 *
 * @author rubensworks
 */
@SuppressWarnings("javadoc")
public class Reference {

    // Mod info
    public static final String MOD_ID = "integratedscriptingcallback";
    public static final String MOD_NAME = "Integrated-Scripting-Callback";
    /** Injected at build time by RFG injectTags (generated {@code com.mpp.ModMeta}). */
    public static final String MOD_VERSION = ModMeta.VERSION;

    // Mod IDs
    public static final String MOD_FORGE = "forge";
    public static final String MOD_CYCLOPSCORE = "cyclopscore";
    public static final String MOD_INTEGRATEDDYNAMICS = "integrateddynamics";
    /** Companion mod that supplies the GraalVM runtime (decision B3). */
    public static final String MOD_LIBS = "integratedscriptingcallbacklibs";

    // Decision B7: no version ranges, so different 1.12.2 builds are not rejected.
    // Audit D6: the GraalVM runtime ships as a separate mod (decision B2/B3), so it
    // is only ordered "after" instead of required: a missing runtime then degrades to
    // a localized in-game error (IntegratedScriptingCallback#init) instead of a
    // "missing dependency" crash, and dev runs without the libs mod still start.
    // Declared after MOD_LIBS on purpose: the dependency string is a constant
    // expression, and keeping the declaration order explicit avoids relying on
    // javac inlining the field.
    public static final String MOD_DEPENDENCIES =
            "required-after:cyclopscore;"
          + "required-after:commoncapabilities;"
          + "required-after:integrateddynamics;"
          + "after:" + MOD_LIBS + ";";

    // 1.12.2 version-check URL, named after this mod to avoid mixing with upstream.
    public static final String VERSION_URL =
            "https://raw.githubusercontent.com/CyclopsMC/Versions/master/1.12/IntegratedScriptingCallback.txt";
}
