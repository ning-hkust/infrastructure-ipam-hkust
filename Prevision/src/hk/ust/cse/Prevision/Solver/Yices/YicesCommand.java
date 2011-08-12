package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.ICommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

public class YicesCommand implements ICommand {

  @Override
  public String translateToCommand(Formula formula, MethodMetaData methData) {
    Hashtable<String, Hashtable<String, Reference>> refMap = formula.getRefMap();
    
    Hashtable<String, Reference> references = null;
    if (refMap.size() > 0) {
      references = refMap.values().iterator().next();
    }
    else {
      references = new Hashtable<String, Reference>();
    }
    
    StringBuilder command = new StringBuilder();
    command.append(defineTypes(references, formula.getConditionList()));
    command.append(defineVariables(formula.getConditionList(), methData));
    command.append(translateConditions(formula.getConditionList(), methData));
    command.append("(check)\n");
    return command.toString();
  }
  
  private String defineTypes(Hashtable<String, Reference> references, List<Condition> conditionList) {
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
              if (lastRef != null && !def_types.containsKey(lastRef.getType())) {
                def_types.put(lastRef.getType(), "");
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

  private String defineVariables(List<Condition> conditionList, MethodMetaData methData) {
    StringBuilder command = new StringBuilder();
    command.append(defineInstances(conditionList, methData));
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
  
  private String defineInstances(List<Condition> conditionList, MethodMetaData methData) {
    StringBuilder command = new StringBuilder();

    // defines
    HashSet<String> defined = new HashSet<String>();
    for (Condition condition : conditionList) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = new Instance[] {term.getInstance1(), term.getInstance2()};
        for (Instance instance : instances) {
          List<String> defines = translateToDefString(instance, methData);
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
  
  private List<String> translateToDefString(Instance instance, MethodMetaData methData) {
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
      List<String> defStrings1 = translateToDefString(instance.getLeft(), methData);
      List<String> defStrings2 = translateToDefString(instance.getRight(), methData);
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
  
  private String translateConditions(List<Condition> conditionList, MethodMetaData methData) {
    StringBuilder command = new StringBuilder();
    
    HashSet<String> defined = new HashSet<String>();
    for (Condition condition : conditionList) {
      StringBuilder define = new StringBuilder();
      define.append("(assert ");
      define.append(translateConditionTerms(condition.getConditionTerms(), methData));
      define.append(")\n");
      if (define.length() > 0 && !defined.contains(define.toString())) {
        command.append(define);
        defined.add(define.toString());
      }
    }
    return command.toString();
  }
  
  private String translateConditionTerms(List<ConditionTerm> terms, MethodMetaData methData) {
    StringBuilder command = new StringBuilder();
    
    command.append((terms.size() > 1) ? "(or " : "");
    for (int i = 0, size = terms.size(); i < size; i++) {
      ConditionTerm term = terms.get(i);
      String instance1Str = translateInstance(term.getInstance1(), methData);
      String instance2Str = translateInstance(term.getInstance2(), methData);
      
      if (!instance1Str.equals("NaN") && !instance2Str.equals("NaN")) {
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
        command.append(instance1Str);
        command.append(" ");
        command.append(instance2Str);
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

  
  private String translateInstance(Instance instance, MethodMetaData methData) {
    StringBuilder ret = new StringBuilder();
    
    // if instance is not bound, try to show its last reference name
    if (!instance.isBounded()) {
      String lastRefName = null;
      String callSites = null;
      Reference lastRef = instance.getLastReference();
      if (lastRef != null) {
        Reference paramRef = tryCreateParamReference(methData, lastRef.getLongName());
        callSites = (lastRef.getCallSites().length() > 0) ? "<" + lastRef.getCallSites() + ">" : "";
        lastRefName = (paramRef != null) ? callSites + paramRef.getName() : lastRef.getLongNameWithCallSites();
      }
      ret.append(lastRefName == null ? "{Unbounded}" : Utils.filterChars(lastRefName));
    }
    else if (instance.isAtomic()) {
      if (instance.getValue().startsWith("##")) {
        ret.append(Utils.filterChars(instance.getValue()));
      }
      else {
        ret.append(Utils.filterChars(instance.getValueWithoutPrefix()));
      }
    }
    else {
      String leftStr  = translateInstance(instance.getLeft(), methData);
      String rightStr = translateInstance(instance.getRight(), methData);
      
      // watch out for NaN, NaN +-*/ any number is still NaN
      if (leftStr.equals("NaN") || rightStr.equals("NaN")) {
        ret.append("NaN");
      }
      else {
        ret.append("(");
        switch (instance.getOp()) {
        case ADD:
          ret.append("+ ");
          break;
        case AND:
          ret.append("and ");
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
        case OR:
          ret.append("or ");
          break;
        case REM:
          ret.append("mod ");
          break;
        case XOR:
          ret.append("xor ");
          break;
        case SHL:
          ret.append("<< ");
          break;
        case SHR:
          ret.append(">> ");
          break;
        case USHR:
          ret.append(">> ");
          break;
        default:
          ret.append("? "); // unknown op
          break;
        }
        ret.append(leftStr);
        ret.append(" ");
        ret.append(rightStr);
        ret.append(")");
      }
    }
    return ret.toString();
  }
  
  private Reference tryCreateParamReference(MethodMetaData methData, String refName) {
    Reference paramRef = null;
    String param = methData.getParamStr(refName);
    if (param != null) {
      int index = param.indexOf(')');
      String paramType = param.substring(1, index);
      String paramName = param.substring(index + 1);
      paramRef = new Reference(paramName, paramType, "", new Instance() /* just a dummy */, null);
    }
    return paramRef;
  }
}
