package randoop.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class PrimitiveTypes {
	private PrimitiveTypes() {
		// no instances
	}

	private static final Map<String, Class<?>> typeNameToBoxed = new LinkedHashMap<String, Class<?>>();
	static {
		typeNameToBoxed.put("int", Integer.class);
		typeNameToBoxed.put("boolean", Boolean.class);
		typeNameToBoxed.put("float", Float.class);
		typeNameToBoxed.put("char", Character.class);
		typeNameToBoxed.put("double", Double.class);
		typeNameToBoxed.put("long", Long.class);
		typeNameToBoxed.put("short", Short.class);
		typeNameToBoxed.put("byte", Byte.class);
		typeNameToBoxed.put(String.class.getName(), String.class);
		
		//array
		typeNameToBoxed.put("int[]", Integer[].class);
		typeNameToBoxed.put("boolean[]", Boolean[].class);
		typeNameToBoxed.put("float[]", Float[].class);
		typeNameToBoxed.put("char[]", Character[].class);
		typeNameToBoxed.put("double[]", Double[].class);
		typeNameToBoxed.put("long[]", Long[].class);
		typeNameToBoxed.put("short[]", Short[].class);
		typeNameToBoxed.put("byte[]", Byte[].class);		
	}

	private static final Map<Class<?>, Class<?>> boxedToPrimitiveAndString = new LinkedHashMap<Class<?>, Class<?>>();
	static {
		boxedToPrimitiveAndString.put(Integer.class, int.class);
		boxedToPrimitiveAndString.put(Boolean.class, boolean.class);
		boxedToPrimitiveAndString.put(Float.class, float.class);
		boxedToPrimitiveAndString.put(Character.class, char.class);
		boxedToPrimitiveAndString.put(Double.class, double.class);
		boxedToPrimitiveAndString.put(Long.class, long.class);
		boxedToPrimitiveAndString.put(Short.class, short.class);
		boxedToPrimitiveAndString.put(Byte.class, byte.class);
		boxedToPrimitiveAndString.put(String.class, String.class);
		
		//arrays
		boxedToPrimitiveAndString.put(Integer[].class, int[].class);
		boxedToPrimitiveAndString.put(Boolean[].class, boolean[].class);
		boxedToPrimitiveAndString.put(Float[].class, float[].class);
		boxedToPrimitiveAndString.put(Character[].class, char[].class);
		boxedToPrimitiveAndString.put(Double[].class, double[].class);
		boxedToPrimitiveAndString.put(Long[].class, long[].class);
		boxedToPrimitiveAndString.put(Short[].class, short[].class);
		boxedToPrimitiveAndString.put(Byte[].class, byte[].class);
		boxedToPrimitiveAndString.put(String[].class, String[].class);		
	}

	private static final Map<Class<?>, Class<?>> primitiveAndStringToBoxed = new LinkedHashMap<Class<?>, Class<?>>(
			8);

	static {
		primitiveAndStringToBoxed.put(boolean.class, Boolean.class);
		primitiveAndStringToBoxed.put(byte.class, Byte.class);
		primitiveAndStringToBoxed.put(char.class, Character.class);
		primitiveAndStringToBoxed.put(double.class, Double.class);
		primitiveAndStringToBoxed.put(float.class, Float.class);
		primitiveAndStringToBoxed.put(int.class, Integer.class);
		primitiveAndStringToBoxed.put(long.class, Long.class);
		primitiveAndStringToBoxed.put(short.class, Short.class);
		primitiveAndStringToBoxed.put(String.class, String.class); // TODO
																	// remove
																	// this
																	// hack!
		
		primitiveAndStringToBoxed.put(boolean[].class, Boolean[].class);
		primitiveAndStringToBoxed.put(byte[].class, Byte[].class);
		primitiveAndStringToBoxed.put(char[].class, Character[].class);
		primitiveAndStringToBoxed.put(double[].class, Double[].class);
		primitiveAndStringToBoxed.put(float[].class, Float[].class);
		primitiveAndStringToBoxed.put(int[].class, Integer[].class);
		primitiveAndStringToBoxed.put(long[].class, Long[].class);
		primitiveAndStringToBoxed.put(short[].class, Short[].class);
		primitiveAndStringToBoxed.put(String[].class, String[].class); 
		
	}

	protected static final Map<String, Class<?>> typeNameToPrimitiveOrString = new LinkedHashMap<String, Class<?>>();
	static {
		typeNameToPrimitiveOrString.put("void", void.class);
		typeNameToPrimitiveOrString.put("int", int.class);
		typeNameToPrimitiveOrString.put("boolean", boolean.class);
		typeNameToPrimitiveOrString.put("float", float.class);
		typeNameToPrimitiveOrString.put("char", char.class);
		typeNameToPrimitiveOrString.put("double", double.class);
		typeNameToPrimitiveOrString.put("long", long.class);
		typeNameToPrimitiveOrString.put("short", short.class);
		typeNameToPrimitiveOrString.put("byte", byte.class);
		typeNameToPrimitiveOrString.put(String.class.getName(), String.class);
		typeNameToPrimitiveOrString.put("String", String.class);
		
		//arrays
		typeNameToPrimitiveOrString.put("int[]", int[].class);
		typeNameToPrimitiveOrString.put("boolean[]", boolean[].class);
		typeNameToPrimitiveOrString.put("float[]", float[].class);
		typeNameToPrimitiveOrString.put("char[]", char[].class);
		typeNameToPrimitiveOrString.put("double[]", double[].class);
		typeNameToPrimitiveOrString.put("long[]", long[].class);
		typeNameToPrimitiveOrString.put("short[]", short[].class);
		typeNameToPrimitiveOrString.put("byte[]", byte[].class);
		
	}

	public static boolean isPrimitiveOrStringTypeName(String typeName) {
		return typeNameToBoxed.containsKey(typeName);
	}

	public static Class<?> getBoxedType(String typeName) {
		Class<?> boxed = typeNameToBoxed.get(typeName);
		if (boxed == null)
			throw new IllegalArgumentException("not a primitive type:"
					+ typeName);
		return boxed;
	}

	public static Class<?> getPrimitiveTypeOrString(String typeName) {
		return typeNameToPrimitiveOrString.get(typeName);
	}

	public static Set<Class<?>> getPrimitiveTypesAndString() {
		return Collections.unmodifiableSet(primitiveAndStringToBoxed.keySet());
	}

	public static Set<Class<?>> getBoxedTypesAndString() {
		return Collections.unmodifiableSet(boxedToPrimitiveAndString.keySet());
	}

	public static Class<?> boxedType(Class<?> c1) {
		return primitiveAndStringToBoxed.get(c1);
	}

	public static boolean isBoxedPrimitiveTypeOrString(Class<?> c) {
		return boxedToPrimitiveAndString.containsKey(c);
	}

	public static boolean isPrimitiveOrStringType(Class<?> type) {
		return primitiveAndStringToBoxed.containsKey(type);
	}

	public static boolean isBoxedOrPrimitiveOrStringType(Class<?> c) {
		if (c.isPrimitive())
			return true;
		if (isBoxedPrimitiveTypeOrString(c))
			return true;
		return false;
	}

	/** Returns null if c is not a primitive or a boxed type. */
	public static Class<?> primitiveType(Class<? extends Object> c) {
		if (c.isPrimitive())
			return c;
		return boxedToPrimitiveAndString.get(c);
	}

	/**
	 * Given a primitive, boxed primitive, or String, returns a String that can
	 * be uesd in Java source to represent it.
	 * 
	 * @param the
	 *            value to create a String representation for. The value's type
	 *            must be a primitive type, a String, or null.
	 */
	public static String toCodeString(Object value) {

		if (value == null) {
			return "null";
		}
		Class<?> valueClass = primitiveType(value.getClass());

		if (String.class.equals(valueClass)) {
			return "\"" + StringEscapeUtils.escapeJava(value.toString()) + "\"";
		} else if (char.class.equals(valueClass)) {
			// XXX This won't always work!
			if (value.equals(' '))
				return "' '";
			return "\'" + StringEscapeUtils.escapeJava(value.toString()) + "\'";

		} else if (double.class.equals(valueClass)) {
			Double d = (Double) value;
			String rep = null;
			if (d.isNaN()) {
				rep = "Double.NaN";
			} else if (d == Double.POSITIVE_INFINITY) {
				rep = "Double.POSITIVE_INFINITY";
			} else if (d == Double.NEGATIVE_INFINITY) {
				rep = "Double.NEGATIVE_INFINITY";
			} else {
				rep = d.toString();
				
				// altered by Ning
				rep = rep + "d";
			}
			assert rep != null;
			// rep = rep + "d";
			if (rep.charAt(0) == '-')
				rep = "(" + rep + ")";
			return rep;

		} else if (float.class.equals(valueClass)) {
			Float d = (Float) value;
			String rep = null;
			if (d.isNaN()) {
				rep = "Float.NaN";
			} else if (d == Float.POSITIVE_INFINITY) {
				rep = "Float.POSITIVE_INFINITY";
			} else if (d == Float.NEGATIVE_INFINITY) {
				rep = "Float.NEGATIVE_INFINITY";
			} else {
				rep = d.toString();

        // altered by Ning
        rep = rep + "f";
			}
			assert rep != null;
      // rep = rep + "f";
			if (rep.charAt(0) == '-')
				rep = "(" + rep + ")";
			return rep;

		} else if (boolean.class.equals(valueClass)) {

			// true and false are explicit enough; don't need cast.
			return value.toString();

		} else if (long.class.equals(valueClass)) {

			String rep = value.toString() + "L";
			if (rep.charAt(0) == '-')
				rep = "(" + rep + ")";
			return rep;

		} else if (byte.class.equals(valueClass)) {

			String rep = value.toString();
			if (rep.charAt(0) == '-')
				rep = "(" + rep + ")";
			rep = "(byte)" + rep;
			return rep;

		} else if (short.class.equals(valueClass)) {

			String rep = value.toString();
			if (rep.charAt(0) == '-')
				rep = "(" + rep + ")";
			rep = "(short)" + rep;
			return rep;

		} else {
			assert int.class.equals(valueClass);

			// We don't need to cast an int.
			String rep = value.toString();
			if (rep.charAt(0) == '-')
				rep = "(" + rep + ")";
			return rep;

		}
	}

	public static String toArrayCodeString(Object value) {
		Object[] val = (Object[]) value;
		if (val.length <= 0)
			return null;

		String ret = "";

		ret = "{";
		for (int i=0; i < val.length - 1; i++)
		{
			ret = ret + toCodeString(val[i]) + ",";
		}
		
		ret = ret + toCodeString(val[val.length-1]) + "}";
		return ret;		
	}
	
	
	public static Class<?> getUnboxType(Class<?> c) {
		return boxedToPrimitiveAndString.get(c);
	}
}