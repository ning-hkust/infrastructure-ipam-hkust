package randoop.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;

import randoop.BugInRandoopException;

public final class Randomness {

	private Randomness() {
		// no instances
	}

	public static final long SEED = 0;

	/**
	 * The random number used any testtime a random choice is made. (Developer
	 * note: do not declare new Random objects; use this one instead).
	 */
	static Random random = new Random(SEED);

	public static void reset(long newSeed) {
		random = new Random(newSeed);
	}

	public static int totalCallsToRandom = 0;

	public static Object nextRandom(Object o) {
		Object ret = null;
		if (o instanceof Boolean)
			ret = nextRandomBool();

		if (o instanceof Integer)
			ret = nextRandomInt();

		if (o instanceof Long)
			ret = nextRandomLong();

		if (o instanceof Float)
			ret = nextRandomFloat();

		if (o instanceof Double)
			ret = nextRandomDouble();

		if (o instanceof Byte)
			ret = nextRandomByte();

		if (o instanceof String)
			ret = nextRandomString();

		return ret;
	}

	public static boolean nextRandomBool() {
		totalCallsToRandom++;
		return random.nextBoolean();
	}

	/**
	 * Uniformly random int from [0, i)
	 */
	public static int nextRandomInt(int i) {
		totalCallsToRandom++;
		return random.nextInt(i);
	}

	public static int nextRandomInt() {
		totalCallsToRandom++;
		return random.nextInt();
	}

	public static short nextRandomShort() {
		totalCallsToRandom++;
		int i = random.nextInt();
		if (i > 32767)
			return (short) (i % 32767);
		if (i < -32768)
			return (short) -(i % -32768);
		return (short) i;
	}	
	
	public static long nextRandomLong() {
		totalCallsToRandom++;
		return random.nextLong();
	}

	public static float nextRandomFloat() {
		totalCallsToRandom++;
		return random.nextFloat() + random.nextInt();
	}

	public static double nextRandomDouble() {
		totalCallsToRandom++;
		return random.nextDouble() + random.nextInt();
	}

	public static byte nextRandomByte() {
		totalCallsToRandom++;
		return (byte) (random.nextInt(256) - 128);
	}

	public static char nextRandomChar()
	{
		return (char) random.nextInt(128);
	}
	
	public static String nextRandomString() {
		totalCallsToRandom++;
		// Use numbers as well as text
		boolean bNum = random.nextBoolean();
		// Use "-" and "_"
		boolean bBar = random.nextBoolean();
		// Use extra special characters (*)
		boolean bSchar = random.nextBoolean();

		char[] schar = { ' ', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
				'+', '=', '~', '`', '\"', '\'', ';', ':', '[', ']', '{', '}',
				'-', '_', ',', '.', '<', '>', '/', '?', '\n', '\t' };

		int size = random.nextInt(512);
		char ret[] = new char[size];

		for (int i = 0; i < size; i++) {
			ret[i] = nextRandomChar();
			if (bNum && Character.isDigit(ret[i]))
				continue;

			if (bBar)
				if (ret[i] == '-' || ret[i] == '_')
					continue;
			if (bSchar)
				for (int j = 0; j < schar.length; j++)
					if (ret[i] == schar[j])
						continue;

			if (Character.isLetter(ret[i]))
				continue;

			if (ret[i] == ' ')
				continue;

			i--;
		}

		// System.out.println("String generated : " + String.valueOf(ret));
		return String.valueOf(ret);
	}

	public static <T> T randomMember(List<T> list) {
		if (list == null || list.isEmpty())
			throw new IllegalArgumentException("Expected non-empty list");
		return list.get(nextRandomInt(list.size()));
	}

	public static <T> T randomMember(SimpleList<T> list) {
		if (list == null || list.size() == 0)
			throw new IllegalArgumentException("Expected non-empty list");
		return list.get(nextRandomInt(list.size()));
	}

	// Warning: iterates through the entire list twice (once to compute interval
	// length, once to select element).
	public static <T extends WeightedElement> T randomMemberWeighted(
			SimpleList<T> list) {

		// Find interval length.
		double max = 0;
		for (int i = 0; i < list.size(); i++) {
			double weight = list.get(i).getWeight();
			if (weight <= 0)
				throw new BugInRandoopException("weight was " + weight);
			max += weight;
		}
		assert max > 0;

		// Select a random point in interval and find its corresponding element.
		double randomPoint = Randomness.random.nextDouble() * max;
		double currentPoint = 0;
		for (int i = 0; i < list.size(); i++) {
			currentPoint += list.get(i).getWeight();
			if (currentPoint >= randomPoint) {
				return list.get(i);
			}
		}
		throw new BugInRandoopException();
	}

	public static <T> T randomSetMember(Collection<T> set) {
		int randIndex = Randomness.nextRandomInt(set.size());
		return CollectionsExt.getNthIteratedElement(set, randIndex);
	}

	public static boolean randomBoolFromDistribution(double falseProb_,
			double trueProb_) {
		totalCallsToRandom++;
		double falseProb = falseProb_ / (falseProb_ + trueProb_);
		return (Randomness.random.nextDouble() >= falseProb);
	}

}
