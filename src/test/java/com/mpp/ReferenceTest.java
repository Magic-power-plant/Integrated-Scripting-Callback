package com.mpp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Invariants of the mod identity.
 *
 * <p>Decision A1 fixes the identity: a typo here changes every registry name,
 * the {@code _type} of script variable cards and the world data folder, so the
 * values are asserted instead of only being documented (docs/PORTING-1.12.2.md
 * §1.6, decision A5/A6/A7).</p>
 */
public class ReferenceTest {

    @Test
    public void testModIdentity() {
        assertEquals("integratedscriptingcallback", Reference.MOD_ID);
        assertEquals("Integrated-Scripting-Callback", Reference.MOD_NAME);
        assertFalse("The version must come from RFG injectTags", Reference.MOD_VERSION.isEmpty());
    }

    @Test
    public void testDependencyString() {
        // Decision B7: no version ranges, so every 1.12.2 build of the dependencies works.
        assertFalse("Version ranges must not be declared: " + Reference.MOD_DEPENDENCIES,
                Reference.MOD_DEPENDENCIES.contains("["));
        assertTrue(Reference.MOD_DEPENDENCIES.contains("required-after:cyclopscore;"));
        assertTrue(Reference.MOD_DEPENDENCIES.contains("required-after:commoncapabilities;"));
        assertTrue(Reference.MOD_DEPENDENCIES.contains("required-after:integrateddynamics;"));
        // Audit D6: the GraalVM library mod is optional (after:, not required-after:),
        // so a missing runtime degrades instead of aborting the mod load.
        assertTrue("The library mod must be ordered but not required: " + Reference.MOD_DEPENDENCIES,
                Reference.MOD_DEPENDENCIES.contains("after:" + Reference.MOD_LIBS + ";"));
        assertFalse("The library mod must never become a hard dependency",
                Reference.MOD_DEPENDENCIES.contains("required-after:" + Reference.MOD_LIBS));
    }

    @Test
    public void testLibraryModIdIsDerivedFromTheMainModId() {
        // Decision B3: the companion jar keeps the main identity plus "libs".
        assertEquals(Reference.MOD_ID + "libs", Reference.MOD_LIBS);
    }

}
