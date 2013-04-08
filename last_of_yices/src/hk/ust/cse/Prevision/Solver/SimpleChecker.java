package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.ICommand.TranslatedCommand;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Relation;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class SimpleChecker {

  public static String simpleCheck(Formula formula, TranslatedCommand translatedCmd, boolean retrieveUnsatCore) {
    boolean contradicted = false;
    List<Condition> unsatCoreConds = new ArrayList<Condition>();
    
    // create instance-name mapping
    Hashtable<Instance, String> instanceNameMapping = new Hashtable<Instance, String>();
    if (translatedCmd != null) {
      instanceNameMapping = translatedCmd.instanceNameMapping;
    }

    Hashtable<List<String>, List<Object[]>> statements = 
      new Hashtable<List<String>, List<Object[]>>();
    
    List<Condition> conditions = formula.getConditionList();
    for (int i = 0, size = conditions.size(); i < size && !contradicted; i++) {
      if (conditions.get(i).getConditionTerms().size() != 1) { // only for conditions with one term
        continue;
      }
      ConditionTerm condTerm = conditions.get(i).getConditionTerms().get(0);
      
      if (!(condTerm instanceof BinaryConditionTerm)) { // only for binary conditions
        continue;
      }
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) condTerm;
      
      BinaryConditionTerm.Comparator op = binaryTerm.getComparator();
      Instance instance1 = binaryTerm.getInstance1();
      Instance instance2 = binaryTerm.getInstance2();
      
      String var1Str = getInstanceName(instance1, formula, instanceNameMapping);
      String var2Str = getInstanceName(instance2, formula, instanceNameMapping);

      // simplify rule 1: v1 != v1
      if (var1Str.equals(var2Str) && 
         (op == BinaryConditionTerm.Comparator.OP_GREATER || 
          op == BinaryConditionTerm.Comparator.OP_INEQUAL || 
          op == BinaryConditionTerm.Comparator.OP_SMALLER)) {
        contradicted = true;
        unsatCoreConds.add(conditions.get(i));
        continue;
      }
      
      // simplify rule 2: #!1 == #!0
      if (var1Str.startsWith("#!") && var2Str.startsWith("#!")) {
        double num1 = Double.parseDouble(var1Str.substring(2));
        double num2 = Double.parseDouble(var2Str.substring(2));
        if (num1 != num2 && op == BinaryConditionTerm.Comparator.OP_EQUAL) {
          contradicted = true;
          unsatCoreConds.add(conditions.get(i));
          continue;
        }
      }
      
      // simplify rule 3: ##str == null
      if (op == BinaryConditionTerm.Comparator.OP_EQUAL && 
         ((var1Str.startsWith("##") && var2Str.equals("null")) || 
          (var2Str.startsWith("##") && var1Str.equals("null")))) {
        contradicted = true;
        unsatCoreConds.add(conditions.get(i));
        continue;
      }
      
      // simplify rule 4: 
      List<String> key = new ArrayList<String>();
      key.add(var1Str);
      key.add(var2Str);
      
      List<Object[]> previousOps = statements.get(key);
      if (previousOps == null) {
        previousOps = new ArrayList<Object[]>();
        statements.put(key, previousOps);
      }
      
      for (int j = 0, size2 = previousOps.size(); j < size2 && !contradicted; j++) {
       BinaryConditionTerm.Comparator previousOp = (Comparator) previousOps.get(j)[0];
       Condition previousOpCondition = (Condition) previousOps.get(j)[1];
        
        if (previousOp == BinaryConditionTerm.Comparator.OP_EQUAL && 
           (op == BinaryConditionTerm.Comparator.OP_GREATER || 
            op == BinaryConditionTerm.Comparator.OP_INEQUAL || 
            op == BinaryConditionTerm.Comparator.OP_SMALLER)) {
          contradicted = true;
          unsatCoreConds.add(previousOpCondition);
          unsatCoreConds.add(conditions.get(i));
        }
        else if (previousOp == BinaryConditionTerm.Comparator.OP_GREATER && 
                (op == BinaryConditionTerm.Comparator.OP_EQUAL || 
                 op == BinaryConditionTerm.Comparator.OP_SMALLER || 
                 op == BinaryConditionTerm.Comparator.OP_SMALLER_EQUAL)) {
          contradicted = true;
          unsatCoreConds.add(previousOpCondition);
          unsatCoreConds.add(conditions.get(i));
        }     
        else if (previousOp == BinaryConditionTerm.Comparator.OP_GREATER_EQUAL && 
                (op == BinaryConditionTerm.Comparator.OP_SMALLER)) {
          contradicted = true;
          unsatCoreConds.add(previousOpCondition);
          unsatCoreConds.add(conditions.get(i));
        }
        else if (previousOp == BinaryConditionTerm.Comparator.OP_INEQUAL && 
                (op == BinaryConditionTerm.Comparator.OP_EQUAL)) {
          contradicted = true;
          unsatCoreConds.add(previousOpCondition);
          unsatCoreConds.add(conditions.get(i));
        }       
        else if (previousOp == BinaryConditionTerm.Comparator.OP_SMALLER && 
                (op == BinaryConditionTerm.Comparator.OP_EQUAL || 
                 op == BinaryConditionTerm.Comparator.OP_GREATER || 
                 op == BinaryConditionTerm.Comparator.OP_GREATER_EQUAL)) {
          contradicted = true;
          unsatCoreConds.add(previousOpCondition);
          unsatCoreConds.add(conditions.get(i));
        } 
        else if (previousOp == BinaryConditionTerm.Comparator.OP_SMALLER_EQUAL && 
                (op == BinaryConditionTerm.Comparator.OP_GREATER)) {
          contradicted = true;
          unsatCoreConds.add(previousOpCondition);
          unsatCoreConds.add(conditions.get(i));
        }
      }
      previousOps.add(new Object[] {op, conditions.get(i)});
    }
    
    StringBuilder outputStr = new StringBuilder();
    if (contradicted) {
      outputStr.append("unsat").append(LINE_SEPARATOR);
      
      // retrieve unsat core
      if (retrieveUnsatCore) {
        List<String> assertCmds                    = translatedCmd.assertCmds;
        Hashtable<String, List<Condition>> mapping = translatedCmd.assertCmdCondsMapping;
        List<Integer> unsatCoreIds = new ArrayList<Integer>();
        for (Condition unsatCoreCond : unsatCoreConds) {
          Enumeration<String> keys = mapping.keys();
          while (keys.hasMoreElements()) {
            String key = (String) keys.nextElement();
            List<Condition> conds = mapping.get(key);
            if (conds.contains(unsatCoreCond)) {
              int index = assertCmds.indexOf(key);
              if (index >= 0) {
                unsatCoreIds.add(index + 1);
              }
            }
          }
        }
        if (unsatCoreIds.size() > 0) {
          outputStr.append("unsat core ids: ");
          for (int i = 0, size = unsatCoreIds.size(); i < size; i++) {
            outputStr.append(unsatCoreIds.get(i));
            if (i != size - 1) {
              outputStr.append(" ");
            }
          }
          outputStr.append(LINE_SEPARATOR);
        }
      }
      outputStr.append("Proven contradicted by simple checker.");
    }
    else {
      outputStr.append("sat");
    }
    
    return outputStr.toString();
  }
    
  private static String getInstanceName(Instance instance, Formula formula, Hashtable<Instance, String> instanceNameMapping) {
    // find in mapping first if any
    String str = instanceNameMapping.get(instance);
    
    if (str == null) {
      if (instance.isBounded()) {
        str = instance.toString();
      }
      else if (instance.isRelationRead()) {
        String relName = Relation.getReadStringRelName(instance.getLastRefName());
        long readTime  = Relation.getReadStringTime(instance.getLastRefName());
        
        Relation relation = formula.getAbstractMemory().getRelation(relName);
        int index = relation.getIndex(readTime);
        if (index >= 0) {
          Instance[] domainValues = relation.getDomainValues().get(index);
          str = "(" + relName;
          for (Instance domainValue : domainValues) {
            str += " " + getInstanceName(domainValue, formula, instanceNameMapping);
          }
          str += ")";
        }
        else {
          str = instance.getLastReference().getLongNameWithCallSites();
        }
      }
      else {
        str = instance.getLastReference().getLongNameWithCallSites();
      }
    }
    
    return str;
  }
  
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
