package com.botzee;

import com.botzee.client.BotzeeController;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = BotzeeMod.MOD_ID, name = "Botzee", version = BotzeeMod.VERSION, clientSideOnly = true)
public class BotzeeMod {
    public static final String MOD_ID = "botzee";
    public static final String VERSION = "1.2.1";

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        BotzeeController.initialize();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        BotzeeController.register();
    }
}
