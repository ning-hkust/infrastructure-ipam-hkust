package edu.mit.csail.pag.utils;

/**
 * Helpers for assertions, and stuff...
 */
public final class Util {

    private Util() {
        throw new IllegalStateException("no instance");
    }

    public static final String newLine = System.getProperty("line.separator");

    public static void assertCond(boolean b) {
        if (!b) {
            throw new RuntimeException("Assertion violation.");
        }
    }

    public static void assertCond(boolean b, String message) {
        if (!b) {
            throw new RuntimeException("Assertion violation: " + message);
        }
    }

    public static boolean iff(boolean a, boolean b) {
        return a == b;
    }

    public static boolean implies(boolean a, boolean b) {
        return !a || b;
    }

    /**
     * If both parameters are null, returns true. If one parameter is null and the other isn't, returns false.
     * Otherwise, returns o1.equals(o2).
     * 
     * @param o1
     * @param o2
     */
    public static boolean equalsWithNull(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        }
        if (o2 == null) {
            return false;
        }
        return (o1.equals(o2));
    }

    public static boolean isJavaIdentifier(String s) {
        if (s == null || s.length() == 0 || !Character.isJavaIdentifierStart(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static String convertToHexString(String unicodeString) {
        char[] chars = unicodeString.toCharArray();
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            output.append("\\u");
            String hex = Integer.toHexString(chars[i]);
            if (hex.length() < 4)
                output.append("0");
            if (hex.length() < 3)
                output.append("0");
            if (hex.length() < 2)
                output.append("0");
            if (hex.length() < 1)
                output.append("0");

            output.append(hex);
        }
        return output.toString();
    }

    public static int occurCount(StringBuilder text, String pattern) {
        if (pattern.length() == 0)
            throw new IllegalArgumentException("empty pattern");
        int i = 0;
        int currIdx = text.indexOf(pattern);
        while (currIdx != -1) {
            i++;
            currIdx = text.indexOf(pattern, currIdx + 1);
        }
        return i;
    }

}
