package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.Solver.ICommand.TranslatedCommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AbstractSolverResult {

  public abstract void parseOutput(String output, Formula formula, TranslatedCommand translatedCmd);
  public abstract void parseOutputModel(String output, Formula formula, TranslatedCommand translatedCmd, boolean retrievePartialModel);

  public boolean isSatisfactory() {
    return m_satisfactory;
  }

  public String getOutputStr() {
    return m_output;
  }

  public List<Integer> getUnsatCoreIds() {
    return m_unsatCoreIds;
  }
  
  public List<Condition> getConditionList() {
    return m_conditionList;
  }
  
  public List<ConditionTerm> getSatModel() {
    return m_satModel;
  }
  
  public List<Condition> getPartialSatModel() {
    return m_partialSatModel;
  }
  
  public String getSatModelString(Formula satisfiable) {
    List<String> modelLines = new ArrayList<String>();
    
    // create assignedValueMap for array index
    Hashtable<String, String> assignedValueMap = new Hashtable<String, String>();
    for (ConditionTerm modelTerm : m_satModel) {
      if (modelTerm instanceof BinaryConditionTerm) {
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) modelTerm;
        assignedValueMap.put(binaryTerm.getInstance1().toString(), 
                             binaryTerm.getInstance2().toString());
      }
    }
    
    for (ConditionTerm modelTerm : m_satModel) {
      // if modelLine contains read_@@array_, replace them
      String modelLine = modelTerm.toString();
      modelLine = replaceArrayRead(modelLine, satisfiable, assignedValueMap);
      modelLines.add(modelLine);
    }
    return "sat\n" + Utils.concatStrings(modelLines, "\n", true);
  }
  
  public String getSatModelSerializeString(Formula satisfiable) {
    return getBinaryTermsSerializeString(m_satModel, satisfiable.getRelationMap());
  }
  
  public String getPartialSatModelString(Formula satisfiable) {
    List<String> conditionLines = new ArrayList<String>();
    
    // create assignedValueMap for array index
    Hashtable<String, String> assignedValueMap = new Hashtable<String, String>();
    for (Condition condition : m_partialSatModel) {
      if (condition.getConditionTerms().size() == 1) {
        ConditionTerm term = condition.getConditionTerms().get(0);
        if (term instanceof BinaryConditionTerm) {
          BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
          if (binaryTerm.getComparator().equals(Comparator.OP_EQUAL) && 
              binaryTerm.getInstance2().isConstant()) {
            assignedValueMap.put(binaryTerm.getInstance1().toString(), 
                                 binaryTerm.getInstance2().toString());
          }
        }
      }
    }
    
    for (Condition condition : m_partialSatModel) {
      // if conditionLine contains read_@@array_, replace them
      String conditionLine = condition.toString();
      conditionLine = replaceArrayRead(conditionLine, satisfiable, assignedValueMap);
      conditionLines.add(conditionLine);
    }
    return "sat\n" + Utils.concatStrings(conditionLines, "\n", true);
  }
  
  public String getPartialSatModelSerializeString(Formula satisfiable) {
    return getConditionsSerializeString(m_partialSatModel, satisfiable.getRelationMap());
  }
  
  public String getConditionListString(Formula satisfiable) {
    List<String> conditionLines = new ArrayList<String>();
    
    Hashtable<String, String> assignedValueMap = new Hashtable<String, String>(); // no assigned values
    for (Condition condition : m_conditionList) {
      // if conditionLine contains read_@@array_, replace them
      String conditionLine = condition.toString();
      conditionLine = replaceArrayRead(conditionLine, satisfiable, assignedValueMap);
      conditionLines.add(conditionLine);
    }
    return "sat\n" + Utils.concatStrings(conditionLines, "\n", true);
  }
  
  public String getConditionListSerializeString(Formula satisfiable) {
    return getConditionsSerializeString(m_partialSatModel, satisfiable.getRelationMap());
  }

  private String getBinaryTermsSerializeString(List<ConditionTerm> terms, Hashtable<String, Relation> relationMap) {
    List<Condition> conditions = new ArrayList<Condition>();
    for (ConditionTerm term : terms) {
      conditions.add(new Condition(term));
    }
    return getConditionsSerializeString(conditions, relationMap);
  }
  
  private String getConditionsSerializeString(List<Condition> conditions, Hashtable<String, Relation> relationMap) {
    StringBuilder str = new StringBuilder();
    Hashtable<Instance, Integer> instanceNumMap   = new Hashtable<Instance, Integer>();
    Hashtable<Reference, Integer> referenceNumMap = new Hashtable<Reference, Integer>();
    
    // collect all references and instances and assign each with a number
    findAllInstancesReferences(referenceNumMap, instanceNumMap, conditions, relationMap);

    // all instances
    Enumeration<Instance> instances = instanceNumMap.keys();
    while (instances.hasMoreElements()) {
      Instance instance = (Instance) instances.nextElement();
      int num1  = instance.getLeft() == null ? -1 : instanceNumMap.get(instance.getLeft());
      int num2  = instance.getRight() == null ? -1 : instanceNumMap.get(instance.getRight());
      int numOp = instance.getOp() == null ? -1 : instance.getOp().toIndex();
      str.append(instanceNumMap.get(instance)).append("\n");
      str.append(num1).append(" ").append(numOp).append(" ").append(num2).append("\n");
      str.append(instance.getValue() == null ? " " : instance.getValue().replace("\n", "@n")).append("\n");
      str.append(instance.getType() == null ? " " : instance.getType()).append("\n");
      
      int num3 = instance.getLastReference() == null ? -1 : referenceNumMap.get(instance.getLastReference());
      str.append(num3).append("\n");
      Hashtable<String, Reference> fields = instance.getFieldSet();
      Enumeration<String> fieldNames = fields.keys();
      while (fieldNames.hasMoreElements()) {
        String fieldName = (String) fieldNames.nextElement();
        str.append(fieldName.replace("\n", "@n")).append(" ")
           .append(referenceNumMap.get(fields.get(fieldName))).append("\n");
      }
      str.append("\n");
    }
    str.append("\n");
    
    // all references
    Enumeration<Reference> references = referenceNumMap.keys();
    while (references.hasMoreElements()) {
      Reference reference = (Reference) references.nextElement();
      str.append(referenceNumMap.get(reference)).append("\n");
      str.append(reference.getType() == null ? " " : reference.getType()).append("\n");
      str.append(reference.getName() == null ? " " : reference.getName().replace("\n", "@n")).append("\n");
      
      for (Instance instance : reference.getInstances()) {
        str.append(instanceNumMap.get(instance)).append("\n");
        Long[] lifeTime = reference.getLifeTime(instance);
        str.append(lifeTime == null ? " " : lifeTime[0]).append("\n");
      }
      str.append("\n");
      for (Instance instance : reference.getOldInstances()) {
        str.append(instanceNumMap.get(instance)).append("\n");
        Long[] lifeTime = reference.getLifeTime(instance);
        str.append(lifeTime == null ? " " : lifeTime[0]).append("\n");
      }
      str.append("\n");
      int num = reference.getDeclaringInstance() == null ? -1 : instanceNumMap.get(reference.getDeclaringInstance());
      str.append(num).append("\n\n");
    }
    str.append("\n");
    
    // conditions
    for (Condition condition : conditions) {
      for (ConditionTerm term : condition.getConditionTerms()) {
        if (term instanceof BinaryConditionTerm) {
          BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
          Integer num1 = instanceNumMap.get(binaryTerm.getInstance1());
          Integer num2 = instanceNumMap.get(binaryTerm.getInstance2());
          str.append("b ").append(num1).append(" ")
             .append(binaryTerm.getComparator().toIndex()).append(" ").append(num2);
        }
        else if (term instanceof TypeConditionTerm) {
          TypeConditionTerm typeTerm = (TypeConditionTerm) term;
          Integer num1 = instanceNumMap.get(typeTerm.getInstance1());
          str.append("t ").append(num1).append(" ")
             .append(typeTerm.getComparator().toIndex()).append(" ").append(typeTerm.getTypeString());
        }
        str.append("|"); // separate each conditionTerm
      }
      str.append("\n");
    }
    str.append("\n");
    
    // relationMap
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      Relation relation = relationMap.get(key);
      str.append(relation.getName()).append("\n");
      str.append(relation.getDomainDimension()).append("\n");
      str.append(relation.getDirection()).append("\n");
      for (int i = 0, size = relation.getFunctionCount(); i < size; i++) {
        Instance[] domainValues = relation.getDomainValues().get(i);
        for (Instance domainValue : domainValues) {
          Integer num = instanceNumMap.get(domainValue);
          str.append(num).append("|");
        }
        str.append("\n");
        Instance rangeValue = relation.getRangeValues().get(i);
        str.append(rangeValue == null ? "" : instanceNumMap.get(rangeValue)).append("\n");
        str.append(relation.getFunctionTimes().get(i)).append("\n");
      }
      str.append("\n");
    }
    str.append("\n");
    
    return str.toString();
  }
  
  private void findAllInstancesReferences(Hashtable<Reference, Integer> references, 
      Hashtable<Instance, Integer> instances, List<Condition> conditions, Hashtable<String, Relation> relationMap) {
    
    HashSet<Object> visited = new HashSet<Object>();
    for (Condition condition : conditions) {
      for (ConditionTerm term : condition.getConditionTerms()) {
        for (Instance instance : term.getInstances()) {
          findAllInstancesReferences(instance, references, instances, visited);
        }
      }
    }

    // m_relationMap
    Enumeration<String> keys = relationMap.keys();
    while (keys.hasMoreElements()) {
      Relation relation = relationMap.get(keys.nextElement());
      for (int i = 0, size = relation.getFunctionCount(); i < size; i++) {
        Instance[] domainValues = relation.getDomainValues().get(i);
        for (Instance domainValue : domainValues) {
          findAllInstancesReferences(domainValue, references, instances, visited);
        }
        Instance rangeValue = relation.getRangeValues().get(i);
        findAllInstancesReferences(rangeValue, references, instances, visited);
      }
    }
  }
  
  private void findAllInstancesReferences(Instance instance, Hashtable<Reference, Integer> references, 
      Hashtable<Instance, Integer> instances, HashSet<Object> visited) {
    if (instance == null || visited.contains(instance)) {
      return;
    }
    
    visited.add(instance);
    instances.put(instance, instances.size());
    
    findAllInstancesReferences(instance.getLeft(), references, instances, visited);
    findAllInstancesReferences(instance.getRight(), references, instances, visited);
    findAllInstancesReferences(instance.getLastReference(), references, instances, visited);
    for (Reference reference : instance.getFields()) {
      findAllInstancesReferences(reference, references, instances, visited);
    }
  }
  
  private void findAllInstancesReferences(Reference reference, Hashtable<Reference, Integer> references, 
      Hashtable<Instance, Integer> instances, HashSet<Object> visited) {
    if (reference == null || visited.contains(reference)) {
      return;
    }
    
    visited.add(reference);
    references.put(reference, references.size());
    
    for (Instance instance : reference.getInstances()) {
      findAllInstancesReferences(instance, references, instances, visited);
    }
    for (Instance instance : reference.getOldInstances()) {
      findAllInstancesReferences(instance, references, instances, visited);
    }
    findAllInstancesReferences(reference.getDeclaringInstance(), references, instances, visited);
  }
  
  // translate occurrences of read_@@array_ into obj[index] format
  private static final Pattern s_readArrayPattern = Pattern.compile("read_@@array_([0-9]+)");
  protected String replaceArrayRead(String str, Formula satisfiable, Hashtable<String, String> assignedValueMap) {
    Matcher matcher = null;
    while ((matcher = s_readArrayPattern.matcher(str)).find()) {
      String readTime = matcher.group(1);
      
      Relation arrayRel = satisfiable.getRelation("@@array");
      int index = arrayRel.getIndex(readTime);
      Instance[] domainValues = arrayRel.getDomainValues().get(index);
      String arrayRead = domainValues[0].toString() + "[" + replaceArrayIndex(domainValues[1], assignedValueMap) + "]"; 
      
      str = str.replace("read_@@array_" + readTime, arrayRead);
    }
    return str;
  }
  
  // try to replace indexInstance with a concrete numeric value
  protected String replaceArrayIndex(Instance indexInstance, Hashtable<String, String> assignedValueMap) {
    String translated = indexInstance.toString();
    if (indexInstance.isBounded()) { // (v1.all.length - #!1)
      try {
        String leftString  = replaceArrayIndex(indexInstance.getLeft(), assignedValueMap);
        String rightString = replaceArrayIndex(indexInstance.getRight(), assignedValueMap);
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
    else if (!indexInstance.isBounded()) { // v1.length, read_@@array_
      String value = assignedValueMap.get(translated);
      translated = value != null ? value : translated;
    }
    return translated;
  }

  protected String              m_output;
  protected boolean             m_satisfactory;
  protected List<Integer>       m_unsatCoreIds;
  protected List<Condition>     m_conditionList;
  protected List<Condition>     m_partialSatModel;
  protected List<ConditionTerm> m_satModel;

  protected static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
