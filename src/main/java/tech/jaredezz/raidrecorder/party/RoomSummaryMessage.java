package tech.jaredezz.raidrecorder.party;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Compact per-room summary broadcast to the RuneLite party when a member finishes a room.
 * One small message per room per member — far under party rate/size limits (same transport
 * pattern as the built-in DPS counter and the earlier toa-raid-log plugin). Short field names
 * keep the wire size down.
 */
@RequiredArgsConstructor
@Getter
public class RoomSummaryMessage extends PartyMemberMessage
{
	@SerializedName("r")
	private final String raid;
	@SerializedName("m")
	private final String room;
	@SerializedName("n")
	private final String rsn;
	@SerializedName("d")
	private final int damageDealt;
	@SerializedName("t")
	private final int damageTaken;
	@SerializedName("k")
	private final int deaths;
	@SerializedName("u")
	private final double dpsUptimePct;
}
