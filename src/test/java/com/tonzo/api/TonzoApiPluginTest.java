package com.tonzo.api;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TonzoApiPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TonzoApiPlugin.class);
		RuneLite.main(args);
	}
}