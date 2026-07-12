package tech.jaredezz.raidrecorder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Dev-mode launcher: {@code ./gradlew run} boots RuneLite with this plugin loaded. */
public class RaidRecorderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RaidRecorderPlugin.class);
		RuneLite.main(args);
	}
}
