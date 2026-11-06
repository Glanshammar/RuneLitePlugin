package com.main;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Apexea")
public interface ApexeaConfig extends Config
{
    @ConfigSection(
            name = "Credentials",
            description = "",
            position = 1
    )
    String userSection = "User";

    @ConfigItem(keyName = "username", name = "Username", description = "Alora username", section = userSection, position = 0)
    default String username() { return ""; }

    @ConfigItem(keyName = "password", name = "Password", description = "Alora password", secret = true, section = userSection, position = 1)
    default String password() { return ""; }

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

    @ConfigItem(
            keyName = "botAfkCombat",
            name = "AFK Combat",
            description = "Runs the AFK combat bot",
            section = botSection,
            position = 2
    )
    default boolean botAfkCombat()
    {
        return false;
    }

    @ConfigItem(
            keyName = "test",
            name = "Test Thing",
            description = "Test something",
            section = botSection,
            position = 3
    )
    default boolean testThing()
    {
        return false;
    }
}
