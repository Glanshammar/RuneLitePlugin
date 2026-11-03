package com.main;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Apexea")
public interface ApexeaConfig extends Config
{
    /*
    @ConfigSection(
            name = "Bots",
            description = "",
            position = 0
    )
    String publisherSection = "Publishers";
     */

    @ConfigSection(
            name = "Bots",
            description = "",
            position = 1
    )
    String botSection = "Bots";

    @ConfigItem(
            keyName = "botWC",
            name = "Woodcutting bot",
            description = "Runs the Woodcutting bot",
            section = botSection,
            position = 0
    )
    default boolean botWC()
    {
        return false;
    }

    @ConfigItem(
            keyName = "botCrabs",
            name = "Sand crabs bot",
            description = "Runs the sand crabs bot",
            section = botSection,
            position = 1
    )
    default boolean botCrabs()
    {
        return false;
    }

    /*
    @ConfigItem(
            keyName = "objectToggle",
            name = "Object Publisher",
            description = "Runs the object scanner publisher",
            section = publisherSection,
            position = 1
    )
    default boolean publisherObjects()
    {
        return false;
    }
     */
}
