package com.maxwelljonez.silentgearjadetiers;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.item.GearType;
import net.silentchaos512.gear.api.material.Material;
import net.silentchaos512.gear.api.part.PartType;
import net.silentchaos512.gear.api.property.HarvestTier;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.gear.material.MaterialInstance;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.item.CompoundPartItem;
import net.silentchaos512.gear.setup.GearItemSets;
import net.silentchaos512.gear.setup.SgItems;
import net.silentchaos512.gear.setup.SgRegistries;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.setup.gear.PartTypes;
import net.silentchaos512.gear.util.Const;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public enum SilentGearTierComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static List<Tier> cachedRuntimeTierList;

    private record Tier(
            ResourceLocation materialId,
            String tierName,
            String levelHint,
            double sortLevel,
            int color,
            ItemStack simulatedPickaxe
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

        if (tiers.isEmpty()) {
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

        String label = required.label();

        if (!label.isBlank()) {
            line.append(Component.literal(label).withStyle(style -> style.withColor(required.color())));
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
        /*
         * Real behavior check:
         * We no longer infer from incorrect_for_* tags.
         * We test real Silent Gear pickaxe ItemStacks built from runtime-loaded materials.
         */
        if (!state.requiresCorrectToolForDrops()) {
            return null;
        }

        for (Tier tier : tiers) {
            ItemStack simulatedPickaxe = tier.simulatedPickaxe();

            if (!simulatedPickaxe.isEmpty() && simulatedPickaxe.isCorrectToolForDrops(state)) {
                return tier;
            }
        }

        return null;
    }

    private static List<Tier> buildRuntimeTierList() {
        if (cachedRuntimeTierList != null) {
            return cachedRuntimeTierList;
        }

        List<Tier> tiers = new ArrayList<>();

        for (Map.Entry<ResourceLocation, Material> entry : SgRegistries.MATERIAL.entrySet()) {
            ResourceLocation materialId = entry.getKey();

            Tier tier = tierFromMaterial(materialId);

            if (tier != null) {
                tiers.add(tier);
            }
        }

        tiers.sort(
                Comparator.comparingDouble(Tier::sortLevel)
                        .thenComparing(tier -> tier.materialId().toString())
        );

        cachedRuntimeTierList = List.copyOf(tiers);
        return cachedRuntimeTierList;
    }

    private static Tier tierFromMaterial(ResourceLocation materialId) {
        try {
            MaterialInstance materialInstance = MaterialInstance.of(DataResource.material(materialId));

            if (!materialInstance.isValid()) {
                return null;
            }

            GearType pickaxeType = GearTypes.PICKAXE.get();

            if (!materialInstance.isCraftingAllowed(PartTypes.MAIN.get(), pickaxeType)) {
                return null;
            }

            HarvestTier harvestTier = materialInstance.getProperty(
                    PartTypes.MAIN.get(),
                    GearProperties.HARVEST_TIER.get()
            );

            if (harvestTier == null) {
                return null;
            }

            String levelHint = harvestTier.levelHint().orElse("").trim();

            if (levelHint.isBlank()) {
                return null;
            }

            String tierName = harvestTier.name();

            if (tierName == null || tierName.isBlank()) {
                tierName = materialId.getPath();
            }

            tierName = tierName.trim();

            ItemStack simulatedPickaxe = createSimulatedPickaxe(materialId);

            if (simulatedPickaxe.isEmpty()) {
                return null;
            }

            int color = safeMaterialColor(materialInstance);

            return new Tier(
                    materialId,
                    tierName,
                    levelHint,
                    parseLevelHint(levelHint),
                    color,
                    simulatedPickaxe
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack createSimulatedPickaxe(ResourceLocation mainMaterialId) {
        try {
            GearItem gearItem = GearItemSets.PICKAXE.gearItem();
            List<PartInstance> parts = new ArrayList<>();

            MaterialInstance mainMaterial = MaterialInstance.of(DataResource.material(mainMaterialId));

            parts.add(PartInstance.from(
                    GearItemSets.PICKAXE.mainPart().create(mainMaterial)
            ));

            addRequiredPart(
                    parts,
                    gearItem,
                    PartTypes.ROD.get(),
                    SgItems.ROD.get(),
                    MaterialInstance.of(Const.Materials.WOOD)
            );

            addRequiredPart(
                    parts,
                    gearItem,
                    PartTypes.CORD.get(),
                    SgItems.CORD.get(),
                    MaterialInstance.of(Const.Materials.STRING)
            );

            addRequiredPart(
                    parts,
                    gearItem,
                    PartTypes.BINDING.get(),
                    SgItems.BINDING.get(),
                    MaterialInstance.of(Const.Materials.STRING)
            );

            addRequiredPart(
                    parts,
                    gearItem,
                    PartTypes.SETTING.get(),
                    SgItems.SETTING.get(),
                    MaterialInstance.of(Const.Materials.DIAMOND)
            );

            ItemStack stack = gearItem.construct(parts);
            stack.setCount(1);

            return stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static void addRequiredPart(
            List<PartInstance> parts,
            GearItem gearItem,
            PartType partType,
            CompoundPartItem partItem,
            MaterialInstance material
    ) {
        if (gearItem.requiresPartOfType(partType)) {
            parts.add(PartInstance.from(partItem.create(material)));
        }
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

    private static String prettifyTierName(String tierName) {
        if (tierName == null || tierName.isBlank()) {
            return "";
        }

        String path = tierName.trim().replace('_', ' ');
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

        return result.isEmpty() ? tierName : result.toString();
    }

    @Override
    public ResourceLocation getUid() {
        return SilentGearJadePlugin.REQUIRED_TIER;
    }
}
