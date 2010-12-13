package hk.ust.cse.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

public class Utils {
  
  // default
  public static String getClassTypeJavaStr(String classType) {
    return getClassTypeJavaStr(classType, true);
  }

  public static String getClassTypeJavaStr(String classType, boolean replaceDollar) {
    StringBuilder classTypeStr = new StringBuilder(classType);
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
  
  public static List<Field> getInheritedFields(Class<?> cls) {
    List<Field> fields = new ArrayList<Field>();
    for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
      Field[] declFields = c.getDeclaredFields();
      // sort, because getDeclaredFields() does not guarantee order
      Arrays.sort(declFields, new Comparator<Field>() {
        public int compare(Field o1, Field o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });
      fields.addAll(Arrays.asList(declFields));
    }
    return fields;
  }
  
  public static Class<?> findClass(String clsName) {
    clsName = Utils.getClassTypeForNameStr(clsName);
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
        cls = Class.forName(clsName);
      }
    } catch (ClassNotFoundException e1) {
      System.err.println("Cannot find class: " + clsName);
    }
    return cls;
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
  
  private static Hashtable<Class<?>, Class<?>> s_boxClassMap;
  private static Hashtable<String, String>     s_encodingMap;
}
