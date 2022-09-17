/*
 * Copyright (C)
 *
 * This program is free software: you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General
 * Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 */

package me.electrohedric;

import net.fabricmc.api.*;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.render.RenderLayer;

public class RedstoneBusModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // add proper rendering for transparent texture
        RedstoneBusMod.LOGGER.info("Client initializing...");
        BlockRenderLayerMap.INSTANCE.putBlock(RedstoneBusMod.REDSTONE_BUS, RenderLayer.getCutout());

        RedstoneBusMod.REDSTONE_BUS_WAND.clientInit();
        RedstoneBusMod.LOGGER.info("Client initialized");
    }
}
