package com.mpp.integratedscripting.api.language;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit tests for {@link LanguageId} (docs/LANGUAGE-API-REFACTOR.md Phase 1).
 */
public class LanguageIdTest {

    @Test
    public void testOfAndToString() {
        LanguageId id = LanguageId.of("mymod", "mylang");
        assertEquals("mymod", id.getNamespace());
        assertEquals("mylang", id.getName());
        assertEquals("mymod:mylang", id.toString());
    }

    @Test
    public void testIdsAreNormalisedToLowerCase() {
        assertEquals(LanguageId.of("myMod", "MyLang"), LanguageId.of("mymod", "mylang"));
        assertEquals("mymod:mylang", LanguageId.of("myMod", "MyLang").toString());
    }

    @Test
    public void testParse() {
        assertEquals(LanguageId.JAVASCRIPT, LanguageId.parse("integratedscriptingcallback:javascript"));
        assertEquals(LanguageId.of("a.b-c", "d_e"), LanguageId.parse("a.b-c:d_e"));
    }

    @Test
    public void testParseRejectsMalformedIds() {
        for (String value : new String[]{null, "", "javascript", ":javascript", "mod:",
                "mod:name:extra", "mod:na me", "mod:na$me"}) {
            try {
                LanguageId.parse(value);
                fail("Expected '" + value + "' to be rejected");
            } catch (IllegalArgumentException expected) {
                // expected
            }
        }
    }

    @Test
    public void testOfRejectsEmptyParts() {
        try {
            LanguageId.of("", "name");
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }
        try {
            LanguageId.of("namespace", null);
            fail();
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }

    @Test
    public void testSlug() {
        assertEquals("javascript", LanguageId.slug("JavaScript"));
        assertEquals("my-lang", LanguageId.slug("My Lang"));
        assertEquals("c", LanguageId.slug("C++"));
        assertEquals("language", LanguageId.slug("  "));
        assertEquals("language", LanguageId.slug(null));
        assertEquals("plain_text", LanguageId.slug("plain_text"));
    }

    @Test
    public void testEqualsAndCompareTo() {
        LanguageId a = LanguageId.of("a", "z");
        LanguageId b = LanguageId.of("a", "z");
        LanguageId c = LanguageId.of("a", "y");
        LanguageId d = LanguageId.of("b", "a");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertTrue(c.compareTo(a) < 0);
        assertTrue(a.compareTo(d) < 0);
        assertFalse(a.equals("a:z"));
        assertEquals(0, a.compareTo(b));
    }

    @Test
    public void testBuiltInIds() {
        assertEquals("integratedscriptingcallback", LanguageId.JAVASCRIPT.getNamespace());
        assertEquals("javascript", LanguageId.JAVASCRIPT.getName());
        assertEquals("plaintext", LanguageId.PLAIN_TEXT.getName());
        assertNotEquals(LanguageId.JAVASCRIPT, LanguageId.PLAIN_TEXT);
    }

}
