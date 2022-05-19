package me.electrohedric;

import net.fabricmc.api.DedicatedServerModInitializer;

public class RedstoneBusModServer implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        RedstoneBusMod.LOGGER.info("Server initializing...");
        RedstoneBusMod.REDSTONE_BUS_WAND.serverInit();
        RedstoneBusMod.LOGGER.info("Server initialized");
    }
}
