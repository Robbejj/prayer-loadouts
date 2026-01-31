package com.prayerloadouts;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrayerLoadoutsPluginTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(PrayerLoadoutsPlugin.class);
		RuneLite.main(args);
	}
}