package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.Solver.AbstractSolverResult;
import hk.ust.cse.Prevision.Solver.ICommand.TranslatedCommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Instance.INSTANCE_OP;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesResult extends AbstractSolverResult {
  
  private static final Pattern s_pattern1 = Pattern.compile("^\\(= ((?:[\\S]+)|(?:\\([\\S]+ [\\S]+\\))|(?:\\([\\S]+ [\\S]+ [\\S]+\\))) ([\\S]+)\\)$");
  //private static final Pattern s_pattern2 = Pattern.compile("v[\\d]+\\$1");
  
  public void parseOutput(String output, Formula formula, TranslatedCommand translatedCmd) {
    // save output first
    m_output = output;

    // split outputs
    String[] outLines = splitOutputLines(output);

    // clear any possible previous contents
    m_conditionList   = null;
    m_partialSatModel = null;
    m_satModel        = null;
    m_unsatCoreIds    = null;
    
    // analyze
    if (outLines.length == 0) {
      m_satisfactory = false; // error
    }
    else if (outLines[0].startsWith("unsat")) {
      m_satisfactory = false;
      
      // parse unsat core ids
      if (outLines.length > 1 && outLines[1].startsWith("unsat core ids: ")) {
        m_unsatCoreIds = new ArrayList<Integer>();
        String[] ids = outLines[1].substring(16).split(" ");
        for (String id : ids) {
          m_unsatCoreIds.add(Integer.parseInt(id));
        }
      }
    }
    else if (!outLines[0].startsWith("sat")) {
      m_satisfactory = false;
    }
    else {
      m_satisfactory = true;
    }
  }

  public void parseOutputModel(String output, Formula formula, TranslatedCommand translatedCmd, boolean retrievePartialModel) {
    // split outputs
    String[] outLines = splitOutputLines(output);

    // clear any possible previous contents
    m_conditionList   = null;
    m_partialSatModel = null;
    m_satModel        = null;
    
    // retrieve satModel
    retrieveSatModel(outLines, formula, translatedCmd);
    
    if (retrievePartialModel) {
      // retrieve non-duplicated condition list
      retrieveConditionList(formula);
      
      // retrieve partial satModel (i.e. only concretes the multi-parameter related variables)
      retrievePartialSatModel(formula);
    }
  }
  
  @Override
  public String getOutputStr() {
    return YicesUtils.unfilterChars(m_output);
  }
  
  private String[] splitOutputLines(String output) {
    // split outputs
    String[] outLines = output.split(LINE_SEPARATOR);
    if (outLines.length > 0 && outLines[0].equals("sat")) {
      List<String> outLinesList = new ArrayList<String>(Arrays.asList(outLines));
      for (int i = 1; i < outLinesList.size(); i++) {
        if (!outLinesList.get(i).startsWith("(= ")) {
          String combine = outLinesList.get(i - 1) + " " + outLinesList.get(i).trim();
          outLinesList.set(i - 1, combine);
          outLinesList.remove(i--);
        }
      }
      outLines = outLinesList.toArray(new String[outLinesList.size()]);
    }
    return outLines;
  }
  
  private BinaryConditionTerm toConditionTerm(String str, Hashtable<String, Instance> nameInstanceMapping) {
    BinaryConditionTerm conditionTerm = null;
    
    Matcher matcher = null;
    if ((matcher = s_pattern1.matcher(str)).find()) {
      String instance1Str = matcher.group(1);
      String instance2Str = matcher.group(2);
      
      // create/retrieve instance1
      boolean discard = false;
      Instance instance1 = null;
      if (!isRelationIntrp(str)) { // (= this 3)
        if (!instance1Str.startsWith("$tmp_")) {
          instance1 = nameInstanceMapping.get(instance1Str);
        }
        else { // if it is an introduced helper variable, discard
          discard = true;
        }
      }
      else if (instance1Str.startsWith("(@@array ")) { // (= (@@array 2147483649 0) 2147483649)
        String subStr = instance1Str.substring(9);
        int index = subStr.indexOf(' ');
        if (index >= 0) {
          String relationObj   = subStr.substring(0, index);
          String relationIndex = subStr.substring(index + 1, subStr.length() - 1); // should always be number

          Instance instanceRelObj   = new Instance("#!" + relationObj, "", null);
          Instance instanceRelIndex = new Instance("#!" + relationIndex, "", null);
          
          // dummy holder for function interpretation line, INSTANCE_OP.ADD is useless
          instance1 = new Instance(instanceRelObj, INSTANCE_OP.DUMMY, instanceRelIndex, null);
        }
      }
      else { // (= (size 3) 1)
        int index = instance1Str.indexOf(' ');
        if (index >= 0) {
          String relationName = YicesUtils.unfilterChars(instance1Str.substring(1, index));
          String relationObj  = instance1Str.substring(index + 1, instance1Str.length() - 1); // should always be number or 0b010102
          
          // create instance for relation name
          Instance instanceRelName = new Instance("##" + relationName, "", null);
          // create instance for reference object
          Instance instanceRelObj = parseNumberOrBV(relationObj, "");
          
          // dummy holder for function interpretation line, INSTANCE_OP.ADD is useless
          instance1 = new Instance(instanceRelName, INSTANCE_OP.DUMMY, instanceRelObj, null);
        }
      }

      // create/retrieve instance2
      Instance instance2 = nameInstanceMapping.get(instance2Str);
      
      // it is possible that var2 is a new value,
      // e.g. a new int or string(bit-vector) value
      if (!discard && instance2 == null) {
        String value = matcher.group(2);
        value = value.equals("true") ? "1" : (value.equals("false") ? "0" : value);
        
        instance2 = parseNumberOrBV(value, instance1.getType());
      }
      
      if (instance1 != null) {
        conditionTerm = new BinaryConditionTerm(instance1, Comparator.OP_EQUAL, instance2);
      }
      else if (!discard) {
        System.err.println("Unable to analyze model line: " + str);
      }
    }
    else {
      System.err.println("Unable to analyze model line: " + str);
    }
    return conditionTerm;
  }
  
  private Instance parseNumberOrBV(String str, String type) {
    String prefix = "";
    try {
      Long.valueOf(str);
      prefix = "#!";
    } catch (NumberFormatException e) {}
    
    return new Instance(prefix + str, type, null);
  }
  
  private void retrieveConditionList(Formula formula) {
    m_conditionList = new ArrayList<Condition>();
    
    // get the model term for each parameter
    Hashtable<Instance, BinaryConditionTerm> paramModelTermMap = new Hashtable<Instance, BinaryConditionTerm>();
    Hashtable<Instance, TypeConditionTerm> paramTypeTermMap    = new Hashtable<Instance, TypeConditionTerm>();
    for (ConditionTerm term : m_satModel) {
      if (term instanceof BinaryConditionTerm) {
        paramModelTermMap.put(term.getInstances()[0], (BinaryConditionTerm) term);
      }
      else if (term instanceof TypeConditionTerm) {
        paramTypeTermMap.put(term.getInstances()[0], (TypeConditionTerm) term);
      }
    }
    
    HashSet<String> duplicated = new HashSet<String>();
    for (Condition condition : formula.getConditionList()) {
      String conditionStr = condition.toString();
      if (!duplicated.contains(conditionStr)) {
        duplicated.add(conditionStr);
        
        // we only add conditions that are related to at least one 
        // parameter (incl. callee) or related to some static fields
        HashSet<Instance> topInstances = condition.getRelatedTopInstances(formula);
        Collection<Instance> common = Utils.intersect(topInstances, paramModelTermMap.keySet());
        if (common.size() > 0) {
          m_conditionList.add(condition);
        }
      }
    }
  }
  
  private void retrieveSatModel(String[] outLines, Formula formula, TranslatedCommand translatedCmd) {
    Hashtable<String, Instance> nameInstanceMapping = translatedCmd.nameInstanceMapping;
    Hashtable<String, List<long[]>> typeRanges      = translatedCmd.typeRanges;
    
    // analyze each model line
    m_satModel = new ArrayList<ConditionTerm>();
    for (int i = 1; i < outLines.length; i++) {
      if (outLines[i].length() > 0) {
        BinaryConditionTerm term = toConditionTerm(outLines[i], nameInstanceMapping);
        if (term != null) {
          m_satModel.add(term);
        }
      }
    }
    
    // discard the function interpretations that are useless
    removeUselessFuncIntrp(formula, translatedCmd);

    // convert constant string
    convertConstString(translatedCmd);
    
    // add type conditions to satModel according to the 
    // hidden information from model value and type ranges
    addTypeConditions(typeRanges);
    
    // substitute embedded final vars
    substEmbeddedFinalVars();
    
    // remove conversion helpers
    removeConvHelperTerm();
  }
  
  private void retrievePartialSatModel(Formula formula) {
    m_partialSatModel = new ArrayList<Condition>();
    
    // get the model term for each parameter
    Hashtable<Instance, BinaryConditionTerm> paramModelTermMap = new Hashtable<Instance, BinaryConditionTerm>();
    Hashtable<Instance, TypeConditionTerm> paramTypeTermMap    = new Hashtable<Instance, TypeConditionTerm>();
    Hashtable<String, String> assignedValueMap                 = new Hashtable<String, String>();
    for (ConditionTerm term : m_satModel) {
      if (term instanceof BinaryConditionTerm) {
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
        paramModelTermMap.put(binaryTerm.getInstance1(), (BinaryConditionTerm) binaryTerm);
        assignedValueMap.put(binaryTerm.getInstance1().toString(), binaryTerm.getInstance2().toString());
      }
      else if (term instanceof TypeConditionTerm) {
        paramTypeTermMap.put(term.getInstances()[0], (TypeConditionTerm) term);
      }
    }
    
    List<Condition> multiRelatedConditions  = new ArrayList<Condition>();
    List<Condition> selfContainedConditions = new ArrayList<Condition>();
    for (Condition condition : m_conditionList) {
      if (isMultiParamRelated(condition, formula)) { // check if it is a multiple-parameter related condition
        multiRelatedConditions.add(condition);
      }
      else {
        selfContainedConditions.add(condition);
      }
    }

    // put in sat-model values
    HashSet<Instance> concretized = new HashSet<Instance>();
    for (Condition condition : multiRelatedConditions) {
      HashSet<Instance> instances = new HashSet<Instance>();
      for (ConditionTerm condTerm : condition.getConditionTerms()) {
        for (Instance instance : condTerm.getInstances()) {
          instances.addAll(instance.getRelatedInstances(formula, true, true));
        }
      }
      for (Instance instance : instances) {
        if (instance.isRelationRead()) {
          // convert read_@@array_ to (#!85899345888 @ #!0), this is necessary 
          // because we want to concretize the array element values as well
          instance = convArrayReadInstance(instance, formula, paramModelTermMap, assignedValueMap);
        }
        
        BinaryConditionTerm modelTerm = paramModelTermMap.get(instance);
        if (modelTerm != null) {
          concretized.add(instance);
          m_partialSatModel.add(new Condition(modelTerm));
        }
        TypeConditionTerm typeTerm = paramTypeTermMap.get(instance);
        if (typeTerm != null) {
          m_partialSatModel.add(new Condition(typeTerm));
        }
      }
    }
    Collections.sort(m_partialSatModel, new java.util.Comparator<Condition>() {
      @Override
      public int compare(Condition o1, Condition o2) {
        int index1 = m_satModel.indexOf(o1.getConditionTerms().get(0));
        int index2 = m_satModel.indexOf(o2.getConditionTerms().get(0));
        return index1 - index2;
      }
    });

    // put in self-contained conditions
    for (Condition condition : selfContainedConditions) {
      HashSet<Instance> relatedInstances = condition.getRelatedInstances(formula, false, false);
      if (relatedInstances.size() == 1) {
        if (!concretized.contains(relatedInstances.iterator().next())) {
          m_partialSatModel.add(condition);
        }
      }
      else {
        m_partialSatModel.add(condition);
      }
    }
  }
  
  private boolean isMultiParamRelated(Condition condition, Formula formula) {
    HashSet<Instance> relatedInstances = condition.getRelatedTopInstances(formula);
    return relatedInstances.size() > 1;
  }
  
  // convert read_@@array_ to (#!85899345888 @ #!0)
  private Instance convArrayReadInstance(Instance readInstance, Formula formula, 
      Hashtable<Instance, BinaryConditionTerm> paramModelTermMap, Hashtable<String, String> assignedValueMap) {
    Instance[] domainValues = formula.getReadRelationDomainValues(readInstance);
    
    Instance array = domainValues[0];
    if (array.isRelationRead()) {
      array = convArrayReadInstance(array, formula, paramModelTermMap, assignedValueMap);
    }
    array = paramModelTermMap.get(array).getInstance2();
    
    Instance index = domainValues[1];
    String indexStr = index.toString();
    if (index.isRelationRead()) {
      index = convArrayReadInstance(index, formula, paramModelTermMap, assignedValueMap);
      index = paramModelTermMap.get(index).getInstance2();
      indexStr = index.toString();
    }
    else if (!index.isConstant()) {
      indexStr = replaceArrayIndex(index, assignedValueMap);
    }
    
    // find the corresponding instance
    Instance converted = null;
    for (Instance instance : paramModelTermMap.keySet()) {
      if (instance.getOp() == INSTANCE_OP.DUMMY && 
          instance.getLeft().toString().equals(array.toString()) && 
          instance.getRight().toString().equals(indexStr)) {
        converted = instance;
        break;
      }
    }
    return converted;
  }
  
  private void removeUselessFuncIntrp(Formula formula, TranslatedCommand translatedCmd) {

    // collect all the constant Instance mappings
    Hashtable<Instance, Instance> updatedAsMapping = new Hashtable<Instance, Instance>();
    Hashtable<String, List<Instance>> constInstanceMapping = new Hashtable<String, List<Instance>>();
    for (int i = 0, size = m_satModel.size(); i < size; i++) {
      ConditionTerm term = m_satModel.get(i);
      if (term instanceof BinaryConditionTerm) {
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
        Instance instance1 = binaryTerm.getInstance1();
        Instance instance2 = binaryTerm.getInstance2();
        if (instance2.isBounded() && instance2.getValue().startsWith("#!") && 
            binaryTerm.getComparator().equals(Comparator.OP_EQUAL)) {
          
          List<Instance> instances = constInstanceMapping.get(binaryTerm.getInstance2().getValue());
          if (instances == null) {
            instances = new ArrayList<Instance>();
            constInstanceMapping.put(binaryTerm.getInstance2().getValue(), instances);
          }
          instances.add(instance1);
        }
      }
    }
    
    // add in the FreshInstanceOf constants
    Enumeration<String> keys = translatedCmd.constInstanceMapping.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      List<Instance> instances = constInstanceMapping.get("#!" + key);
      if (instances == null) {
        instances = new ArrayList<Instance>();
        constInstanceMapping.put("#!" + key, instances);
      }
      instances.add(translatedCmd.constInstanceMapping.get(key));
    }
    
    // find all used read's time
    HashSet<Long> readTimes = findUsedReadTime(formula);
    
    // decide usefulness
    for (int i = 0; i < m_satModel.size(); i++) {
      ConditionTerm term = m_satModel.get(i);
      if (term instanceof BinaryConditionTerm) {
        if (!isFuncIntrpUseful(formula, (BinaryConditionTerm) term, constInstanceMapping, updatedAsMapping, readTimes)) {
          m_satModel.remove(i--);
        }
      }
    }
  }

  private HashSet<Long> findUsedReadTime(Formula formula) {
    HashSet<Long> readTimes = new HashSet<Long>();

    Hashtable<String, Relation> relMap = formula.getRelationMap();
    for (Condition condition : formula.getConditionList()) {
      for (ConditionTerm term : condition.getConditionTerms()) {
        for (Instance instance : term.getInstances()) {
          findUsedReadTime(instance, relMap, readTimes);
        }
      }
    }
    Enumeration<String> keys = relMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relMap.get(key);
      for (int i = 0, size = relation.getFunctionCount(); i < size; i++) {
        Instance rangeValue = relation.getRangeValues().get(i);
        if (rangeValue != null) { // an update
          Instance[] domainValues = relation.getDomainValues().get(i);
          for (Instance domainValue : domainValues) {
            findUsedReadTime(domainValue, relMap, readTimes);
          }
          findUsedReadTime(rangeValue, relMap, readTimes);
        }
      }
    }
    return readTimes;
  }

  private void findUsedReadTime(Instance instance, Hashtable<String, Relation> relMap, HashSet<Long> readTimes) {
    if (!instance.isBounded()) {
      String str = instance.getLastRefName();
      if (str.startsWith("read_")) {
        int index = str.lastIndexOf('_');
        String relName = str.substring(5, index);
        long time = Long.parseLong(str.substring(index + 1));
        readTimes.add(time);
        
        // if there are further read instances in domain or 
        // range, their times should also be included
        Relation relation = relMap.get(relName);
        int index2 = relation.getIndex(time);
        Instance[] domainValues = relation.getDomainValues().get(index2);
        for (Instance domainValue : domainValues) {
          findUsedReadTime(domainValue, relMap, readTimes);
        }
        Instance rangeValue = relation.getRangeValues().get(index2);
        if (rangeValue != null) {
          findUsedReadTime(rangeValue, relMap, readTimes);
        }
      }      
    }
    else {
      if (instance.getLeft() != null) {
        findUsedReadTime(instance.getLeft(), relMap, readTimes);
      }
      if (instance.getRight() != null) {
        findUsedReadTime(instance.getRight(), relMap, readTimes);
      }
    }
  }
  
  private boolean isFuncIntrpUseful(Formula formula, BinaryConditionTerm binaryTerm, 
      Hashtable<String, List<Instance>> constInstanceMapping, Hashtable<Instance, Instance> updatedAsMapping, HashSet<Long> readTimes) {
    boolean useful = true;
    
    if (binaryTerm.getInstance1().getRight() != null && 
        binaryTerm.getInstance1().getOp().equals(Instance.INSTANCE_OP.DUMMY)) { // it is function interpretation
      if (binaryTerm.getInstance1().getLeft().getValue().startsWith("##")) {
        useful = isFieldFuncIntrpUseful(formula, binaryTerm, constInstanceMapping, updatedAsMapping, readTimes);
      }
      else {
        useful = isArrayFuncIntrpUseful(formula, binaryTerm, constInstanceMapping, updatedAsMapping, readTimes);
      }
    }
    return useful;
  }
  
  // (##length @ #!9395240963) == #!3
  private boolean isFieldFuncIntrpUseful(Formula formula, BinaryConditionTerm binaryTerm, 
      Hashtable<String, List<Instance>> constInstanceMapping, Hashtable<Instance, Instance> updatedAsMapping, HashSet<Long> readTimes) {
    boolean useful = false;
    
    String relName = binaryTerm.getInstance1().getLeft().getValueWithoutPrefix();
    Relation relation = formula.getRelation(relName);
    if (relation != null) {
      boolean meetUpdate = false;
      for (int i = 0; i < relation.getFunctionCount() && !meetUpdate && !useful; i++) {
        Instance objInstance = relation.getDomainValues().get(i)[0];
        if (relation.isUpdate(i)) { // an update
          meetUpdate = isObjInstanceSame(binaryTerm.getInstance1().getRight(), 
              constInstanceMapping, updatedAsMapping, objInstance, formula);
        }
        else {
          useful = readTimes.contains(relation.getFunctionTimes().get(i)) && isObjInstanceSame(binaryTerm.getInstance1().getRight(), 
                constInstanceMapping, updatedAsMapping, objInstance, formula);
        }
      }
    }
    
    // if it is constant string field, also not useful
    if (useful) {
      String modelValue = binaryTerm.getInstance1().getRight().getValue();
      List<Instance> objInstances = constInstanceMapping.get(modelValue);
      for (int i = 0, size = objInstances.size(); i < size && useful; i++) {
        String value = objInstances.get(i).getValue();
        useful = value == null || !value.startsWith("##");
      }
    }
    
    return useful;
  }
  
  // (#!9395240963 @ #!1) == #!14227079174
  private boolean isArrayFuncIntrpUseful(Formula formula, BinaryConditionTerm binaryTerm, 
      Hashtable<String, List<Instance>> constInstanceMapping, Hashtable<Instance, Instance> updatedAsMapping, HashSet<Long> readTimes) {
    boolean useful = false;
    
    String index  = binaryTerm.getInstance1().getRight().getValue();
    List<Instance> indexInstances = constInstanceMapping.get(index);
    
    Relation relation = formula.getRelation("@@array");
    if (relation != null) {
      boolean meetUpdate = false;
      for (int i = 0; i < relation.getFunctionCount() && !meetUpdate && !useful; i++) {
        Instance objInstance   = relation.getDomainValues().get(i)[0];
        Instance indexInstance = relation.getDomainValues().get(i)[1];
        if (relation.isUpdate(i)) { // an update
          meetUpdate = isObjInstanceSame(binaryTerm.getInstance1().getLeft(), 
              constInstanceMapping, updatedAsMapping, objInstance, formula);
          if (meetUpdate) { // check index
            if (indexInstance.isAtomic() && indexInstance.getValue().startsWith("#!")) {
              meetUpdate &= index.equals(indexInstance.getValue());
            }
            else {
              meetUpdate &= isObjInstanceSame(
                  binaryTerm.getInstance1().getRight(), constInstanceMapping, updatedAsMapping, indexInstance, formula);
            }
          }
        }
        else {
          if (readTimes.contains(relation.getFunctionTimes().get(i))) {
            useful = isObjInstanceSame(binaryTerm.getInstance1().getLeft(), 
                constInstanceMapping, updatedAsMapping, objInstance, formula);
            if (useful) { // check index
              if (indexInstance.isAtomic() && indexInstance.getValue().startsWith("#!")) {
                useful &= index.equals(indexInstance.getValue());
              }
              else if (indexInstances != null) {
                useful &= isObjInstanceSame(
                    binaryTerm.getInstance1().getRight(), constInstanceMapping, updatedAsMapping, indexInstance, formula);
              }
            }
          }
        }
      }
    }
    
    return useful;
  }
  
  private boolean isObjInstanceSame(Instance instanceModelValue, Hashtable<String, List<Instance>> constInstanceMapping, 
      Hashtable<Instance, Instance> updatedAsMapping, Instance instanceFormula, Formula formula) {

    boolean equals = false;
    List<Instance> objInstances = constInstanceMapping.get(instanceModelValue.getValue());
    if (objInstances == null) {
      objInstances = new ArrayList<Instance>();
    }
    else {
      objInstances = new ArrayList<Instance>(objInstances);
    }
    objInstances.add(instanceModelValue); // could be just a number instead of model value
    for (int i = 0, size = objInstances.size(); i < size && !equals; i++) {
      equals = isObjInstanceSameRec(objInstances.get(i), constInstanceMapping, updatedAsMapping, instanceFormula, formula);
    }
    return equals;
  }
  
  private boolean isObjInstanceSameRec(Instance instanceSolver, Hashtable<String, List<Instance>> constInstanceMapping, 
      Hashtable<Instance, Instance> updatedAsMapping, Instance instanceFormula, Formula formula) {
    boolean equals = false;
    
    if (!instanceFormula.isBounded() && instanceFormula.getLastRefName().startsWith("read_")) {
      // find the real instance, e.g. read_@@table_11111, there may already
      // be an update to @@table, so we may want to use the updated value
      Instance instanceFound = findUpdatedAs(instanceFormula, constInstanceMapping, updatedAsMapping, formula);
      
      if (instanceFound == instanceFormula) {
        String readStr      = instanceFormula.getLastRefName();
        long readTime       = Relation.getReadStringTime(readStr);
        String relationName = Relation.getReadStringRelName(readStr);
        
        Relation relation = formula.getRelation(relationName);
        int index = relation.getIndex(readTime);
        
        Instance solverLeft  = instanceSolver.getLeft();
        Instance solverRight = instanceSolver.getRight();
        if (solverRight != null) { // (##table @ #!0) v.s. read_@@table_11111
          Instance[] domainValues = relation.getDomainValues().get(index);
          
          boolean allEquals = true;
          if (solverLeft.getValue().startsWith("##")) { // field read
            allEquals &= solverLeft.getValueWithoutPrefix().equals(relationName);
            allEquals &= isObjInstanceSame(solverRight, constInstanceMapping, updatedAsMapping, domainValues[0], formula);
            equals = allEquals;
          }
          else if (domainValues.length == 2) { // array read
            allEquals &= isObjInstanceSame(solverLeft, constInstanceMapping, updatedAsMapping, domainValues[0], formula);
            allEquals &= isObjInstanceSame(solverRight, constInstanceMapping, updatedAsMapping, domainValues[1], formula);
            equals = allEquals;
          }
          else {
            equals = false;
          }
        }
        else {
          // try to get last update's range value
          Instance rangeValue = null;
          for (int i = index - 1; i >= 0 && rangeValue == null; i--) {
            if (relation.getRangeValues().get(i) != null) {
              rangeValue = relation.getRangeValues().get(i);
            }
          }
          
          if (rangeValue != null) {
            equals = instanceSolver == rangeValue;
          }
          else {
            equals = instanceSolver == instanceFormula;
          }
        }
      }
      else {
        return isObjInstanceSameRec(instanceSolver, constInstanceMapping, updatedAsMapping, instanceFound, formula);
      }
    }
    else if (instanceSolver.isConstant()) {
      if (!instanceFormula.isConstant()) { // #!0 vs (v1.all.length - #!1)
        // translate to constant
        String translated = translateArrayIndex(instanceFormula, constInstanceMapping, updatedAsMapping, formula);
        equals = translated.equals(instanceSolver.toString());
      }
      else { // #!0 vs #!0
        equals = instanceFormula.toString().equals(instanceSolver.toString());
      }
    }
    else {
      equals = instanceFormula.toString().equals(instanceSolver.toString());
    }
    return equals;
  }

  private Instance findUpdatedAs(Instance instanceFormula, Hashtable<String, List<Instance>> constInstanceMapping, 
      Hashtable<Instance, Instance> updatedAsMapping, Formula formula) {
    Instance instanceFound = instanceFormula;
    
    // find in cache first
    if (updatedAsMapping.containsKey(instanceFormula)) {
      return updatedAsMapping.get(instanceFormula);
    }
    
    String readStr      = instanceFormula.getLastRefName();
    long readTime       = Relation.getReadStringTime(readStr);
    String relationName = Relation.getReadStringRelName(readStr);
    
    Relation relation = formula.getRelation(relationName);
    int index = relation.getIndex(readTime);

    String[] solverConsts1 = matchToSolverConsts(
        relation.getDomainValues().get(index), constInstanceMapping, updatedAsMapping, formula);
    for (int i = index - 1; i >= 0; i--) {
      if (relation.isUpdate(i)) { // an update
        String[] solverConsts2 = matchToSolverConsts(
            relation.getDomainValues().get(i), constInstanceMapping, updatedAsMapping, formula);
        if (Arrays.equals(solverConsts1, solverConsts2)) {
          instanceFound = relation.getRangeValues().get(i);
          break;
        }
      }
    }
    
    if (instanceFound != instanceFormula && 
       !instanceFound.isBounded() && instanceFound.getLastRefName().startsWith("read_")) {
      instanceFound = findUpdatedAs(instanceFound, constInstanceMapping, updatedAsMapping, formula);
    }
    
    // save to cache
    updatedAsMapping.put(instanceFormula, instanceFound);
    
    return instanceFound;
  }
  
  private String[] matchToSolverConsts(Instance[] domainValues, Hashtable<String, List<Instance>> constInstanceMapping, 
      Hashtable<Instance, Instance> updatedAsMapping, Formula formula) {
    String[] solverConsts = new String[domainValues.length];
    
    for (int i = 0; i < domainValues.length; i++) {
      Enumeration<String> keys = constInstanceMapping.keys();
      while (keys.hasMoreElements() && solverConsts[i] == null) {
        String key = (String) keys.nextElement();
        List<Instance> solverInstances = constInstanceMapping.get(key);
        for (int j = 0, size = solverInstances.size(); j < size && solverConsts[i] == null; j++) {
          if (isObjInstanceSameRec(solverInstances.get(j), constInstanceMapping, updatedAsMapping, domainValues[i], formula)) {
            solverConsts[i] = key;
          }
        }
      }
    }
    return solverConsts;
  }
  
  private String translateArrayIndex(Instance indexInstance, Hashtable<String, List<Instance>> constInstanceMapping, 
      Hashtable<Instance, Instance> updatedAsMapping, Formula formula) {
    
    String translated = indexInstance.toString();
    if (indexInstance.isBounded()) { // (v1.all.length - #!1)
      try {
        String leftString  = translateArrayIndex(indexInstance.getLeft(), constInstanceMapping, updatedAsMapping, formula);
        String rightString = translateArrayIndex(indexInstance.getRight(), constInstanceMapping, updatedAsMapping, formula);
        if (leftString.startsWith("#!") && rightString.startsWith("#!")) {
          int leftInt  = Integer.parseInt(leftString.substring(2));
          int rightInt = Integer.parseInt(rightString.substring(2));
          
          switch (indexInstance.getOp()) {
          case ADD:
            translated = "#!" + String.valueOf(leftInt + rightInt);
            break;
          case AND:
            translated = "#!" + String.valueOf(leftInt & rightInt);
            break;
          case SUB:
            translated = "#!" + String.valueOf(leftInt - rightInt);
            break;
          case MUL:
            translated = "#!" + String.valueOf(leftInt * rightInt);
            break;
          case DIV:
            translated = "#!" + String.valueOf(leftInt / rightInt);
            break;
          case OR:
            translated = "#!" + String.valueOf(leftInt | rightInt);
            break;
          case REM:
            translated = "#!" + String.valueOf(leftInt % rightInt);
            break;
          case XOR:
            translated = "#!" + String.valueOf(leftInt ^ rightInt);
            break;
          case SHL:
            translated = "#!" + String.valueOf(leftInt << rightInt);
            break;
          case SHR:
            translated = "#!" + String.valueOf(leftInt >> rightInt);
            break;
          case USHR:
            translated = "#!" + String.valueOf(leftInt >> rightInt);
            break;
          default:
            break;
          }
        }
      } catch (Exception e) {}
    }
    else if (!indexInstance.isBounded()) { // v1.length, read_@@array_2094825414036419, read_offset_2115465414036419
      for (int i = 0, size = m_satModel.size(); i < size; i++) {
        if (m_satModel.get(i) instanceof BinaryConditionTerm) {
          BinaryConditionTerm binaryTerm = (BinaryConditionTerm) m_satModel.get(i);
          if (translated.equals(binaryTerm.getInstance1().toString())) {
            translated = binaryTerm.getInstance2().toString();
            break;
          }
          else if (isObjInstanceSameRec(binaryTerm.getInstance1(), constInstanceMapping, 
              updatedAsMapping, indexInstance, formula)) { // (##offset @ #!107374182360) vs read_offset_2115465414036419
            translated = binaryTerm.getInstance2().toString();
            break;
          }
        }
      }
    }
    return translated;
  }
  
  private boolean isRelationIntrp(String str) {
    return str.startsWith("(= (");
  }
  
  public void addTypeConditions(Hashtable<String, List<long[]>> typeRanges) {
    // obtain variable type information from model value
    for (int i = 0, size = m_satModel.size(); i < size; i++) {
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) m_satModel.get(i);
      
      Instance instance1 = binaryTerm.getInstance1();
      Instance instance2 = binaryTerm.getInstance2();

      if (instance2.getValue().startsWith("#!")) { // model value
        long value = Long.parseLong(instance2.getValueWithoutPrefix());

        // if it is non-primitive type
        if (value >= 21474836471L) {
          TypeConditionTerm typeTerm = null;
          Enumeration<String> keys = typeRanges.keys();
          while (keys.hasMoreElements() && typeTerm == null) {
            String key = (String) keys.nextElement();
            long[] range = typeRanges.get(key).get(0);
            if (value >= range[0] && value <= range[1]) {
              // model value within type range, obtain this type information
              typeTerm = new TypeConditionTerm(
                  instance1, TypeConditionTerm.Comparator.OP_INSTANCEOF, key);
            }
          }
          
          if (typeTerm != null) {
            m_satModel.add(typeTerm);
          }
        }
      }
    }
  }
  
  private void convertConstString(TranslatedCommand translatedCmd) {
    for (int i = 0, size = m_satModel.size(); i < size; i++) {
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) m_satModel.get(i);
      Instance instance1 = binaryTerm.getInstance1();
      Instance instance2 = binaryTerm.getInstance2();

      if (instance2.getValue().startsWith("#!")) { // model value
        long value = Long.parseLong(instance2.getValueWithoutPrefix());

        Instance constInstance = translatedCmd.constInstanceMapping.get(String.valueOf(value));
        if (constInstance != null && constInstance.getValue() != null && constInstance.getValue().startsWith("##")) {
          binaryTerm = new BinaryConditionTerm(instance1, binaryTerm.getComparator(), constInstance);
          m_satModel.set(i, binaryTerm);
        }
      }
    }
  }
  
  private void removeConvHelperTerm() {
//    if (m_satModel != null) {
//      for (int i = 0; i < m_satModel.size(); i++) {
//        String var1Name = m_satModel.get(i).getVar1().getVarName();
//        if (s_pattern2.matcher(var1Name).matches()) {
//          m_satModel.remove(i--);
//        }
//      }
//    }
  }
  
  private void substEmbeddedFinalVars() {
//    if (m_satModel != null) {
//      // create a map
//      Hashtable<SMTVariable, SMTVariable> finalVarValueMap = new Hashtable<SMTVariable, SMTVariable>();
//      for (SMTTerm term : m_satModel) {
//        SMTVariable var1 = term.getVar1();
//        SMTVariable var2 = term.getVar2();
//        
//        // only substitute with numbers and booleans
//        if (var2.getVarCategory() == VarCategory.VAR_CONST && 
//            var2.getVarType().equals("#ConstantNumber") && 
//           !var2.getVarName().equals("notnull") && 
//           !var2.getVarName().equals("null")) {
//          finalVarValueMap.put(var1, var2);
//        }
//      }
//      
//      // substitute all embedded final vars with concrete model values
//      for (SMTTerm term : m_satModel) {
//        term.getVar1().substExtraVars(finalVarValueMap);
//        term.getVar2().substExtraVars(finalVarValueMap);
//      }
//    }
  }
}
