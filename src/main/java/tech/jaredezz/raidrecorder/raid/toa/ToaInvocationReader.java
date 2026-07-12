package tech.jaredezz.raidrecorder.raid.toa;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import tech.jaredezz.raidrecorder.RaidRecorderConfig;

/**
 * Reads the ToA invocation interface. The interface only exists in the lobby (it unloads
 * in-raid), so reads are opportunistic: poll while it's open, keep the last seen set for the
 * whole raid. Layout knowledge validated live in the toa-raid-log plugin: the invocation list is
 * child 52 of TOA_PARTYDETAILS; each invocation's toggle widget is child {@code ordinal()*3};
 * a toggle is active when the 4th arg of its on-op listener is 1.
 */
@Slf4j
@Singleton
public class ToaInvocationReader
{
	private static final int INVOCATIONS_CHILD = 52;
	private static final int ACTIVE_OP_ARG = 3;

	private final Client client;
	private final RaidRecorderConfig config;

	/** Last successfully-read invocation set — survives the interface unloading in-raid. */
	@Getter
	private Set<ToaInvocation> lastKnownInvocations = EnumSet.noneOf(ToaInvocation.class);
	/** Best-known raid level: the in-raid varbit when it's populated, else the invocation-point sum. */
	@Getter
	private int lastKnownRaidLevel = -1;
	/** Raw varbit value, kept for diagnostics/export transparency even when it's 0 (pre-entry). */
	@Getter
	private int lastKnownRaidLevelVarbit = -1;

	/** Invoked with the freshly-read set whenever the interface is readable (for the sync panel). */
	@Setter
	private Consumer<Set<ToaInvocation>> onRead;

	@Inject
	ToaInvocationReader(Client client, RaidRecorderConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** Poll from the game tick; refreshes the cached set when the interface is readable. */
	public void poll()
	{
		Widget parent = client.getWidget(InterfaceID.TOA_PARTYDETAILS, INVOCATIONS_CHILD);
		if (parent == null || parent.isHidden() || parent.getChildren() == null)
		{
			return;
		}

		Set<ToaInvocation> active = EnumSet.noneOf(ToaInvocation.class);
		for (ToaInvocation invocation : ToaInvocation.values())
		{
			Widget toggle = parent.getChild(invocation.getWidgetIx());
			if (toggle == null)
			{
				continue;
			}
			Object[] ops = toggle.getOnOpListener();
			if (ops != null && ops.length > ACTIVE_OP_ARG && ops[ACTIVE_OP_ARG] instanceof Integer
				&& (Integer) ops[ACTIVE_OP_ARG] == 1)
			{
				active.add(invocation);
			}
		}
		int varbitLevel = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
		int computedLevel = ToaInvocation.sumRaidLevel(active);
		int bestLevel = varbitLevel > 0 ? varbitLevel : computedLevel;

		if (config.verboseLog() && (!active.equals(lastKnownInvocations) || bestLevel != lastKnownRaidLevel))
		{
			log.info("[raid-recorder] invocations read: level={} (varbit={}, computed={}) count={} [{}]",
				bestLevel, varbitLevel, computedLevel, active.size(),
				active.stream().map(ToaInvocation::getDisplayName).collect(Collectors.joining(", ")));
		}
		lastKnownInvocations = active;
		lastKnownRaidLevel = bestLevel;
		lastKnownRaidLevelVarbit = varbitLevel;
		if (onRead != null)
		{
			onRead.accept(active);
		}
	}

	/**
	 * Best-known raid level right now: a fresh varbit read when in-raid (authoritative,
	 * server-confirmed), else the invocation-point sum from the last lobby read.
	 */
	public int readRaidLevel()
	{
		int varbitLevel = client.getVarbitValue(VarbitID.TOA_CLIENT_RAID_LEVEL);
		return varbitLevel > 0 ? varbitLevel : ToaInvocation.sumRaidLevel(lastKnownInvocations);
	}
}
