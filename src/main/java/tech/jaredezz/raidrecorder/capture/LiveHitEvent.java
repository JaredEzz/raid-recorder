package tech.jaredezz.raidrecorder.capture;

/**
 * One damage-dealt hit, packaged for the panel's live feed. Ephemeral UI data — not part of the
 * persisted {@code RaidRecord} schema.
 */
public class LiveHitEvent
{
	public final long epochMs;
	public final String weapon;
	public final String target;
	public final int amount;
	public final int priorMax;
	public final boolean isFirst;
	public final boolean newMax;
	public final double luckPct;
	public final double rollingDps;
	public final double sessionDps;

	public LiveHitEvent(long epochMs, String weapon, String target, int amount, int priorMax,
		boolean isFirst, boolean newMax, double luckPct, double rollingDps, double sessionDps)
	{
		this.epochMs = epochMs;
		this.weapon = weapon;
		this.target = target;
		this.amount = amount;
		this.priorMax = priorMax;
		this.isFirst = isFirst;
		this.newMax = newMax;
		this.luckPct = luckPct;
		this.rollingDps = rollingDps;
		this.sessionDps = sessionDps;
	}
}
