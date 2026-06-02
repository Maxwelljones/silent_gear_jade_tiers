package com.maxwelljonez.silentgearjadetiers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.List;

public enum KidzTierComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private record Tier(int level, String name, String incorrectTagPath, int color) {
        TagKey<Block> incorrectTag() {
            return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("silentgear", incorrectTagPath));
        }

        String label() {
            return String.format("Tier %02d - %s", level, name);
        }
    }

    /*
     * Display logic reads your real Silent Gear mining authority tags:
     *   silentgear:incorrect_for_wood_tools
     *   silentgear:incorrect_for_stone_tools
     *   silentgear:incorrect_for_copper_tools
     *   ...
     *
     * A block requires tier N when:
     *   - it is blocked by the previous lower tier
     *   - it is no longer blocked by tier N
     *
     * Example:
     *   Redstone in incorrect_for_zinc_tools but not in incorrect_for_gold_tools
     *   => Required: Tier 06 - Gold
     */
    private static final List<Tier> TIERS = List.of(
            new Tier(0, "Wood / Flint / Bone", "incorrect_for_wood_tools", 0x9A6A35),
            new Tier(1, "Stone", "incorrect_for_stone_tools", 0xA0A0A0),
            new Tier(2, "Copper", "incorrect_for_copper_tools", 0xD87945),
            new Tier(3, "Iron", "incorrect_for_iron_tools", 0xD8D8D8),
            new Tier(4, "Andesite", "incorrect_for_andesite_tools", 0xA08F79),
            new Tier(5, "Zinc", "incorrect_for_zinc_tools", 0xC7D7D9),
            new Tier(6, "Gold", "incorrect_for_gold_tools", 0xFFD84A),
            new Tier(7, "Rose Gold", "incorrect_for_rose_gold_tools", 0xF0A080),
            new Tier(8, "Lapis", "incorrect_for_lapis_tools", 0x315CCF),
            new Tier(9, "Brass", "incorrect_for_brass_tools", 0xD6A83A),
            new Tier(10, "Electrum", "incorrect_for_electrum_tools", 0xFFE36E),
            new Tier(11, "Osmium", "incorrect_for_osmium_tools", 0x8AA6B8),
            new Tier(12, "Steel", "incorrect_for_steel_tools", 0x666A70),
            new Tier(13, "Obsidian", "incorrect_for_obsidian_tools", 0x332044),
            new Tier(14, "Bronze", "incorrect_for_bronze_tools", 0xB2743A),
            new Tier(15, "Diamond", "incorrect_for_diamond_tools", 0x55FFFF),
            new Tier(16, "Refined Obsidian", "incorrect_for_refined_obsidian_tools", 0x6A3FA0),
            new Tier(17, "Refined Glowstone", "incorrect_for_refined_glowstone_tools", 0xFFD35A),
            new Tier(18, "Netherite", "incorrect_for_netherite_tools", 0x4A424A),
            new Tier(19, "Uranium", "incorrect_for_uranium_tools", 0x6F9E4A),
            new Tier(20, "Yellorium", "incorrect_for_yellorium_tools", 0xC9D83E),
            new Tier(21, "Bort", "incorrect_for_bort_tools", 0x53616D),
            new Tier(22, "Blutonium", "incorrect_for_blutonium_tools", 0x3B83D8),
            new Tier(23, "Crimson", "incorrect_for_crimson_tools", 0xB32626),
            new Tier(24, "Desh", "incorrect_for_desh_tools", 0x8B6FFF),
            new Tier(25, "Todabonium", "incorrect_for_todabonium_tools", 0xC06BFF)
    );

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();

        Tier required = findRequiredTier(state);
        if (required == null) {
            return;
        }

        tooltip.add(
                Component.literal("⛏ Required: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(required.label()).withStyle(style -> style.withColor(required.color())))
        );
    }

    private static Tier findRequiredTier(BlockState state) {
        boolean blockedByPreviousTier = state.is(TIERS.get(0).incorrectTag());

        // If wood-tier tools are not blocked, this block is not part of the custom mining progression.
        if (!blockedByPreviousTier) {
            return null;
        }

        for (int i = 1; i < TIERS.size(); i++) {
            Tier tier = TIERS.get(i);
            boolean blockedByCurrentTier = state.is(tier.incorrectTag());

            if (blockedByPreviousTier && !blockedByCurrentTier) {
                return tier;
            }

            blockedByPreviousTier = blockedByCurrentTier;
        }

        // If the block is still blocked by every known tier, do not display a misleading tier.
        return null;
    }

    @Override
    public ResourceLocation getUid() {
        return KidzJadePlugin.REQUIRED_TIER;
    }
}
