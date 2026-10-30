package com.mpp.template;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = ModMeta.MOD_ID, name = ModMeta.NAME, version = ModMeta.VERSION,
     dependencies = "required-after:mixinbooter")
public class TemplateMod
{
    public static final String MODID = ModMeta.MOD_ID;

    private static Logger logger;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event)
    {
        logger = event.getModLog();
    }
}
