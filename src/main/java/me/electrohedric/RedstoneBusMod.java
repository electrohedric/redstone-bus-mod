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

import me.electrohedric.blocks.RedstoneBusBlock;
import me.electrohedric.items.RedstoneBusWand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Material;
import net.minecraft.item.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.slf4j.*;

public class RedstoneBusMod implements ModInitializer {
    
    public static final String MODID = "redstone_bus";
    
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    
    public static final RedstoneBusBlock REDSTONE_BUS = new RedstoneBusBlock(FabricBlockSettings.of(Material.DECORATION).strength(0.0f).nonOpaque());
    public static final BlockItem REDSTONE_BUS_BLOCKITEM = new BlockItem(REDSTONE_BUS, new FabricItemSettings().group(ItemGroup.REDSTONE));
    public static final RedstoneBusWand REDSTONE_BUS_WAND = new RedstoneBusWand(new FabricItemSettings().group(ItemGroup.REDSTONE).maxCount(1));

    @Override
    public void onInitialize() {
        // for all intents and purposes, this is the server.
        // the client has all the server stuff on it anyway for integrated server environment stuff
        RedstoneBusMod.LOGGER.info("Server initializing...");
        Registry.register(Registry.BLOCK, new Identifier(MODID, "redstone_bus"), REDSTONE_BUS);
        Registry.register(Registry.ITEM, new Identifier(MODID, "redstone_bus"), REDSTONE_BUS_BLOCKITEM);
        Registry.register(Registry.ITEM, new Identifier(MODID, "redstone_bus_wand"), REDSTONE_BUS_WAND);

        REDSTONE_BUS_WAND.serverInit();
        RedstoneBusMod.LOGGER.info("Server initialized");
    }
}
