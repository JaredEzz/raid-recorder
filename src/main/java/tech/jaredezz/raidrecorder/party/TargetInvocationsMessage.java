package tech.jaredezz.raidrecorder.party;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.party.messages.PartyMemberMessage;

/** Broadcast by whoever hits "Set target": the invocation checklist the party should match. */
@RequiredArgsConstructor
@Getter
public class TargetInvocationsMessage extends PartyMemberMessage
{
	@SerializedName("t")
	private final long targetMask;
	@SerializedName("l")
	private final int raidLevel;
}
