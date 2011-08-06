package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.Solver.SMTVariable.VarCategory;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SMTVariableMap {

  private static final Pattern s_pattern1, s_pattern2, s_pattern3, s_pattern4, 
                               s_pattern5, s_pattern6, s_pattern7, s_pattern8, 
                               s_pattern9, s_pattern10, s_pattern11;
  
  private static final Pattern s_floatPattern1, s_floatPattern2;
  
  static {
    String regSimple      = "v[\\d@$]+";                 // v5, v5@2, v5$1
    String regConstant    = "(?:(?:##.*)|(?:#!-*(?:(?:[\\d\\.]+[\\d\\.E-]*)|Infinity|NaN))|(?:null))"; // #!11 or #!2.3 or 1.0E-6 or ##str1 or #!-Infinity or #!NaN
    String regVarType     = "[\\w_\\[/$,]+";            // [Ljava/lang/String, etc
    String regName        = "[\\w_\\[/$#@]+";           // args
    String regMethodArgs  = "(?:[\\S]+[, ]*)*";         // might have problem if it's a str constant with ', ' in it, but good enough anyway!
    String regBinaryOp    = "(?:[+-/&|^%\\*]|(?:<<)|(?:>>))"; // binaryOps (should be identical to SMTVariable.java)
    s_pattern1 = Pattern.compile("^" + regSimple + "$");
    s_pattern2 = Pattern.compile("^" + regConstant + "$");
    s_pattern3 = Pattern.compile("^\\((" + regVarType + ")\\)(" + regName + ")$");
    s_pattern4 = Pattern.compile("^\\((" + regVarType + ")\\)(" + regName + ")\\.(" + regName + ")$");
    s_pattern5 = Pattern.compile("^FreshInstanceOf\\((" + regVarType + ")\\)$");
    s_pattern6 = Pattern.compile("^(" + regSimple + ").length$");
    s_pattern7 = Pattern.compile("^\\((" + regVarType + ")\\)(" + regName + "|" + regConstant + ")\\.(" + regName + ")\\((" + regMethodArgs + ")\\);$");  // (I)##}.length();
    s_pattern8 = Pattern.compile("^(" + regSimple + ")( isInstanceOf\\((?:" + regVarType + ")\\))$");
    s_pattern9 = Pattern.compile("^subType\\(typeOf\\((" + regName + ")\\),(" + regVarType + ")\\)$");
    s_pattern10 = Pattern.compile("^(" + regName + "|" + regConstant + ")( " + regBinaryOp + " )(" + regName + "|" + regConstant + ")$");
    s_pattern11 = Pattern.compile("^\\((" + regVarType + ")\\)(" + regName + ")\\[(" + regName + "|" + regConstant + ")\\]$");
  
    s_floatPattern1 = Pattern.compile("-*[\\d]+\\.[\\d]+"); // 1.0
    s_floatPattern2 = Pattern.compile("-*[\\d]+\\.[\\d]+E-*[\\d]+");  // 1.0E-6 or 1.0E6
    
    // keeps a record of how a var string is matched by the patterns
    s_translateVarCache = new LinkedHashMap<String, Integer>() {
      private static final long serialVersionUID = 1L;

      @Override
      protected boolean removeEldestEntry(java.util.Map.Entry<String, Integer> eldest) {
        return size() > 5000;
      }
    }; 
  }
  
  public SMTVariableMap(Hashtable<String, List<String>> plainVarMap, int maxRecDepth) {
    // create SMTVariableMap object from plain varMap
    buildSMTVarMap(plainVarMap);
    
    // set the maximum recursive depth for SMTVariable objects
    SMTVariable.setMaxRecursiveDepth(maxRecDepth);
  }

  public String genYicesInput() {
    StringBuilder yicesStr = new StringBuilder();

    // finalize variables in m_varMap
    finalizeAllSMTVariables();

    // add primitive define-type statements
    Hashtable<String, String> def_types = new Hashtable<String, String>();
    def_types.put("I", "int");
    def_types.put("J", "int");
    def_types.put("S", "int");
    def_types.put("B", "int");
    def_types.put("C", "int");
    def_types.put("D", "real");
    def_types.put("F", "real");
    def_types.put("Z", "bool");
    def_types.put("Unknown-Type", "int");
    
    // add other define-type statements
    for (SMTVariable finalVar : m_finalVars) {
      String typeName = finalVar.getVarType();
      // when containing space, it is a constant definition
      // e.g., Fresh_0_(Ljava/lang/Object)::Ljava/lang/Object notnull
      // e.g., this::Ljava/lang/Object notnull
      int index = typeName.indexOf(' ');
      if (index >= 0) {
        typeName = typeName.substring(0, index);
      }
      
      if (!def_types.containsKey(typeName)) {
        def_types.put(typeName, "");
      }
    }

    // reset Yices
    yicesStr.append("(reset)\n");

    // set Yices Option
    yicesStr.append("(set-evidence! true)\n");

    // define-types
    // the reference type needs to come at first
    yicesStr.append("(define-type reference (scalar null notnull))\n");
    Enumeration<String> define_types = def_types.keys();
    while (define_types.hasMoreElements()) {
      String define_type = (String) define_types.nextElement();
      // not constant types
      if (!define_type.startsWith("#")) {
        yicesStr.append("(define-type ");
        yicesStr.append(define_type);
        yicesStr.append(" ");

        String basicType = def_types.get(define_type);
        if (basicType != null && basicType.length() > 0) {
          yicesStr.append(basicType);
        }
        else {
          // everything else is treated as reference type
          yicesStr.append("reference");
        }
        yicesStr.append(")\n");
      }
    }

    // add true/false, null/not into m_defFinalVarMap as they're defined implicitly
    m_defFinalVarMap = new Hashtable<String, SMTVariable>();
    m_defFinalVarMap.put("false", new SMTVariable("false", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    m_defFinalVarMap.put("true", new SMTVariable("true", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    m_defFinalVarMap.put("null", new SMTVariable("null", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    m_defFinalVarMap.put("notnull", new SMTVariable("notnull", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    
    // defines
    HashSet<String> m_defined = new HashSet<String>();
    for (SMTVariable finalVar : m_finalVars) {
      String toYicesDefStr = finalVar.toYicesDefString();
      if (toYicesDefStr.length() > 0 && !m_defined.contains(toYicesDefStr)) {
        yicesStr.append(toYicesDefStr + "\n");

        // avoid duplication
        m_defined.add(toYicesDefStr);

        // store into m_defFinalVarMap for later use
        m_defFinalVarMap.put(finalVar.toYicesExprString(0), finalVar);
      }
    }

    return yicesStr.toString();
  }

  public Hashtable<String, SMTVariable> getAllVarMap() {
    return m_allVarMap;
  }

  public Hashtable<SMTVariable, SMTVariable> getFinalVarMap() {
    return m_finalVarMap;
  }

  public Hashtable<String, SMTVariable> getDefFinalVarMap() {
    return m_defFinalVarMap;
  }

  private void buildSMTVarMap(Hashtable<String, List<String>> plainVarMap) {
    // initialize members
    m_finalVars   = new ArrayList<SMTVariable>();
    m_allVarMap   = new Hashtable<String, SMTVariable>();
    m_finalVarMap = new Hashtable<SMTVariable, SMTVariable>();

    // add true/false, null/not into m_allVarMap as they're defined implicitly
    m_allVarMap.put("false", new SMTVariable("false", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    m_allVarMap.put("true", new SMTVariable("true", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    m_allVarMap.put("null", new SMTVariable("null", "#ConstantNumber",
        VarCategory.VAR_CONST, null));
    m_allVarMap.put("notnull", new SMTVariable("notnull", "#ConstantNumber",
        VarCategory.VAR_CONST, null));

    m_nFreshInstance = 0;
    Enumeration<String> keys = plainVarMap.keys();
    while (keys.hasMoreElements()) {
      String finalVar = (String) keys.nextElement();
      
      // translate finalVar into SMTVariable form
      SMTVariable smtFinalVar = translateSMTVar(finalVar);
      if (smtFinalVar != null) {
        List<String> midVars = plainVarMap.get(finalVar);
        for (String midVar : midVars) {
          // build a mapping from mid vars (always in the form of v#)
          // to the actual concrete SMTVariable form smtFinalVar
          SMTVariable smtMidVar = translateSMTVar(midVar);
          if (smtMidVar != null) {
            m_finalVarMap.put(smtMidVar, smtFinalVar);
            m_allVarMap.put(midVar, smtMidVar);
          }
          else {
            System.err.println("Unable to analyze mid-variable: " + midVar);
          }
        }
        m_finalVars.add(smtFinalVar);
        m_allVarMap.put(finalVar, smtFinalVar);
      }
      else {
        // neglect Caught/Triggered exception flag variable
        if (finalVar.startsWith("Triggered") || finalVar.startsWith("Caught")) {
          continue;
        }
        
        System.err.println("Unable to analyze variable: " + finalVar);
      }
    }
  }

  private SMTVariable translateSMTVar(String finalVarStr) {
    // try to obtain the previous pattern matching history for this var string
    Integer matchedPattern = s_translateVarCache.get(finalVarStr);
    matchedPattern = (matchedPattern == null) ? 0 : matchedPattern;
    
    int matchedBy           = 0;
    Matcher matcher         = null;
    SMTVariable smtVariable = null;
    if ((matchedPattern == 1 && (matcher = s_pattern1.matcher(finalVarStr)).find()) || 
        (matchedPattern == 0 && (matcher = s_pattern1.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 1;
      
      smtVariable = new SMTVariable(finalVarStr, "Unknown-Type", null);
    }
    else if ((matchedPattern == 2 && (matcher = s_pattern2.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern2.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 2;
      
      // if is number constant
      if (finalVarStr.startsWith("#!")) {
        String num = translateJavaNumber(finalVarStr);
        smtVariable = new SMTVariable(num, "#ConstantNumber", VarCategory.VAR_CONST, null);
      }
      // if is string constant
      else if (finalVarStr.startsWith("##")) {
        // we keep one '#' at the beginning of the string to
        // make sure that the string will not begin with a number
        smtVariable = new SMTVariable(finalVarStr.substring(1), "#ConstantString",
            VarCategory.VAR_CONST, null);
      }
      else if (finalVarStr.equals("null")) {
        smtVariable = new SMTVariable(finalVarStr, "#ConstantNumber",
            VarCategory.VAR_CONST, null);
      }
      else {
        // 
      }
    }
    else if ((matchedPattern == 3 && (matcher = s_pattern3.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern3.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 3;
      
      String varType = matcher.group(1);
      String varName = matcher.group(2);
      
      // special handling for 'this'
      if (varName.equals("this")) {
        varType += " notnull";
      }
      
      smtVariable = new SMTVariable(varName, varType, VarCategory.VAR_ARG, null);
    }
    else if ((matchedPattern == 4 && (matcher = s_pattern4.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern4.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 4;
      
      String fieldType = matcher.group(1);
      String refName   = matcher.group(2);
      String fieldName = matcher.group(3);

      StringBuilder getStr = new StringBuilder();
      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      if (refName.startsWith("v")) {
        extraVars.add(new SMTVariable(refName, "Unknown-Type", null));
        getStr.append("($extraVar1)");
      }
      else {
        getStr.append(refName);
      }
      getStr.append(".");
      getStr.append(fieldName);
      
      smtVariable = new SMTVariable(getStr.toString(), fieldType, VarCategory.VAR_FIELD, extraVars);
    }
    else if ((matchedPattern == 5 && (matcher = s_pattern5.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern5.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 5;
      
      String varType = matcher.group(1);
      String varName = "Fresh_" + m_nFreshInstance++ + "_(" + varType + ")";
      smtVariable = new SMTVariable(varName, varType + " notnull", null);
    }
    else if ((matchedPattern == 6 && (matcher = s_pattern6.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern6.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 6;
      
      String varName = matcher.group(1);

      String lengthOfStr = "($extraVar1).length";
      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      extraVars.add(new SMTVariable(varName, "Unknown-Type", null));

      smtVariable = new SMTVariable(lengthOfStr, "I", VarCategory.VAR_FIELD, extraVars);
    }
    else if ((matchedPattern == 7 && (matcher = s_pattern7.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern7.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 7;
      
      String retType    = matcher.group(1);
      String ref        = matcher.group(2);
      String methodName = matcher.group(3);
      String args       = matcher.group(4);

      int nExtraVar = 0;
      StringBuilder methodStr = new StringBuilder();
      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      // if is static call, ref starts with [ or L
      if (ref.startsWith("v")) {
        extraVars.add(new SMTVariable(ref, "Unknown-Type", null));
        methodStr.append("($extraVar");
        methodStr.append(++nExtraVar);
        methodStr.append(")");
      }
      else {
        methodStr.append(ref);
      }
      methodStr.append(".");
      methodStr.append(methodName);
      methodStr.append("(");
      
      // append args
      if (args.length() > 0) {
        String[] argList = args.split(", ");
        for (int i = 0; i < argList.length; i++) {
          extraVars.add(new SMTVariable(argList[i], "Unknown-Type", null));
          methodStr.append("$extraVar");
          methodStr.append(++nExtraVar);
          // separate each arg
          if (i != argList.length - 1) {
            methodStr.append(", ");
          }
        }
      }

      methodStr.append(")");
      smtVariable = new SMTVariable(methodStr.toString(), retType, extraVars);
    }
    else if ((matchedPattern == 8 && (matcher = s_pattern8.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern8.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 8;
      
      String ref    = matcher.group(1);
      String instOf = matcher.group(2);

      instOf = "$extraVar1" + instOf;
      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      extraVars.add(new SMTVariable(ref, "Unknown-Type", null));
      smtVariable = new SMTVariable(instOf, "Z", extraVars);
    }
    else if ((matchedPattern == 9 && (matcher = s_pattern9.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern9.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 9;
      
      String varName    = matcher.group(1);
      String superType  = matcher.group(2);

      String subTypeStr = "subType(typeOf($extraVar1), " + superType + ")";
      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      extraVars.add(new SMTVariable(varName, "Unknown-Type", null));
      smtVariable = new SMTVariable(subTypeStr, "Z", extraVars);
    }
    else if ((matchedPattern == 10 && (matcher = s_pattern10.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern10.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 10;
      
      String varName1 = matcher.group(1);
      String varName2 = matcher.group(3);
      String binaryOp = matcher.group(2);
      
      // translate if is number
      varName1 = translateJavaNumber(varName1);
      varName2 = translateJavaNumber(varName2);

      int nExtraVar = 0;
      StringBuilder binaryOpStr = new StringBuilder();
      binaryOpStr.append("$extraVar");
      binaryOpStr.append(++nExtraVar);
      binaryOpStr.append(binaryOp);
      binaryOpStr.append("$extraVar");
      binaryOpStr.append(++nExtraVar);

      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      extraVars.add(new SMTVariable(varName1, "Unknown-Type", null));
      extraVars.add(new SMTVariable(varName2, "Unknown-Type", null));

      smtVariable = new SMTVariable(binaryOpStr.toString(), "#BinaryOp", extraVars);
    }
    else if ((matchedPattern == 11 && (matcher = s_pattern11.matcher(finalVarStr)).find()) || 
             (matchedPattern == 0 && (matcher = s_pattern11.matcher(finalVarStr)).find())) {
      // save matched history
      matchedBy = 11;
      
      String varType  = matcher.group(1);
      String varName  = matcher.group(2);
      String varIndex = matcher.group(3);

      // translate if is number
      varIndex = translateJavaNumber(varIndex);

      int nExtraVar = 0;
      StringBuilder arrayRefStr = new StringBuilder();
      arrayRefStr.append("$extraVar");
      arrayRefStr.append(++nExtraVar);
      arrayRefStr.append("[");
      arrayRefStr.append("$extraVar");
      arrayRefStr.append(++nExtraVar);
      arrayRefStr.append("]");

      List<SMTVariable> extraVars = new ArrayList<SMTVariable>();
      extraVars.add(new SMTVariable(varName, "Unknown-Type", null));
      extraVars.add(new SMTVariable(varIndex, "Unknown-Type", null));

      smtVariable = new SMTVariable(arrayRefStr.toString(), varType, extraVars);
    }
    
    // save matched information into cache
    if (matchedPattern == 0 && matchedBy > 1) {
      s_translateVarCache.put(finalVarStr, matchedBy);
    }
    
    return smtVariable;
  }

  private void finalizeAllSMTVariables() {
    // finalize extraVars in every finalVar
    for (SMTVariable finalVar : m_finalVars) {
      finalVar.finalizeExtraVars(m_finalVarMap, 0);
    }
  }

  // translate to a yices acceptable number format
  private String translateJavaNumber(String number) {
    // not a number
    if (!number.startsWith("#!")) {
      return number;
    }
    
    number = number.substring(2);

    // replace Infinity
    if (number.endsWith("Infinity")) {
      number = number.substring(0, number.length() - 8);
      number += String.valueOf(Integer.MAX_VALUE);
    }
    else if (s_floatPattern1.matcher(number).matches()) {
      float fNum = Float.parseFloat(number);

      if (fNum < 1) {
        fNum *= 1000000;  /* precision up to .000000 */
        // yices format for real number
        number = String.valueOf((int)fNum /* lose some precision here*/) + "/1000000"; 
      }
      else {
        fNum *= 1000;     /* precision up to .000 */
        // yices format for real number
        number = String.valueOf((int)fNum /* lose some precision here*/) + "/1000"; 
      }
    }
    else if (s_floatPattern2.matcher(number).matches()) {  // 1.0E-6 or 1.0E6
      float fNum = Float.parseFloat(number);
      
      if (fNum < 1) {
        fNum *= 1000000;  /* precision up to .000000 */
        // yices format for real number
        number = String.valueOf((int)fNum /* lose some precision here*/) + "/1000000"; 
      }
      else {
        // the number should be big, transfer directly to int
        number = String.valueOf((int)fNum);
      }
    }

    return number;
  }

  private int                                 m_nFreshInstance;
  private List<SMTVariable>                   m_finalVars;
  private Hashtable<String, SMTVariable>      m_allVarMap;      /*plain var string to smtvariable*/
  private Hashtable<SMTVariable, SMTVariable> m_finalVarMap;    /*mid-vars to final vars*/
  private Hashtable<String, SMTVariable>      m_defFinalVarMap; /*yices defined var string to smtvariable*/

  private static LinkedHashMap<String, Integer> s_translateVarCache;
}
