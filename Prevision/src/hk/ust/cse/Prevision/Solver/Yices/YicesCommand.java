package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.ICommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

public class YicesCommand implements ICommand {

  private class TranslatedInstance {
    public TranslatedInstance(String value, String type) {
      m_value = value;
      m_type  = type;
    }
    
    public String m_type;
    public String m_value;
  }
  
  @Override
  public TranslatedCommand translateToCommand(Formula formula, 
      MethodMetaData methData, boolean keepUnboundedField, boolean retrieveUnsatCore) {
    
    TranslatedCommand result = new TranslatedCommand();
    
    Hashtable<String, Hashtable<String, Reference>> refMap = formula.getRefMap();
    Hashtable<String, Reference> references = null;
    if (refMap.size() > 0) {
      references = refMap.values().iterator().next();
    }
    else {
      references = new Hashtable<String, Reference>();
    }
    
    StringBuilder command = new StringBuilder();
    command.append(defineTypes(references, formula.getConditionList(), methData));
    command.append(defineVariables(formula.getConditionList(), methData, result));
    
    Hashtable<String, TranslatedInstance> helperVars = new Hashtable<String, TranslatedInstance>();
    String translatedCondStr = translateConditions(
        formula.getConditionList(), methData, result, keepUnboundedField, retrieveUnsatCore, helperVars);
    
    command.append(defineHelperVariables(helperVars));
    command.append(translatedCondStr);
    command.append("(check)\n");
    result.command = command.toString();
    return result;
  }
  
  private String defineTypes(Hashtable<String, Reference> references, List<Condition> conditionList, MethodMetaData methData) {
    StringBuilder command = new StringBuilder();
    
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
    
    // add other define-type statements from references
//    Enumeration<String> keys = references.keys();
//    while (keys.hasMoreElements()) {
//      String key = (String) keys.nextElement();
//      Reference ref = references.get(key);
//      
//      String typeName = ref.getType();
//      // when containing space, it is a constant definition
//      // e.g., Fresh_0_(Ljava/lang/Object)::Ljava/lang/Object notnull
//      // e.g., this::Ljava/lang/Object notnull
//      int index = typeName.indexOf(' ');
//      if (index >= 0) {
//        typeName = typeName.substring(0, index);
//      }
//      
//      if (!def_types.containsKey(typeName)) {
//        def_types.put(typeName, "");
//      }
//    }
    
    // add define-type statements from instances in conditions
    for (Condition condition : conditionList) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = new Instance[] {term.getInstance1(), term.getInstance2()};
        for (Instance instance : instances) {
          if (!instance.isConstant()) {
            if (instance.isAtomic()) { // FreshInstanceOf(...)
              if (!def_types.containsKey(instance.getType())) {
                def_types.put(instance.getType(), "");
              }
            }
            else if (!instance.isBounded()) { // Ljava/lang/System.out
              Reference lastRef = instance.getLastReference();
              if (lastRef != null) {
                Reference paramRef = tryCreateParamReference(methData, lastRef.getLongName()); // lastRef may be a parameter
                String varType = paramRef != null ? paramRef.getType() : lastRef.getType();
                if (!def_types.containsKey(varType)) {
                  def_types.put(varType, "");
                }
              }
            }
          }
        }
      }
    }

    // reset Yices
    command.append("(reset)\n");

    // set Yices Option to output a model
    command.append("(set-evidence! true)\n");

    // the reference type needs to come at first
    command.append("(define-type reference (scalar null notnull))\n");
    
    // define-types
    Enumeration<String> define_types = def_types.keys();
    while (define_types.hasMoreElements()) {
      String define_type = (String) define_types.nextElement();
      // not constant types
      command.append("(define-type ");
      command.append(Utils.filterChars(define_type));
      command.append(" ");

      String basicType = def_types.get(define_type);
      if (basicType != null && basicType.length() > 0) {
        command.append(basicType);
      }
      else {
        // everything else is treated as reference type
        command.append("reference");
      }
      command.append(")\n");
    }
    return command.toString();
  }

  private String defineVariables(List<Condition> conditionList, MethodMetaData methData, TranslatedCommand result) {
    StringBuilder command = new StringBuilder();
    command.append(defineInstances(conditionList, methData, result));
    return command.toString();
  }
  
  private String defineHelperVariables(Hashtable<String, TranslatedInstance> helperVars) {
    StringBuilder command = new StringBuilder();

    if (helperVars.size() > 0) {
      command.append(numberToBVFunctions());
    }
    
    // first time for define
    Enumeration<String> keys = helperVars.keys();
    while (keys.hasMoreElements()) {
      String translatedInstance = (String) keys.nextElement();
      TranslatedInstance helperInstance = helperVars.get(translatedInstance);
      
      boolean isHelperBitVector = helperInstance.m_type.equals("bitvector32");
      command.append("(define ");
      command.append(helperInstance.m_value);
      command.append("::");
      command.append(isHelperBitVector ? "(bitvector 32))\n" : "int)\n");
    }
    
    // second time for assert
    keys = helperVars.keys();
    while (keys.hasMoreElements()) {
      String translatedInstance = (String) keys.nextElement();
      TranslatedInstance helperInstance = helperVars.get(translatedInstance);
      
      boolean isHelperBitVector = helperInstance.m_type.equals("bitvector32");
      command.append("(assert ($numBitSame ");
      command.append(isHelperBitVector ? helperInstance.m_value : translatedInstance);
      command.append(" ");
      command.append(isHelperBitVector ? translatedInstance : helperInstance.m_value);
      command.append("))\n");
    }
    
    return command.toString();
  }
  
//  private String defineReferences(Hashtable<String, Reference> references) {
//    StringBuilder command = new StringBuilder();
//    
//    // defines
//    HashSet<String> m_defined = new HashSet<String>();
//    Enumeration<String> keys = references.keys();
//    while (keys.hasMoreElements()) {
//      String key = (String) keys.nextElement();
//      Reference ref = references.get(key);
//      
//      if (ref.getInstance().isAtomic() && ref.getInstance().isBounded() && !ref.getInstance().isConstant()) {
//        String define = translateToDefString(ref);
//        if (define.length() > 0 && !m_defined.contains(define)) {
//          command.append(define + "\n");
//
//          // avoid duplication
//          m_defined.add(define);
//        }
//      }
//    }
//    return command.toString();
//  }
  
//  private String translateToDefString(Reference ref) {
//    return normalToYicesDefStr(ref);
//  }
  
  private String defineInstances(List<Condition> conditionList, MethodMetaData methData, TranslatedCommand result) {
    StringBuilder command = new StringBuilder();

    // defines
    HashSet<String> defined = new HashSet<String>();
    for (Condition condition : conditionList) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = new Instance[] {term.getInstance1(), term.getInstance2()};
        for (Instance instance : instances) {
          List<String> defines = translateToDefString(instance, methData, result);
          for (String define : defines) {
            if (define.length() > 0 && !defined.contains(define)) {
              command.append(define);
              defined.add(define);
            }
          }
        }
      }
    }
    return command.toString();
  }
  
  private List<String> translateToDefString(Instance instance, MethodMetaData methData, TranslatedCommand result) {
    List<String> defStrings = new ArrayList<String>();
    
    if (instance.isAtomic()) {
      StringBuilder defString = new StringBuilder();
      if (instance.isConstant()) {
        String value = instance.getValue();
        if (value.startsWith("##")) {
          defString.append(stringToDefStr(value));
        }
        else if (value.startsWith("#!")) {
          defString.append(numberToDefStr(value));
        }
      }
      else {
        // should only be FreshInstanceOf(...)
        String value = instance.getValue();
        if (value.startsWith("FreshInstanceOf(")) {
          defString.append(freshToDefStr(instance));
        }
      }
      defStrings.add(defString.toString());
    }
    else if (instance.isBounded() /* not atomic but still bounded */) {
      List<String> defStrings1 = translateToDefString(instance.getLeft(), methData, result);
      List<String> defStrings2 = translateToDefString(instance.getRight(), methData, result);
      defStrings.addAll(defStrings1);
      defStrings.addAll(defStrings2);
    }
    else if (!instance.isBounded()){ // field reference
      StringBuilder defString = new StringBuilder();
      // check if it is a constant string field, some of these fields have known values
      int type = isConstStringField(instance);
      if (type >= 0) {
        defString.append(constStringFieldToDefStr(instance, type));
      }
      else {
        defString.append(unboundToDefStr(instance, methData));
      }
      defStrings.add(defString.toString());
    }
    
    // only save if there is an 1:1 matching
    if (defStrings.size() == 1 && defStrings.get(0).length() > 0) {
      String defString = defStrings.get(0);
      int index = defString.indexOf("::");
      if (index >= 0) {
        result.nameInstanceMapping.put(defString.substring(8, index), instance);
      }
    }
    
    return defStrings;
  }
  
  private String unboundToDefStr(Instance instance, MethodMetaData methData) {
    StringBuilder defString = new StringBuilder();
    Reference lastRef = instance.getLastReference();
    if (lastRef != null) {
      Reference paramRef = tryCreateParamReference(methData, lastRef.getLongName()); // lastRef may be a parameter
      String callSites = (lastRef.getCallSites().length() > 0) ? "<" + lastRef.getCallSites() + ">" : "";
      String varName   = paramRef != null ? callSites + paramRef.getName() : lastRef.getLongNameWithCallSites();
      String varType   = paramRef != null ? paramRef.getType() : lastRef.getType();
      defString.append("(define ");
      defString.append(Utils.filterChars(varName)); // instance may be from inner method
      defString.append("::");
      defString.append(Utils.filterChars(varType));
      defString.append(")\n");
    }
    return defString.toString();
  }
  
  private String constStringFieldToDefStr(Instance instance, int type) {
    StringBuilder defString = new StringBuilder();
    
    Reference lastRef = instance.getLastReference();
    Instance lastDeclInstance = (lastRef != null) ? lastRef.getDeclaringInstance() : null;
    switch (type) {
    case 0:
      defString.append("(define ");
      defString.append(Utils.filterChars(lastDeclInstance.getValue() + ".count"));
      defString.append("::I ");
      defString.append(lastDeclInstance.getValue().length() - 2);
      defString.append(")\n");
      break;
    case 1:
      defString.append("(define ");
      defString.append(Utils.filterChars(lastDeclInstance.getValue() + ".value"));
      defString.append("::[C notnull)\n");
      break;
    case 2:
      Reference lastLastRef = (lastDeclInstance != null) ? lastDeclInstance.getLastReference() : null;
      Instance lastLastDeclInstance = (lastLastRef != null) ? lastLastRef.getDeclaringInstance() : null;
      defString.append("(define ");
      defString.append(Utils.filterChars(lastLastDeclInstance.getValue() + ".value.length"));
      defString.append("::I ");
      defString.append(lastLastDeclInstance.getValue().length() - 2);
      defString.append(")\n");
      break;
    default:
      break;
    }
    return defString.toString();
  }

  // check if it is a constant string field, some of these fields have known values
  private int isConstStringField(Instance instance) {
    int type = -1;
    
    Reference lastRef = instance.getLastReference();
    Instance lastDeclInstance = (lastRef != null) ? lastRef.getDeclaringInstance() : null;
    if (lastDeclInstance != null && lastDeclInstance.isConstant() && lastDeclInstance.getValue().startsWith("##")) {
      String fieldName = lastRef.getName();
      if (fieldName.equals("count")) {
        type = 0;
      }
      else if (fieldName.equals("value")) {
        type = 1;
      }
    }
    else {
      Reference lastLastRef = (lastDeclInstance != null) ? lastDeclInstance.getLastReference() : null;
      Instance lastLastDeclInstance = (lastLastRef != null) ? lastLastRef.getDeclaringInstance() : null;
      if (lastLastDeclInstance != null && lastLastDeclInstance.isConstant() && lastLastDeclInstance.getValue().startsWith("##")) {
        String fieldName  = lastRef.getName();
        String fieldName2 = lastLastRef.getName();
        if (fieldName2.equals("value") && fieldName.equals("length")) {
          type = 2;
        }
      }
    }
    return type;
  }
  
  private String freshToDefStr(Instance instance) {
    StringBuilder defString = new StringBuilder();
    defString.append("(define ");
    defString.append(Utils.filterChars(instance.getValue()));
    defString.append("::");
    defString.append(Utils.filterChars(instance.getType()));
    defString.append(" notnull)\n");
    return defString.toString();
  }
  
  // create bit-vector for string constant
  // str in the form of ##somestr
  private String stringToDefStr(String str) {
    StringBuilder defString = new StringBuilder();

    String binaryStr = strToBinaryStr(str.substring(2));
    defString.append("(define ");
    defString.append(Utils.filterChars(str));
    defString.append("::(bitvector ");
    defString.append(binaryStr.length());
    defString.append(")");
    defString.append(" 0b" + binaryStr);
    defString.append(")\n");
    return defString.toString();
  }
  
  // no need to define any thing for number constant
  private String numberToDefStr(String str) {
    return "";
  }
  
  private String strToBinaryStr(String str) {
    char[] strChar = str.toCharArray();
    StringBuilder result = new StringBuilder();
    for (char aChar : strChar) {
      result.append(Integer.toBinaryString(aChar));
    }
    return result.toString();
  }
  
  private String translateConditions(List<Condition> conditionList, MethodMetaData methData, 
      TranslatedCommand result, boolean keepUnboundedField, boolean retrieveUnsatCore, 
      Hashtable<String, TranslatedInstance> helperVars) {
    
    StringBuilder command = new StringBuilder();
    
    HashSet<String> addedAssertCmds = new HashSet<String>();
    for (Condition condition : conditionList) {
      StringBuilder assertCmd = new StringBuilder();
      assertCmd.append(retrieveUnsatCore ? "(assert+ " : "(assert ");
      assertCmd.append(translateConditionTerms(condition.getConditionTerms(), methData, keepUnboundedField, helperVars));
      assertCmd.append(")\n");
      String assertCmdStr = assertCmd.toString();
      if (assertCmdStr.length() > 0 && !assertCmdStr.contains("%%UnboundField%%")) {
        if (!addedAssertCmds.contains(assertCmdStr)) {
          command.append(assertCmdStr);
          addedAssertCmds.add(assertCmdStr);

          // save an assert command list
          result.assertCmds.add(assertCmdStr);
        }
        
        // save a command and condition mapping
        List<Condition> cmdConditions = result.assertCmdCondsMapping.get(assertCmdStr);
        if (cmdConditions == null) {
          cmdConditions = new ArrayList<Condition>();
          result.assertCmdCondsMapping.put(assertCmdStr, cmdConditions);
        }
        cmdConditions.add(condition);
      }
    }
    return command.toString();
  }
  
  private String translateConditionTerms(List<ConditionTerm> terms, MethodMetaData methData, 
      boolean keepUnboundedField, Hashtable<String, TranslatedInstance> helperVars) {
    
    StringBuilder command = new StringBuilder();
    
    command.append((terms.size() > 1) ? "(or " : "");
    for (int i = 0, size = terms.size(); i < size; i++) {
      ConditionTerm term = terms.get(i);
      TranslatedInstance instance1 = translateInstance(term.getInstance1(), methData, keepUnboundedField, helperVars);
      TranslatedInstance instance2 = translateInstance(term.getInstance2(), methData, keepUnboundedField, helperVars);
      
      // always convert to number
      if (!instance1.m_type.equals("number") && !instance1.m_value.contains("%%UnboundField%%")) {
        TranslatedInstance instance1Num = helperVars.get(instance1.m_value);
        if (instance1Num == null) {
          instance1Num = new TranslatedInstance("$tmp_" + helperVars.size(), "number");
          helperVars.put(instance1.m_value, instance1Num);
        }
        instance1 = instance1Num;
      }
      if (!instance2.m_type.equals("number") && !instance2.m_value.contains("%%UnboundField%%")) {
        TranslatedInstance instance2Num = helperVars.get(instance2.m_value);
        if (instance2Num == null) {
          instance2Num = new TranslatedInstance("$tmp_" + helperVars.size(), "number");
          helperVars.put(instance2.m_value, instance2Num);
        }
        instance2 = instance2Num;
      }
      
      // convert #!0/#!1 to false/true
      String type1 = term.getInstance1().getLastReference() != null ? term.getInstance1().getLastReference().getType() : null;
      String type2 = term.getInstance2().getLastReference() != null ? term.getInstance2().getLastReference().getType() : null;
      if ((type1 != null && type1.equals("Z")) || (type2 != null && type2.equals("Z"))) {
        if (instance1.m_value.equals("0")) {
          instance1.m_value = "false";
        }
        else if (instance1.m_value.equals("1")) {
          instance1.m_value = "true";
        }
        if (instance2.m_value.equals("0")) {
          instance2.m_value = "false";
        }
        else if (instance2.m_value.equals("1")) {
          instance2.m_value = "true";
        }
      }
      
      if (!instance1.m_value.equals("NaN") && !instance2.m_value.equals("NaN")) {
        command.append("(");
        switch (term.getComparator()) {
        case OP_EQUAL:
          command.append("= ");
          break;
        case OP_INEQUAL:
          command.append("/= ");
          break;
        case OP_GREATER:
          command.append("> ");
          break;
        case OP_GREATER_EQUAL:
          command.append(">= ");
          break;
        case OP_SMALLER:
          command.append("< ");
          break;
        case OP_SMALLER_EQUAL:
          command.append("<= ");
          break;
        default:
          command.append("? ");
          break;
        }
        command.append(instance1.m_value);
        command.append(" ");
        command.append(instance2.m_value);
        command.append(")");
      }
      else {
        // boolean expression with NaN (NaN is not equal to everything!)
        command.append((term.getComparator() == Comparator.OP_INEQUAL) ? "true" : "false");
      }
      command.append((i != terms.size() - 1) ? " " : "");
    }
    command.append((terms.size() > 1) ? ")" : "");

    return command.toString();
  }

  
  private TranslatedInstance translateInstance(Instance instance, MethodMetaData methData, 
      boolean keepUnboundedField, Hashtable<String, TranslatedInstance> helperVars) {
    TranslatedInstance transIntance = null;
    
    // if instance is not bound, try to show its last reference name
    if (!instance.isBounded()) {
      String lastRefName = null;
      Reference lastRef = instance.getLastReference();
      if (lastRef != null) {
        Reference paramRef = tryCreateParamReference(methData, lastRef.getLongName());
        if (paramRef != null) {
          String callSites = (lastRef.getCallSites().length() > 0) ? "<" + lastRef.getCallSites() + ">" : "";
          lastRefName = callSites + paramRef.getName();
        }
        else if (keepUnboundedField || lastRef.getDeclaringInstance() == null) {
          lastRefName = lastRef.getLongNameWithCallSites();
        }
        else {
          // since fields could be assigned many times at different time
          // we may not want to compare fields that are not yet bounded
          lastRefName = "%%UnboundField%%";
        }
      }
      transIntance = new TranslatedInstance(lastRefName == null ? "{Unbounded}" : Utils.filterChars(lastRefName), "number");
    }
    else if (instance.isAtomic()) {
      if (instance.getValue().startsWith("##")) {
        transIntance = new TranslatedInstance(Utils.filterChars(instance.getValue()), "number");
      }
      else {
        transIntance = new TranslatedInstance(Utils.filterChars(instance.getValueWithoutPrefix()), "number");
      }
    }
    else {
      TranslatedInstance leftInstance  = translateInstance(instance.getLeft(), methData, keepUnboundedField, helperVars);
      TranslatedInstance rightInstance = translateInstance(instance.getRight(), methData, keepUnboundedField, helperVars);
      
      // watch out for NaN, NaN +-*/ any number is still NaN
      if (leftInstance.m_value.equals("NaN") || rightInstance.m_value.equals("NaN")) {
        transIntance = new TranslatedInstance("NaN", "number");
      }
      else {
        switch (instance.getOp()) {
        case ADD:
        case SUB:
        case MUL:
        case DIV:
        case REM:
          transIntance = translateNumberInstance(leftInstance, instance.getOp(), rightInstance, helperVars);
          break;
        case SHL:
        case SHR:
        case USHR:
        case AND:
        case OR:
        case XOR:
        default:
          transIntance = translateBitVectorInstance(leftInstance, instance.getOp(), rightInstance, helperVars);
          break;
        }
      }
    }
    return transIntance;
  }
  
  private TranslatedInstance translateBitVectorInstance(TranslatedInstance leftInstance, 
      INSTANCE_OP op, TranslatedInstance rightInstance, Hashtable<String, TranslatedInstance> helperVars) {
    
    StringBuilder ret = new StringBuilder();

    // create bit vector helper variables for the bit vector operation
    if (!leftInstance.m_type.equals("bitvector32") && !leftInstance.m_value.contains("%%UnboundField%%")) {
      TranslatedInstance leftInstanceBV = helperVars.get(leftInstance.m_value);
      if (leftInstanceBV == null) {
        leftInstanceBV = new TranslatedInstance("$tmp_" + helperVars.size(), "bitvector32");
        helperVars.put(leftInstance.m_value, leftInstanceBV);
      }
      leftInstance = leftInstanceBV;
    }
    if (op.equals(INSTANCE_OP.AND) || op.equals(INSTANCE_OP.OR) || op.equals(INSTANCE_OP.XOR)) {
      if (!rightInstance.m_type.equals("bitvector32") && !rightInstance.m_value.contains("%%UnboundField%%")) {
        TranslatedInstance rightInstanceBV = helperVars.get(rightInstance.m_value);
        if (rightInstanceBV == null) {
          rightInstanceBV = new TranslatedInstance("$tmp_" + helperVars.size(), "bitvector32");
          helperVars.put(rightInstance.m_value, rightInstanceBV);
        }
        rightInstance = rightInstanceBV;
      }
    }
    else if (!rightInstance.m_type.equals("number") && !rightInstance.m_value.contains("%%UnboundField%%")) {
      TranslatedInstance rightInstanceNum = helperVars.get(rightInstance.m_value);
      if (rightInstanceNum == null) {
        rightInstanceNum = new TranslatedInstance("$tmp_" + helperVars.size(), "number");
        helperVars.put(rightInstance.m_value, rightInstanceNum);
      }
      rightInstance = rightInstanceNum;
    }
    
    ret.append("(");
    switch (op) {
      case SHL: // << in java
        ret.append("bv-shift-left0 ");
        break;
      case SHR: // >> in java
        ret.append("bv-shift-right0 "); //XXX: not correct, but yices don't have the correct one
        break;
      case USHR: // >>> in java
        ret.append("bv-shift-right0 ");
        break;
      case AND:
        ret.append("bv-and ");
        break;
      case OR:
        ret.append("bv-or ");
        break;
      case XOR:
        ret.append("bv-xor ");
        break;  
    }
    ret.append(leftInstance.m_value);
    ret.append(" ");
    ret.append(rightInstance.m_value);
    ret.append(")");
    
    return new TranslatedInstance(ret.toString(), "bitvector32");
  }
  

  private TranslatedInstance translateNumberInstance(TranslatedInstance leftInstance, 
      INSTANCE_OP op, TranslatedInstance rightInstance, Hashtable<String, TranslatedInstance> helperVars) {
    
    StringBuilder ret = new StringBuilder();

    // create number helper variables for the number operation
    if (!leftInstance.m_type.equals("number") && !leftInstance.m_value.contains("%%UnboundField%%")) {
      TranslatedInstance leftInstanceNum = helperVars.get(leftInstance.m_value);
      if (leftInstanceNum == null) {
        leftInstanceNum = new TranslatedInstance("$tmp_" + helperVars.size(), "number");
        helperVars.put(leftInstance.m_value, leftInstanceNum);
      }
      leftInstance = leftInstanceNum;
    }
    if (!rightInstance.m_type.equals("number") && !rightInstance.m_value.contains("%%UnboundField%%")) {
      TranslatedInstance rightInstanceNum = helperVars.get(rightInstance.m_value);
      if (rightInstanceNum == null) {
        rightInstanceNum = new TranslatedInstance("$tmp_" + helperVars.size(), "number");
        helperVars.put(rightInstance.m_value, rightInstanceNum);
      }
      rightInstance = rightInstanceNum;
    }
    
    ret.append("(");
    switch (op) {
      case ADD:
        ret.append("+ ");
        break;
      case SUB:
        ret.append("- ");
        break;
      case MUL:
        ret.append("* ");
        break;
      case DIV:
        ret.append("/ ");
        break;
      case REM:
        ret.append("mod ");
        break;
    }
    ret.append(leftInstance.m_value);
    ret.append(" ");
    ret.append(rightInstance.m_value);
    ret.append(")");
    
    return new TranslatedInstance(ret.toString(), "number");
  }
  
  private Reference tryCreateParamReference(MethodMetaData methData, String refName) {
    Reference paramRef = null;
    String param = methData.getParamStr(refName);
    if (param != null) {
      int index = param.indexOf(')');
      String paramType = param.substring(1, index);
      String paramName = param.substring(index + 1);
      paramRef = new Reference(paramName, paramType, "", new Instance("", null) /* just a dummy */, null);
    }
    return paramRef;
  }
  
  private String numberToBVFunctions() {
    StringBuilder functions = new StringBuilder();
    functions.append("(define $funcBitNum0::(-> (bitvector 1) int) (lambda (bv::(bitvector 1)) (if (= bv 0b1) 1 0)))\n");
    functions.append("(define $funcBitNum1::(-> (bitvector 2) int) (lambda (bv::(bitvector 2)) (+ ($funcBitNum0 (bv-extract 0 0 bv)) (* ($funcBitNum0 (bv-extract 1 1 bv)) 2))))\n");
    functions.append("(define $funcBitNum2::(-> (bitvector 4) int) (lambda (bv::(bitvector 4)) (+ ($funcBitNum1 (bv-extract 1 0 bv)) (* ($funcBitNum1 (bv-extract 3 2 bv)) 4))))\n");
    functions.append("(define $funcBitNum3::(-> (bitvector 8) int) (lambda (bv::(bitvector 8)) (+ ($funcBitNum2 (bv-extract 3 0 bv)) (* ($funcBitNum2 (bv-extract 7 4 bv)) 16))))\n");
    functions.append("(define $funcBitNum4::(-> (bitvector 16) int) (lambda (bv::(bitvector 16)) (+ ($funcBitNum3 (bv-extract 7 0 bv)) (* ($funcBitNum3 (bv-extract 15 8 bv)) 256))))\n");
    functions.append("(define $funcBitNum5::(-> (bitvector 32) int) (lambda (bv::(bitvector 32)) (+ ($funcBitNum4 (bv-extract 15 0 bv)) (* ($funcBitNum4 (bv-extract 31 16 bv)) 65536))))\n");
    functions.append("(define $convertSigned::(-> int int) (lambda (num::int) (if (< num 2147483648) num (+ -4294967296 num))))\n");
    functions.append("(define $numBitSame::(-> (bitvector 32) int bool) (lambda (bv::(bitvector 32) num::int) (if (= ($convertSigned ($funcBitNum5 bv)) num) true false)))\n");
    return functions.toString();
  }

}
