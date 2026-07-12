package tech.jaredezz.raidrecorder;

import org.junit.Test;
import tech.jaredezz.raidrecorder.capture.ConsumableClassifier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConsumableClassifierTest
{
	@Test
	public void normalizesDoses()
	{
		assertEquals("Saradomin brew", ConsumableClassifier.supplyKey("Saradomin brew(4)"));
		assertEquals("Saradomin brew", ConsumableClassifier.supplyKey("Saradomin brew(1)"));
		assertEquals("Super restore", ConsumableClassifier.supplyKey("Super restore(3)"));
		assertEquals("Nectar", ConsumableClassifier.supplyKey("Nectar(4)"));
		assertEquals("Smelling salts", ConsumableClassifier.supplyKey("Smelling salts(2)"));
	}

	@Test
	public void recognizesFood()
	{
		assertEquals("Shark", ConsumableClassifier.supplyKey("Shark"));
		assertEquals("Manta ray", ConsumableClassifier.supplyKey("Manta ray"));
		assertEquals("Cooked karambwan", ConsumableClassifier.supplyKey("Cooked karambwan"));
	}

	@Test
	public void ignoresGear()
	{
		assertNull(ConsumableClassifier.supplyKey("Osmumten's fang"));
		assertNull(ConsumableClassifier.supplyKey("Rune pouch"));
	}

	@Test
	public void ignoresChargedJewellery()
	{
		assertNull(ConsumableClassifier.supplyKey("Amulet of glory(6)"));
		assertNull(ConsumableClassifier.supplyKey("Ring of dueling(8)"));
		assertNull(ConsumableClassifier.supplyKey("Games necklace(8)"));
	}
}
