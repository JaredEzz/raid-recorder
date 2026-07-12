package tech.jaredezz.raidrecorder.export;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import tech.jaredezz.raidrecorder.RaidRecorderConfig;
import tech.jaredezz.raidrecorder.coach.CoachEngine;
import tech.jaredezz.raidrecorder.model.RaidRecord;
import tech.jaredezz.raidrecorder.raid.RaidModule;

/**
 * Writes the three per-raid artifacts to the export directory:
 * raid-&lt;timestamp&gt;.json, raid-&lt;timestamp&gt;-summary.md, raid-&lt;timestamp&gt;-prompt.md.
 * Everything here runs on RuneLite's shared background executor — never the client thread.
 */
@Slf4j
@Singleton
public class RaidExporter
{
	private final RaidRecorderConfig config;
	private final CoachEngine coachEngine;
	private final ScheduledExecutorService executor;
	private final Gson gson;

	@Inject
	RaidExporter(RaidRecorderConfig config, CoachEngine coachEngine,
		ScheduledExecutorService executor, Gson gson)
	{
		this.config = config;
		this.coachEngine = coachEngine;
		this.executor = executor;
		this.gson = gson.newBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	}

	/**
	 * Coach + export off-thread. The record must already be fully detached from client state
	 * (it is — the capture engine builds it from plain POJOs before handing it over).
	 */
	public void export(RaidRecord record, RaidModule module)
	{
		executor.execute(() -> doExport(record, module));
	}

	private void doExport(RaidRecord record, RaidModule module)
	{
		try
		{
			Path dir = exportDir();
			Files.createDirectories(dir);

			if (config.coachEnabled())
			{
				record.setCoachFindings(coachEngine.evaluate(record, module, thresholdsPath(dir)));
			}

			String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss")
				.format(new Date(record.getTiming().getStartEpochMs()));
			String base = "raid-" + stamp;

			if (config.exportJson())
			{
				write(dir.resolve(base + ".json"), gson.toJson(record));
			}
			if (config.exportSummary())
			{
				write(dir.resolve(base + "-summary.md"), SummaryWriter.render(record, module));
			}
			if (config.exportPrompt())
			{
				write(dir.resolve(base + "-prompt.md"), PromptWriter.render(record, module, gson));
			}
			log.info("[raid-recorder] exported {} to {}", base, dir);
		}
		catch (IOException | RuntimeException e)
		{
			log.error("[raid-recorder] export failed", e);
		}
	}

	private void write(Path path, String content) throws IOException
	{
		Files.write(path, content.getBytes(StandardCharsets.UTF_8));
	}

	public Path exportDir()
	{
		String configured = config.exportDir();
		if (configured != null && !configured.trim().isEmpty())
		{
			return Paths.get(configured.trim());
		}
		return RuneLite.RUNELITE_DIR.toPath().resolve("raid-recorder");
	}

	private Path thresholdsPath(Path exportDir)
	{
		String configured = config.thresholdsPath();
		if (configured != null && !configured.trim().isEmpty())
		{
			return Paths.get(configured.trim());
		}
		return exportDir.resolve("coach-thresholds.json");
	}
}
