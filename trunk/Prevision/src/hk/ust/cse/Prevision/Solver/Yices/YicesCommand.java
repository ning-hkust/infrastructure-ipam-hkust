package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.ICommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

public class YicesCommand implements ICommand {

  private class TranslatedInstance {
    public TranslatedInstance(String value, String type) {
      m_value     = value;
      m_type      = type;
      m_increment = -1;
    }
    
    public int    m_increment;
    public String m_type;
    public String m_value;
  }
  
  @Override
  public TranslatedCommand translateToCommand(Formula formula, 
      MethodMetaData methData, boolean keepUnboundedField, boolean retrieveUnsatCore) {
    
    // avoids too many parameter passing
    m_formula            = formula;
    m_methData           = methData;
    m_keepUnboundedField = keepUnboundedField;
    m_retrieveUnsatCore  = retrieveUnsatCore;
    m_helperVars         = new Hashtable<String, TranslatedInstance>();
    m_result             = new TranslatedCommand();
    
    Hashtable<String, Hashtable<String, Reference>> refMap = formula.getRefMap();
    Hashtable<String, Reference> references = (refMap.size() > 0) ? refMap.values().iterator().next() : 
                                                                    new Hashtable<String, Reference>();
    
    StringBuilder command = new StringBuilder();
    command.append(defineTypes(references));
    command.append(defineInstances());
    String translatedCondStr = translateConditions();
    command.append(defineHelperVariables());
    command.append(defineRelations());
    command.append(assertNumBitSame());
    command.append(translatedCondStr);
    command.append("(check)\n");
    m_result.command = command.toString();
    return m_result;
  }
  
  private String defineTypes(Hashtable<String, Reference> references) {
    StringBuilder command = new StringBuilder();
    
    // add primitive define-type statements
    Hashtable<String, String> basic_types = new Hashtable<String, String>();
    basic_types.put("I", "int");
    basic_types.put("J", "int");
    basic_types.put("S", "int");
    basic_types.put("B", "int");
    basic_types.put("C", "int");
    basic_types.put("D", "real");
    basic_types.put("F", "real");
    basic_types.put("Z", "bool");
    basic_types.put("Unknown-Type", "int");
    basic_types.put("reference", "int");
    
    // add define-type statements from instances in conditions
    Hashtable<String, String> def_types = new Hashtable<String, String>();
    for (Condition condition : m_formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = term.getInstances();
        for (Instance instance : instances) {
          if (!instance.isConstant()) {
            if (instance.isAtomic()) { // FreshInstanceOf(...)
              if (!basic_types.containsKey(instance.getType()) && !def_types.containsKey(instance.getType())) {
                def_types.put(instance.getType(), "");
              }
            }
            else if (!instance.isBounded()) { // Ljava/lang/System.out
              Reference lastRef = instance.getLastReference();
              if (lastRef != null) {
                Reference paramRef = tryCreateParamReference(lastRef.getLongName()); // lastRef may be a parameter
                String varType = paramRef != null ? paramRef.getType() : lastRef.getType();
                if (!basic_types.containsKey(varType) && !def_types.containsKey(varType)) {
                  def_types.put(varType, "");
                }
              }
            }
          }
          else if (instance.getValue().startsWith("##")) { // constant string
            if (!def_types.containsKey("[C")) {
              def_types.put("[C", "");
            }
          }
        }
      }
    }

    // reset Yices
    command.append("(reset)\n");

    // set Yices Option to output a model
    command.append("(set-evidence! true)\n");

    // define basic types
    Enumeration<String> keys = basic_types.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      command.append("(define-type ");
      command.append(Utils.filterChars(key));
      command.append(" ");
      command.append(basic_types.get(key));
      command.append(")\n");
    }
    
    // define other types
    keys = def_types.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      command.append("(define-type ");
      command.append(Utils.filterChars(key));
      command.append(" ");
      command.append("reference)\n");
    }
    
    // define null
    command.append("(define null::int 0)\n");
    
    return command.toString();
  }
  
  private String defineHelperVariables() {
    StringBuilder command = new StringBuilder();

    if (m_helperVars.size() > 0) {
      command.append(numberToBVFunctions());
    }
    
    // for define
    Enumeration<String> keys = m_helperVars.keys();
    while (keys.hasMoreElements()) {
      String translatedInstance = (String) keys.nextElement();
      TranslatedInstance helperInstance = m_helperVars.get(translatedInstance);
      
      boolean isHelperBitVector = helperInstance.m_type.equals("bitvector32");
      command.append("(define ");
      command.append(helperInstance.m_value);
      command.append("::");
      command.append(isHelperBitVector ? "(bitvector 32))\n" : "int)\n");
    }
    
    return command.toString();
  }
  
  private String assertNumBitSame() {
    StringBuilder command = new StringBuilder();
    
    // for assert
    Enumeration<String> keys = m_helperVars.keys();
    while (keys.hasMoreElements()) {
      String translatedInstance = (String) keys.nextElement();
      TranslatedInstance helperInstance = m_helperVars.get(translatedInstance);
      
      boolean isHelperBitVector = helperInstance.m_type.equals("bitvector32");
      command.append("(assert ($numBitSame ");
      command.append(isHelperBitVector ? helperInstance.m_value : translatedInstance);
      command.append(" ");
      command.append(isHelperBitVector ? translatedInstance : helperInstance.m_value);
      command.append("))\n");
    }
    
    return command.toString();
  }

  private String defineInstances() {
    StringBuilder command = new StringBuilder();

    // defines
    HashSet<String> defined = new HashSet<String>();
    for (Condition condition : m_formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = term.getInstances();
        for (Instance instance : instances) {
          List<String> defines = translateToDefString(instance);
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
  
  private String defineRelations() {
    StringBuilder command = new StringBuilder();
    
    Hashtable<String, Relation> relationMap = m_formula.getRelationMap();
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      command.append(defineRelation(relation));
    }
    return command.toString();
  }
  
  private String defineRelation(Relation relation) {
    StringBuilder command = new StringBuilder();
    
    // define relation
    command.append("(define ");
    command.append(relation.getName());
    command.append("::(-> reference ");
    command.append(relation.isArrayRelation() ? "I " : "");
    command.append("reference))\n");
    
    // updates and reads
    List<Long> functionTimes = relation.getFunctionTimes();
    for (int i = functionTimes.size() - 1; i >= 0; i--) {
      Instance[] domainValues = relation.getDomainValues().get(i);
      Instance rangeValue     = relation.getRangeValues().get(i);
      if (rangeValue != null) { // it is an update function
        StringBuilder updateCmd = new StringBuilder();
        updateCmd.append("(define ");
        updateCmd.append(relation.getName());
        updateCmd.append("@");
        updateCmd.append(relation.getFunctionCount() - i);
        updateCmd.append("::(-> reference ");
        updateCmd.append(relation.isArrayRelation() ? "I " : "");
        updateCmd.append("reference) (update ");
        updateCmd.append(relation.getName());
        
        int lastUpdate = relation.getLastUpdateIndex(i);
        updateCmd.append(lastUpdate >= 0 ? ("@" + (relation.getFunctionCount() - lastUpdate)) : "");
        updateCmd.append(" (");
        for (int j = 0; j < domainValues.length; j++) {
          updateCmd.append(translateInstance(domainValues[j]).m_value);
          if (j != domainValues.length - 1) {
            updateCmd.append(" ");
          }
        }
        updateCmd.append(") ");
        updateCmd.append(translateInstance(rangeValue).m_value);
        updateCmd.append("))\n");
        
        String updateCmdStr = updateCmd.toString();
        if (!updateCmdStr.contains("%%UnboundField%%")) {
          command.append(updateCmdStr);
        }
      }
    }
    return command.toString();
  }
  
  private List<String> translateToDefString(Instance instance) {
    List<String> defStrings = new ArrayList<String>();
    
    if (instance.isAtomic()) {
      StringBuilder defString = new StringBuilder();
      if (instance.isConstant()) {
        String value = instance.getValue();
        if (value.startsWith("##")) {
          defString.append(translateStringToDefStr(value));
        }
        else if (value.startsWith("#!")) {
          defString.append(translateNumberToDefStr(value));
        }
      }
      else {
        // should only be FreshInstanceOf(...)
        String value = instance.getValue();
        if (value.startsWith("FreshInstanceOf(")) {
          defString.append(translateFreshToDefStr(instance));
        }
      }
      defStrings.add(defString.toString());
    }
    else if (instance.isBounded() /* not atomic but still bounded */) {
      List<String> defStrings1 = translateToDefString(instance.getLeft());
      List<String> defStrings2 = translateToDefString(instance.getRight());
      defStrings.addAll(defStrings1);
      defStrings.addAll(defStrings2);
    }
    else if (!instance.isBounded()){ // field reference, array read
      // check if it is a constant string field, some of these fields have known values
      int type = isConstStringField(instance);
      if (type >= 0) {
        defStrings.add(constStringFieldToDefStr(instance, type));
      }
      else if (!instance.isRelationRead()) {
        defStrings.add(translateUnboundToDefStr(instance));
      }
    }
    
    // only save if there is an 1:1 matching
    if (defStrings.size() == 1 && defStrings.get(0).length() > 0) {
      int index = defStrings.get(0).indexOf("::");
      if (index >= 0) {
        m_result.nameInstanceMapping.put(defStrings.get(0).substring(8, index), instance);
      }
    }
    
    return defStrings;
  }
  
  // create bit-vector for string constant
  // str in the form of ##somestr
  private String translateStringToDefStr(String str) {
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
  
  private String strToBinaryStr(String str) {
    char[] strChar = str.toCharArray();
    StringBuilder result = new StringBuilder();
    for (char aChar : strChar) {
      result.append(Integer.toBinaryString(aChar));
    }
    return result.toString();
  }
  
  // no need to define any thing for number constant
  private String translateNumberToDefStr(String str) {
    return "";
  }
  
  private String translateFreshToDefStr(Instance instance) {
    StringBuilder defString = new StringBuilder();
    defString.append("(define ");
    defString.append(Utils.filterChars(instance.getValue()));
    defString.append("::");
    defString.append(Utils.filterChars(instance.getType()));
    
    // get the new time
    String freshName = instance.getValue();
    String newTime = freshName.substring(freshName.lastIndexOf('_') + 1, freshName.length() - 1);
    defString.append(" " + newTime + ")\n");
    
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
      defString.append("::[C ");
      defString.append(lastDeclInstance.getSetValueTime());
      defString.append(")\n");
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
  
  private String translateUnboundToDefStr(Instance instance) {
    StringBuilder defString = new StringBuilder();
    Reference lastRef = instance.getLastReference();
    if (lastRef != null) {
      Reference paramRef = tryCreateParamReference(lastRef.getLongName()); // lastRef may be a parameter
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
  
  private String translateConditions() {
    StringBuilder command = new StringBuilder();
    
    HashSet<String> addedAssertCmds = new HashSet<String>();
    for (Condition condition : m_formula.getConditionList()) {
      // translate condition into assert command
      String translated = translateConditionTerms(condition.getConditionTerms());
      if (translated.length() > 0) {
        StringBuilder assertCmd = new StringBuilder();
        assertCmd.append(m_retrieveUnsatCore ? "(assert+ " : "(assert ");
        assertCmd.append(translated);
        assertCmd.append(")\n");
        String assertCmdStr = assertCmd.toString();
        
        // add condition to command
        if (!assertCmdStr.contains("%%UnboundField%%")) {
          if (!addedAssertCmds.contains(assertCmdStr)) {
            command.append(assertCmdStr);
            addedAssertCmds.add(assertCmdStr);

            // save an assert command list
            m_result.assertCmds.add(assertCmdStr);
          }
          
          // save a command and condition mapping
          List<Condition> cmdConditions = m_result.assertCmdCondsMapping.get(assertCmdStr);
          if (cmdConditions == null) {
            cmdConditions = new ArrayList<Condition>();
            m_result.assertCmdCondsMapping.put(assertCmdStr, cmdConditions);
          }
          cmdConditions.add(condition);
        }
      }
    }
    return command.toString();
  }
  
  private String translateConditionTerms(List<ConditionTerm> terms) {
    StringBuilder command = new StringBuilder();
    
    command.append((terms.size() > 1) ? "(or " : "");
    for (int i = 0, size = terms.size(); i < size; i++) {
      ConditionTerm term = terms.get(i);
      if (term instanceof BinaryConditionTerm) { // only translates binary condition terms
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
        TranslatedInstance instance1 = translateInstance(binaryTerm.getInstance1());
        TranslatedInstance instance2 = translateInstance(binaryTerm.getInstance2());
        
        // always convert to number
        instance1 = makeHelperWhenNecessary(instance1, "number");
        instance2 = makeHelperWhenNecessary(instance2, "number");
        
        // convert #!0/#!1 to false/true
        convertTrueFalse(binaryTerm, instance1, instance2);
        
        if (!instance1.m_value.equals("NaN") && !instance2.m_value.equals("NaN")) {
          command.append("(");
          switch (binaryTerm.getComparator()) {
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
          command.append((binaryTerm.getComparator() == Comparator.OP_INEQUAL) ? "true" : "false");
        }
      }
      else { // for type condition term
        command.append("true");
      }
      command.append((i != terms.size() - 1) ? " " : "");
    }
    command.append((terms.size() > 1) ? ")" : "");
    return command.toString();
  }

  
  private TranslatedInstance translateInstance(Instance instance) {
    TranslatedInstance transIntance = null;
    
    // if instance is not bound, try to show its last reference name
    if (!instance.isBounded()) {
      Reference lastRef = instance.getLastReference();
      if (lastRef != null) {
        if (instance.isRelationRead()) { // read from a relation
          StringBuilder readRelation = new StringBuilder();
          
          String readStr    = instance.getLastReference().getName();
          Relation relation = m_formula.getRelation(readStr);
          readRelation.append("(");
          readRelation.append(relation.getName());

          int readIndex = relation.getIndex(relation.getReadStringTime(readStr));
          int lastUpdate = relation.getLastUpdateIndex(readIndex);
          readRelation.append(lastUpdate >= 0 ? ("@" + (relation.getFunctionCount() - lastUpdate)) : "");
          readRelation.append(" ");

          Instance[] domainValues = relation.getDomainValues().get(readIndex);
          for (int i = 0; i < domainValues.length; i++) {
            TranslatedInstance domainInstance = translateInstance(domainValues[i]);
            if (relation.isArrayRelation() && i == 1) { 
              domainInstance = makeHelperWhenNecessary(domainInstance, "number"); // the index should always be number
            }
            readRelation.append(domainInstance.m_value);

            if (i != domainValues.length - 1) {
              readRelation.append(" ");
            }
          }
          readRelation.append(")");
          transIntance = new TranslatedInstance(readRelation.toString(), "number");
        }
        else {
          String lastRefName = null;
          Reference paramRef = tryCreateParamReference(lastRef.getLongName());
          if (paramRef != null) {
            String callSites = (lastRef.getCallSites().length() > 0) ? "<" + lastRef.getCallSites() + ">" : "";
            lastRefName = callSites + paramRef.getName();
          }
          else if (m_keepUnboundedField || lastRef.getDeclaringInstance() == null) {
            lastRefName = lastRef.getLongNameWithCallSites();
          }
          else {
            // since fields could be assigned many times at different time
            // we may not want to compare fields that are not yet bounded
            lastRefName = "%%UnboundField%%";
          }
          transIntance = new TranslatedInstance(Utils.filterChars(lastRefName), "number");
        }
      }
      else {
        transIntance = new TranslatedInstance("{Unbounded}", "number");
      }
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
      TranslatedInstance leftInstance  = translateInstance(instance.getLeft());
      TranslatedInstance rightInstance = translateInstance(instance.getRight());
      
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
          transIntance = translateNumberInstance(leftInstance, instance.getOp(), rightInstance);
          break;
        case SHL:
        case SHR:
        case USHR:
        case AND:
        case OR:
        case XOR:
        default:
          transIntance = translateBitVectorInstance(leftInstance, instance.getOp(), rightInstance);
          break;
        }
      }
    }
    return transIntance;
  }
  
  private TranslatedInstance translateBitVectorInstance(TranslatedInstance leftInstance, 
      INSTANCE_OP op, TranslatedInstance rightInstance) {
    
    StringBuilder ret = new StringBuilder();

    // create bit vector helper variables for the bit vector operation
    leftInstance  = makeHelperWhenNecessary(leftInstance, "bitvector32");
    rightInstance = makeHelperWhenNecessary(rightInstance, (op.equals(INSTANCE_OP.AND) || 
        op.equals(INSTANCE_OP.OR) || op.equals(INSTANCE_OP.XOR)) ? "bitvector32" : "number");
    
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
      INSTANCE_OP op, TranslatedInstance rightInstance) {

    // create number helper variables for the number operation
    leftInstance  = makeHelperWhenNecessary(leftInstance, "number");
    rightInstance = makeHelperWhenNecessary(rightInstance, "number");

    TranslatedInstance translated = null;
    if (op == INSTANCE_OP.ADD && rightInstance.m_value.equals("1")) {
      if (leftInstance.m_increment > 0) {
        String cut = leftInstance.m_value.substring(0, leftInstance.m_value.lastIndexOf(' '));
        translated = new TranslatedInstance(cut + " " + (leftInstance.m_increment + 1) + ")", "number");
      }
      else {
        translated = new TranslatedInstance("(+ " + leftInstance.m_value + " 1)", "number");
      }
      translated.m_increment = leftInstance.m_increment > 0 ? leftInstance.m_increment + 1 : 1;
    }
    else {
      StringBuilder ret = new StringBuilder();
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
      translated = new TranslatedInstance(ret.toString(), "number");
    }
    return translated;
  }
  
  private Reference tryCreateParamReference(String refName) {
    Reference paramRef = null;
    String param = m_methData.getParamStr(refName);
    if (param != null) {
      int index = param.indexOf(')');
      String paramType = param.substring(1, index);
      String paramName = param.substring(index + 1);
      paramRef = new Reference(paramName, paramType, "", new Instance("", null) /* just a dummy */, null);
    }
    return paramRef;
  }
  
  private TranslatedInstance makeHelperWhenNecessary(TranslatedInstance instance, String destType) {
    
    if (!instance.m_type.equals(destType) && !instance.m_value.contains("%%UnboundField%%")) {
      TranslatedInstance instance1Helper = m_helperVars.get(instance.m_value);
      if (instance1Helper == null) {
        instance1Helper = new TranslatedInstance("$tmp_" + m_helperVars.size(), destType);
        m_helperVars.put(instance.m_value, instance1Helper);
      }
      instance = instance1Helper;
    }
    return instance;
  }
  
  private void convertTrueFalse(BinaryConditionTerm term, TranslatedInstance instance1, TranslatedInstance instance2) {
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
  
  private Formula                               m_formula;
  private MethodMetaData                        m_methData;
  private boolean                               m_keepUnboundedField;
  private boolean                               m_retrieveUnsatCore;
  private Hashtable<String, TranslatedInstance> m_helperVars;
  private TranslatedCommand                     m_result;

}
