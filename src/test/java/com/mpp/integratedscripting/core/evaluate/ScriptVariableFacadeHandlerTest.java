package com.mpp.integratedscripting.core.evaluate;

import net.minecraft.nbt.NBTTagCompound;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.item.IVariableFacade;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.core.item.VariableFacadeHandlerRegistry;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import com.mpp.Reference;
import com.mpp.integratedscripting.api.item.IScriptVariableFacade;
import com.mpp.integratedscripting.core.item.ScriptVariableFacade;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the M4 script variable facade and its handler
 * (docs/PORTING-1.12.2.md §5.7, §10.2).
 *
 * <p>Scope is deliberately limited to pure logic. The 1.12.2 dependency jars
 * are compiled against SRG method names, so ID code that writes/reads NBT
 * cannot be executed from plain JUnit (see the M3 notes in
 * docs/MILESTONES.md): the handler itself is tested directly, and the full
 * registry round-trip — including the {@code _type}/{@code _id} keys written by
 * {@code VariableFacadeHandlerRegistry} — is verified in-game by
 * {@code /integratedscriptingcallback testfacade}. The advancement predicate
 * overrides also load ID criterion classes that require a Minecraft
 * environment; they are covered by the M6 advancement acceptance instead.</p>
 */
public class ScriptVariableFacadeHandlerTest {

    private static final String TYPE_ID = Reference.MOD_ID + ":script";

    private static IVariableFacadeHandlerRegistry registry;

    @BeforeClass
    public static void registerHandler() {
        registry = VariableFacadeHandlerRegistry.getInstance();
        registry.registerHandler(ScriptVariableFacadeHandler.getInstance());
    }

    @Before
    public void assertHandlerRegistered() {
        assertNotNull("handler must be registered for the type id",
                registry.getHandler(TYPE_ID));
        assertSame(ScriptVariableFacadeHandler.getInstance(), registry.getHandler(TYPE_ID));
    }

    @Test
    public void testTypeId() {
        assertEquals(TYPE_ID, ScriptVariableFacadeHandler.getInstance().getTypeId());
        // Decision A5: the handler id is the namespaced script type, not the old namespace.
        assertEquals("integratedscriptingcallback:script", TYPE_ID);
    }

    @Test
    public void testHandlerRoundTrip() {
        ScriptVariableFacadeHandler handler = ScriptVariableFacadeHandler.getInstance();
        ScriptVariableFacade facade = new ScriptVariableFacade(7, 3, Paths.get("script.js"), "foo");

        NBTTagCompound tag = new NBTTagCompound();
        handler.setVariableFacade(tag, facade);

        assertEquals(3, tag.getInteger("disk"));
        assertEquals("script.js", tag.getString("path"));
        assertEquals("foo", tag.getString("member"));

        IScriptVariableFacade read = handler.getVariableFacade(7, tag);
        assertEquals(7, read.getId());
        assertEquals(3, read.getDisk());
        assertEquals(Paths.get("script.js"), read.getPath());
        assertEquals("foo", read.getMember());
        assertTrue(read.isValid());
        assertEquals(facade, read);
    }

    @Test
    public void testPathIsStoredWithForwardSlashes() {
        Path nested = Paths.get("sub" + File.separator + "script.js");
        ScriptVariableFacade facade = new ScriptVariableFacade(1, 2, nested, "bar");

        NBTTagCompound tag = new NBTTagCompound();
        ScriptVariableFacadeHandler.getInstance().setVariableFacade(tag, facade);

        // Decision C10: never store host-specific separators.
        assertEquals("sub/script.js", tag.getString("path"));
        assertEquals(Paths.get("sub", "script.js"), Paths.get(tag.getString("path")));
        assertEquals(Paths.get("sub", "script.js"),
                ScriptVariableFacadeHandler.getInstance().getVariableFacade(1, tag).getPath());
    }

    @Test
    public void testMissingKeysReturnInvalidFacade() {
        ScriptVariableFacadeHandler handler = ScriptVariableFacadeHandler.getInstance();

        NBTTagCompound noDisk = new NBTTagCompound();
        noDisk.setString("path", "script.js");
        noDisk.setString("member", "foo");
        assertFalse(handler.getVariableFacade(1, noDisk).isValid());

        NBTTagCompound noPath = new NBTTagCompound();
        noPath.setInteger("disk", 1);
        noPath.setString("member", "foo");
        assertFalse(handler.getVariableFacade(1, noPath).isValid());

        NBTTagCompound noMember = new NBTTagCompound();
        noMember.setInteger("disk", 1);
        noMember.setString("path", "script.js");
        assertFalse(handler.getVariableFacade(1, noMember).isValid());

        // A wrong NBT type is treated as missing as well.
        NBTTagCompound wrongType = new NBTTagCompound();
        wrongType.setString("disk", "1");
        wrongType.setString("path", "script.js");
        wrongType.setString("member", "foo");
        assertFalse(handler.getVariableFacade(1, wrongType).isValid());
    }

    @Test
    public void testInvalidFacade() {
        ScriptVariableFacade facade = new ScriptVariableFacade(false, -1, Paths.get(""), "");
        assertFalse(facade.isValid());
        assertEquals(-1, facade.getId());
        assertNull(facade.getVariable(null));
    }

    @Test
    public void testFacadeWithoutNetworkHasNoVariable() {
        ScriptVariableFacade facade = new ScriptVariableFacade(1, 0, Paths.get("script.js"), "foo");
        assertTrue(facade.isValid());
        // No part network -> no INetwork -> no scripting network capability.
        assertNull(facade.getVariable(null));
    }

    @Test
    public void testValidateReportsInvalidNetwork() {
        ScriptVariableFacade facade = new ScriptVariableFacade(1, 0, Paths.get("script.js"), "foo");
        RecordingValidator validator = new RecordingValidator();

        // Passing null as the part network resolves to "no network"; the value type is
        // not touched on that path, so it can stay null in this pure unit test.
        facade.validate(null, validator, null);

        assertEquals(1, validator.errors.size());
        assertEquals("script.integratedscripting.error.invalid_network",
                validator.errorKeys.get(0));
    }

    @Test
    public void testFacadeEquality() {
        ScriptVariableFacade a = new ScriptVariableFacade(5, 2, Paths.get("a.js"), "foo");
        ScriptVariableFacade b = new ScriptVariableFacade(5, 2, Paths.get("a.js"), "foo");
        ScriptVariableFacade differentMember = new ScriptVariableFacade(5, 2, Paths.get("a.js"), "bar");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, differentMember);
        assertNotSame(a, b);
    }

    /**
     * Records validator errors without localizing them (localization needs a
     * Minecraft environment).
     */
    private static class RecordingValidator implements IVariableFacade.IValidator {

        private final List<L10NHelpers.UnlocalizedString> errors = new ArrayList<>();
        private final List<String> errorKeys = new ArrayList<>();

        @Override
        public void addError(L10NHelpers.UnlocalizedString error) {
            this.errors.add(error);
            // Recover the translation key that the facade used for the error.
            try {
                java.lang.reflect.Field field = L10NHelpers.UnlocalizedString.class
                        .getDeclaredField("parameterizedString");
                field.setAccessible(true);
                this.errorKeys.add((String) field.get(error));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

}
