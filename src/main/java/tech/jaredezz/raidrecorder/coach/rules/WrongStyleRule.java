package tech.jaredezz.raidrecorder.coach.rules;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tech.jaredezz.raidrecorder.coach.CoachContext;
import tech.jaredezz.raidrecorder.coach.CoachRule;
import tech.jaredezz.raidrecorder.model.CoachFinding;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.model.RoomRecord;
import tech.jaredezz.raidrecorder.model.Severity;

/**
 * Uses the equipment timeline to attribute each damage tick to the weapon style active at that
 * tick, then flags rooms where a meaningful share of damage was dealt on a style the raid module
 * doesn't recommend there (e.g. maging Ba-Ba). Skips rooms where the module has no opinion.
 */
public class WrongStyleRule implements CoachRule
{
	@Override
	public String id()
	{
		return "wrong_style";
	}

	@Override
	public List<CoachFinding> evaluate(RaidRecord record, CoachContext context)
	{
		List<CoachFinding> findings = new ArrayList<>();
		double warnShare = context.getThresholds().getWrongStyleDamageShareWarn();

		for (RoomRecord room : record.getRooms())
		{
			List<String> recommended = context.getModule().recommendedStyles(room.getRoom());
			if (recommended.isEmpty() || room.getEquipmentTimeline().isEmpty())
			{
				continue;
			}

			Map<String, Integer> damageByStyle = new LinkedHashMap<>();
			int total = 0;
			for (int[] hit : room.getDamageDealt().getByTick())
			{
				int absoluteTick = room.getEntryTick() + hit[0];
				String style = styleAtTick(room, absoluteTick);
				if (style == null)
				{
					continue;
				}
				damageByStyle.merge(style, hit[1], Integer::sum);
				total += hit[1];
			}
			if (total == 0)
			{
				continue;
			}

			int offStyle = damageByStyle.entrySet().stream()
				.filter(e -> !recommended.contains(e.getKey()))
				.mapToInt(Map.Entry::getValue).sum();
			double share = (double) offStyle / total;
			if (share >= warnShare)
			{
				findings.add(new CoachFinding(room.getRoom(), Severity.WARN, "wrong_gear_style",
					String.format("%.0f%% of your damage in %s was dealt on a non-recommended style "
							+ "(recommended: %s). Check your switches for this room.",
						share * 100, room.getRoom(), String.join("/", recommended)),
					ImmutableMap.of("offStyleShare", share, "damageByStyle", damageByStyle,
						"recommended", recommended)));
			}
		}
		return findings;
	}

	/** The weapon style in force at a tick, from the most recent equipment snapshot at or before it. */
	private static String styleAtTick(RoomRecord room, int tick)
	{
		RoomRecord.EquipmentSnapshot active = null;
		for (RoomRecord.EquipmentSnapshot snapshot : room.getEquipmentTimeline())
		{
			if (snapshot.getTick() <= tick)
			{
				active = snapshot;
			}
			else
			{
				break;
			}
		}
		if (active == null)
		{
			return null;
		}
		RoomRecord.Item weapon = active.getItems().get("WEAPON");
		return weapon != null ? GearStyles.classifyWeapon(weapon.getName()) : null;
	}
}
