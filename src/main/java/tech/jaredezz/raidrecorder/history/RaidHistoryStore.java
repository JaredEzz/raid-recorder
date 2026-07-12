package tech.jaredezz.raidrecorder.history;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

/**
 * Persists the raid history as a JSON array in a local file, one per account, under
 * {@code RUNELITE_DIR/raid-recorder/history/<accountHash>.json}. A local file is used instead of
 * {@code ConfigManager} because the log grows without bound over a player's lifetime.
 *
 * <p>Writes go to a temp file and are moved into place atomically, so a crash mid-write can't
 * corrupt the log. All methods do file I/O and must not be called on the client thread or EDT.</p>
 */
@Slf4j
@Singleton
public class RaidHistoryStore
{
	private static final Type LOG_TYPE = new TypeToken<ArrayList<RaidHistoryEntry>>() { }.getType();

	private final Gson gson;
	private final File dir;

	@Inject
	RaidHistoryStore(Gson gson)
	{
		this.gson = gson;
		this.dir = new File(new File(RuneLite.RUNELITE_DIR, "raid-recorder"), "history");
	}

	private File fileFor(long accountHash)
	{
		return new File(dir, accountHash + ".json");
	}

	/** Load the full raid history for an account, or an empty list if none is stored yet. */
	public List<RaidHistoryEntry> load(long accountHash)
	{
		File file = fileFor(accountHash);
		if (!file.isFile())
		{
			return new ArrayList<>();
		}
		try
		{
			String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			List<RaidHistoryEntry> entries = gson.fromJson(json, LOG_TYPE);
			return entries != null ? entries : new ArrayList<>();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Could not read raid history at {}", file, e);
			return new ArrayList<>();
		}
	}

	/** Append one entry and persist. Loads + rewrites the whole file (histories are small: KB-scale). */
	public List<RaidHistoryEntry> append(long accountHash, RaidHistoryEntry entry)
	{
		List<RaidHistoryEntry> entries = load(accountHash);
		entries.add(entry);
		save(accountHash, entries);
		return entries;
	}

	private void save(long accountHash, List<RaidHistoryEntry> entries)
	{
		if (!dir.isDirectory() && !dir.mkdirs())
		{
			log.warn("Could not create raid history directory {}", dir);
			return;
		}
		File file = fileFor(accountHash);
		File tmp = new File(dir, file.getName() + ".tmp");
		try
		{
			Files.write(tmp.toPath(), gson.toJson(entries, LOG_TYPE).getBytes(StandardCharsets.UTF_8));
			Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
				StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e)
		{
			log.warn("Could not write raid history to {}", file, e);
		}
	}
}
