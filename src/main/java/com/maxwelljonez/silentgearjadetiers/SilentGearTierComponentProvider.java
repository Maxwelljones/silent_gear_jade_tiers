package com.maxwelljonez.silentgearjadetiers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.silentchaos512.gear.api.material.Material;
import net.silentchaos512.gear.api.property.HarvestTier;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.setup.gear.PartTypes;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public enum SilentGearTierComponentProvider implements IBlockComponentProvider {
    INSTANCE;

private record Tier(
        ResourceLocation materialId,
        TagKey<Block> incorrectTag,
        String tierName,
        String levelHint,
        double sortLevel,
        int color,
        boolean preferredNameForTag
) {
    String label() {
        boolean showNumeric = SilentGearJadeTiersConfig.SHOW_NUMERIC_LEVEL.get();
        boolean showName = SilentGearJadeTiersConfig.SHOW_TIER_NAME.get();

        String displayName = prettifyTierName(tierName);

        if (showNumeric && showName) {
            return "Tier " + formattedLevelHint() + " - " + displayName;
        }

        if (showNumeric) {
            return "Tier " + formattedLevelHint();
        }

        if (showName) {
            return displayName;
        }

        return "";
    }

    private String formattedLevelHint() {
        try {
            double value = Double.parseDouble(levelHint);

            if (value == Math.rint(value)) {
                int intValue = (int) value;

                if (intValue >= 0 && intValue < 10) {
                    return "0" + intValue;
                }

                return Integer.toString(intValue);
            }

            return levelHint;
        } catch (NumberFormatException ignored) {
            return levelHint;
        }
    }
}

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!SilentGearJadeTiersConfig.SHOW_REQUIRED_TIER.get()) {
            return;
        }

        BlockState state = accessor.getBlockState();

        if (!shouldShowForHeldItem(accessor, state)) {
            return;
        }

        List<Tier> tiers = buildRuntimeTierList();
        if (tiers.size() < 2) {
            return;
        }

        Tier required = findRequiredTier(state, tiers);
        if (required == null) {
            return;
        }

        MutableComponent line = Component.empty();

        if (SilentGearJadeTiersConfig.SHOW_CROSSED_PICKAXE_ICON.get()) {
            line.append(Component.literal("⛏").withStyle(style -> style.withColor(required.color())));
            line.append(Component.literal(" ✕ ").withStyle(ChatFormatting.RED));
        }

        line.append(Component.literal("Required: ").withStyle(ChatFormatting.GRAY));

        String label() {
            boolean showNumeric = SilentGearJadeTiersConfig.SHOW_NUMERIC_LEVEL.get();
            boolean showName = SilentGearJadeTiersConfig.SHOW_TIER_NAME.get();

            if (showNumeric && showName) {
                return "Tier " + formattedLevelHint() + " - " + name;
            }

            if (showNumeric) {
                return "Tier " + formattedLevelHint();
            }

            if (showName) {
                return name;
            }

            return "";
        }

        tooltip.add(line);
    }

    private static boolean shouldShowForHeldItem(BlockAccessor accessor, BlockState state) {
        if (accessor.getPlayer() == null) {
            return true;
        }

        ItemStack held = accessor.getPlayer().getMainHandItem();

        if (held.isEmpty()) {
            return SilentGearJadeTiersConfig.SHOW_IF_HOLDING_NO_TOOL.get();
        }

        boolean correctTool = held.isCorrectToolForDrops(state);

        if (correctTool) {
            return SilentGearJadeTiersConfig.SHOW_IF_HOLDING_CORRECT_TOOL.get();
        }

        return SilentGearJadeTiersConfig.SHOW_IF_HOLDING_WRONG_TOOL.get();
    }

    private static Tier findRequiredTier(BlockState state, List<Tier> tiers) {
        boolean blockedByPreviousTier = state.is(tiers.get(0).incorrectTag());

        // If the lowest Silent Gear tier does not block this block, the block is not part
        // of the custom Silent Gear mining progression chain.
        if (!blockedByPreviousTier) {
            return null;
        }

        for (int i = 1; i < tiers.size(); i++) {
            Tier tier = tiers.get(i);
            boolean blockedByCurrentTier = state.is(tier.incorrectTag());

            if (blockedByPreviousTier && !blockedByCurrentTier) {
                return tier;
            }

            blockedByPreviousTier = blockedByCurrentTier;
        }

        // Still blocked by every known tier. Do not display misleading data.
        return null;
    }

    private static List<Tier> buildRuntimeTierList() {
        Map<ResourceLocation, Tier> tiersByIncorrectTag = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, Material> entry : SgRegistries.MATERIAL.entrySet()) {
            ResourceLocation materialId = entry.getKey();
            Material material = entry.getValue();

            Tier tier = tierFromMaterial(materialId, material);
            if (tier == null) {
                continue;
            }

            ResourceLocation tagId = tier.incorrectTag().location();
            Tier existing = tiersByIncorrectTag.get(tagId);

            if (existing == null || shouldReplaceExistingTier(existing, tier)) {
                tiersByIncorrectTag.put(tagId, tier);
            }
        }

        List<Tier> tiers = new ArrayList<>(tiersByIncorrectTag.values());

        tiers.sort(
                Comparator.comparingDouble(Tier::sortLevel)
                        .thenComparing(tier -> tier.materialId().toString())
        );

        return tiers;
    }

        private static Tier tierFromMaterial(ResourceLocation materialId, Material material) {
        try {
            MaterialInstance instance = MaterialInstance.of(material);

            HarvestTier harvestTier = instance.getProperty(
                PartTypes.MAIN.get(),
                GearProperties.HARVEST_TIER.get()
            );

            if (harvestTier == null) {
                return null;
            }
    
            TagKey<Block> incorrectTag = harvestTier.incorrectForTool();

            if (incorrectTag == null) {
                return null;
            }

            ResourceLocation tagLocation = incorrectTag.location();

            if (!"silentgear".equals(tagLocation.getNamespace())) {
                return null;
            }

            if (!tagLocation.getPath().startsWith("incorrect_for_")
                || !tagLocation.getPath().endsWith("_tools")) {
                return null;
            }

            String levelHint = harvestTier.levelHint().orElse("").trim();

            if (levelHint.isBlank()) {
                return null;
            }

            String tierName = harvestTier.name().trim();

            if (tierName.isBlank()) {
                tierName = expectedMaterialPathFromTag(tagLocation);
            }

            int color = safeMaterialColor(instance);

            String expectedName = expectedMaterialPathFromTag(tagLocation);
            boolean preferredNameForTag = tierName.equals(expectedName);

            return new Tier(
                materialId,
                incorrectTag,
                tierName,
                levelHint,
                parseLevelHint(levelHint),
                color,
                preferredNameForTag
            );
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean shouldReplaceExistingTier(Tier existing, Tier candidate) {
        if (candidate.preferredNameForTag() && !existing.preferredNameForTag()) {
            return true;
        }

        if (!candidate.preferredNameForTag() && existing.preferredNameForTag()) {
            return false;
        }

        // Prefer same namespace as the tag if possible.
        if ("silentgear".equals(candidate.materialId().getNamespace())
                && !"silentgear".equals(existing.materialId().getNamespace())) {
            return true;
        }

        return candidate.materialId().toString().compareTo(existing.materialId().toString()) < 0;
    }

    private static int safeMaterialColor(MaterialInstance instance) {
        try {
            return instance.getNameColor(PartTypes.MAIN.get(), GearTypes.PICKAXE.get()) & 0xFFFFFF;
        } catch (Exception ignored) {
            return 0xFFFFFF;
        }
    }

    private static double parseLevelHint(String levelHint) {
        try {
            return Double.parseDouble(levelHint);
        } catch (NumberFormatException ignored) {
            return Double.MAX_VALUE;
        }
    }

    private static String expectedMaterialPathFromTag(ResourceLocation tagId) {
        String path = tagId.getPath();

        if (path.startsWith("incorrect_for_")) {
            path = path.substring("incorrect_for_".length());
        }

        if (path.endsWith("_tools")) {
            path = path.substring(0, path.length() - "_tools".length());
        }

        return path;
    }

    private static String prettifyMaterialId(ResourceLocation materialId) {
        String path = materialId.getPath().replace('_', ' ');
        String[] words = path.split(" ");

        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (!result.isEmpty()) {
                result.append(' ');
            }

            result.append(Character.toUpperCase(word.charAt(0)));

            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }

        return result.isEmpty() ? materialId.toString() : result.toString();
    }

    @Override
    public ResourceLocation getUid() {
        return SilentGearJadePlugin.REQUIRED_TIER;
    }
}
