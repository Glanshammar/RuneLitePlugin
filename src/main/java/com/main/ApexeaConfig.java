package com.main;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Apexea")
public interface ApexeaConfig extends Config
{
    @ConfigSection(
            name = "Functions",
            description = "Activate functions in the plugin.",
            position = 0
    )
    String sectionFunctions = "Functions";

    @ConfigItem(
            keyName = "funcWidgets",
            name = "Widgets",
            description = "Runs the widgets function",
            section = sectionFunctions,
            position = 0
    )
    default boolean funcWidgets() { return false; }

    @ConfigSection(
            name = "Bots",
            description = "",
            position = 1
    )
    String sectionBots = "Bots";

    @ConfigItem(
            keyName = "botWoodcutting",
            name = "Woodcutting",
            description = "Runs the Woodcutting bot",
            section = sectionBots,
            position = 0
    )
    default boolean botWoodcutting() { return false; }

    @ConfigItem(
            keyName = "botThieveStalls",
            name = "Thieving Stalls",
            description = "Runs the thieving stalls bot",
            section = sectionBots,
            position = 1
    )
    default boolean botThieveStalls() { return false; }

    @ConfigItem(
            keyName = "botFiremaking",
            name = "Firemaking",
            description = "Runs the firemaking bot",
            section = sectionBots,
            position = 2
    )
    default boolean botFiremaking() { return false; }

    @ConfigSection(
            name = "Combat",
            description = "",
            position = 2
    )
    String sectionCombat = "Combat";

    @ConfigItem(
            keyName = "botCombat",
            name = "Combat bot",
            description = "Runs the combat bot",
            section = sectionCombat,
            position = 0
    )
    default boolean botCombat()
    { return false; }

    @ConfigItem(
            keyName = "combatScript",
            name = "Combat Script",
            description = "Combat script. Pick one before enabling the combat bot.",
            section = sectionCombat,
            position = 1)
    default CombatScript botCombatScript() { return CombatScript.NEAREST; }

    enum CombatScript{
        NEAREST,
        GEMSTONE_CRAB,
        SLAYER,
    }
}