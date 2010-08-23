package randoop.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.regex.Pattern;

import randoop.Globals;
import randoop.ocat.OCATGlobals;

/**
 * Returns true for anything declared public, with two exceptions: (1) returns
 * false for methods declared by Object (i.e. hashCode, wait, notifyAll, etc).
 * These are typically useless for most input generation purposes. (2) Returns
 * false for any method with name "hashCode" (these are also typically useless
 * for input generation, and result in non-repro behavior).
 * 
 */
public class DefaultReflectionFilter implements ReflectionFilter {

	private Pattern omitmethods = null;

	private static final boolean VERBOSE = false;

	/** omitmethods can be null (which means "omit no methods") */
	public DefaultReflectionFilter(Pattern omitmethods) {
		super();
		this.omitmethods = omitmethods;
	}

	public boolean canUse(Class<?> c) {
		if (c == null)
			return false;
		boolean ret = Modifier.isPublic(c.getModifiers());

		// JJ: check package name if a class is private.
		if (!ret) {
			if (c.getPackage() == null)
			{
				System.out.print("Will not use: " + c.toString());
				System.out.println("  reason: it is not a public class.");
				return false;
			}
			
			String cpack = c.getPackage().getName();		
			ret = Globals.junit_package_name.toString().equals(cpack);
			if (!ret) {
				System.out.print("Will not use: " + c.toString());
				System.out.println("  reason: it is not a public class.");
			}
		}
		return ret;
	}

	public boolean canUse(Method m) {
		if (m == null)
			return false;
		if (matchesOmitMethodPattern(m.toString())) {
			if (VERBOSE) {
				System.out.println("Will not use: " + m.toString());
				System.out.println("  reason: matches regexp specified in -omitmethods option.");
			}
			return false;
		}

		if (m.isBridge()) {
			if (VERBOSE) {
				System.out.println("Will not use: " + m.toString());
				System.out.println("  reason: it's a bridge method");
			}
			return false;
		}

		if (m.isSynthetic()) {
			if (VERBOSE) {
				System.out.println("Will not use: " + m.toString());
				System.out.println("  reason: it's a synthetic method");
			}
			return false;
		}

		if (Modifier.isPrivate(m.getModifiers())) {
			return false;
		}

		if (!Modifier.isPublic(m.getModifiers())) {
			// JJ: Although it's protected, if it's in package, it should be
			// added.
			if (OCATGlobals.JJ_protect) {
				boolean ret = Globals.junit_package_name.toString().equals(
						m.getDeclaringClass().getPackage().getName());
				if (!ret)
					return false;
			} else
				return false;
		}

		// TODO we could enable some methods from Object, like getClass
		if (m.getDeclaringClass().equals(java.lang.Object.class))
			return false;// handled here to avoid printing reasons

		if (m.getDeclaringClass().equals(java.lang.Thread.class))
			return false;// handled here to avoid printing reasons

		String reason = doNotUseSpecialCase(m);
		if (reason != null) {
			if (VERBOSE) {
				System.out.println("Will not use: " + m.toString());
				System.out.println("  reason: " + reason);
			}
			return false;
		}

		return true;
	}

	@SuppressWarnings("finally")
	private String doNotUseSpecialCase(Method m) {

		// Special case 1:
		// We're skipping compareTo method in enums - you can call it only with
		// the same type as receiver
		// but the signature does not tell you that
		try {
			if (m.getDeclaringClass().getCanonicalName().equals("java.lang.Enum")
					&& m.getName().equals("compareTo") && m.getParameterTypes().length == 1
					&& m.getParameterTypes()[0].equals(Enum.class))
				return "We're skipping compareTo method in enums";

			// Sepcial case 2:
			if (m.getName().equals("randomUUID"))
				return "We're skipping this to get reproducibility when running java.util tests.";

			// Special case 2:
			// hashCode is bad in general but String.hashCode is fair game
			if (m.getName().equals("hashCode") && !m.getDeclaringClass().equals(String.class))
				return "hashCode";

			// Special case 3: (just clumps together a bunch of hashCodes, so
			// skip it)
			if (m.getName().equals("deepHashCode") && m.getDeclaringClass().equals(Arrays.class))
				return "deepHashCode";

			// Special case 4: (differs too much between JDK installations)
			if (m.getName().equals("getAvailableLocales"))
				return "getAvailableLocales";
		} catch (Exception e) {
		} finally {
			return null;
		}
	}

	public boolean canUse(Constructor<?> c) {

		if (matchesOmitMethodPattern(c.getName())) {
			System.out.println("Will not use: " + c.toString());
			return false;
		}

		// synthetic constructors are OK

		if (Modifier.isAbstract(c.getDeclaringClass().getModifiers()))
			return false;
		if (Modifier.isPublic(c.getModifiers()))
			return true;

		// JJ: Although it's protected, if it's in package, it should be added.
		if (OCATGlobals.JJ_protect && true) {
			boolean ret = Globals.junit_package_name.toString().equals(
					c.getDeclaringClass().getPackage().getName());
			return ret;
		}
		if (Modifier.isPrivate(c.getModifiers()))
			return false;

		return false;
	}

	private boolean matchesOmitMethodPattern(String name) {
		return omitmethods != null && omitmethods.matcher(name).find();
	}

}
