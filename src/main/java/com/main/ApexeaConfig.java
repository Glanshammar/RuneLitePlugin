package com.main;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Apexea")
public interface ApexeaConfig extends Config
{
    @ConfigSection(
            name = "Bots",
            description = "",
            position = 1
    )
    String botSection = "Bots";

    @ConfigItem(
            keyName = "botWoodcutting",
            name = "Woodcutting",
            description = "Runs the Woodcutting bot",
            section = botSection,
            position = 0
    )
    default boolean botWoodcutting()
    {
        return false;
    }

    @ConfigItem(
            keyName = "botThieveStalls",
            name = "Thieving Stalls",
            description = "Runs the thieving stalls bot",
            section = botSection,
            position = 1
    )
    default boolean botThieveStalls()
    {
        return false;
    }
}
