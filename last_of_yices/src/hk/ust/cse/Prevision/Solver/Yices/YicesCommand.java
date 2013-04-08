package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.AndConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.Solver.ICommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesCommand implements ICommand {

  private class TranslatedInstance {
    public TranslatedInstance(String value, String type) {
      m_value     = value;
      m_type      = type;
      m_increment = -1;
    }
    
    public int    m_increment;
    public String m_type; /* bitvector32 or number */
    public String m_value;
  }
  
  @Override
  public String getCheckCommand() {
    return "(check)\n";
  }
  
  @Override
  public TranslatedCommand translateToCommand(Formula formula, boolean keepUnboundedField, boolean retrieveUnsatCore) {
    
    // avoids too many parameter passing
    m_formula            = formula;
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
    String defineRelations = defineRelations();
    addCommonContracts();
    String translatedCondStr = translateConditions();
    command.append(defineHelperVariables());
    command.append(defineRelations);
    command.append(assertNumBitSame());
    command.append(translatedCondStr);
    command.append("(check)\n");
    m_result.command = command.toString();
    m_result.typeRanges = m_typeRanges;
    return m_result;
  }
  
  private String defineTypes(Hashtable<String, Reference> references) {
    StringBuilder command = new StringBuilder();

    long[] byteRange   = new long[] {(long) Byte.MIN_VALUE, (long) Byte.MAX_VALUE};
    long[] charRange   = new long[] {(long) Character.MIN_VALUE, (long) Character.MAX_VALUE};
    long[] intRange    = new long[] {(long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE};
    long[] floatRange  = new long[] {(long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE};
    long[] doubleRange = new long[] {(long) Integer.MIN_VALUE * 10, (long) Integer.MAX_VALUE * 10};
    
    Long nullRef = doubleRange[1] + 1;
    Long minRef  = nullRef + 1;
    Long maxRef  = minRef * 10;
    long[] refRange1 = new long[] {nullRef, maxRef};
    long[] refRange2 = new long[] {minRef, maxRef};
    long[] fullRange = new long[] {doubleRange[0], maxRef};
    
    // add primitive define-type statements
    Hashtable<String, String> basic_types = new Hashtable<String, String>();
    String byteRangeStr = "(subrange " + String.valueOf(byteRange[0]) + " " + String.valueOf(byteRange[1]) + ")";
    String charRangeStr = "(subrange " + String.valueOf(charRange[0]) + " " + String.valueOf(charRange[1]) + ")";
    String intRangeStr  = "(subrange " + String.valueOf(intRange[0]) + " " + String.valueOf(intRange[1]) + ")";
    String refRange1Str = "(subrange " + String.valueOf(refRange1[0]) + " " + String.valueOf(refRange1[1]) + ")";
    String refRange2Str = "(subrange " + String.valueOf(refRange2[0]) + " " + String.valueOf(refRange2[1]) + ")";
    String fullRangeStr = "(subrange " + String.valueOf(fullRange[0]) + " " + String.valueOf(fullRange[1]) + ")";
    String boolRangeStr = "(subrange 0 1)";
    
    // cannot use subrange as it is a syntax sugar for int only
    String floatRangeStr  = "(subtype (n::real) (and (>= n " + String.valueOf(floatRange[0]) + ") (<= n " + 
                                                               String.valueOf(floatRange[1]) + ")))";
    String doubleRangeStr = "(subtype (n::real) (and (>= n " + String.valueOf(doubleRange[0]) + ") (<= n " + 
                                                               String.valueOf(doubleRange[1]) + ")))";
    
    basic_types.put("I", intRangeStr);
    basic_types.put("J", intRangeStr);
    basic_types.put("S", intRangeStr);
    basic_types.put("B", byteRangeStr);
    basic_types.put("C", charRangeStr);
    basic_types.put("D", doubleRangeStr);
    basic_types.put("F", floatRangeStr);
    basic_types.put("Z", boolRangeStr);
    basic_types.put("Unknown-Type", fullRangeStr);
    basic_types.put("reference", refRange1Str);
    basic_types.put("not_null_reference", refRange2Str);
    
    // add define-type statements from instances in conditions
    Hashtable<String, String> def_types = new Hashtable<String, String>();
    for (Condition condition : m_formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = term.getInstances();
        for (Instance instance : instances) {
          defineType(instance, basic_types, def_types);
        }
        if (term instanceof TypeConditionTerm) {
          String typeName = ((TypeConditionTerm) term).getTypeString();
          if (!basic_types.containsKey(typeName) && !def_types.containsKey(typeName)) {
            def_types.put(typeName, "");
          }
        }
      }
    }
    
    // add define-type statements from instances in relations
    Hashtable<String, Relation> relationMap = m_formula.getRelationMap();
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      for (int i = 0; i < relation.getFunctionCount(); i++) {
        Instance[] domainValues = relation.getDomainValues().get(i);
        Instance rangeValue     = relation.getRangeValues().get(i);
        
        List<Instance> instances = new ArrayList<Instance>();
        Collections.addAll(instances, domainValues);
        if (rangeValue != null) {
          instances.add(rangeValue);
        }
        for (Instance instance : instances) {
          defineType(instance, basic_types, def_types);
        }
      }
    }
    
    // compute type ranges for non-primitive types
    m_typeRanges = computeTypeRanges(def_types, minRef, maxRef);
    
    // add the type ranges for primitive types
    List<long[]> intRangeList    = new ArrayList<long[]>();
    List<long[]> floatRangeList  = new ArrayList<long[]>();
    List<long[]> doubleRangeList = new ArrayList<long[]>();
    List<long[]> fullRangeList   = new ArrayList<long[]>();
    List<long[]> refRange1List   = new ArrayList<long[]>();
    List<long[]> refRange2List   = new ArrayList<long[]>();
    List<long[]> boolRangeList   = new ArrayList<long[]>();
    intRangeList.add(intRange);
    floatRangeList.add(floatRange);
    doubleRangeList.add(doubleRange);
    fullRangeList.add(fullRange);
    refRange1List.add(refRange1);
    refRange2List.add(refRange2);
    boolRangeList.add(new long[] {0, 1});
    m_typeRanges.put("I", intRangeList);
    m_typeRanges.put("J", intRangeList);
    m_typeRanges.put("S", intRangeList);
    m_typeRanges.put("B", intRangeList);
    m_typeRanges.put("C", intRangeList);
    m_typeRanges.put("D", doubleRangeList);
    m_typeRanges.put("F", floatRangeList);
    m_typeRanges.put("Z", boolRangeList);
    
    // reset Yices
    command.append("(reset)\n");

    // set Yices Option to output a model
    command.append("(set-evidence! true)\n");

    // define basic types
    keys = basic_types.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      command.append("(define-type ");
      command.append(YicesUtils.filterChars(key));
      command.append(" ");
      command.append(basic_types.get(key));
      command.append(")\n");
    }
    
    // define other types
    keys = def_types.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      List<long[]> ranges = m_typeRanges.get(key);
      
      command.append("(define-type ");
      command.append(YicesUtils.filterChars(key));
      command.append(" (subtype (n::int) (or ");
      for (long[] range : ranges) {
        command.append("(and (>= n ");
        command.append(range[0]);
        command.append(") (<= n ");
        command.append(range[1]);
        command.append(")) ");
      }
      command.append("(= n ");
      command.append(nullRef);
      command.append("))))\n");
    }
    
    // define null
    command.append("(define null::int ").append(nullRef).append(")\n");
    
    return command.toString();
  }
  
  private void defineType(Instance instance, Hashtable<String, String> basic_types, Hashtable<String, String> def_types) {
    if (!instance.isConstant()) {
      if (instance.isAtomic()) { // FreshInstanceOf(...)
        if (!basic_types.containsKey(instance.getType()) && !def_types.containsKey(instance.getType())) {
          def_types.put(instance.getType(), "");
        }
      }
      else if (!instance.isBounded()) { // e.g. Ljava/lang/System.out
        Reference lastRef = instance.getLastReference();
        if (lastRef != null) {
          String varType = lastRef.getType();
          if (!basic_types.containsKey(varType) && !def_types.containsKey(varType)) {
            def_types.put(varType, "");
          }
          
          // for "__instanceof__" field: v2.__instanceof__Lorg/apache/tools/ant/Task
          String lastRefName = lastRef.getName();
          if (lastRefName.contains("__instanceof__")) {
            String typeName = null;
            int index = lastRefName.indexOf("__instanceof__") + 14;
            if (lastRefName.startsWith("read___instanceof__")) { // read___instanceof__[B_2247616843578211
              int index2 = lastRefName.lastIndexOf("_");
              typeName = lastRefName.substring(index, index2);
            }
            else {
              typeName = lastRefName.substring(index);
            }
            if (!basic_types.containsKey(typeName) && !def_types.containsKey(typeName)) {
              def_types.put(typeName, "");
            }
            
            // we also need to define v2's type
            Instance declInstance = lastRef.getDeclaringInstance();
            if (declInstance != null) {
              defineType(declInstance, basic_types, def_types);
            }
          }
          if (isConstStringField(instance) >= 0) {
            if (!def_types.containsKey("Ljava/lang/String")) {
              def_types.put("Ljava/lang/String", "");
            }
            if (!def_types.containsKey("[C")) {
              def_types.put("[C", "");
            }
          }
        }
      }
    }
    else if (instance.getValue().startsWith("##")) { // constant string
      if (!def_types.containsKey("Ljava/lang/String")) {
        def_types.put("Ljava/lang/String", "");
      }
      if (!def_types.containsKey("[C")) {
        def_types.put("[C", "");
      }
    }
  }
  
  private Hashtable<String, List<long[]>> computeTypeRanges(Hashtable<String, String> def_types, long min, long max) {
    Hashtable<String, List<long[]>> ranges = new Hashtable<String, List<long[]>>();
    
    if (def_types.size() > 0) {
      // obtain subClass information
      List<String> allClasses = new ArrayList<String>();
      final Hashtable<String, List<String>> subClassMap = new Hashtable<String, List<String>>();
      Enumeration<String> keys = def_types.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        allClasses.add(key);
        List<String> subClasses = new ArrayList<String>();
        subClassMap.put(key, subClasses);
        
        Enumeration<String> keys2 = def_types.keys();
        while (keys2.hasMoreElements()) {
          String key2 = (String) keys2.nextElement();
          if (key != key2 && Utils.canCastTo(key, key2)) {
            subClasses.add(key2);
          }
        }
      }
      
      // sort such that super class is at front
      List<String> sorted = new ArrayList<String>();
      for (String clazz : allClasses) {
        int insertAt = sorted.size();
        for (int i = 0, size = sorted.size(); i < size && insertAt == size; i++) {
          insertAt = subClassMap.get(clazz).contains(sorted.get(i)) ? i : insertAt;
        }
        sorted.add(insertAt, clazz);
      }
      
      long current = min;
      long splitSize = (max - min) / (long) def_types.size();
      for (String className : sorted) {
        List<long[]> subRanges = new ArrayList<long[]>();
        subRanges.add(new long[] {current, (current += splitSize) - 1});
        ranges.put(className, subRanges);
      }

      keys = ranges.keys();
      while (keys.hasMoreElements()) {
        String key = (String) keys.nextElement();
        List<long[]> subRanges = ranges.get(key);
        Enumeration<String> keys2 = ranges.keys();
        while (keys2.hasMoreElements()) {
          String key2 = (String) keys2.nextElement();
          List<long[]> otherSubRanges = ranges.get(key2);
          if (subRanges != otherSubRanges && subClassMap.get(key).contains(key2)) {
            subRanges.add(otherSubRanges.get(0));
          }
        }
      }
    }
    return ranges;
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

    // define instances appear in conditions
    HashSet<String> definedNames = new HashSet<String>();
    for (Condition condition : m_formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        List<Instance> instances = new ArrayList<Instance>(Arrays.asList(term.getInstances()));
        
        // make sure constant strings get define
        for (int i = 0; i < instances.size(); i++) {
          Instance instance = instances.get(i);
          if (isConstStringField(instance) >= 0) {
            instances.add(0, instance.getToppestInstance());
            i++;
          }
        }
        
        for (Instance instance : instances) {
          List<String> defines = translateToDefString(instance);
          for (String define : defines) {
            if (define.length() > 0) {
              String defineName = define.substring(0, define.indexOf("::"));
              if (!definedNames.contains(defineName)) {
                command.append(define);
                definedNames.add(defineName);
              }
            }
          }
        }
      }
    }
    
    // define instances appear in relations
    Hashtable<String, Relation> relationMap = m_formula.getRelationMap();
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      for (int i = 0; i < relation.getFunctionCount(); i++) {
        Instance[] domainValues = relation.getDomainValues().get(i);
        Instance rangeValue     = relation.getRangeValues().get(i);
        
        List<Instance> instances = new ArrayList<Instance>();
        Collections.addAll(instances, domainValues);
        if (rangeValue != null) {
          instances.add(rangeValue);
        }
        // make sure constant strings get define
        for (int j = 0; j < instances.size(); j++) {
          Instance instance = instances.get(j);
          if (isConstStringField(instance) >= 0) {
            instances.add(0, instance.getToppestInstance());
            j++;
          }
        }
        
        for (Instance instance : instances) {
          List<String> defines = translateToDefString(instance);
          for (String define : defines) {
            if (define.length() > 0) {
              String defineName = define.substring(0, define.indexOf("::"));
              if (!definedNames.contains(defineName)) {
                command.append(define);
                definedNames.add(defineName);
              }
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
    
    // obtain all relation update define commands
    List<String> defineUpdateCmds = new ArrayList<String>();
    final Hashtable<String, HashSet<String>> dependBy = new Hashtable<String, HashSet<String>>();
    keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      defineRelationUpdates(relation, defineUpdateCmds, dependBy);
    }
    
    // sort by dependencies
    boolean changed = true;
    List<String> sortedUpdateCmds = defineUpdateCmds;
    for (int i = 0; i < 10 /* avoid any possible endless loop */ && changed; i++) {
      defineUpdateCmds = sortedUpdateCmds;
      sortedUpdateCmds = new ArrayList<String>();
      for (String defineUpdateCmd : defineUpdateCmds) {
        String relName = defineUpdateCmd.substring(8, defineUpdateCmd.indexOf("::"));
        HashSet<String> dependBySet = dependBy.get(relName);
        int insertAt = sortedUpdateCmds.size();
        for (int j = 0; j < sortedUpdateCmds.size() && dependBySet != null; j++) {
          String sortedUpdateCmd = sortedUpdateCmds.get(j);
          String relName2 = sortedUpdateCmd.substring(8, sortedUpdateCmd.indexOf("::"));
          if (dependBySet.contains(relName2)) {
            insertAt = j;
            break;
          }
        }
        sortedUpdateCmds.add(insertAt, defineUpdateCmd);
      }
      changed = !defineUpdateCmds.equals(sortedUpdateCmds);
    }

    for (String sortedUpdateCmd : sortedUpdateCmds) {
      command.append(sortedUpdateCmd);
    }
    
    return command.toString();
  }
  
  private String defineRelation(Relation relation) {
    StringBuilder command = new StringBuilder();
    
    // define relation
    command.append("(define ");
    command.append(YicesUtils.filterChars(relation.getName()));
    command.append("::(-> not_null_reference ");
    command.append(relation.isArrayRelation() ? "I " : "");
    command.append("Unknown-Type))\n");
    return command.toString();
  }
  
  private static final Pattern s_pattern = Pattern.compile("\\(([\\S]+@[0-9]+) "); // (@@array@1 
  private void defineRelationUpdates(Relation relation, List<String> defineUpdateCmds, Hashtable<String, HashSet<String>> dependBy) {
    // updates and reads
    String relationName = YicesUtils.filterChars(relation.getName());
    
    // adjust order such that, constant string values are always update first
    if (relationName.equals("@@array")) {
      for (int i = 0; i < relation.getFunctionCount(); i++) {
        if (relation.isUpdate(i)) {
          Instance domain = relation.getDomainValues().get(i)[0];
          if (domain.getLastReference() != null && domain.getLastRefName().equals("value") && 
              domain.getLastReference().getDeclaringInstance() != null && 
              domain.getLastReference().getDeclaringInstance().isConstant()) {
            relation.move(i, 0);
            relation.getFunctionTimes().set(0, Long.MIN_VALUE);
          }
        }
      }
    }
    
    int maxUpdates = Math.min(relation.getFunctionCount(), 256); // avoid JVM crash
    for (int i = 0; i < maxUpdates; i++) {
      Instance[] domainValues = relation.getDomainValues().get(i);
      Instance rangeValue     = relation.getRangeValues().get(i);
      if (rangeValue != null) { // it is an update function
        StringBuilder updateCmd = new StringBuilder();
        updateCmd.append("(define ");
        updateCmd.append(relationName);
        updateCmd.append("@");
        updateCmd.append(i + 1);
        updateCmd.append("::(-> not_null_reference ");
        updateCmd.append(relation.isArrayRelation() ? "I " : "");
        updateCmd.append("Unknown-Type) (update ");
        updateCmd.append(relationName);
        
        int lastUpdate = relation.getLastUpdateIndex(i);
        updateCmd.append(lastUpdate >= 0 ? ("@" + (lastUpdate + 1)) : "");
        updateCmd.append(" (");
        for (int j = 0; j < domainValues.length; j++) {
          if (relation.isArrayRelation() && j == 1) {
            TranslatedInstance indexInstance = translateInstance(domainValues[j]);
            indexInstance = makeHelperWhenNecessary(indexInstance, "number"); // the index should always be number
            updateCmd.append(indexInstance.m_value);
          }
          else {
            updateCmd.append(translateInstance(domainValues[j]).m_value);
          }
          
          if (j != domainValues.length - 1) {
            updateCmd.append(" ");
          }
        }
        updateCmd.append(") ");
        updateCmd.append(translateInstance(rangeValue).m_value);
        updateCmd.append("))\n");
        
        String updateCmdStr = updateCmd.toString();
        if (!updateCmdStr.contains("%%UnboundField%%")) {
          defineUpdateCmds.add(updateCmdStr);
          
          // find dependents
          Matcher matcher = s_pattern.matcher(updateCmdStr);
          while (matcher.find()) {
            String dependUpdate = matcher.group(1);
            HashSet<String> dependBySet = dependBy.get(dependUpdate);
            if (dependBySet == null) {
              dependBySet = new HashSet<String>();
              dependBy.put(dependUpdate, dependBySet);
            }
            dependBySet.add(relationName + "@" + (i + 1));
          }
        }
        
        // also depend on the relation to update
        if (lastUpdate >= 0) {
          String updateTo = relationName + "@" + (lastUpdate + 1);
          HashSet<String> dependBySet = dependBy.get(updateTo);
          if (dependBySet == null) {
            dependBySet = new HashSet<String>();
            dependBy.put(updateTo, dependBySet);
          }
          dependBySet.add(relationName + "@" + (i + 1));
        }
      }
    }
  }
  
  private List<String> translateToDefString(Instance instance) {
    List<String> defStrings = new ArrayList<String>();
    
    boolean justDefStr = false;
    if (instance.isAtomic()) {
      StringBuilder defString = new StringBuilder();
      if (instance.isConstant()) {
        String value = instance.getValue();
        if (value.startsWith("##")) {
          defString.append(translateStringToDefStr(instance));
          justDefStr = defString.length() > 0;
        }
        else if (value.startsWith("#!")) {
          defString.append(translateNumberToDefStr(instance));
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
        //defStrings.add(constStringFieldToDefStr(instance, type));
      }
      else if (!instance.isRelationRead()) {
        if (instance.getLastReference() != null && instance.getLastRefName().contains("__instanceof__")) {
          instance = instance.getLastReference().getDeclaringInstance();
        }
        defStrings.add(translateUnboundToDefStr(instance));
      }
    }
    
    // only save if there is an 1:1 matching (can never have 1:1 matching for left op right)
    if (defStrings.size() == 1 && defStrings.get(0).length() > 0 && (!instance.isBounded() || instance.isAtomic())) {
      int index = defStrings.get(0).indexOf("::");
      if (index >= 0) {
        String name = defStrings.get(0).substring(8, index);
        m_result.nameInstanceMapping.put(name, instance);
        m_result.instanceNameMapping.put(instance, name);
      }
    }
    
    // define constant string's fields
    if (justDefStr) {
      defStrings.addAll(translateStringFieldsToDefStr(instance));
    }
    
    return defStrings;
  }
  
  // create bit-vector for string constant
  // str in the form of ##somestr
  private String translateStringToDefStr(Instance instance) {
    StringBuilder defString = new StringBuilder();

    // avoid multiple define
    boolean alreadyDefined = false;
    Enumeration<String> keys = m_result.constInstanceMapping.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Instance instance2 = m_result.constInstanceMapping.get(key);
      if (instance.getValue().equals(instance2.getValue())) {
        alreadyDefined = true;
        break;
      }
    }
    
    if (!alreadyDefined) {
      String strType = "Ljava/lang/String";
      defString.append("(define ");
      defString.append(YicesUtils.filterChars(instance.getValue()));
      defString.append("::");
      defString.append(YicesUtils.filterChars(strType));
      
      // get the new value
      long[] range = m_typeRanges.get(strType).get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);
      
      defString.append(" " + defValue + ")\n");
      
      // save the constant value
      m_result.constInstanceMapping.put(String.valueOf(defValue), instance);
    }
    
    return defString.toString();
  }
  
  private List<String> translateStringFieldsToDefStr(Instance instance) {
    List<String> defStrings = new ArrayList<String>();

    // define constant string count
    StringBuilder defString = new StringBuilder();
    defString.append("(define ");
    defString.append(YicesUtils.filterChars(instance.getValue() + ".count"));
    defString.append("::I ");
    defString.append(instance.getValue().length() - 2);
    defString.append(")\n");
    defStrings.add(defString.toString());
    
    // define constant string offset
    defString = new StringBuilder();
    defString.append("(define ");
    defString.append(YicesUtils.filterChars(instance.getValue() + ".offset"));
    defString.append("::I 0)\n");
    defStrings.add(defString.toString());
    
    // define constant string value not null
    if (m_typeRanges.containsKey("[C")) {
      // define constant string value not null
      defString = new StringBuilder();
      defString.append("(define ");
      defString.append(YicesUtils.filterChars(instance.getValue() + ".value"));
      defString.append("::[C ");
      
      // get the new value
      long[] range = m_typeRanges.get("[C").get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);
      defString.append(defValue + ")\n");
      defStrings.add(defString.toString());

      // define constant string value.length
      defString = new StringBuilder();
      defString.append("(define ");
      defString.append(YicesUtils.filterChars(instance.getValue() + ".value.length"));
      defString.append("::I ");
      defString.append(instance.getValue().length() - 2);
      defString.append(")\n");
      defStrings.add(defString.toString());
    }
    
    return defStrings;
  }
  
  // no need to define any thing for number constant
  private String translateNumberToDefStr(Instance instance) {
    return "";
  }
  
  private String translateFreshToDefStr(Instance instance) {
    StringBuilder defString = new StringBuilder();
    
    // avoid multiple define
    boolean alreadyDefined = false;
    Enumeration<String> keys = m_result.constInstanceMapping.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Instance instance2 = m_result.constInstanceMapping.get(key);
      if (instance.getValue().equals(instance2.getValue())) {
        alreadyDefined = true;
        break;
      }
    }
    
    if (!alreadyDefined) {
      defString.append("(define ");
      defString.append(YicesUtils.filterChars(instance.getValue()));
      defString.append("::");
      defString.append(YicesUtils.filterChars(instance.getType()));
   
      // get the new value
      long[] range = m_typeRanges.get(instance.getType()).get(0);
      long half = (long) (0.5 * (range[1] - range[0]));
      long defValue = range[0] + half + (long) (Math.random() * half);
      
      defString.append(" " + defValue + ")\n");
      
      // save the constant value
      m_result.constInstanceMapping.put(String.valueOf(defValue), instance);
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
  
//  private String constStringFieldToDefStr(Instance instance, int type) {
//    StringBuilder defString = new StringBuilder();
//    
//    Reference lastRef = instance.getLastReference();
//    Instance lastDeclInstance = (lastRef != null) ? lastRef.getDeclaringInstance() : null;
//    switch (type) {
//    case 0:
//      defString.append("(define ");
//      defString.append(YicesUtils.filterChars(lastDeclInstance.getValue() + ".count"));
//      defString.append("::I ");
//      defString.append(lastDeclInstance.getValue().length() - 2);
//      defString.append(")\n");
//      break;
//    case 1:
//      defString.append("(define ");
//      defString.append(YicesUtils.filterChars(lastDeclInstance.getValue() + ".value"));
//      defString.append("::[C ");
//      defString.append(lastDeclInstance.getSetValueTime());
//      defString.append(")\n");
//      break;
//    case 2:
//      Reference lastLastRef = (lastDeclInstance != null) ? lastDeclInstance.getLastReference() : null;
//      Instance lastLastDeclInstance = (lastLastRef != null) ? lastLastRef.getDeclaringInstance() : null;
//      defString.append("(define ");
//      defString.append(YicesUtils.filterChars(lastLastDeclInstance.getValue() + ".value.length"));
//      defString.append("::I ");
//      defString.append(lastLastDeclInstance.getValue().length() - 2);
//      defString.append(")\n");
//      break;
//    default:
//      break;
//    }
//    return defString.toString();
//  }
  
  private String translateUnboundToDefStr(Instance instance) {
    StringBuilder defString = new StringBuilder();
    Reference lastRef = instance.getLastReference();
    if (lastRef != null) {
      //String callSites = (lastRef.getCallSites().length() > 0) ? "<" + lastRef.getCallSites() + ">" : "";
      String varName = lastRef.getLongNameWithCallSites();
      String varType = lastRef.getType();
      defString.append("(define ");
      defString.append(YicesUtils.filterChars(varName)); // instance may be from inner method
      defString.append("::");
      defString.append(YicesUtils.filterChars(varType));
      defString.append(")\n");
    }
    return defString.toString();
  }

  @Override
  public String translateToCommand(Condition condition, boolean keepUnboundedField, boolean retrieveUnsatCore) {
    // avoids too many parameter passing
    m_keepUnboundedField = keepUnboundedField;
    m_retrieveUnsatCore  = retrieveUnsatCore;
    
    // translate condition into assert command
    StringBuilder assertCmd = new StringBuilder();
    String translated = translateConditionTerms(condition.getConditionTerms(), "or");
    if (translated.length() > 0) {
      assertCmd.append(m_retrieveUnsatCore ? "(assert+ " : "(assert ");
      assertCmd.append(translated);
      assertCmd.append(")\n");
    }
    return assertCmd.toString();
  }
  
  private void addCommonContracts() {
    List<Condition> newContracts = new ArrayList<Condition>();

    Instance zero = new Instance("#!0", "I", null);
    HashSet<Instance> added = new HashSet<Instance>();
    for (Condition condition : m_formula.getConditionList()) {
      HashSet<Instance> instances = condition.getRelatedInstances(m_formula, false, true);
      for (Instance instance : instances) {
        String fieldName = null;
        if (instance.isRelationRead()) {
          fieldName = Relation.getReadStringRelName(instance.toString()).toLowerCase();
        }
        else if (instance.getLastReference() != null && instance.getLastRefType().equals("I")) {
          fieldName = instance.getLastRefName().toLowerCase();
        }
        
        if (fieldName != null && 
           (fieldName.endsWith("size") || fieldName.endsWith("count") || 
            fieldName.endsWith("length") || fieldName.endsWith("cursor"))) {
          if (!added.contains(instance)) {
            newContracts.add(new Condition(
                new BinaryConditionTerm(instance, Comparator.OP_GREATER_EQUAL, zero)));
            added.add(instance);
          }
        }
      }
    }
    
    m_formula.getConditionList().addAll(newContracts);
  }
  
  private String translateConditions() {
    StringBuilder command = new StringBuilder();
    
    HashSet<String> addedAssertCmds = new HashSet<String>();
    for (Condition condition : m_formula.getConditionList()) {
      // translate condition into assert command
      String assertCmdStr = translateToCommand(condition, m_keepUnboundedField, m_retrieveUnsatCore);
      if (assertCmdStr.length() > 0) {
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
  
  private String translateConditionTerms(List<ConditionTerm> terms, String connector) {
    StringBuilder command = new StringBuilder();
    
    command.append((terms.size() > 1) ? ("(" + connector + " ") : "");
    for (int i = 0, size = terms.size(); i < size; i++) {
      ConditionTerm term = terms.get(i);
      if (term instanceof BinaryConditionTerm) { // translates binary condition terms
        // convert instanceof to TypeConditionTerm
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
        Instance instance1 = binaryTerm.getInstance1();
        Instance instance2 = binaryTerm.getInstance2();
        if (instance1.getLastReference() != null && 
            instance1.getLastRefName().contains("__instanceof__") && 
            instance2.isConstant()) {
          boolean equals = binaryTerm.getComparator().equals(Comparator.OP_EQUAL);
          boolean toTrue = instance2.getValue().equals("#!1");
          boolean isInstanceOf = !(equals ^ toTrue);
          
          Instance instance = null;
          String typeName   = null;
          String lastRefName = instance1.getLastRefName();
          int index = lastRefName.indexOf("__instanceof__") + 14;
          if (lastRefName.startsWith("read___instanceof__")) { // read___instanceof__[B_2247616843578211
            // get type name
            int index2 = lastRefName.lastIndexOf("_");
            typeName = lastRefName.substring(index, index2);
            Relation relation = m_formula.getRelation(lastRefName.substring(index - 14, index2));
            int relIndex = relation.getIndex(lastRefName.substring(index2 + 1));
            instance = relation.getDomainValues().get(relIndex)[0];
          }
          else {
            // get type name
            typeName = instance1.getLastRefName().substring(index);
            instance = instance1.getLastReference().getDeclaringInstance();
          }
          
          term = new TypeConditionTerm(instance, isInstanceOf ? TypeConditionTerm.Comparator.OP_INSTANCEOF : 
                                                                TypeConditionTerm.Comparator.OP_NOT_INSTANCEOF, typeName);
          command.append(translateConditionTerm((TypeConditionTerm) term));
        }
        else {
          command.append(translateConditionTerm((BinaryConditionTerm) term));
        }
      }
      else if (term instanceof TypeConditionTerm) { // for type condition term
        command.append(translateConditionTerm((TypeConditionTerm) term));
      }
      else { // for and condition term
        command.append(translateConditionTerm((AndConditionTerm) term));
      }
      command.append((i != terms.size() - 1) ? " " : "");
    }
    command.append((terms.size() > 1) ? ")" : "");
    return command.toString();
  }

  private String translateConditionTerm(BinaryConditionTerm binaryTerm) {
    StringBuilder command = new StringBuilder();
    
    TranslatedInstance instance1 = translateInstance(binaryTerm.getInstance1());
    TranslatedInstance instance2 = translateInstance(binaryTerm.getInstance2());
    
    // always convert to number
    instance1 = makeHelperWhenNecessary(instance1, "number");
    instance2 = makeHelperWhenNecessary(instance2, "number");
    
    // convert #!0/#!1 to false/true
    //convertTrueFalse(binaryTerm, instance1, instance2);
    
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
    return command.toString();
  }
  
  private String translateConditionTerm(TypeConditionTerm typeTerm) {
    StringBuilder command = new StringBuilder();

    TranslatedInstance instance1 = translateInstance(typeTerm.getInstance1());
    
    switch (typeTerm.getComparator()) {
    case OP_INSTANCEOF:
      List<long[]> subRanges = m_typeRanges.get(typeTerm.getTypeString());
      command.append("(or");
      for (long[] subRange : subRanges) {
        command.append(" (and (>= ");
        command.append(instance1.m_value);
        command.append(" ");
        command.append(subRange[0]);
        command.append(") (<= ");
        command.append(instance1.m_value);
        command.append(" ");
        command.append(subRange[1]);
        command.append("))");
      }
      command.append(")");
      break;
    case OP_NOT_INSTANCEOF:
      subRanges = m_typeRanges.get(typeTerm.getTypeString());     
      command.append("(and");
      for (long[] subRange : subRanges) {
        command.append(" (or (< ");
        command.append(instance1.m_value);
        command.append(" ");
        command.append(subRange[0]);
        command.append(") (> ");
        command.append(instance1.m_value);
        command.append(" ");
        command.append(subRange[1]);
        command.append("))");
      }
      command.append(")");
      break;
    default:
      command.append("true");
      break;
    }
    
    // since constant strings are currently translated as bit-vectors, 
    // comparing them with integers causes type errors, so ignore such conditions
    if (instance1.m_value.startsWith("##")) {
      command = new StringBuilder("true");
    }
    
    return command.toString();
  }
  
  private String translateConditionTerm(AndConditionTerm andTerm) {
    StringBuilder command = new StringBuilder();
    command.append(translateConditionTerms(andTerm.getAndConditionTerms(), "and"));
    return command.toString();
  }
  
  private TranslatedInstance translateInstance(Instance instance) {
    TranslatedInstance transInstance = null;
    
    // if instance is not bound, try to show its last reference name
    if (!instance.isBounded()) {
      Reference lastRef = instance.getLastReference();
      if (lastRef != null) {
        if (instance.isRelationRead()) { // read from a relation
          StringBuilder readRelation = new StringBuilder();
          
          String readStr    = instance.getLastRefName();
          Relation relation = m_formula.getRelation(readStr);
          readRelation.append("(");
          readRelation.append(YicesUtils.filterChars(relation.getName()));

          int readIndex = relation.getIndex(Relation.getReadStringTime(readStr));
          int lastUpdate = relation.getLastUpdateIndex(readIndex);
          readRelation.append(lastUpdate >= 0 ? ("@" + (lastUpdate + 1)) : "");
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
          transInstance = new TranslatedInstance(readRelation.toString(), "number");
        }
        else {
          String lastRefName = null;
          if (m_keepUnboundedField || lastRef.getDeclaringInstance() == null) {
            lastRefName = lastRef.getLongNameWithCallSites();
          }
          else {
            // since fields could be assigned many times at different time
            // we may not want to compare fields that are not yet bounded
            lastRefName = "%%UnboundField%%";
          }
          transInstance = new TranslatedInstance(YicesUtils.filterChars(lastRefName), "number");
        }
      }
      else {
        transInstance = new TranslatedInstance("{Unbounded}", "number");
      }
    }
    else if (instance.isAtomic()) {
      if (instance.getValue().startsWith("##")) {
        transInstance = new TranslatedInstance(YicesUtils.filterChars(instance.getValue()), "number");
      }
      else { // #! or FreshInstanceOf
        String yicesFormat = translateJavaNumber(instance.getValue());
        transInstance = new TranslatedInstance(YicesUtils.filterChars(yicesFormat), "number");
      }
    }
    else {
      TranslatedInstance leftInstance  = translateInstance(instance.getLeft());
      TranslatedInstance rightInstance = translateInstance(instance.getRight());
      
      // watch out for NaN, NaN +-*/ any number is still NaN
      if (leftInstance.m_value.equals("NaN") || rightInstance.m_value.equals("NaN")) {
        transInstance = new TranslatedInstance("NaN", "number");
      }
      else {
        switch (instance.getOp()) {
        case ADD:
        case SUB:
        case MUL:
        case DIV:
        case REM:
          transInstance = translateNumberInstance(
              instance.getLeft(), leftInstance, instance.getOp(), instance.getRight(), rightInstance);
          break;
        case SHL:
        case SHR:
        case USHR:
          if (Utils.isInteger(rightInstance.m_value)) {
            transInstance = translateShiftConstantInstance(leftInstance, instance.getOp(), rightInstance);
          }
          else {
            transInstance = translateBitVectorInstance(leftInstance, instance.getOp(), rightInstance);
          }
          break;
        case AND:
        case OR:
        case XOR:
        default:
          transInstance = translateBitVectorInstance(leftInstance, instance.getOp(), rightInstance);
          break;
        }
      }
    }
    return transInstance;
  }
  
  // translate to a yices acceptable number format
  private static final Pattern s_floatPattern1 = Pattern.compile("-*[\\d]+\\.[\\d]+"); // 1.0
  private static final Pattern s_floatPattern2 = Pattern.compile("-*[\\d]+\\.[\\d]+E-*[\\d]+");  // 1.0E-6 or 1.0E6
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
        ret.append("bv-shift-right0 "); //XXX: not correct, but yices doesn't have the correct one
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
      default: 
        ret.append("? "); /* not supported operations by yices */
        break;
    }
    ret.append(leftInstance.m_value);
    ret.append(" ");
    ret.append(rightInstance.m_value);
    ret.append(")");
    
    return new TranslatedInstance(ret.toString(), "bitvector32");
  }
  
  // there are two notes here: 1) SHR will have the new leftmost bit same as the 
  // original leftmost bit, thus in case of leftmost bit is 1, it does not equal to / 2^k. 
  // 2) the good news is: although division in Java for negative odd numbers will be one 
  // bigger than right-shift, for example: -15 / 4 = -3, -15 >> 2 = -4, 'div' operation in 
  // yices have the same result as right-shift, meaning that -15 div 4 = -4. So replacing 
  // >>/>>> with 'div' in yices is precise in this situation.
  private TranslatedInstance translateShiftConstantInstance(TranslatedInstance leftInstance, 
      INSTANCE_OP op, TranslatedInstance rightInstance) {
    
    StringBuilder ret = new StringBuilder();
    ret.append("(");
    switch (op) {
      case SHL: // << in java
        ret.append("* ");
        break;
      case SHR: // >> in java
        ret.append("div ");
        break;
      case USHR: // >>> in java
        ret.append("div ");
        break;
      default: 
        ret.append("? "); /* not supported operations by yices */
        break;
    }
    ret.append(leftInstance.m_value);
    ret.append(" ");
    int rightConstant = Integer.parseInt(rightInstance.m_value);
    ret.append(String.valueOf(((int) Math.pow(2.0, rightConstant))));
    ret.append(")");
    
    return new TranslatedInstance(ret.toString(), "number");
  }
  
  private TranslatedInstance translateNumberInstance(Instance left, TranslatedInstance leftInstance, 
      INSTANCE_OP op, Instance right, TranslatedInstance rightInstance) {

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
          ret.append((isInteger(left) && isInteger(right)) ? "div " : "/ ");
          break;
        case REM:
          ret.append("mod ");
          break;
        default:
          ret.append("? "); /* not supported operations by yices */
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
  
  private boolean isInteger(Instance instance) {
    boolean isInt = false;
    if (instance.getLastReference() != null) {
      isInt = instance.getLastRefType().equals("I");
    }
    else {
      try {
        Long.parseLong(instance.getValueWithoutPrefix());
        isInt = true;
      } catch (Exception e) {}
    }
    return isInt;
  }
  
//  private Reference tryCreateParamReference(String refName) {
//    Reference paramRef = null;
//    String param = m_methData.getParamStr(refName);
//    if (param != null) {
//      int index = param.indexOf(')');
//      String paramType = param.substring(1, index);
//      String paramName = param.substring(index + 1);
//      paramRef = new Reference(paramName, paramType, "", new Instance("", null) /* just a dummy */, null, true);
//    }
//    return paramRef;
//  }
  
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
  
  @SuppressWarnings("unused")
  private void convertTrueFalse(BinaryConditionTerm term, TranslatedInstance instance1, TranslatedInstance instance2) {
    // convert #!0/#!1 to false/true
    String type1 = term.getInstance1().getLastReference() != null ? term.getInstance1().getLastRefType() : null;
    String type2 = term.getInstance2().getLastReference() != null ? term.getInstance2().getLastRefType() : null;
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
  private boolean                               m_keepUnboundedField;
  private boolean                               m_retrieveUnsatCore;
  private Hashtable<String, TranslatedInstance> m_helperVars;
  private Hashtable<String, List<long[]>>       m_typeRanges;
  private TranslatedCommand                     m_result;

}
