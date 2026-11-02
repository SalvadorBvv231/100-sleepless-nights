package com.sansslonce.sleepless100;

import net.minecraftforge.common.MinecraftForge;

import com.sansslonce.sleepless100.command.CommandSleepless;
import com.sansslonce.sleepless100.mobs.ExtraNightSpawner;
import com.sansslonce.sleepless100.mobs.MobEffectHandler;
import com.sansslonce.sleepless100.party.PartyManager;
import com.sansslonce.sleepless100.village.VillageHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    // Runs first: read the config file.
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        SleeplessNights.LOG.info("100 Sleepless Nights loaded, version " + Tags.VERSION);
    }

    // Register event handlers.
    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(new DifficultyManager());

        // Mob effects: listens to Forge events (spawn, damage, explosions) and FML world ticks.
        MobEffectHandler mobEffects = new MobEffectHandler();
        MinecraftForge.EVENT_BUS.register(mobEffects);
        FMLCommonHandler.instance()
            .bus()
            .register(mobEffects);
        FMLCommonHandler.instance()
            .bus()
            .register(new ExtraNightSpawner());

        // Friday Night Party and villages: Forge events plus FML server ticks.
        registerBoth(new PartyManager());
        registerBoth(new VillageHandler());
    }

    private static void registerBoth(Object handler) {
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance()
            .bus()
            .register(handler);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    // Register server commands.
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandSleepless());
    }
}
