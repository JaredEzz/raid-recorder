package tech.jaredezz.raidrecorder.raid;

import com.google.common.collect.ImmutableList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import tech.jaredezz.raidrecorder.raid.cox.CoxModule;
import tech.jaredezz.raidrecorder.raid.toa.ToaModule;
import tech.jaredezz.raidrecorder.raid.tob.TobModule;

/**
 * Owns the installed raid modules and answers "which raid, if any, is the player in right now".
 * Raid selection is purely by region detection, so modules never conflict.
 */
@Singleton
public class RaidModuleRegistry
{
	private final List<RaidModule> modules;

	@Inject
	RaidModuleRegistry(ToaModule toa, CoxModule cox, TobModule tob)
	{
		this.modules = ImmutableList.of(toa, cox, tob);
	}

	/** The module whose raid the player is currently inside, or null. */
	public RaidModule activeModule(Client client)
	{
		for (RaidModule module : modules)
		{
			if (module.isInRaid(client))
			{
				return module;
			}
		}
		return null;
	}

	public List<RaidModule> all()
	{
		return modules;
	}
}
