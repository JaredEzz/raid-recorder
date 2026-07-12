package tech.jaredezz.raidrecorder.panel;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.ImageIcon;

/**
 * Small pixel-art hitsplat badges for the live feed. Rendered at low resolution then upscaled
 * with nearest-neighbor interpolation for a chunky, retro look echoing the OSRS hitsplat diamond
 * — no external image assets, no network fetches, just local Graphics2D drawing.
 */
final class PixelIcon
{
	private static final int BASE = 16;
	private static final int SCALE = 2;
	private static final int SIZE = BASE * SCALE;

	private PixelIcon()
	{
	}

	enum Tier
	{
		NEW_MAX(new Color(255, 176, 0)),
		FIRST(new Color(150, 150, 160)),
		HIGH(new Color(216, 62, 44)),
		MID(new Color(230, 196, 40)),
		LOW(new Color(96, 148, 214)),
		POOR(new Color(80, 80, 90));

		final Color color;

		Tier(Color color)
		{
			this.color = color;
		}
	}

	/** Tier for a normal (non-first, non-new-max) hit, purely from its luck percentage. */
	static Tier tierFor(double luckPct)
	{
		if (luckPct >= 85)
		{
			return Tier.HIGH;
		}
		if (luckPct >= 55)
		{
			return Tier.MID;
		}
		if (luckPct >= 25)
		{
			return Tier.LOW;
		}
		return Tier.POOR;
	}

	static ImageIcon hitBadge(int amount, Tier tier)
	{
		BufferedImage low = new BufferedImage(BASE, BASE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D lg = low.createGraphics();
		lg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		Polygon diamond = new Polygon(
			new int[]{BASE / 2, BASE - 1, BASE / 2, 0},
			new int[]{0, BASE / 2, BASE - 1, BASE / 2}, 4);
		lg.setColor(tier.color);
		lg.fillPolygon(diamond);
		lg.setColor(tier.color.darker());
		lg.drawPolygon(diamond);

		String text = amount > 999 ? "999+" : Integer.toString(amount);
		lg.setFont(new Font(Font.SANS_SERIF, Font.BOLD, text.length() > 2 ? 6 : 7));
		lg.setColor(Color.WHITE);
		FontMetrics fm = lg.getFontMetrics();
		int tx = (BASE - fm.stringWidth(text)) / 2;
		int ty = (BASE + fm.getAscent()) / 2 - 1;
		lg.drawString(text, tx, ty);
		lg.dispose();

		BufferedImage scaled = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D sg = scaled.createGraphics();
		sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		sg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		sg.drawImage(low, 0, 0, SIZE, SIZE, null);
		sg.dispose();

		return new ImageIcon(scaled);
	}
}
