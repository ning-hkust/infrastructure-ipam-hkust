package hk.ust.cse.Prevision.Solver;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMTVariable implements Cloneable {
  public enum VarCategory {
    VAR_FIELD, VAR_ARG, VAR_CONST, VAR_OTHER // don't care about other categories
  }
  
  private static final Pattern s_pattern;
  static {
    String regName     = "[\\w_\\[/$#<>\\.]+";
    String regBinaryOp = "[+-/&|^%\\*]";
    s_pattern = Pattern.compile("^(" + regName + ") (" + regBinaryOp + ") (" + regName + ")$");
  }

  public SMTVariable(String varName, String varType, VarCategory category,
      List<SMTVariable> extraVars) {
    m_varName     = varName;
    m_varType     = varType;
    m_varCategory = category;
    m_extraVars   = extraVars;
  }
  
  public SMTVariable(String varName, String varType, List<SMTVariable> extraVars) {
    m_varName     = varName;
    m_varType     = varType;
    m_varCategory = VarCategory.VAR_OTHER;
    m_extraVars   = extraVars;
  }

  public String toYicesDefString() {
    if (m_varType.equals("#ConstantNumber")) {
      return numberToYicesDefStr();
    }
    else if (m_varType.equals("#ConstantString")) {
      return stringToYicesDefStr();
    }
    else if (m_varType.equals("#BinaryOp")) {
      return binaryOpToYicesDefStr();
    }
    else {
      return normalToYicesDefStr();
    }
  }

  public String toYicesExprString(int currDepth) {
    // we don't want to compute YicesExprString repeatedly
    if (m_yicesExprStr != null) {
      return m_yicesExprStr;
    }
    
    // limit the number of recursive expansions
    if (currDepth >= s_maxRecDepth) {
      return "0";
    }
    
    if (m_varType.equals("#BinaryOp")) {
      StringBuilder str = new StringBuilder();
      Matcher matcher = s_pattern.matcher(m_varName);
      if (matcher.find()) {
        String[] varNames = new String[2];
        varNames[0]       = matcher.group(1);
        varNames[1]       = matcher.group(3);
        String binaryOp   = matcher.group(2);
        
        // translate into yices binaryOp format
        binaryOp = translateBinaryOp(binaryOp);
        
        // fill in extra vars
        for (int i = 0; i < varNames.length; i++) {
          if (varNames[i].equals("$extraVar" + (i + 1))) {
            SMTVariable extraVar = m_extraVars.get(i);
            String varName = extraVar.toYicesExprString(currDepth + 1);

            // when extraVar is a #BinaryOp itself, then it's in the
            // form of (op var1 var2), no need to filter it
            if (!extraVar.getVarType().equals("#BinaryOp")) {
              varName = Utils.filterChars(varName);
            }
            varNames[i] = varName;
          }
        }

        str.append("(");
        str.append(binaryOp);
        str.append(" ");
        str.append(varNames[0]);
        str.append(" ");
        str.append(varNames[1]);
        str.append(")");
      }
      m_yicesExprStr = str.toString();
    }
    else {
      m_yicesExprStr = Utils.filterChars(fillExtraVars2YicesExpr(currDepth));
    }
    return m_yicesExprStr;
  }
  
  public String toJavaExprString(Hashtable<String, String> paramNameMap, int currDepth) {
    if (m_javaExprStr != null) {
      return m_javaExprStr;
    }
    
    // limit the number of recursive expansions
    if (currDepth >= s_maxRecDepth) {
      return "0";
    }
    
    m_javaExprStr = fillExtraVars2JavaExpr(paramNameMap, currDepth);
    return m_javaExprStr;
  }
  
  public void finalizeExtraVars(Hashtable<SMTVariable, SMTVariable> finalVarMap, int currDepth) {
    // finalize extra vars
    for (int i = 0; m_extraVars != null && i < m_extraVars.size(); i++) {
      SMTVariable finalVar = finalVarMap.get(m_extraVars.get(i));
      if (finalVar != null) {
        if (currDepth < s_maxRecDepth) {
          // finalVar = finalVar.clone();
          finalVar.finalizeExtraVars(finalVarMap, currDepth + 1);
        }
        m_extraVars.set(i, finalVar);
      }
      else {
        if (currDepth < s_maxRecDepth) {
          // this is already a final var, just finalize it
          m_extraVars.get(i).finalizeExtraVars(finalVarMap, currDepth + 1);
        }
      }
    }
  }

  public String toString() {
    if (m_toString != null) {
      return m_toString;
    }
    
    StringBuilder str = new StringBuilder();
    if (m_varType != null && 
        m_varType.length() > 0 && 
        !m_varType.startsWith("#")) {
      str.append("(");
      str.append(m_varType);
      str.append(")");
    }
    str.append(fillExtraVars2YicesExpr(0));
    
    m_toString = str.toString();
    return m_toString;
  }
  
  // we need to find it from hashtable
  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }

    String yicesExprStr   = toYicesExprString(0);
    String yicesExprStr2  = null;
    // if is constant, add the prefix
    if (m_varType.equals("#ConstantNumber")) {
      yicesExprStr2 = "#!" + yicesExprStr;
    }
    else if (m_varType.equals("#ConstantString")) {
      yicesExprStr2 = "#" + yicesExprStr;
    }
    else {
      yicesExprStr2 = yicesExprStr;
    }

    if (o instanceof String) {
      return yicesExprStr2.equals(o.toString());
    }
    else if (o instanceof SMTVariable) {
      SMTVariable var = (SMTVariable) o;
      if (var.m_varType.startsWith("#Constant")) {
        // if var is also a constant, than it must have already
        // clear out the prefix, just compare directly!
        return yicesExprStr.equals(var.toYicesExprString(0));
      }
      else {
        return yicesExprStr2.equals(var.toYicesExprString(0));
      }
    }
    else {
      return false;
    }
  }

  // we need to find it from hashtable
  public int hashCode() {
    if (m_hashCode >= 0) {
      return m_hashCode;
    }
    
    // if is constant, add the prefix
    String yicesExprStr = toYicesExprString(0);
    if (m_varType.equals("#ConstantNumber")) {
      yicesExprStr = "#!" + yicesExprStr;
    }
    else if (m_varType.equals("#ConstantString")) {
      yicesExprStr = "#" + yicesExprStr;
    }

    m_hashCode = yicesExprStr.hashCode();
    return m_hashCode;
  }

  public SMTVariable clone() {
    List<SMTVariable> extraVars = null;
    if (m_extraVars != null) {
      extraVars = new ArrayList<SMTVariable>();
      for (int i = 0; i < m_extraVars.size(); i++) {
        extraVars.add(m_extraVars.get(i));
      }
    }
    return new SMTVariable(m_varName, m_varType, m_varCategory, extraVars);
  }
  

  public String getVarName() {
    return m_varName;
  }

  public String getVarType() {
    return m_varType;
  }

  public VarCategory getVarCategory() {
    return m_varCategory;
  }

  public List<SMTVariable> getExtraVars() {
    return m_extraVars;
  }
  
  // set the maximum allow depth for recursion expansion of 
  // all SMTVariable objects. The default value is MAX_INT
  public static void setMaxRecursiveDepth(int maxDepth) {
    s_maxRecDepth = maxDepth;
  }

  // no need to define any thing for number constant
  private String numberToYicesDefStr() {
    return "";
  }

  // create bit-vector for string constant
  private String stringToYicesDefStr() {
    StringBuilder yicesStr = new StringBuilder();

    // m_varName in the form of #somestr
    String binaryStr = strToBinaryStr(m_varName.substring(1));
    yicesStr.append("(define ");
    yicesStr.append(Utils.filterChars(m_varName));
    yicesStr.append("::(bitvector ");
    yicesStr.append(binaryStr.length());
    yicesStr.append("))\n");
    
    // limit string constant
    yicesStr.append("(assert (= ");
    yicesStr.append(Utils.filterChars(m_varName));;
    yicesStr.append(" 0b");
    yicesStr.append(binaryStr);
    yicesStr.append("))");

    return yicesStr.toString();
  }

  private String binaryOpToYicesDefStr() {
    return "";
  }

  private String normalToYicesDefStr() {
    StringBuilder yicesStr = new StringBuilder();

    yicesStr.append("(define ");
    yicesStr.append(toYicesExprString(0));
    yicesStr.append("::");
    if (m_varType != null && m_varType.length() > 0) {
      yicesStr.append(m_varType);
    }
    else {
      yicesStr.append("Unknown-Type");
    }
    yicesStr.append(")");

    return yicesStr.toString();
  }

  private String fillExtraVars2YicesExpr(int currDepth) {
    // fill in extra vars in recursion
    StringBuilder realVarName = new StringBuilder(m_varName);
    for (int i = 0; m_extraVars != null && i < m_extraVars.size(); i++) {
      realVarName = Utils.replace(realVarName, "$extraVar" + (i + 1),
          m_extraVars.get(i).toYicesExprString(currDepth + 1));
    }
    return realVarName.toString();
  }
  
  private String fillExtraVars2JavaExpr(Hashtable<String, String> paramNameMap, 
      int currDepth) {
    String mappedName = paramNameMap.get(m_varName);
    mappedName = (mappedName == null) ? m_varName : mappedName;

    // fill in extra vars in recursion
    StringBuilder realVarName = new StringBuilder(mappedName);
    for (int i = 0; m_extraVars != null && i < m_extraVars.size(); i++) {
      realVarName = Utils.replace(realVarName, "$extraVar" + (i + 1),
          m_extraVars.get(i).toJavaExprString(paramNameMap, currDepth + 1));
    }
    return realVarName.toString();
  }

  private String strToBinaryStr(String str) {
    char[] strChar = str.toCharArray();
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < strChar.length; i++) {
      result.append(Integer.toBinaryString(strChar[i]));
    }
    return result.toString();
  }
  
  
  //translate to a yices acceptable binary operator
  private String translateBinaryOp(String binaryOp) {
    switch (binaryOp.charAt(0)) {
    case '%':
      binaryOp = "mod";
      break;
    default:
      // others remain unchanged
      break;
    }
    return binaryOp;
  }

  private final String            m_varName;
  private final String            m_varType;
  private final VarCategory      m_varCategory;
  private final List<SMTVariable> m_extraVars;
  
  // cache results
  private String m_yicesExprStr;
  private String m_javaExprStr;
  private String m_toString;
  private int    m_hashCode = -1;
  
  private static int s_maxRecDepth = Integer.MAX_VALUE;
}
