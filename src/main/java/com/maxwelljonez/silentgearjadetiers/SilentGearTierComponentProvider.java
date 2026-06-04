package com.maxwelljonez.silentgearjadetiers;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec2;
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
import org.slf4j.Logger;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum SilentGearTierComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Logger LOGGER = LogUtils.getLogger();

    /*
     * Temporary debug logging.
     * Keep this true for the next test.
     * After we confirm the logic, set it to false.
     */
    private static final boolean DEBUG_LOGGING = true;

    private static final ResourceLocation PICKAXE_CROSS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "silentgear_jade_tiers",
            "textures/gui/generic_pickaxe_cross.png"
    );

    private static final Set<ResourceLocation> DEBUGGED_BLOCKS = new HashSet<>();
    private static boolean loggedTierList = false;

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

    private record ToolCheckResult(boolean allowed, String reason) {
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

        MutableComponent text = Component.empty()
                .append(Component.literal("Required: ").withStyle(ChatFormatting.GRAY));

        String label = required.label();

        if (!label.isBlank()) {
            text.append(Component.literal(label).withStyle(style -> style.withColor(required.color())));
        }

        List<IElement> line = new ArrayList<>();

        if (SilentGearJadeTiersConfig.SHOW_CROSSED_PICKAXE_ICON.get()) {
            line.add(
                    IElementHelper.get()
                            .sprite(PICKAXE_CROSS_TEXTURE, 10, 10)
                            .size(new Vec2(10, 10))
                            .message(null)
            );
        }

        line.add(IElementHelper.get().text(text).message(null));

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
        if (!state.requiresCorrectToolForDrops()) {
            return null;
        }

        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        boolean debugThisBlock = DEBUG_LOGGING && DEBUGGED_BLOCKS.add(blockId);

        if (debugThisBlock) {
            LOGGER.info("[SGJT] Testing required Silent Gear tier for block {}", blockId);
        }

        for (Tier tier : tiers) {
            ToolCheckResult result = toolComponentAllowsDrops(tier.simulatedPickaxe(), state);

            if (debugThisBlock) {
                LOGGER.info(
                        "[SGJT]   tier={} level={} material={} allowed={} reason={} tool={}",
                        tier.tierName(),
                        tier.levelHint(),
                        tier.materialId(),
                        result.allowed(),
                        result.reason(),
                        tier.simulatedPickaxe().getHoverName().getString()
                );
            }

            if (result.allowed()) {
                if (debugThisBlock) {
                    LOGGER.info(
                            "[SGJT]   RESULT block={} requiredTier={} level={}",
                            blockId,
                            tier.tierName(),
                            tier.levelHint()
                    );
                }

                return tier;
            }
        }

        if (debugThisBlock) {
            LOGGER.info("[SGJT]   RESULT block={} no matching Silent Gear tier found", blockId);
        }

        return null;
    }

    /*
     * This mirrors Jade's harvest tool logic more closely than ItemStack.isCorrectToolForDrops.
     *
     * We intentionally inspect DataComponents.TOOL rules directly:
     * - first matching deniesDrops rule => false
     * - first matching minesAndDrops rule => true
     *
     * We do NOT fall back to ItemStack.isCorrectToolForDrops here, because that can hide
     * whether Silent Gear's generated Tool component is actually correct.
     */
    private static ToolCheckResult toolComponentAllowsDrops(ItemStack stack, BlockState state) {
        Tool tool = stack.get(DataComponents.TOOL);

        if (tool == null) {
            return new ToolCheckResult(false, "no_tool_component");
        }

        int index = 0;

        for (Tool.Rule rule : tool.rules()) {
            if (rule.correctForDrops().isPresent() && state.is(rule.blocks())) {
                boolean allowed = rule.correctForDrops().get();

                return new ToolCheckResult(
                        allowed,
                        allowed ? "rule_" + index + "_allows_drops" : "rule_" + index + "_denies_drops"
                );
            }

            index++;
        }

        return new ToolCheckResult(false, "no_matching_correct_for_drops_rule");
    }

    private static List<Tier> buildRuntimeTierList() {
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

        if (DEBUG_LOGGING && !loggedTierList) {
            loggedTierList = true;

            LOGGER.info("[SGJT] Runtime Silent Gear tier list: {} entries", tiers.size());

            for (Tier tier : tiers) {
                Tool tool = tier.simulatedPickaxe().get(DataComponents.TOOL);
                int ruleCount = tool == null ? 0 : tool.rules().size();

                LOGGER.info(
                        "[SGJT]   loaded tier={} level={} material={} color={} toolComponent={} rules={}",
                        tier.tierName(),
                        tier.levelHint(),
                        tier.materialId(),
                        Integer.toHexString(tier.color()),
                        tool != null,
                        ruleCount
                );
            }
        }

        return tiers;
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

            TagKey<Block> incorrectTag = harvestTier.incorrectForTool();

            if (incorrectTag == null) {
                return null;
            }

            ResourceLocation incorrectTagId = incorrectTag.location();

            if (!"silentgear".equals(incorrectTagId.getNamespace())) {
                return null;
            }

            if (!incorrectTagId.getPath().startsWith("incorrect_for_")
                    || !incorrectTagId.getPath().endsWith("_tools")) {
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

            if (simulatedPickaxe.get(DataComponents.TOOL) == null) {
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
        } catch (Exception e) {
            if (DEBUG_LOGGING) {
                LOGGER.warn("[SGJT] Failed to create tier from material {}", materialId, e);
            }

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
        } catch (Exception e) {
            if (DEBUG_LOGGING) {
                LOGGER.warn("[SGJT] Failed to create simulated Silent Gear pickaxe for material {}", mainMaterialId, e);
            }

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
