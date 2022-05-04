package me.electrohedric.mixin;

import me.electrohedric.RedstoneBusMod;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class RedstoneBusMixin {
    @Inject(at = @At("HEAD"), method = "init()V")
    private void init(CallbackInfo info) {
        RedstoneBusMod.LOGGER.info("Redstone Bus mod initializing...");
    }
}
