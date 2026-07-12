package tech.jaredezz.raidrecorder.capture;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies inventory items as consumable supplies by name, and normalizes potion dose suffixes
 * so "Super restore(3)" and "Super restore(4)" both count as "Super restore".
 *
 * <p>Name-based on purpose: it needs no item-id table, survives new items, and works for the ToA
 * raid supplies (nectar, ambrosia, etc.) exactly like bank-brought food. The trade-off is that a
 * dropped shark counts as an eaten shark; the capture engine corroborates with the eat/drink
 * animation when it can and this classifier stays honest about being a heuristic.</p>
 */
public final class ConsumableClassifier
{
	private static final Pattern DOSE_SUFFIX = Pattern.compile("\\s*\\((\\d)\\)$");

	private ConsumableClassifier()
	{
	}

	/**
	 * Normalized supply name if the item is a consumable we track, else null.
	 * E.g. "Saradomin brew(3)" -> "Saradomin brew"; "Shark" -> "Shark"; "Rune pouch" -> null.
	 */
	public static String supplyKey(String itemName)
	{
		if (itemName == null || itemName.isEmpty())
		{
			return null;
		}

		Matcher doseMatcher = DOSE_SUFFIX.matcher(itemName);
		boolean hasDose = doseMatcher.find();
		String base = hasDose ? itemName.substring(0, doseMatcher.start()) : itemName;
		String lower = base.toLowerCase();

		// Anything with a dose suffix is a potion/drink (brews, restores, ToA nectar, ambrosia,
		// smelling salts, liquid adrenaline, blessed crystal scarab, silk dressing all use (n)) —
		// except charged jewellery/teleport items, which reuse the same "(n)" convention.
		if (hasDose)
		{
			return isChargedNonConsumable(lower) ? null : base;
		}

		if (isFood(lower))
		{
			return base;
		}

		return null;
	}

	private static final String[] CHARGED_KEYWORDS = {
		"amulet", "ring", "necklace", "bracelet", "pendant", "sceptre", "crystal", "chronicle",
		"rod of", "burning", "enchanted lyre", "waterskin", "teleport",
	};

	private static boolean isChargedNonConsumable(String lowerName)
	{
		for (String keyword : CHARGED_KEYWORDS)
		{
			if (lowerName.contains(keyword))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isFood(String lowerName)
	{
		for (String food : FOODS)
		{
			if (lowerName.equals(food))
			{
				return true;
			}
		}
		// Cakes and pies report partial-eat states with a suffix word instead of a dose.
		return lowerName.startsWith("2/3 ") || lowerName.startsWith("1/2 ") || lowerName.startsWith("half a ");
	}

	private static final String[] FOODS = {
		"shark", "manta ray", "anglerfish", "dark crab", "karambwan", "cooked karambwan",
		"tuna potato", "sea turtle", "paddlefish", "monkfish", "bass", "swordfish", "lobster",
		"wild pie", "summer pie", "admiral pie", "mushroom potato", "curry", "potato with cheese",
		"peach", "honey locust", "silk dressing", "cake", "chocolate cake", "pineapple pizza",
		"anchovy pizza", "meat pizza", "plain pizza", "cooked bream", "purple sweets",
	};
}
