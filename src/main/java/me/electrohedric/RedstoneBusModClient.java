package me.electrohedric;

import net.fabricmc.api.*;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

public class RedstoneBusModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // add proper rendering for transparent texture
        BlockRenderLayerMap.INSTANCE.putBlock(RedstoneBusMod.REDSTONE_BUS, RenderLayer.getCutout());
    }
}
