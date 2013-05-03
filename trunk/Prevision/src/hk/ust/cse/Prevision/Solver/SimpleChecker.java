package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

public class SimpleChecker {

  public static String simpleCheck(Formula formula, NeutralInput neutralInput, boolean retrieveUnsatCore) {
    boolean contradicted = false;
    List<Condition> unsatCoreConds = new ArrayList<Condition>();
    
    // create instance-name mapping
    Hashtable<Instance, String> instanceNameMapping = new Hashtable<Instance, String>();
    if (neutralInput != null) {
      instanceNameMapping = neutralInput.getInstanceNameMapping();
    }

    Hashtable<List<String>, List<Object[]>> statements = 
        new Hashtable<List<String>, List<Object[]>>();
    
    Hashtable<String, List<Condition>> prevInstanceOf = 
        new Hashtable<String, List<Condition>>();
    
    List<Condition> conditions = formula.getConditionList();
    for (int i = 0, size = conditions.size(); i < size && !contradicted; i++) {
      if (conditions.get(i).getConditionTerms().size() != 1) { // only for conditions with one term
        continue;
      }
      ConditionTerm condTerm = conditions.get(i).getConditionTerms().get(0);
      
      if (condTerm instanceof TypeConditionTerm) {
        TypeConditionTerm thisTypeTerm = (TypeConditionTerm) condTerm;
        Class<?> typeClass = Utils.findClass(thisTypeTerm.getTypeString());
        
        if (thisTypeTerm.getComparator() == TypeConditionTerm.Comparator.OP_INSTANCEOF && 
            thisTypeTerm.getInstance1().getLastReference() != null && 
            typeClass != null && !typeClass.isInterface()) {
          String instanceStr1 = thisTypeTerm.getInstance1().getLastReference().getNameWithCallSite();
          List<Condition> prevTypes = prevInstanceOf.get(instanceStr1);
          if (prevTypes == null) {
            prevTypes = new ArrayList<Condition>();
            prevInstanceOf.put(instanceStr1, prevTypes);
          }
          else {
            for (Condition prevType : prevTypes) {
              TypeConditionTerm prevTypeTerm = (TypeConditionTerm) prevType.getConditionTerms().get(0);
              if (!Utils.canCastTo(thisTypeTerm.getTypeString(), prevTypeTerm.getTypeString()) && 
                  !Utils.canCastTo(prevTypeTerm.getTypeString(), thisTypeTerm.getTypeString())) {
                contradicted = true;
                unsatCoreConds.add(prevType);
                unsatCoreConds.add(conditions.get(i));
                break;
              }
            }
          }
          prevTypes.add(conditions.get(i));
        }
      }
      else if (condTerm instanceof BinaryConditionTerm) {
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
          if ((num1 != num2 && op == BinaryConditionTerm.Comparator.OP_EQUAL) || 
              (num1 == num2 && op == BinaryConditionTerm.Comparator.OP_INEQUAL) || 
              (num1 > num2 && op == BinaryConditionTerm.Comparator.OP_SMALLER_EQUAL) ||
              (num1 >= num2 && op == BinaryConditionTerm.Comparator.OP_SMALLER) ||
              (num1 < num2 && op == BinaryConditionTerm.Comparator.OP_GREATER_EQUAL) || 
              (num1 <= num2 && op == BinaryConditionTerm.Comparator.OP_GREATER)) {
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
        List<String> key = Arrays.asList(var1Str, var2Str);
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
        
        // also put in the reverse, e.g. null != v1
        if (!var1Str.equals(var2Str)) {
          key = Arrays.asList(var2Str, var1Str);
          previousOps = statements.get(key);
          if (previousOps == null) {
            previousOps = new ArrayList<Object[]>();
            statements.put(key, previousOps);
          }
          switch (op) {
          case OP_EQUAL:
          case OP_INEQUAL:
            break;
          case OP_GREATER:
            op = Comparator.OP_SMALLER;
            break;
          case OP_GREATER_EQUAL:
            op = Comparator.OP_SMALLER_EQUAL;
            break;
          case OP_SMALLER:
            op = Comparator.OP_GREATER;
            break;
          case OP_SMALLER_EQUAL:
            op = Comparator.OP_GREATER_EQUAL;
          default:
            break;
          }
          previousOps.add(new Object[] {op, conditions.get(i)});
        }
      }
    }
    
    StringBuilder outputStr = new StringBuilder();
    if (contradicted) {
      outputStr.append("unsat").append(LINE_SEPARATOR);
      
      // retrieve unsat core
      if (retrieveUnsatCore) {
        List<Assertion> assertions                    = neutralInput.getAssertions();
        Hashtable<Assertion, List<Condition>> mapping = neutralInput.getAssertionCondsMapping();
        List<Integer> unsatCoreIds = new ArrayList<Integer>();
        for (Condition unsatCoreCond : unsatCoreConds) {
          Enumeration<Assertion> keys = mapping.keys();
          while (keys.hasMoreElements()) {
            Assertion key = (Assertion) keys.nextElement();
            List<Condition> conds = mapping.get(key);
            if (conds.contains(unsatCoreCond)) {
              int index = assertions.indexOf(key);
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
        if (instance.isAtomic()) {
          str = instance.toString();
        }
        else {
          String left  = getInstanceName(instance.getLeft(), formula, instanceNameMapping);
          String right = getInstanceName(instance.getRight(), formula, instanceNameMapping);
          if (left.startsWith("#!") && right.startsWith("#!")) {
            try {
              double leftNum  = Double.parseDouble(left.substring(2));
              double rightNum = Double.parseDouble(right.substring(2));
              switch (instance.getOp()) {
              case ADD:
                str = "#!" + (leftNum + rightNum);
                break;
              case SUB:
                str = "#!" + (leftNum - rightNum);
                break;
              case MUL:
                str = "#!" + (leftNum * rightNum);
                break;
              case DIV:
                str = "#!" + (leftNum / rightNum);
                break;
              case REM:
                str = "#!" + (leftNum % rightNum);
                break;
              default:
                str = instance.toString();
                break;
              }
            } catch (Exception e) {
              str = instance.toString();
            }
          }
          else {
            str = instance.toString();
          }
        }
      }
      else if (instance.isRelationRead()) {
        String relName = instance.getLastReference().getReadRelName();
        long readTime  = instance.getLastReference().getReadRelTime();
        
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
