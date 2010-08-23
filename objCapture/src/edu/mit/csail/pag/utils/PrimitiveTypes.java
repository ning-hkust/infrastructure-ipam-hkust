package edu.mit.csail.pag.utils;

import java.util.*;

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
        typeNameToBoxed.put("String", String.class);
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
    }

    private static final Map<Class<?>, Class<?>> primitiveAndStringToBoxed = new LinkedHashMap<Class<?>, Class<?>>(8);

    static {
        primitiveAndStringToBoxed.put(boolean.class, Boolean.class);
        primitiveAndStringToBoxed.put(byte.class, Byte.class);
        primitiveAndStringToBoxed.put(char.class, Character.class);
        primitiveAndStringToBoxed.put(double.class, Double.class);
        primitiveAndStringToBoxed.put(float.class, Float.class);
        primitiveAndStringToBoxed.put(int.class, Integer.class);
        primitiveAndStringToBoxed.put(long.class, Long.class);
        primitiveAndStringToBoxed.put(short.class, Short.class);
        primitiveAndStringToBoxed.put(String.class, String.class); // TODO remove this hack!
    }

    private static final Map<String, Class<?>> typeNameToPrimitiveOrString = new LinkedHashMap<String, Class<?>>();
    static {
        typeNameToPrimitiveOrString.put("int", int.class);
        typeNameToPrimitiveOrString.put("boolean", boolean.class);
        typeNameToPrimitiveOrString.put("float", float.class);
        typeNameToPrimitiveOrString.put("char", char.class);
        typeNameToPrimitiveOrString.put("double", double.class);
        typeNameToPrimitiveOrString.put("long", long.class);
        typeNameToPrimitiveOrString.put("short", short.class);
        typeNameToPrimitiveOrString.put("byte", byte.class);
        typeNameToPrimitiveOrString.put(String.class.getName(), String.class);
    }

    public static boolean isPrimitiveOrStringTypeName(String typeName) {
        return typeNameToBoxed.containsKey(typeName);
    }

    public static Class<?> getBoxedType(String typeName) {
        Class<?> boxed = typeNameToBoxed.get(typeName);
        if (boxed == null)
            throw new IllegalArgumentException("not a primitive type:" + typeName);
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

    public static Class<?> getUnboxType(Class<?> c) {
        return boxedToPrimitiveAndString.get(c);
    }
}