package com.maxwelljonez.silentgearjadetiers;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SilentGearJadeTiersConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue SHOW_REQUIRED_TIER;
    public static final ModConfigSpec.BooleanValue SHOW_CROSSED_PICKAXE_ICON;
    public static final ModConfigSpec.BooleanValue SHOW_NUMERIC_LEVEL;
    public static final ModConfigSpec.BooleanValue SHOW_TIER_NAME;

    public static final ModConfigSpec.BooleanValue SHOW_IF_HOLDING_NO_TOOL;
    public static final ModConfigSpec.BooleanValue SHOW_IF_HOLDING_WRONG_TOOL;
    public static final ModConfigSpec.BooleanValue SHOW_IF_HOLDING_CORRECT_TOOL;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("display");

        SHOW_REQUIRED_TIER = builder
                .comment("Show the Silent Gear required mining tier line in Jade.")
                .define("show_required_tier", true);

        SHOW_CROSSED_PICKAXE_ICON = builder
                .comment("Show a small crossed pickaxe text marker before the required tier line. The PNG icon renderer will be added later.")
                .define("show_crossed_pickaxe_icon", true);

        SHOW_NUMERIC_LEVEL = builder
                .comment("Show the Silent Gear harvest tier level_hint value.")
                .define("show_numeric_level", true);

        SHOW_TIER_NAME = builder
                .comment("Show the Silent Gear material/tier name.")
                .define("show_tier_name", false);

        builder.pop();

        builder.push("held_tool_filter");

        SHOW_IF_HOLDING_NO_TOOL = builder
                .comment("Show the required tier line when the player is holding no tool.")
                .define("show_if_holding_no_tool", true);

        SHOW_IF_HOLDING_WRONG_TOOL = builder
                .comment("Show the required tier line when the held item cannot harvest the block.")
                .define("show_if_holding_wrong_tool", true);

        SHOW_IF_HOLDING_CORRECT_TOOL = builder
                .comment("Show the required tier line when the held item can harvest the block.")
                .define("show_if_holding_correct_tool", true);

        builder.pop();

        SPEC = builder.build();
    }

    private SilentGearJadeTiersConfig() {
    }
}
