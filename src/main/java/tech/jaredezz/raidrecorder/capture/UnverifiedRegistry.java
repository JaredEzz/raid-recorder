package tech.jaredezz.raidrecorder.capture;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where every TODO(verify) constant registers itself, so each export honestly lists which values
 * in it rest on unverified data. See KNOWN_UNKNOWNS.md for the full list and how to verify each.
 */
public final class UnverifiedRegistry
{
	private static final Set<String> ENTRIES = Collections.synchronizedSet(new TreeSet<>());

	private UnverifiedRegistry()
	{
	}

	/** Register a TODO(verify) note; returns the value unchanged so it can wrap initializers. */
	public static int note(String what, int value)
	{
		ENTRIES.add(what);
		return value;
	}

	public static void note(String what)
	{
		ENTRIES.add(what);
	}

	public static Set<String> all()
	{
		synchronized (ENTRIES)
		{
			return new TreeSet<>(ENTRIES);
		}
	}
}
