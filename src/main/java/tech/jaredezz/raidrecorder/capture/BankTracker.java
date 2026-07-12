package tech.jaredezz.raidrecorder.capture;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import tech.jaredezz.raidrecorder.model.RaidRecord;

/**
 * Keeps the latest bank snapshot. The bank container is only readable while the bank interface is
 * open, so we snapshot opportunistically on every {@link ItemContainerChanged} for the bank and
 * keep the most recent one for the whole session. Feeds the owned-but-unused-upgrade coach rule.
 */
@Singleton
public class BankTracker
{
	private final ItemManager itemManager;

	@Getter
	private RaidRecord.BankSnapshot latestSnapshot;

	@Inject
	BankTracker(ItemManager itemManager)
	{
		this.itemManager = itemManager;
	}

	/** Call from the plugin's ItemContainerChanged handler (client thread — name lookups are safe). */
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.BANK)
		{
			return;
		}
		ItemContainer bank = event.getItemContainer();
		if (bank == null)
		{
			return;
		}

		RaidRecord.BankSnapshot snapshot = new RaidRecord.BankSnapshot();
		snapshot.setCapturedEpochMs(System.currentTimeMillis());
		Map<String, Integer> items = new LinkedHashMap<>();
		Map<String, String> names = new LinkedHashMap<>();
		for (Item item : bank.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			String key = Integer.toString(item.getId());
			items.merge(key, item.getQuantity(), Integer::sum);
			names.putIfAbsent(key, itemManager.getItemComposition(item.getId()).getName());
		}
		snapshot.setItems(items);
		snapshot.setNames(names);
		latestSnapshot = snapshot;
	}
}
