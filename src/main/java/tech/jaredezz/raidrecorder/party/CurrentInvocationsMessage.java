package tech.jaredezz.raidrecorder.party;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Broadcast by each party member: the invocations they currently have toggled on, so the panel
 * can show who matches the target.
 */
@RequiredArgsConstructor
@Getter
public class CurrentInvocationsMessage extends PartyMemberMessage
{
	@SerializedName("c")
	private final long currentMask;
}
