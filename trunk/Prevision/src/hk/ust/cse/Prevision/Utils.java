package hk.ust.cse.Prevision;


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
}
