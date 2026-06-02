package com.maxwelljonez.silentgearjadetiers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class SilentGearJadePlugin implements IWailaPlugin {
    public static final ResourceLocation REQUIRED_TIER =
            ResourceLocation.fromNamespaceAndPath(SilentGearJadeTiers.MODID, "required_tier");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // No custom server payload is needed.
        // Silent Gear material data and block tags are already available through normal game/runtime data.
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(SilentGearTierComponentProvider.INSTANCE, Block.class);
    }
}
