package hk.ust.cse.util;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Random;

public class Utils {
  
  // default
  public static String getClassTypeJavaStr(String classType) {
    return getClassTypeJavaStr(classType, true);
  }

  public static String getClassTypeJavaStr(String classType, boolean replaceDollar) {
    // to primitive type first, in case it is a primitive encoding
    StringBuilder classTypeStr = new StringBuilder(toPrimitiveType(classType));
    int length = classTypeStr.length();
    
    // delete dimension signs
    int nDimension = 0;
    for (int i = 0; i < length; i++) {
      if (classTypeStr.charAt(i) == '[') {
        nDimension++;
      }
      else {
        break;
      }
    }

    // delete L if any
    int nL = 0;
    if (classTypeStr.charAt(nDimension) == 'L') {
      nL++;
    }
    
    // delete ; at the end
    int nSim = 0;
    if (classTypeStr.charAt(length - 1) == ';') {
      nSim++;
    }
    
    // reconstruct string
    classTypeStr = classTypeStr.delete(0, nDimension + nL);
    classTypeStr = classTypeStr.delete(classTypeStr.length() - nSim, classTypeStr.length());

    // if the new string is primitive encoding
    if (classTypeStr.length() == 1) { // for cass, [C, [B, etc
      classTypeStr = new StringBuilder(toPrimitiveType(classTypeStr.toString()));
    }
    
    // place [] at the back
    for (int i = 0; i < nDimension; i++) {
      classTypeStr.append("[]");
    }
    
    // replace / with .
    classTypeStr = replace(classTypeStr, "/", ".");

    // if contains $, it should be a inner class type
    if (replaceDollar) {
      classTypeStr = replace(classTypeStr, "$", ".");
    }
    
    return classTypeStr.toString();
  }
  
  private static String getClassTypeForNameStr(String classType) {
    if (classType.startsWith("[")) {
      if (classType.contains("[L")) {
        // [Ljava/lang/String -> [Ljava.lang.String;
        // [[Ljava/lang/Object -> [[Ljava.lang.Object;
        classType = classType.replace('/', '.');
        if (!classType.endsWith(";")) {
          classType += ";";
        }
        return classType;
      }
      else {
        // [I, [C etc, leave them unchanged
        return classType;
      }
    }
    else {
      // Ljava/lang/String -> java.lang.String
      return getClassTypeJavaStr(classType, false);
    }
  }
  
  public static Method[] getPublicMethods(Class<?> cls) {
    Method[] allPublicMethods = cls.getMethods();
    // sort, because getMethods() does not guarantee order
    Arrays.sort(allPublicMethods, new Comparator<Method>() {
      public int compare(Method o1, Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return allPublicMethods;
  }
  
  public static Constructor<?>[] getPublicCtors(Class<?> cls) {
    Constructor<?>[] allPublicCtors = cls.getConstructors();
    // sort, because getConstructors() does not guarantee order
    Arrays.sort(allPublicCtors, new Comparator<Constructor<?>>() {
      public int compare(Constructor<?> o1, Constructor<?> o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });
    return allPublicCtors;
  }

  public static Method[] getNonPrivateMethods(Class<?> cls) {
    return getNonPrivateMethods(cls, true);
  }
  
  public static Method[] getNonPrivateMethods(Class<?> cls, boolean discardOverrided) {
    List<Method> allMethods = getInheritedMethods(cls, discardOverrided);
    
    // remove private methods
    for (int i = 0; i < allMethods.size(); i++) {
      if (Modifier.isPrivate(allMethods.get(i).getModifiers())) {
        allMethods.remove(i--);
      }
    }

    // sort, because getMethods() does not guarantee order
    Collections.sort(allMethods, new Comparator<Method>() {
      public int compare(Method o1, Method o2) {
        return o1.getName().compareTo(o2.getName());
      }
    });
    return allMethods.toArray(new Method[allMethods.size()]);
  }
  
  public static Constructor<?>[] getNonPrivateCtors(Class<?> cls) {
    Constructor<?>[] allCtors = cls.getDeclaredConstructors();
    
    // remove private construction
    List<Constructor<?>> allCtorList = new ArrayList<Constructor<?>>(Arrays.asList(allCtors));
    for (int i = 0; i < allCtorList.size(); i++) {
      if (Modifier.isPrivate(allCtorList.get(i).getModifiers())) {
        allCtorList.remove(i--);
      }
    }
    
    // sort, because getConstructors() does not guarantee order
    Collections.sort(allCtorList, new Comparator<Constructor<?>>() {
      public int compare(Constructor<?> o1, Constructor<?> o2) {
        return o1.toString().compareTo(o2.toString());
      }
    });
    return allCtorList.toArray(new Constructor<?>[allCtorList.size()]);
  }
  
  public static List<Field> getInheritedFields(Class<?> cls) {
    return getInheritedFields(cls, true);
  }

  public static List<Field> getInheritedFields(Class<?> cls, boolean discardOverrided) {
    HashSet<String> fieldNames = new HashSet<String>();
    
    List<Field> fields = new ArrayList<Field>();
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      Field[] declFields = c.getDeclaredFields();
      // sort, because getDeclaredFields() does not guarantee order
      Arrays.sort(declFields, new Comparator<Field>() {
        public int compare(Field o1, Field o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      
      for (Field field : declFields) {
        if (discardOverrided) {
          String fieldName = field.getName();
          if (!fieldNames.contains(fieldName)) {
            fields.add(field);
            fieldNames.add(fieldName);
          }
        }
        else {
          fields.add(field);
        }
      }
    }
    return fields;
  }
  
  public static List<Method> getInheritedMethods(Class<?> cls) {
    return getInheritedMethods(cls, true);
  }
  
  public static List<Method> getInheritedMethods(Class<?> cls, boolean discardOverrided) {
    HashSet<String> methodSelectors = new HashSet<String>();
    
    List<Method> methods = new ArrayList<Method>();
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      Method[] declMethods = c.getDeclaredMethods();
      // sort, because getDeclaredMethods() does not guarantee order
      Arrays.sort(declMethods, new Comparator<Method>() {
        public int compare(Method o1, Method o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      
      for (Method method : declMethods) {
        if (discardOverrided) {
          String genericStr = method.toGenericString();
          String selector = method.getName() + genericStr.substring(genericStr.lastIndexOf('('));
          if (!methodSelectors.contains(selector)) {
            methods.add(method);
            methodSelectors.add(selector);
          }
        }
        else {
          methods.add(method);
        }
      }
    }
    return methods;
  }
  
  public static Field getInheritedField(Class<?> cls, String fieldName) {
    Field field = null;
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      Field[] declFields = c.getDeclaredFields();
      for (Field declField : declFields) {
        if (declField.getName().equals(fieldName)) {
          field = declField;
          break;
        }
      }
    }
    return field;
  }
  
  public static Method getInheritedMethod(Class<?> cls, String methodName, Class<?>[] paramTypes) {
    Method method = null;
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      Method[] declMethods = c.getDeclaredMethods();
      for (Method declMethod : declMethods) {
        if (declMethod.getName().equals(methodName)) {
          Class<?>[] declMethodParamTypes = declMethod.getParameterTypes();
          if ((paramTypes == null && declMethodParamTypes.length == 0) || 
              (paramTypes != null && paramTypes.equals(declMethodParamTypes))) {
            method = declMethod;
            break;
          }
        }
      }
    }
    return method;
  }
  
  public static Class<?> getClosestFieldDeclClass(String startingClassName, String fieldName) {
    Class<?> declClass = null;
    
    Class<?> startingClass = Utils.findClass(startingClassName);
    if (startingClass != null) {
      declClass = getClosestFieldDeclClass(startingClass, fieldName);
    }
    return declClass;
  }
  
  public static Class<?> getClosestFieldDeclClass(Class<?> startingClass, String fieldName) {
    Class<?> declClass = null;
    
    for (Field declField : startingClass.getDeclaredFields()) {
      if (declField.getName().equals(fieldName)) {
        declClass = startingClass;
        break;
      }
    }
    
    if (declClass == null) {
      Class<?> superClass = startingClass.getSuperclass();
      if (superClass != null) {
        declClass = getClosestFieldDeclClass(superClass, fieldName);
      }
    }
    return declClass;
  }
  
  public static Class<?> getClosestPublicSuperClass(Class<?> cls) {
    Class<?> superClass = null;
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      if (Modifier.isPublic(c.getModifiers())) {
        superClass = c;
        break;
      }
    }
    return superClass;
  }
  
  public static Class<?>[] getPublicInterfaces(Class<?> cls) {
    List<Class<?>> publicInterfaces = new ArrayList<Class<?>>();
    
    Class<?>[] interfaces = cls.getInterfaces();
    for (Class<?> anInterface : interfaces) {
      if (Modifier.isPublic(anInterface.getModifiers())) {
        publicInterfaces.add(anInterface);
      }
    }
    return publicInterfaces.toArray(new Class<?>[0]);
  }
  
  public static String getTypeDefaultValue(String typeName) {
    if (typeName.equals("I") || typeName.equals("J") || typeName.equals("S")) {
      return "0";
    }
    else if (typeName.equals("D") || typeName.equals("F")) {
      return "0.0";
    }
    else if (typeName.equals("C") || typeName.equals("B")) {
      return "'a'";
    }
    else if (typeName.equals("Z")) {
      return "true";
    }
    else {
      return "null";
    }
  }
  
  public static String getTypeRandomValue(String typeName) {
    Random random = new Random();
    if (typeName.equals("I")) {
      return String.valueOf(random.nextInt());
    }
    else if (typeName.equals("J")) {
      return String.valueOf(random.nextLong());
    }
    else if (typeName.equals("S")) {
      return String.valueOf(Short.MIN_VALUE + (int) (Math.random() * ((Short.MAX_VALUE - Short.MIN_VALUE) + 1)));
    }
    else if (typeName.equals("D")) {
      return String.valueOf(random.nextDouble());
    }
    else if (typeName.equals("F")) {
      return String.valueOf(random.nextFloat());
    }
    else if (typeName.equals("C")) {
      return String.valueOf((char) ('a' + random.nextInt(26)));
    }
    else if (typeName.equals("B")) {
      return String.valueOf(((int) Byte.MIN_VALUE) + (int) (Math.random() * ((((int) Byte.MAX_VALUE) - ((int) Byte.MIN_VALUE)) + 1)));
    }
    else if (typeName.equals("Z")) {
      return String.valueOf(random.nextBoolean());
    }
    else {
      return "null";
    }
  }
  
  public static boolean setField(Class<?> cls, String fieldName, Object obj, Object setAs) {
    boolean succeed = false;
    try {
      Field field = cls.getDeclaredField(fieldName);
      boolean accessible = field.isAccessible();
      field.setAccessible(true);
      field.set(obj, setAs);
      field.setAccessible(accessible);
      succeed = true;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return succeed;
  }
  
  public static Class<?> findClass(String clsName) {
    Class<?> cls = null;
    try {
      if (clsName.length() == 1) {
        char primitive = clsName.charAt(0);
        switch (primitive) {
        case 'I':
          cls = int.class;
          break;
        case 'J':
          cls = long.class;
          break;
        case 'S':
          cls = short.class;
          break;
        case 'D':
          cls = double.class;
          break;
        case 'F':
          cls = float.class;
          break;
        case 'Z':
          cls = boolean.class;
          break;
        case 'B':
          cls = byte.class;
          break;
        case 'C':
          cls = char.class;
          break;
        default:
          cls = Class.forName(clsName);
          break;
        }
      }
      else {
        String javaClsName = Utils.getClassTypeForNameStr(clsName);
        cls = Class.forName(javaClsName);
      }
    } catch (Throwable e1) {
      //System.err.println("Cannot find class: " + clsName);
    }
    return cls;
  }
  
  public static Field findClassField(String clsName, String fieldName) {
    Field field = null;
    
    Class<?> cls = findClass(clsName);
    if (cls != null) {
      field = findClassField(cls, fieldName);
    }
    return field;
  }
  
  public static Field findClassField(Class<?> cls, String fieldName) {
    Field field = null;
    
    try {
      field = cls.getDeclaredField(fieldName);
    } catch (Exception e) {}
    
    if (field == null) {
      // try super class
      Class<?> superClass = cls.getSuperclass();
      if (superClass != null) {
        field = findClassField(superClass, fieldName);
      }
    }
    
    return field;
  }
  
  public static Class<?> findClassFieldType(String clsName, String fieldName) {
    Class<?> fieldType = null;
    
    Field field = findClassField(clsName, fieldName);
    if (field != null) {
      fieldType = field.getType();
    }
    else if (fieldName.equals("length")) {
      Class<?> cls = findClass(clsName);
      if (cls != null && cls.isArray()) {
        fieldType = int.class;
      }
    }
    return fieldType;
  }
  
  public static String findClassJarLocation(Class<?> cls) {
    return cls.getProtectionDomain().getCodeSource().getLocation().toString();  
  }
  
  // by define, a class is a sub-class of itself
  public static boolean isSubClass(String superClass, String subClass) {
    Class<?> cls1 = findClass(superClass);
    Class<?> cls2 = findClass(subClass);
    return isSubClass(cls1, cls2);
  }
  
  // by define, a class is a sub-class of itself
  public static boolean isSubClass(Class<?> superClass, Class<?> subClass) {
    boolean isSubClass = false;
    
    Class<?> cls1 = superClass;
    Class<?> cls2 = subClass;
    if (cls1 != null && cls2 != null) {
      // check super classes
      for (Class<?> currentClass = cls2; currentClass != null && !isSubClass; currentClass = currentClass.getSuperclass()) {
        isSubClass = cls1 == currentClass;
        
        // check interfaces
        if (!isSubClass) {
          Class<?>[] interfaces = currentClass.getInterfaces();
          for (int i = 0; i < interfaces.length && !isSubClass; i++) {
            isSubClass = interfaces[i].equals(cls1) || isSubClass(cls1, interfaces[i]);
          }
        }
      }
    }
    
    return isSubClass;
  }
  
  // cls.isAnonymousClass() does not work, don't know why
  // org.apache.commons.collections.buffer.BoundedFifoBuffer$1
  public static boolean isAnonymousClass(Class<?> cls) {
    boolean isAnonymousClass = false;
    
    String clsName = cls.getName();
    int index = clsName.lastIndexOf('$');
    if (index >= 0) {
      try {
        Integer.parseInt(clsName.substring(index + 1));
        isAnonymousClass = true;
      } catch (NumberFormatException e) {}
    }
    return isAnonymousClass;
  }
  
  public static String replaceInitByCtor(String methodName) {
    String newMethName = methodName;
    if (methodName.endsWith("<init>")) {
      newMethName = methodName.substring(0, methodName.length() - 7);
      
      int nIndex     = newMethName.lastIndexOf('.');
      String clsName = newMethName.substring(nIndex + 1);
      newMethName    += "." + clsName;
    }
    return newMethName;
  }
  
  public static String getStateString(List<Integer> state) {
    StringBuilder str = new StringBuilder();
    for (int i = 0, size = state.size(); i < size; i++) {
      str.append(state.get(i));
      if (i != size-1) {
        str.append("_");
      }
    }
    return str.toString();
  }
  
  public static List<Integer> translateStateString(String stateStr) {
    String[] values = stateStr.split("_");
    
    List<Integer> stateValues = new ArrayList<Integer>();
    for (int i = 0; i < values.length; i++) {
      stateValues.add(Integer.parseInt(values[i]));
    }
    return stateValues;
  }
  
  // translate to a Java acceptable number format
  public static String translateYicesNumber(String number) {
    if (number.matches("-*[\\d]+/[\\d]+")) {  // -1/2
      try {
        boolean negative = number.startsWith("-");
        String[] nums = number.substring(negative ? 1 : 0).split("/");

        float num1 = Float.parseFloat(nums[0]);
        float num2 = Float.parseFloat(nums[1]);

        number = String.valueOf(((num1 / num2) * (negative ? -1.0 : 1.0)));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return number;
  }
  
  public static Class<?> toBoxClass(Class<?> cls) {
    if (s_boxClassMap == null) {
      s_boxClassMap = new Hashtable<Class<?>, Class<?>>();
      
      // init
      s_boxClassMap.put(boolean.class, Boolean.class);
      s_boxClassMap.put(int.class, Integer.class);
      s_boxClassMap.put(short.class, Short.class);
      s_boxClassMap.put(long.class, Long.class);
      s_boxClassMap.put(float.class, Float.class);
      s_boxClassMap.put(double.class, Double.class);
      s_boxClassMap.put(char.class, Character.class);
      s_boxClassMap.put(byte.class, Byte.class);
    }
    
    // find in map
    Class<?> boxedCls = s_boxClassMap.get(cls);
    if (boxedCls == null) {
      boxedCls = cls;
    }
    return boxedCls;
  }
  
  public static String toPrimitiveType(String typeStr) {
    String ret = typeStr;
    if (typeStr.length() >= 1) {
      String ret2 = null;
      char typeChar = typeStr.charAt(0);
      switch (typeChar) {
      case 'I':
        ret2 = "int";
        break;
      case 'J':
        ret2 = "long";
        break;
      case 'S':
        ret2 = "short";
        break;
      case 'D':
        ret2 = "double";
        break;
      case 'F':
        ret2 = "float";
        break;
      case 'Z':
        ret2 = "boolean";
        break;
      case 'B':
        ret2 = "byte";
        break;
      case 'C':
        ret2 = "char";
        break;
      case 'V':
        ret2 = "void";
        break;
      default:
        ret2 = String.valueOf(typeChar);
        break;
      }
      if (typeStr.length() == 1) {
        ret = ret2;
      }
      else if (typeStr.charAt(1) == '[') {
        ret = ret2 + typeStr.substring(1);
      }
    }
    return ret;
  }
  
  public static String toEncoding(String typeStr) {
    if (s_encodingMap == null) {
      s_encodingMap = new Hashtable<String, String>();
      
      // init
      s_encodingMap.put("boolean", "Z");
      s_encodingMap.put("int", "I");
      s_encodingMap.put("short", "S");
      s_encodingMap.put("long", "J");
      s_encodingMap.put("float", "F");
      s_encodingMap.put("double", "D");
      s_encodingMap.put("char", "C");
      s_encodingMap.put("byte", "B");
    }
    
    // find in map
    String encoding = s_encodingMap.get(typeStr);
    if (encoding == null) {
      encoding = typeStr;
    }
    return encoding;
  }

  public static String toPrimitiveBagType(String typeStr) {
    String ret = typeStr;
    if (typeStr.length() == 1) {
      char typeChar = typeStr.charAt(0);
      switch (typeChar) {
      case 'I':
        ret = "Integer";
        break;
      case 'J':
        ret = "Long";
        break;
      case 'S':
        ret = "Short";
        break;
      case 'D': 
        ret = "Double";
        break;
      case 'F':
        ret = "Float";
        break;
      case 'Z':
        ret = "Boolean";
        break;
      case 'B':
        ret = "Byte";
        break;
      case 'C':
        ret = "Character";
        break;
      default:
        ret = String.valueOf(typeChar);
        break;
      }
    }
    else if (typeStr.charAt(1) == '[') {
      ret = toPrimitiveType(typeStr.substring(0, 1));
      ret = ret + typeStr.substring(1);
    }
    return ret;
  }

  public static boolean isPrimitiveType(String typeStr) {
    boolean ret = false;
    if (typeStr.length() == 1) {
      char typeChar = typeStr.charAt(0);
      switch (typeChar) {
      case 'I':
      case 'J':
      case 'S':
      case 'D':
      case 'F':
      case 'Z':
      case 'B':
      case 'C':
        ret = true;
        break;
      default:
        ret = false;
        break;
      }
    }
    return ret;
  }
  
  public static boolean isPrimitiveType(Class<?> cls) {
    return cls.isPrimitive();
  }
  
  public static boolean isPrimitiveBagType(Class<?> cls) {
    return (cls.equals(Boolean.class) || 
            cls.equals(Integer.class) || 
            cls.equals(Short.class) || 
            cls.equals(Long.class) || 
            cls.equals(Float.class) || 
            cls.equals(Double.class) || 
            cls.equals(Character.class) || 
            cls.equals(Byte.class));
  }
  
  public static Object castToBoxClass(Class<?> cls, String str) {
    cls = toBoxClass(cls);
    
    Object boxedObject = null;
    try {
      if (cls.equals(Boolean.class)){
        boxedObject = Boolean.parseBoolean(str);
      }
      else if (cls.equals(Integer.class)){
        boxedObject = Integer.parseInt(str);
      }
      else if (cls.equals(Long.class)){
        boxedObject = Long.parseLong(str);
      }
      else if (cls.equals(Short.class)){
        boxedObject = Short.parseShort(str);
      }
      else if (cls.equals(Float.class)){
        boxedObject = Float.parseFloat(str);
      }
      else if (cls.equals(Double.class)){
        boxedObject = Double.parseDouble(str);
      }
      else if (cls.equals(Byte.class)){
        boxedObject = Byte.parseByte(str);
      }
      else if (cls.equals(Character.class)){
        boxedObject = new Character(str.charAt(0));
      }
    } catch (Exception e) {
      System.err.println("Failed to cast: " + str + " to " + cls.getName() + "!");
    }
    return boxedObject;
  }

  public static boolean isMethodSignature(String methodNameOrSign) {
    return methodNameOrSign.contains("(");
  }
  
  public static StringBuilder replace(StringBuilder str, String fromStr, String toStr) {
    int nLength1 = fromStr.length();
    int nLength2 = toStr.length();  
    
    int nIndex = str.indexOf(fromStr);
    while (nIndex >= 0) {
      str.replace(nIndex, nIndex + nLength1, toStr);
      nIndex = str.indexOf(fromStr, nIndex + nLength2);
    }
    return str;
  }
  
  public static List<?> deleteRedundents(List<?> list) {
    HashSet<Object> hashSet = new HashSet<Object>();
    for (int i = 0; i < list.size(); i++) {
      Object o = list.get(i);
      if (hashSet.contains(o)) {
        list.remove(i--);
      }
      else {
        hashSet.add(o);
      }
    }
    return list;
  }
  
  public static String[] splitRecursive(String str, char opening, char closing, char splitWith) {
    List<String> splits = new ArrayList<String>();
    
    int openingCount = 0;
    int nextSplitPos = 0;
    for (int i = 0, size = str.length(); i < size; i++) {
      if (str.charAt(i) == opening) {
        openingCount++;
      }
      else if (str.charAt(i) == closing && openingCount > 0) {
        openingCount--;
      }
      else if (str.charAt(i) == splitWith && openingCount == 0) {
        splits.add(str.substring(nextSplitPos, i));
        nextSplitPos = i + 1;
      }
    }
    // the rest of the strings
    if (nextSplitPos < str.length()) {
      splits.add(str.substring(nextSplitPos));
    }
    
    return splits.toArray(new String[0]);
  }
  
  public static <E> Collection<E> intersect(Collection<E> collection1, Collection<E> collection2) {
    HashSet<E> common = new HashSet<E>();
    for (E element : collection1) {
      if (collection2.contains(element)) {
        common.add(element);
      }
    }
    return common;
  }
  
  public static String concatStrings(List<String> strings, String concatBy, boolean appendLast) {
    StringBuilder str = new StringBuilder();
    for (int i = 0, size = strings.size(); i < size; i++) {
      str.append(strings.get(i));
      if (appendLast || i < size - 1) {
        str.append(concatBy);
      }
    }
    return str.toString();
  }
  
  public static void deleteFile(File file){ 
    if (file.exists()) {
      if (file.isFile()) {
        file.delete();
      }
      else if (file.isDirectory()) {
        for (File subFile : file.listFiles()) {
          deleteFile(subFile);
        }
        file.delete();
      }
    }
  } 
  
  // there should not be any null values in both the keys and the values
  public static <E, T> Hashtable<T, E> reverseHashtable(Hashtable<E, T> map) {
    Hashtable<T, E> reverseMap = new Hashtable<T, E>();
    
    Enumeration<E> keys = map.keys();
    while (keys.hasMoreElements()) {
      E key = (E) keys.nextElement();
      T val = (T) map.get(key);
      if (key != null && val != null) {
        reverseMap.put(val, key);
      }
    }
    return reverseMap;
  }
  
  public static String trim(String str, String trimStart, String trimEnd) {
    return str.replaceAll("^" + trimStart, "").replaceAll(trimEnd + "$", "");
  }
  
  public static boolean loadJarFile(String jarFilePath) {
    boolean succeeded = false;
    try {
      // assuming the system classLoader is a URLClassLoader
      URLClassLoader classLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();

      Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
      boolean accessible = method.isAccessible();
      method.setAccessible(true);
      method.invoke(classLoader, new Object[]{new File(jarFilePath).toURI().toURL()});
      method.setAccessible(accessible);
      succeeded = true;
    } catch (Exception e) {e.printStackTrace();}
    return succeeded;
  }
  
  private static Hashtable<Class<?>, Class<?>> s_boxClassMap;
  private static Hashtable<String, String>     s_encodingMap;
}
