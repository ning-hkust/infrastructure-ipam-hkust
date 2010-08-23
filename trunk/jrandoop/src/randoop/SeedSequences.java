package randoop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import randoop.util.Randomness;

import randoop.util.PrimitiveTypes;
import randoop.util.Reflection;

/**
 * Provides functionality for creating a set of sequences that create a set of
 * primitive values. Used by sequence generators.
 */
public final class SeedSequences {
	private SeedSequences() {
		throw new IllegalStateException("no instance");
	}

	public static final List<Object> primitiveSeeds = Arrays.<Object> asList(
			(byte) (-1), (byte) 0, (byte) 1, (byte) 10, (byte) 100,
			(short) (-1), (short) 0, (short) 1, (short) 10, (short) 100, (-1),
			0, 1, 10, 100, (-1L), 0L, 1L, 10L, 100L, (float) -1.0, (float) 0.0,
			(float) 1.0, (float) 10.0, (float) 100.0, -1.0, 0.0, 1.0, 10.0,
			100.0, '#', ' ', '4', 'a', true, false, "", "hi!", " ",
			"hi this is randoop test\n");

	/**
	 * A set of sequences that create primitive values, e.g. int i = 0; or
	 * String s = "hi";
	 */
	public static Set<Sequence> defaultSeeds() {
		List<Object> seeds = new ArrayList<Object>(primitiveSeeds);
		return SeedSequences.objectsToSeeds(seeds);
	}

	/**
	 * Precondition: objs consists exclusively of boxed primitives and strings.
	 * Returns a set of sequences that create the given objects.
	 */
	public static Set<Sequence> objectsToSeeds(Collection<Object> objs) {
		Set<Sequence> retval = new LinkedHashSet<Sequence>();
		for (Object o : objs) {
			Class<?> cls = o.getClass();

			if (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(cls)) {
				cls = PrimitiveTypes.primitiveType(cls);
			}
			retval
					.add(Sequence
							.create(new PrimitiveOrStringOrNullDecl(cls, o)));
		}
		return retval;
	}

	public static Set<Object> getSeeds(Class<?> c) {
		Set<Object> result = new LinkedHashSet<Object>();
		for (Object seed : primitiveSeeds) {
			boolean seedOk = isOk(c, seed);
			if (seedOk)
				result.add(seed);
		}
		return result;
	}

	private static boolean isOk(Class<?> c, Object seed) {
		if (PrimitiveTypes.isBoxedPrimitiveTypeOrString(c)) {
			c = PrimitiveTypes.getUnboxType(c);
		}
		return Reflection.canBePassedAsArgument(seed, c);
	}

	// JJ
	public static Set<Sequence> RandomSeeds(int num) {
		Set<Sequence> retval = new LinkedHashSet<Sequence>();

		for (int i = 0; i < num; i++) {
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					boolean.class, Randomness.nextRandomBool())));
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					int.class, Randomness.nextRandomInt())));
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					long.class, Randomness.nextRandomLong())));
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					float.class, Randomness.nextRandomFloat())));
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					double.class, Randomness.nextRandomDouble())));
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					byte.class, Randomness.nextRandomByte())));
			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(""
					.getClass(), Randomness.nextRandomString())));
		}

		return retval;
	}

	// JJ
	public static Sequence primitiveSeedsForType(Class<?> clazz) {
		List<Object> selected = new ArrayList<Object>();
		if (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(clazz)) {
			clazz = PrimitiveTypes.primitiveType(clazz);
		}

		for (Object seed : primitiveSeeds) {
			if (seed.getClass() == clazz)
				selected.add(seed);
		}
		
		if (selected.size() == 0)
			return null;
		Object randomlySelected = Randomness.randomMember(selected);

		return Sequence.create(new PrimitiveOrStringOrNullDecl(clazz,
				randomlySelected));
	}

	public static Object getValue(Class<?> inarg) {
		for (Object o : primitiveSeeds) {
			Class<?> cls = o.getClass();

			if (cls.getName().equals(inarg.getName())) {
				return o;
			}
		}
		return null;
	}
}
