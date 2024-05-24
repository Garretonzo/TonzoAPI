package com.tonzo.api;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("tonzoapi")
public interface TonzoApiConfig extends Config
{
	@ConfigItem(
		keyName = "apiPort",
		name = "HTTP Server Port",
		description = "Port # the HTTP server will run on"
	)
	default String apiPort()
	{
		return "5252";
	}
}
