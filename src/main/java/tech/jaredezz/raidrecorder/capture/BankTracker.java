package tech.jaredezz.raidrecorder.capture;

import java.util.LinkedHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
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
			int id = item.getId();
			int quantity = item.getQuantity();
			if (id <= 0 || quantity <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(id);
			if (!isRealOwnedItem(id, quantity, comp.getPlaceholderTemplateId()))
			{
				continue;
			}
			String key = Integer.toString(id);
			items.merge(key, quantity, Integer::sum);
			names.putIfAbsent(key, comp.getName());
		}
		snapshot.setItems(items);
		snapshot.setNames(names);
		latestSnapshot = snapshot;
	}

	/**
	 * A bank slot counts as a <em>real, owned</em> item only when it holds a positive quantity of a
	 * non-placeholder item variant. Pure logic, extracted so it can be unit tested without a client
	 * mock.
	 *
	 * <p><b>Why the old {@code quantity > 0} check was not enough.</b> OSRS's bank-placeholder feature
	 * leaves an empty "ghost" slot behind when you withdraw an item with placeholders enabled, so you
	 * remember what belongs there. That ghost is a <em>distinct item variant</em> with its own item id
	 * and the <em>same display name</em> as the real item (e.g. a "Twisted bow" placeholder reports the
	 * name "Twisted bow"). Because it is a real slot with a real id and name, it was being captured as
	 * owned gear — which produced the confirmed-wrong coach advice "your bank has a twisted bow" when
	 * the player only has the placeholder (his group-ironman mate holds the actual bow).</p>
	 *
	 * <p><b>The verifiable signal.</b> {@link ItemComposition#getPlaceholderTemplateId()} returns
	 * {@code 14401} for a placeholder variant and {@code -1} for a real item. This is documented in the
	 * RuneLite API javadoc ("Gets a value specifying whether the item is a placeholder. Returns 14401 if
	 * placeholder, -1 otherwise") and is the same signal RuneLite core uses to zero out placeholders in
	 * {@code BankPlugin.valueSearch}. We test {@code != -1} rather than {@code == 14401} to match core
	 * and stay robust if the template id ever changes.</p>
	 *
	 * <p><b>Remaining uncertainty (marked, not guessed).</b> Whether OSRS reports a placeholder slot's
	 * quantity as {@code 0} or {@code 1} in the bank {@link ItemContainer} is not authoritatively
	 * documented, and sources conflict: RuneLite core's {@code BankPlugin.calculate} filters placeholders
	 * out with {@code quantity == 0} alone, yet this player's real bug proves at least some placeholder
	 * slots reach us with a non-zero quantity (otherwise the pre-existing {@code quantity <= 0} guard
	 * would already have dropped it). The placeholder-template-id check does not depend on the quantity
	 * value at all, so it is correct either way; the {@code quantity > 0} guard is kept only to skip
	 * genuinely empty slots.</p>
	 */
	static boolean isRealOwnedItem(int itemId, int quantity, int placeholderTemplateId)
	{
		return itemId > 0 && quantity > 0 && placeholderTemplateId == -1;
	}
}
