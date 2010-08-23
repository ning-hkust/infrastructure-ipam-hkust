package edu.mit.csail.pag.utils;

import java.util.*;

public final class Randomness {

    private Randomness() {
        // no instances
    }

    public static final long SEED = 0;

    /**
     * The random number used any testtime a random choice is made. (Developer note: do not declare new Random objects;
     * use this one instead).
     */
    private static Random random = new Random(SEED);

    public static void reset(long newSeed) {
        random = new Random(newSeed);
    }

    public static int totalCallsToRandom = 0;

    public static boolean nextRandomBool() {
        totalCallsToRandom++;
        return random.nextBoolean();
    }

    public static String nextRandomString(int length) {
        totalCallsToRandom++;
        byte[] bytes = new byte[length];
        random.nextBytes(bytes);
        return new String(bytes);
    }

    public static String nextRandomASCIIString(int length) {
        char firstAscii = ' ';
        char lastAscii = '~';
        return nextRandomStringFromRange(length, firstAscii, lastAscii);
    }

    public static String nextRandomStringFromRange(int length, char min, char max) {
        if (min > max)
            throw new IllegalArgumentException("max lower than min " + max + " " + min);
        totalCallsToRandom++;
        char[] chars = new char[length];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (min + random.nextInt(max - min + 1));
        }
        return new String(chars);
    }

    /**
     * Uniformly random int from [0, i)
     */
    public static int nextRandomInt(int i) {
        totalCallsToRandom++;
        return random.nextInt(i);
    }

    /**
     * Uniformly random int from MIN to MAX
     */
    public static int nextRandomInt() {
        totalCallsToRandom++;
        return random.nextInt();
    }

    public static <T> T randomMember(List<T> list) {
        if (list == null || list.isEmpty())
            throw new IllegalArgumentException("Expected non-empty list");
        return list.get(nextRandomInt(list.size()));
    }

    // NOTE: this method is a clone of randomMemberWeighted(SimpleList).
    // TODO To avoid this duplication, we could make SimpleList implement the java.util.List
    // interface.
    public static <T extends WeightedElement> T randomMemberWeighted(List<T> list) {

        // Find interval length.
        double max = 0;
        for (int i = 0; i < list.size(); i++) {
            double weight = list.get(i).getWeight();
            if (weight <= 0)
                throw new IllegalStateException("weight was " + weight);
            max += weight;
        }
        Util.assertCond(max > 0);

        // Select a random point in interval and find its corresponding element.
        double randomPoint = Randomness.random.nextDouble() * max;
        double currentPoint = 0;
        for (int i = 0; i < list.size(); i++) {
            currentPoint += list.get(i).getWeight();
            if (currentPoint >= randomPoint)
                return list.get(i);
        }
        throw new IllegalStateException();
    }

    public static <T> T randomSetMember(Collection<T> set) {
        int randIndex = Randomness.nextRandomInt(set.size());
        return CollectionsExt.getNthIteratedElement(set, randIndex);
    }

    // NOTE: this method is a clone of randomMemberWeighted(SimpleList).
    // TODO To avoid this duplication, we could make SimpleList implement the java.util.List
    // interface.
    public static boolean randomBoolFromDistribution(double falseProb_, double trueProb_) {
        totalCallsToRandom++;
        double falseProb = falseProb_ / (falseProb_ + trueProb_);
        return (Randomness.random.nextDouble() >= falseProb);
    }

    // TODO Should be made more efficient
    public static <T> Set<T> randomNonEmptySubset(Set<T> set) {
        Set<T> copy = new LinkedHashSet<T>(set);
        int i = nextRandomInt(set.size()) + 1;
        Set<T> result = new LinkedHashSet<T>(i);
        for (; i > 0; i--) {
            T randomSetMember = randomSetMember(copy);
            result.add(randomSetMember);
            copy.remove(randomSetMember);
        }
        return result;
    }

}
