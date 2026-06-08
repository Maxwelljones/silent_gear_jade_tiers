package com.maxwelljonez.silentgearjadetiers;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.gui.GuiGraphics;
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
import snownee.jade.api.ui.Element;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public enum SilentGearTierComponentProvider implements IBlockComponentProvider {
    INSTANCE;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean debugLogging() {
        return SilentGearJadeTiersConfig.DEBUG_LOGGING.get();
    }

    private static final ResourceLocation PICKAXE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "silentgear_jade_tiers",
            "generic_pickaxe"
    );

    private static final ResourceLocation PICKAXE_CROSS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "silentgear_jade_tiers",
            "generic_pickaxe_cross"
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
            boolean heldCanMine = heldItemCanMine(accessor, state);
        
            line.add(
                    new TierPickaxeElement(
                            required.color(),
                            !heldCanMine
                    ).message(null)
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

        boolean correctTool = jadeStyleCanHarvest(held, state).allowed();

        if (correctTool) {
            return SilentGearJadeTiersConfig.SHOW_IF_HOLDING_CORRECT_TOOL.get();
        }

        return SilentGearJadeTiersConfig.SHOW_IF_HOLDING_WRONG_TOOL.get();
    }
    private static boolean heldItemCanMine(BlockAccessor accessor, BlockState state) {
    if (accessor.getPlayer() == null) {
        return false;
    }

    ItemStack held = accessor.getPlayer().getMainHandItem();

    if (held.isEmpty()) {
        return false;
    }

    return jadeStyleCanHarvest(held, state).allowed();
}

    private static Tier findRequiredTier(BlockState state, List<Tier> tiers) {
        if (!state.requiresCorrectToolForDrops()) {
            return null;
        }
    
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        boolean debugThisBlock = debugLogging() && DEBUGGED_BLOCKS.add(blockId);
    
        if (debugThisBlock) {
            LOGGER.info("[SGJT] Testing required Silent Gear tier for block {}", blockId);
        }
    
        Tier best = null;
    
        for (Tier tier : tiers) {
            ToolCheckResult result = jadeStyleCanHarvest(tier.simulatedPickaxe(), state);
    
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
    
            if (!result.allowed()) {
                continue;
            }
    
            if (best == null || tier.sortLevel() < best.sortLevel()) {
                best = tier;
            }
        }
    
        if (debugThisBlock && best != null) {
            LOGGER.info(
                    "[SGJT]   RESULT block={} requiredLevel={} winningMaterial={} winningTier={}",
                    blockId,
                    best.levelHint(),
                    best.materialId(),
                    best.tierName()
            );
        }
    
        return best;
    }

    private static ToolCheckResult jadeStyleCanHarvest(ItemStack stack, BlockState state) {
    Tool tool = stack.get(DataComponents.TOOL);

    if (tool != null) {
        int index = 0;

        for (Tool.Rule rule : tool.rules()) {
            if (rule.correctForDrops().isPresent() && state.is(rule.blocks())) {
                boolean allowed = rule.correctForDrops().get();

                return new ToolCheckResult(
                        allowed,
                        allowed ? "tool_rule_" + index + "_allows_drops" : "tool_rule_" + index + "_denies_drops"
                );
            }

            index++;
        }

        if (tool.getMiningSpeed(state) > tool.defaultMiningSpeed()) {
            return new ToolCheckResult(true, "tool_component_mining_speed");
        }
    }

    if (stack.isCorrectToolForDrops(state)) {
        return new ToolCheckResult(true, "stack_is_correct_tool_for_drops");
    }

    return new ToolCheckResult(false, "not_correct_tool");
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

        if (debugLogging() && !loggedTierList) {
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

        String tagPath = incorrectTagId.getPath();

        if (!tagPath.startsWith("incorrect_for_") || !tagPath.endsWith("_tools")) {
            return null;
        }

        String tierName = harvestTier.name();
        
        if (tierName == null || tierName.isBlank()) {
            tierName = materialId.getPath();
        }
        
        tierName = tierName.trim();

        String levelHint = harvestTier.levelHint().orElse("").trim();

        if (levelHint.isBlank()) {
            return null;
        }

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
        if (debugLogging()) {
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
            if (debugLogging()) {
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

    private static final class TierPickaxeElement extends Element {
        private static final int DISPLAY_SIZE = 10;
        private static final int Y_OFFSET = 1;
        private static final float TINT_STRENGTH = 0.50F;
    
        private final int color;
        private final boolean crossed;
    
        private TierPickaxeElement(int color, boolean crossed) {
            this.color = color & 0xFFFFFF;
            this.crossed = crossed;
        }
    
        @Override
        public Vec2 getSize() {
            return new Vec2(DISPLAY_SIZE + 3, DISPLAY_SIZE);
        }
    
        private static float blendWithWhite(int channel, float strength) {
            float colorChannel = channel / 255.0F;
            return 1.0F - ((1.0F - colorChannel) * strength);
        }
    
        @Override
        public void render(GuiGraphics guiGraphics, float x, float y, float maxX, float maxY) {
            int drawX = Math.round(x);
            int drawY = Math.round(y) + Y_OFFSET;
    
            float red = blendWithWhite((color >> 16) & 0xFF, TINT_STRENGTH);
            float green = blendWithWhite((color >> 8) & 0xFF, TINT_STRENGTH);
            float blue = blendWithWhite(color & 0xFF, TINT_STRENGTH);
    
            RenderSystem.setShaderColor(red, green, blue, 1.0F);
            guiGraphics.blitSprite(PICKAXE_TEXTURE, drawX, drawY, DISPLAY_SIZE, DISPLAY_SIZE);
    
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    
            if (crossed) {
                guiGraphics.blitSprite(PICKAXE_CROSS_TEXTURE, drawX, drawY, DISPLAY_SIZE, DISPLAY_SIZE);
            }
        }
    }
    
    @Override
    public ResourceLocation getUid() {
        return SilentGearJadePlugin.REQUIRED_TIER;
    }
}
