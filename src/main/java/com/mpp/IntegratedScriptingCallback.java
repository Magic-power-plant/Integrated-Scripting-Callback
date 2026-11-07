package com.mpp;

import com.google.common.collect.Maps;
import net.minecraft.command.ICommand;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.command.CommandMod;
import org.cyclops.cyclopscore.config.ConfigHandler;
import org.cyclops.cyclopscore.config.extendedconfig.ItemConfigReference;
import org.cyclops.cyclopscore.infobook.IInfoBook;
import org.cyclops.cyclopscore.infobook.IInfoBookRegistry;
import org.cyclops.cyclopscore.infobook.InfoBookParser;
import org.cyclops.cyclopscore.init.ItemCreativeTab;
import org.cyclops.cyclopscore.init.ModBaseVersionable;
import org.cyclops.cyclopscore.init.RecipeHandler;
import org.cyclops.cyclopscore.proxy.ICommonProxy;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.api.item.IVariableFacadeHandlerRegistry;
import org.cyclops.integrateddynamics.infobook.OnTheDynamicsOfIntegrationBook;
import com.mpp.integratedscripting.acceptance.HarnessLoader;
import com.mpp.integratedscripting.api.evaluate.translation.IValueTranslatorRegistry;
import com.mpp.integratedscripting.api.language.ILanguageHandlerRegistry;
import com.mpp.integratedscripting.api.language.LanguageId;
import com.mpp.integratedscripting.api.language.service.ILanguageServiceRegistry;
import com.mpp.integratedscripting.block.BlockMendesiteConfig;
import com.mpp.integratedscripting.block.BlockAddressedMemoryConfig;
import com.mpp.integratedscripting.block.BlockScriptingDriveConfig;
import com.mpp.integratedscripting.capability.ScriptingNetworkCapabilityConstructors;
import com.mpp.integratedscripting.capability.network.ScriptingNetworkConfig;
import com.mpp.integratedscripting.callback.OperatorBindings;
import com.mpp.integratedscripting.callback.ScriptErrorTracker;
import com.mpp.integratedscripting.command.CommandTestFacade;
import com.mpp.integratedscripting.command.CommandTestScript;
import com.mpp.integratedscripting.core.evaluate.ScriptVariableFacadeHandler;
import com.mpp.integratedscripting.core.evaluate.operator.AddressedMemoryOperators;
import com.mpp.integratedscripting.core.evaluate.value.AddressedMemoryValues;
import com.mpp.integratedscripting.core.language.LanguageHandlers;
import com.mpp.integratedscripting.core.network.ScriptingData;
import com.mpp.integratedscripting.core.recipe.ScriptingRecipeHandler;
import com.mpp.integratedscripting.evaluate.ScriptHelpers;
import com.mpp.integratedscripting.evaluate.ScriptRuntime;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslatorRegistry;
import com.mpp.integratedscripting.evaluate.translation.ValueTranslators;
import com.mpp.integratedscripting.infobook.TextfieldAppendix;
import com.mpp.integratedscripting.item.ItemRefresherConfig;
import com.mpp.integratedscripting.item.ItemScriptingDiskConfig;
import com.mpp.integratedscripting.part.PartTypes;

import java.util.Map;

/**
 * The main mod class of Integrated Scripting Callback.
 *
 * <p>This is the 1.12.2 port of Integrated Scripting. The class only wires the
 * Cyclops mod base; the actual implementation lives in
 * {@code com.mpp.integratedscripting.**} and is added milestone by milestone
 * (see docs/PORTING-1.12.2.md §12).</p>
 *
 * @author rubensworks
 */
@Mod(
        modid = Reference.MOD_ID,
        name = Reference.MOD_NAME,
        version = Reference.MOD_VERSION,
        dependencies = Reference.MOD_DEPENDENCIES,
        useMetadata = true
)
public class IntegratedScriptingCallback extends ModBaseVersionable {

    @SidedProxy(
            clientSide = "com.mpp.integratedscripting.proxy.ClientProxy",
            serverSide = "com.mpp.integratedscripting.proxy.CommonProxy"
    )
    public static ICommonProxy proxy;

    @Mod.Instance(Reference.MOD_ID)
    public static IntegratedScriptingCallback _instance;

    /**
     * Server-side scripting data; null on the client and before the world loaded (decision C6).
     */
    public ScriptingData scriptingData;

    public IntegratedScriptingCallback() {
        super(Reference.MOD_ID, Reference.MOD_NAME, Reference.MOD_VERSION);
    }

    // ---------------------------------------------------------------- lifecycle

    @Mod.EventHandler
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        // 1) Mod-owned registries, must be known before the Cyclops config handler runs.
        //    The language registries are reached through LanguageHandlers, the same
        //    convenience entry third-party mods use (docs/LANGUAGE-API-REFACTOR.md §4.7).
        getRegistryManager().addRegistry(ILanguageHandlerRegistry.class, LanguageHandlers.REGISTRY);
        getRegistryManager().addRegistry(ILanguageServiceRegistry.class, LanguageHandlers.SERVICES);
        getRegistryManager().addRegistry(IValueTranslatorRegistry.class, ValueTranslatorRegistry.getInstance());

        // 2) M4: register the script variable facade in Integrated Dynamics' handler registry.
        //    This has to happen in preInit, before advancements are loaded, so that
        //    "_type": "integratedscriptingcallback:script" is resolvable.
        IntegratedDynamics._instance.getRegistryManager()
                .getRegistry(IVariableFacadeHandlerRegistry.class)
                .registerHandler(ScriptVariableFacadeHandler.getInstance());

        // 2b) Addressed memory: register its object value type and its get/set
        //     operators. The value type must exist before the operator registry and
        //     the script translators are built in init().
        AddressedMemoryValues.load();
        AddressedMemoryOperators.load();

        // 3) M4: create and register the scripting terminal part. PartTypeBase creates the
        //    part's block, item and GUI entry in its constructor, so this must run before
        //    Cyclops handles the configs (same order as Integrated Dynamics' own PartTypes).
        PartTypes.load();

        // 4) Cyclops config handling: registers the scripting network capability,
        //    the scripting disk item and the scripting drive block.
        super.preInit(event);

        // 5) Attach the scripting network capability to every ID network.
        MinecraftForge.EVENT_BUS.register(new ScriptingNetworkCapabilityConstructors());
    }

    @Mod.EventHandler
    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Needed for the server tick that flushes script changes and applies disk changes.
        MinecraftForge.EVENT_BUS.register(this);

        // M3: evaluation core. Translators must be registered before scripts are read,
        // and the JavaScript language handler before the server starts (it writes the .d.ts).
        // Audit D6: a missing GraalVM runtime (the separate libs mod) must degrade to
        // one actionable log line instead of a LinkageError that kills the mod.
        try {
            ValueTranslators.load();
            LanguageHandlers.load();
            clog(ScriptHelpers.getRuntimeDescription());
            logTypeScriptStatus();
        } catch (LinkageError e) {
            ScriptRuntime.reportUnavailable(e);
        }

        // M7: temporary acceptance harness, only active with -Disc.m7Smoke=true.
        // Loaded reflectively because the harnesses are excluded from the release jar (M8).
        HarnessLoader.registerIfEnabled(HarnessLoader.PROPERTY_SERVER, HarnessLoader.SERVER_ACCEPTANCE);
    }

    /**
     * Log that {@code .ts} scripts are executable.
     *
     * <p>The converse - TypeScript disabled by config, or a missing Translanguage
     * jar in a slim install - is logged by the registration itself, so this only
     * reports success and keeps the start-up log informative for both cases.</p>
     */
    private static void logTypeScriptStatus() {
        if (LanguageHandlers.REGISTRY.getHandler(LanguageId.TYPESCRIPT) != null) {
            clog("TypeScript support enabled: .ts scripts are compiled to JavaScript "
                    + "(Translanguage 2.0.3 SNAPSHOT, Java 8 build) before evaluation.");
        }
    }

    @Mod.EventHandler
    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        // M6: the 1.12.2 CyclopsCore does not ship a "textfield" appendix type,
        // which the reference manual uses for its code examples. Register it before
        // the sections are parsed (docs/PORTING-1.12.2.md §9.8.4).
        InfoBookParser.registerFactory("textfield", new InfoBookParser.IAppendixFactory() {
            @Override
            public TextfieldAppendix create(IInfoBook infoBook, org.w3c.dom.Element node) {
                return new TextfieldAppendix(infoBook, node);
            }
        });

        // M6: add the Scripting chapters to "On the Dynamics of Integration".
        // The book itself is registered by Integrated Dynamics in init().
        IInfoBookRegistry infoBookRegistry = IntegratedDynamics._instance.getRegistryManager()
                .getRegistry(IInfoBookRegistry.class);
        IInfoBook infoBook = OnTheDynamicsOfIntegrationBook.getInstance();
        infoBookRegistry.registerSection(infoBook, "info_book.integrateddynamics.manual",
                "/assets/" + Reference.MOD_ID + "/info/scripting_info.xml");
        infoBookRegistry.registerSection(infoBook, "info_book.integrateddynamics.tutorials",
                "/assets/" + Reference.MOD_ID + "/info/scripting_tutorials.xml");
    }

    @Mod.EventHandler
    @Override
    public void onServerStarting(FMLServerStartingEvent event) {
        super.onServerStarting(event);
    }

    @Mod.EventHandler
    @Override
    public void onServerStarted(FMLServerStartedEvent event) {
        super.onServerStarted(event);

        // 1.12 FMLServerStartedEvent has no getServer(); resolve the current server explicitly.
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        World world = server.getWorld(0);
        if (world == null) {
            clog(Level.WARN, "Skipping scripting data initialization: overworld is not available.");
            return;
        }
        // Decision A6 / §10.1: <world>/integratedscriptingcallback/scripting-disks/<id>/...
        this.scriptingData = new ScriptingData(world.getSaveHandler().getWorldDirectory());

        // M3: generate <world>/integratedscriptingcallback/integratedscriptingcallback.d.ts
        // and the operator-bindings.log diagnostics. The typings are a contributed
        // server-start task of the JavaScript language now, and the lifecycle entry
        // is the language layer itself, not a registry singleton.
        OperatorBindings.dumpBindings(this.scriptingData.getRootPath());
        LanguageHandlers.onServerStarted();
    }

    @Mod.EventHandler
    @Override
    public void onServerStopping(FMLServerStoppingEvent event) {
        if (this.scriptingData != null) {
            this.scriptingData.close();
        }
        this.scriptingData = null;
        ScriptErrorTracker.reset();
        super.onServerStopping(event);
    }

    /**
     * Drives the scripting data on the server thread: external disk changes and
     * pending in-memory writes (decision C8).
     * @param event The server tick event.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && this.scriptingData != null) {
            this.scriptingData.tick();
        }
    }

    // ---------------------------------------------------------------- Cyclops hooks

    @Override
    protected RecipeHandler constructRecipeHandler() {
        // M6: Cyclops XML machine recipes (Mendesite in the drying basin). The
        // subclass also covers the dev classloader case; see ScriptingRecipeHandler.
        return new ScriptingRecipeHandler(this, "mendesite.xml");
    }

    @Override
    public CreativeTabs constructDefaultCreativeTab() {
        // The reference uses the Scripting Disk as the tab icon.
        return new ItemCreativeTab(this, new ItemConfigReference(ItemScriptingDiskConfig.class));
    }

    @Override
    protected ICommand constructBaseCommand() {
        Map<String, ICommand> subcommands = Maps.newHashMap();
        subcommands.put("testscript", new CommandTestScript(this));
        subcommands.put("testfacade", new CommandTestFacade(this));
        return new CommandMod(this, subcommands);
    }

    @Override
    public ICommonProxy getProxy() {
        return proxy;
    }

    @Override
    public void onGeneralConfigsRegister(ConfigHandler configHandler) {
        configHandler.add(new GeneralConfig());
    }

    @Override
    public void onMainConfigsRegister(ConfigHandler configHandler) {
        configHandler.add(new ScriptingNetworkConfig());
        configHandler.add(new ItemScriptingDiskConfig());
        // Network refresher: right-click a Display Panel / cable to repair stale
        // variables and desynced panels (docs/NETWORK-REFRESHER.md).
        configHandler.add(new ItemRefresherConfig());
        configHandler.add(new BlockScriptingDriveConfig());
        // The addressed memory, which stores sixteen addressable variables.
        configHandler.add(new BlockAddressedMemoryConfig());
        // M6: Mendesite, the basic crafting material.
        configHandler.add(new BlockMendesiteConfig());
        // The scripting terminal part is not a config: PartTypes.load() is called in preInit
        // because PartTypeBase creates its block/item/GUI entries itself (see §5.8.5).
        // Tile entities, containers and GUIs need no config: Cyclops' BlockAction derives them (see §5.8).
    }

    // ---------------------------------------------------------------- logging

    public static void clog(String message) {
        clog(Level.INFO, message);
    }

    public static void clog(Level level, String message) {
        if (_instance != null) {
            _instance.getLoggerHelper().log(level, message);
        }
    }
}
