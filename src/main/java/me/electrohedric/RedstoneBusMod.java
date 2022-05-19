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
