package com.maxwelljonez.silentgearjadetiers;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public final class KidzJadePlugin implements IWailaPlugin {
    public static final ResourceLocation REQUIRED_TIER = ResourceLocation.fromNamespaceAndPath(KidzJadeTiers.MODID, "required_tier");

    @Override
    public void register(IWailaCommonRegistration registration) {
        // No server data needed. We only read client-known block tags.
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Register for all blocks. The provider stays silent unless the block belongs
        // to one of the kidz:needs_tier_X_tool tags.
        registration.registerBlockComponent(KidzTierComponentProvider.INSTANCE, Block.class);
    }
}
