package edu.mit.csail.pag.utils;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import utilMDE.*;

/** Utility methods that operate on reflection objects (classes, methods, etc.). */
public final class Reflection {

    /**
     * Used by methods that that a java.lang.Class object as argument and use it to compute something based on it.
     */
    public static enum Match {
        EXACT_TYPE, COMPATIBLE_TYPE
    }

    private Reflection() {
        // no instance
    }

    /**
     * Returns the set of classes that appear, recursively, in the interface of the given class, to a given depth. For
     * example, if class C1 declares only method foo(C2)/C3, and class C2 declares method bar(C4)/C5, then:
     * 
     * We say that C1, C2 and C3 are related to C1 at depth >= 0. We say that C4 and C5 are related to C1 at depth >= 1.
     * 
     * We say that a class C2 appears in the interface of C iff: (1) C2 is C (2) C2 is a return value of some method in
     * C.getMethods() (2) C2 is a parameter of some method in C.getMethods() or some constructor in C.getConstructors().
     */
    public static Set<Class<?>> relatedClasses(Class<?> clazz, int depth) {
        if (clazz == null)
            throw new IllegalArgumentException("clazz cannot be null.");
        if (depth < 0)
            throw new IllegalArgumentException("depth must be non-negative.");
        return relatedClassesInternal(Collections.<Class<?>> singleton(clazz), depth);
    }

    public static Set<Class<?>> relatedClasses(Collection<Class<?>> classes, int i) {
        Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        for (Class<?> c : classes) {
            result.addAll(relatedClasses(c, i));
        }
        return result;
    }

    private static Set<Class<?>> relatedClassesInternal(Set<Class<?>> classes, int depth) {
        if (depth < 0)
            return classes;
        Set<Class<?>> acc = new LinkedHashSet<Class<?>>();
        for (Class<?> c : classes) {
            acc.addAll(classesAppearingInInterface(c));
        }
        return relatedClassesInternal(acc, depth - 1);
    }

    private static Set<Class<?>> classesAppearingInInterface(Class<?> c) {
        Set<Class<?>> retval = new LinkedHashSet<Class<?>>();
        retval.add(c);
        for (Method m : c.getMethods()) {
            retval.add(m.getReturnType());
            retval.addAll(Arrays.asList(m.getParameterTypes()));
        }
        for (Constructor<?> cons : c.getConstructors()) {
            retval.addAll(Arrays.asList(cons.getParameterTypes()));
        }
        return Collections.unmodifiableSet(retval);
    }

    public static Method getMethodForReceiver(Method m, Class<?> receiver) {
        if (m == null || receiver == null)
            throw new IllegalArgumentException("parameters cannot be null.");
        if (!canBeUsedAs(receiver, m.getDeclaringClass()))
            throw new IllegalArgumentException("receiver type " + receiver.getName() + " must be a subtype of method " + m);
        try {
            if (receiver.isPrimitive()) {
                receiver = PrimitiveTypes.boxedType(receiver);
            }
            return receiver.getMethod(m.getName(), m.getParameterTypes());
        } catch (Exception e) {
            throw new RuntimeException("method=" + m + ", receiver=" + receiver + ", exception=" + e);
        }
    }

    public static final Comparator<Member> SORT_MEMBERS_BY_NAME = new Comparator<Member>() {
        public int compare(Member o1, Member o2) {
            return o1.toString().compareTo(o2.toString());
        }
    };

    /**
     * Like Class.getMethods(), but guarantees always same order.
     */
    public static Method[] getMethodsOrdered(Class<?> c) {
        if (c == null)
            throw new IllegalArgumentException("c cannot be null.");
        Method[] ret = c.getMethods();
        Arrays.sort(ret, SORT_MEMBERS_BY_NAME);
        return ret;
    }

    /**
     * Like Class.getDeclaredMethods(), but guarantees always same order.
     */
    public static Method[] getDeclaredMethodsOrdered(Class<?> c) {
        if (c == null)
            throw new IllegalArgumentException("c cannot be null.");
        Method[] ret = c.getDeclaredMethods();
        Arrays.sort(ret, SORT_MEMBERS_BY_NAME);
        return ret;
    }

    /**
     * Like Class.getConstructors(), but guarantees always same order.
     */
    public static Constructor<?>[] getConstructorsOrdered(Class<?> c) {
        if (c == null)
            throw new IllegalArgumentException("c cannot be null.");
        Constructor<?>[] ret = c.getConstructors();
        Arrays.sort(ret, SORT_MEMBERS_BY_NAME);
        return ret;
    }

    /**
     * Like Class.getDeclaredConstructors(), but guarantees always same order.
     */
    public static Constructor<?>[] getDeclaredConstructorsOrdered(Class<?> c) {
        if (c == null)
            throw new IllegalArgumentException("c cannot be null.");
        Constructor<?>[] ret = c.getDeclaredConstructors();
        Arrays.sort(ret, SORT_MEMBERS_BY_NAME);
        return ret;
    }

    /**
     * Convert a fully-qualified classname from Java format to JVML format. For example, convert "java.lang.Object[]" to
     * "[Ljava/lang/Object;".
     */
    // TODO incorporate into UtilMDE.
    public static Class<?> classForName(String classname) {
        int dims = 0;
        while (classname.endsWith("[]")) {
            dims++;
            classname = classname.substring(0, classname.length() - 2);
        }
        String result = null;
        if (dims == 0) {
            if (PrimitiveTypes.isPrimitiveOrStringTypeName(classname))
                return PrimitiveTypes.getPrimitiveTypeOrString(classname);
            else
                result = classname;
        } else {

            boolean isPrimitive = false;
            result = UtilMDE.primitive_name_to_jvm(classname);
            if (result == null) {
                result = "L" + classname;
            } else {
                isPrimitive = true;
            }
            for (int i = 0; i < dims; i++) {
                result = "[" + result;
            }
            result = result + (isPrimitive ? "" : ";");
        }

        try {
            return Class.forName(result);
        } catch (Throwable e) {
            throw new IllegalStateException("when looking for class " + classname + " transformed into " + result + " the following exception:" + e);
        }
    }

    private static Set<Class<?>> getInterfacesTransitive(Class<?> c1) {

        Set<Class<?>> ret = new LinkedHashSet<Class<?>>();

        Class<?>[] c1Interfaces = c1.getInterfaces();
        for (int i = 0; i < c1Interfaces.length; i++) {
            ret.add(c1Interfaces[i]);
            ret.addAll(getInterfacesTransitive(c1Interfaces[i]));
        }

        Class<?> superClass = c1.getSuperclass();
        if (superClass != null)
            ret.addAll(getInterfacesTransitive(superClass));

        return ret;
    }

    public static Set<Class<?>> getDirectSuperTypes(Class<?> c) {
        Set<Class<?>> result = new LinkedHashSet<Class<?>>();
        Class<?> superclass = c.getSuperclass();
        if (superclass != null)
            result.add(superclass);
        result.addAll(Arrays.<Class<?>> asList(c.getInterfaces()));
        return result;
    }

    /**
     * Preconditions (established because this method is only called from canBeUsedAs): params are non-null, are not
     * Void.TYPE, and are not isInterface().
     * 
     * @param c1
     * @param c2
     */
    private static boolean isSubclass(Class<?> c1, Class<?> c2) {
        assert (c1 != null);
        assert (c2 != null);
        assert (!c1.equals(Void.TYPE));
        assert (!c2.equals(Void.TYPE));
        assert (!c1.isInterface());
        assert (!c2.isInterface());
        return c2.isAssignableFrom(c1);
    }

    private static Map<Pair<Class<?>, Class<?>>, Boolean> canBeUsedCache = new LinkedHashMap<Pair<Class<?>, Class<?>>, Boolean>();

    public static long num_times_canBeUsedAs_called = 0;

    /**
     * Checks if an object of class c1 can be used as an object of class c2. This is more than subtyping: for example,
     * int can be used as Integer, but the latter is not a subtype of the former.
     */
    public static boolean canBeUsedAs(Class<?> c1, Class<?> c2) {
        if (c1 == null || c2 == null)
            throw new IllegalArgumentException("Parameters cannot be null.");
        if (c1.equals(void.class) && c2.equals(void.class))
            return true;
        if (c1.equals(void.class) || c2.equals(void.class))
            return false;
        Pair<Class<?>, Class<?>> classPair = new Pair<Class<?>, Class<?>>(c1, c2);
        Boolean cachedRetVal = canBeUsedCache.get(classPair);
        boolean retval;
        if (cachedRetVal == null) {
            retval = canBeUsedAs0(c1, c2);
            canBeUsedCache.put(classPair, retval);
        } else {
            retval = cachedRetVal;
        }
        return retval;
    }

    // TODO testclasses array code (third if clause)
    private static boolean canBeUsedAs0(Class<?> c1, Class<?> c2) {
        if (c1.isArray()) {
            if (c2.equals(Object.class))
                return true;
            if (!c2.isArray())
                return false;
            Class<?> c1SequenceType = c1.getComponentType();
            Class<?> c2componentType = c2.getComponentType();

            if (c1SequenceType.isPrimitive()) {
                if (c2componentType.isPrimitive())
                    return (c1SequenceType.equals(c2componentType));
                else
                    return false;
            } else {
                if (c2componentType.isPrimitive())
                    return false;
                else {
                    c1 = c1SequenceType;
                    c2 = c2componentType;
                }
            }
        }

        if (c1.isPrimitive())
            c1 = PrimitiveTypes.boxedType(c1);
        if (c2.isPrimitive())
            c2 = PrimitiveTypes.boxedType(c2);

        boolean ret = false;

        if (c1.equals(c2)) {
            ret = true;
        } else if (c2.isInterface()) {
            Set<Class<?>> c1Interfaces = getInterfacesTransitive(c1);
            if (c1Interfaces.contains(c2))
                ret = true;
            else
                ret = false;
        } else if (c1.isInterface()) {
            // c1 represents an interface and c2 a class.
            // The only safe possibility is when c2 is Object.
            if (c2.equals(Object.class))
                ret = true;
            else
                ret = false;
        } else {
            ret = isSubclass(c1, c2);
        }
        return ret;
    }

    public static <M extends Member> Set<M> publicMembers(Set<M> set) {
        Set<M> result = new LinkedHashSet<M>();
        for (M member : set) {
            if (Modifier.isPublic(member.getModifiers()))
                result.add(member);
        }
        return result;
    }

    /**
     * Checks whether the inputs can be used as arguments for the specified parameter types. This method considers
     * "null" as always being a valid argument. errMsgContext is uninterpreted - just printed in error messages Returns
     * null if inputs are OK wrt paramTypes. Returns error message otherwise.
     */
    public static String checkArgumentTypes(Object[] inputs, Class<?>[] paramTypes, Object errMsgContext) {
        if (inputs.length != paramTypes.length)
            return "Bad number of parameters for " + errMsgContext + " was:" + inputs.length;

        for (int i = 0; i < paramTypes.length; i++) {
            Object input = inputs[i];
            Class<?> pType = paramTypes[i];
            if (!canBePassedAsArgument(input, pType))
                return "Invalid type of argument at pos " + i + " for:" + errMsgContext + " expected:" + pType + " was:" + (input == null ? "n/a(input was null)" : input.getClass());
        }
        return null;
    }

    /**
     * Returns whether the input can be used as argument for the specified parameter type.
     */
    public static boolean canBePassedAsArgument(Object inputObject, Class<?> parameterType) {
        if (parameterType == null || parameterType.equals(Void.TYPE))
            throw new IllegalStateException("Illegal type of parameter " + parameterType);
        if (inputObject == null)
            return true;
        else if (!Reflection.canBeUsedAs(inputObject.getClass(), parameterType))
            return false;
        else
            return true;
    }

    /**
     * Blank lines and lines starting with "#" are ignored. Other lines must contain string such that Class.forName(s)
     * returns a class.
     */
    public static List<Class<?>> loadClassesFromStream(InputStream in) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        return loadClassesFromReader(reader);
    }

    /**
     * Blank lines and lines starting with "#" are ignored. Other lines must contain string such that Class.forName(s)
     * returns a class.
     */
    public static List<Class<?>> loadClassesFromReader(BufferedReader reader) {
        try {
            List<String> lines = Files.readWhole(reader);
            return loadClassesFromLines(lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Blank lines and lines starting with "#" are ignored. Other lines must contain string such that Class.forName(s)
     * returns a class.
     */
    public static List<Class<?>> loadClassesFromLines(List<String> lines) {
        List<Class<?>> result = new ArrayList<Class<?>>(lines.size());
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.equals("") || trimmed.startsWith("#"))
                continue;
            result.add(classForName(trimmed));
        }
        return result;
    }

    /**
     * Blank lines and lines starting with "#" are ignored. Other lines must contain string such that Class.forName(s)
     * returns a class.
     */
    public static List<Class<?>> loadClassesFromFile(File classListingFile) throws IOException {
        BufferedReader reader = null;
        try {
            reader = Files.getFileReader(classListingFile);
            return loadClassesFromReader(reader);
        } finally {
            if (reader != null)
                reader.close();
        }
    }

    // XXX stolen from Constructor.toString - but we don't need modifiers or exceptions
    // and we need a slightly different format
    public static String getSignature(Constructor<?> c) {
        StringBuilder sb = new StringBuilder();
        sb.append(c.getName() + ".<init>(");
        Class<?>[] params = c.getParameterTypes();
        for (int j = 0; j < params.length; j++) {
            sb.append(getTypeName(params[j]));
            if (j < (params.length - 1))
                sb.append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    // XXX stolen from Method.toString - but we don't need modifiers or exceptions
    public static String getSignature(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(getTypeName(m.getDeclaringClass()) + ".");
        sb.append(m.getName() + "(");
        Class<?>[] params = m.getParameterTypes();
        for (int j = 0; j < params.length; j++) {
            sb.append(getTypeName(params[j]));
            if (j < (params.length - 1))
                sb.append(",");
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Returns a string representation of the type's name.
     */
    // XXX stolen from Field because it's not visible
    public static String getTypeName(Class<?> type) {
        if (type.isArray()) {
            Class<?> cl = type;
            int dimensions = 0;
            while (cl.isArray()) {
                dimensions++;
                cl = cl.getComponentType();
            }
            StringBuilder sb = new StringBuilder();
            sb.append(cl.getName());
            for (int i = 0; i < dimensions; i++) {
                sb.append("[]");
            }
            return sb.toString();
        }
        return type.getName();
    }

    public static boolean isVisible(Class<?> c) {
        if (c.isAnonymousClass())
            return false;

        int mods = c.getModifiers();
        boolean classPublic = Modifier.isPublic(mods);
        if (c.isMemberClass())
            return classPublic && isVisible(c.getDeclaringClass());
        else
            return classPublic;
    }

    public static void saveClassesToFile(List<Class<?>> classes, String file) throws IOException {
        FileWriter fw = new FileWriter(file);
        for (Class<?> s : classes) {
            fw.append(s.getName() + "\n");
        }
        fw.close();

    }
}
