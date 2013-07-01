package hk.ust.cse.Prevision.VirtualMachine.Executor;

import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;

public class PrepInitFormula {
  
  static List<Formula> prepInitFormula(Formula formulaBeforeExcepLine, MethodMetaData methData, ExecutionOptions execOptions) {
    // get exception line number
    CallStack innerMostCallStack = execOptions.fullCallStack.getInnerMostCallStack();
    int excepLineNo = innerMostCallStack.getCurLineNo();
    
    List<Formula> prepFormulas = null;
    switch (execOptions.exceptionType) {
    case NPE:
      prepFormulas = prepNullPointerException(excepLineNo, formulaBeforeExcepLine, methData);
      break;
    case AIOBE:
      prepFormulas = prepArrayIndexOutOfBoundsException(excepLineNo, formulaBeforeExcepLine, methData);
      break;
    case CUSTOM:
    default:
      prepFormulas = new ArrayList<Formula>();
      prepFormulas.add(formulaBeforeExcepLine);
      break;
    }
    return prepFormulas;
  }
  
  static List<Formula> prepNullPointerException(int excepLineNo, Formula formulaBeforeExcepLine, MethodMetaData methData) {
    List<Formula> prepFormulas = new ArrayList<Formula>();
    
    // find the indices of != null conditions introduced in the exception line
    List<Integer> nonNullIndices = new ArrayList<Integer>();
    List<Condition> conditions = formulaBeforeExcepLine.getConditionList();
    for (int i = 0, size = conditions.size(); i < size; i++) {
      Condition condition = conditions.get(i);
      ConditionTerm term = condition.getConditionTerms().get(0);

      if (condition.getConditionTerms().size() == 1 && term instanceof BinaryConditionTerm) {
        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
        if (binaryTerm.isNotEqualToNull()) { // v1 != null
          // XXX may not always be sound, but should be good in most cases
          ISSABasicBlock createBB = binaryTerm.getInstance2().getCreateBlock(); // where did we create this null
          if (createBB != null && createBB.getMethod().equals(methData.getIR().getMethod())) {
            int firstIndex = createBB.getFirstInstructionIndex();
            int lastIndex  = createBB.getLastInstructionIndex();
            int line1 = firstIndex >= 0 ? methData.getLineNumber(firstIndex) : -1;
            int line2 = lastIndex >= 0 ? methData.getLineNumber(lastIndex) : -1;
            if (line1 == excepLineNo || line2 == excepLineNo) { // created at the exception line
              nonNullIndices.add(i);
            }
          }
        }
      }
    }
    
    // remove duplicate != null conditions
    List<Instance> nonNullInstances = new ArrayList<Instance>();
    for (int i = 0; i < nonNullIndices.size(); i++) {
      int notNullIndex = nonNullIndices.get(i);
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) conditions.get(notNullIndex).getConditionTerms().get(0);
      
      int prevIndex = nonNullInstances.indexOf(binaryTerm.getInstance1());
      if (prevIndex >= 0) {
        nonNullIndices.remove(prevIndex);
        nonNullInstances.remove(prevIndex);
        i--;
      }
      nonNullInstances.add(binaryTerm.getInstance1());
    }

    // create formulas according to the nonNullIndices
    for (int notNullIndex : nonNullIndices) {
      // create a new formula
      Formula formula = formulaBeforeExcepLine.clone();
      //formula.setTimeStamp(formulaBeforeExcepLine.getTimeStamp()); // explicitly set its start time
      formula.setTimeStamp(Long.MIN_VALUE); // avoid being heuristically backtracked
      
      // all conditions before this != null condition are discarded
      for (int i = 0; i < notNullIndex; i++) {
        formula.getConditionList().remove(0);
      }
      
      // substitute the != null condition with = null condition
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) formula.getConditionList().get(0).getConditionTerms().get(0);
      BinaryConditionTerm nullTerm = new BinaryConditionTerm(
          binaryTerm.getInstance1(), BinaryConditionTerm.Comparator.OP_EQUAL, binaryTerm.getInstance2());
      formula.getConditionList().remove(0);
      formula.getConditionList().add(0, new Condition(nullTerm));
      prepFormulas.add(formula);
    }
    
    return prepFormulas;
  }
  
  private static List<Formula> prepArrayIndexOutOfBoundsException(int excepLineNo, 
      Formula formulaBeforeExcepLine, MethodMetaData methData) {
    
    List<Formula> prepFormulas = new ArrayList<Formula>();
    
    // find the indices of >= #!0 conditions introduced in the exception line
    List<Integer> zeroIndices = new ArrayList<Integer>();
    List<Condition> conditions = formulaBeforeExcepLine.getConditionList();
    for (int i = 0, size = conditions.size(); i < size - 1; i++) {
      Condition condition1 = conditions.get(i);
      Condition condition2 = conditions.get(i + 1);
      ConditionTerm term1 = condition1.getConditionTerms().get(0);
      ConditionTerm term2 = condition2.getConditionTerms().get(0);
      
      if (condition1.getConditionTerms().size() == 1 && term1 instanceof BinaryConditionTerm && 
          condition2.getConditionTerms().size() == 1 && term2 instanceof BinaryConditionTerm) {
        BinaryConditionTerm binaryTerm1 = (BinaryConditionTerm) term1;
        BinaryConditionTerm binaryTerm2 = (BinaryConditionTerm) term2;
        
        if (binaryTerm1.getComparator().equals(BinaryConditionTerm.Comparator.OP_GREATER_EQUAL) && 
            binaryTerm1.getInstance2().isAtomic() && binaryTerm1.getInstance2().getValue().equals("#!0")) {

          // very likely, but we need to examine its following term
          if (binaryTerm2.getComparator().equals(BinaryConditionTerm.Comparator.OP_SMALLER) && 
             !binaryTerm2.getInstance2().isBounded() && binaryTerm2.getInstance2().getLastRefName().equals("length")) {
            
            // XXX may not always be sound, but should be good in most cases
            ISSABasicBlock createBB = binaryTerm1.getInstance2().getCreateBlock(); // where did we create this zero
            if (createBB != null && createBB.getMethod().equals(methData.getIR().getMethod())) {
              int firstIndex = createBB.getFirstInstructionIndex();
              int lastIndex  = createBB.getLastInstructionIndex();
              int line1 = firstIndex >= 0 ? methData.getLineNumber(firstIndex) : -1;
              int line2 = lastIndex >= 0 ? methData.getLineNumber(lastIndex) : -1;
              if (line1 == excepLineNo || line2 == excepLineNo) { // created at the exception line
                zeroIndices.add(i);
              }
            }
          }
        }
      }
    }

    // create formulas according to the zeroIndices
    for (int zeroIndex : zeroIndices) {
      // create a new formula
      Formula formula = formulaBeforeExcepLine.clone();
      //formula.setTimeStamp(formulaBeforeExcepLine.getTimeStamp()); // explicitly set its start time
      formula.setTimeStamp(Long.MIN_VALUE); // avoid being heuristically backtracked
      
      // all conditions before this >= #!0 condition are discarded
      for (int i = 0; i < zeroIndex; i++) {
        formula.getConditionList().remove(0);
      }
      
      // all array relation operations before this >= #!0 condition are discarded
      Relation relation = formula.getRelation("@@array");
      for (int i = relation.getFunctionCount() - 1; i >= 0; i--) {
        if (relation.getFunctionTimes().get(i) < formula.getConditionList().get(0).getTimeStamp()) {
          relation.remove(i);
        }
      }
      
      // substitute the >= #!0 condition with < #!0 condition
      BinaryConditionTerm binaryTerm1 = (BinaryConditionTerm) formula.getConditionList().get(0).getConditionTerms().get(0);
      BinaryConditionTerm binaryTerm2 = (BinaryConditionTerm) formula.getConditionList().get(1).getConditionTerms().get(0);        
      BinaryConditionTerm zeroTerm = new BinaryConditionTerm(
          binaryTerm1.getInstance1(), BinaryConditionTerm.Comparator.OP_SMALLER, binaryTerm1.getInstance2());
      BinaryConditionTerm lengthTerm = new BinaryConditionTerm(
          binaryTerm2.getInstance1(), BinaryConditionTerm.Comparator.OP_GREATER_EQUAL, binaryTerm2.getInstance2());
      List<ConditionTerm> terms = new ArrayList<ConditionTerm>();
      terms.add(zeroTerm);
      terms.add(lengthTerm);
      
      formula.getConditionList().remove(0); // remove original zero term
      formula.getConditionList().remove(0); // remove original length term
      formula.getConditionList().add(0, new Condition(terms));
      prepFormulas.add(formula);
    }
    
    return prepFormulas;
  }
//  
//  static List<Formula> prepInitFormula(WalaAnalyzer walaAnalyzer, BBorInstInfo initInfoItem, 
//      ExecutionOptions execOptions, CallStack callStack, boolean forward) {
//    
//    // get exception line number
//    CallStack innerCallStack = execOptions.fullCallStack;
//    while (innerCallStack.getDepth() > 1) {
//      innerCallStack = innerCallStack.getInnerCallStack();
//    }
//    int excepLineNo = innerCallStack.getCurLineNo();
//    
//    List<Formula> prepFormulas = null;
//    switch (execOptions.exceptionType) {
//    case NPE:
//      prepFormulas = prepNullPointerException(walaAnalyzer, excepLineNo, initInfoItem, execOptions, callStack, forward);
//      break;
//    case AIOBE:
//      prepFormulas = prepArrayIndexOutOfBoundsException(excepLineNo, initInfoItem, execOptions, callStack, forward);
//      break;
//    case CUSTOM:
//    default:
//      prepFormulas = new ArrayList<Formula>();
//      prepFormulas.add(new Formula(forward));
//      break;
//    }
//    return prepFormulas;
//  }
//  
//  private static List<Formula> prepNullPointerException(WalaAnalyzer walaAnalyzer, int excepLineNo, 
//      BBorInstInfo initInfoItem, ExecutionOptions execOptions, CallStack callStack, boolean forward) {
//    List<Formula> prepFormulas = new ArrayList<Formula>();
//    
//    // should be a "TRUE" Formula to begin with
//    Formula initFormula = new Formula(forward);
//    long timeStamp = initFormula.getTimeStamp();
//
//    MethodMetaData methData = initInfoItem.methData;
//    int index = methData.getLastInstructionIndexForLine(excepLineNo);
//    if (index >= 0) {
//      // go through the exception line
//      List<Integer> nonNullIndices = new ArrayList<Integer>();
//      SSAInstruction[] methodInsts = methData.getcfg().getInstructions();
//      for (int i = index; i >= 0; i--) {
//        if (methodInsts[i] == null) {
//          continue;
//        }
//        
//        int currLine = methData.getLineNumber(i);
//        if (currLine == excepLineNo) {
//          BBorInstInfo instInfo = initInfoItem.executor.new BBorInstInfo(initInfoItem.currentBB, 
//              initInfoItem.startingBB, initInfoItem.skipToBB, initFormula, initFormula, Formula.NORMAL_SUCCESSOR, 
//              initInfoItem.previousBB, initInfoItem.previousInfo, methData, 
//              initInfoItem.callSites, initInfoItem.workList, initInfoItem.executor);
//          
//          // find dispatch target of the invocation instruction
//          if (methodInsts[i] instanceof SSAInvokeInstruction) {
//            SSAInvokeInstruction invokeInst = (SSAInvokeInstruction) methodInsts[i];
//            Object[] ret = AbstractExecutor.findInvokeTargets(
//                walaAnalyzer, null, invokeInst.getCallSite(), invokeInst.getDeclaredTarget(), 1);
//            IR[] targetIRs = (IR[]) ret[0];
//            
//            // if found new targets, add them
//            if (targetIRs != null && targetIRs.length > 0 && targetIRs[0] != null && 
//               !targetIRs[0].getMethod().getSignature().equals(invokeInst.getDeclaredTarget().getSignature())) {
//              instInfo.target = new Object[] {invokeInst, targetIRs[0], null};
//            }
//          }
//          
//          // handle instruction
//          int oriSize = initFormula.getConditionList().size();
//          initFormula = initInfoItem.executor.getInstructionHandler().handle(execOptions, null, 
//              initFormula, methodInsts[i], instInfo, callStack, 1 /* get into one level */);
//          
//          if (!(methodInsts[i] instanceof SSAArrayReferenceInstruction) && 
//              !(methodInsts[i] instanceof SSAArrayLengthInstruction) && 
//              !(methodInsts[i] instanceof SSAGetInstruction) && 
//              !(methodInsts[i] instanceof SSAInvokeInstruction) && 
//              !(methodInsts[i] instanceof SSAPutInstruction) && 
//              !(methodInsts[i] instanceof SSACheckCastInstruction)) {
//            continue;
//          }
//
//          // collect the indices of the newly added != statements
//          if (!(methodInsts[i] instanceof SSAInvokeInstruction) || ((SSAInvokeInstruction) methodInsts[i]).isStatic()) {
//            for (int j = oriSize, size = initFormula.getConditionList().size(); j < size; j++) {
//              Condition condition = initFormula.getConditionList().get(j);
//              ConditionTerm term = condition.getConditionTerms().get(0);
//
//              if (condition.getConditionTerms().size() == 1 && term instanceof BinaryConditionTerm) {
//                BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
//                if (binaryTerm.getComparator().equals(BinaryConditionTerm.Comparator.OP_INEQUAL) && 
//                    binaryTerm.getInstance2().isAtomic() && binaryTerm.getInstance2().getValue().equals("null")) { // v1 != null
//                  nonNullIndices.add(j);
//                }
//              }
//            }
//          }
//          else {
//            for (int j = initFormula.getConditionList().size() - 1; j >= oriSize; j--) {
//              Condition condition = initFormula.getConditionList().get(j);
//              ConditionTerm term = condition.getConditionTerms().get(0);
//
//              if (condition.getConditionTerms().size() == 1 && term instanceof BinaryConditionTerm) {
//                BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
//                if (binaryTerm.getComparator().equals(BinaryConditionTerm.Comparator.OP_INEQUAL) && 
//                    binaryTerm.getInstance2().isAtomic() && binaryTerm.getInstance2().getValue().equals("null")) { // v1 != null
//
//                  // if we got into invoke target, there may be multiple != null statements, 
//                  // but we are only interested in the last one which is caller != null
//                  nonNullIndices.add(j);
//                  break;
//                }
//              }
//            }
//          }
//        }
//        else {
//          break;
//        }
//      }
//      
//      // remove duplicate != null conditions
//      List<Instance> nonNullInstances = new ArrayList<Instance>();
//      for (int i = 0; i < nonNullIndices.size(); i++) {
//        int notNullIndex = nonNullIndices.get(i);
//        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) 
//            initFormula.getConditionList().get(notNullIndex).getConditionTerms().get(0);
//        
//        int prevIndex = nonNullInstances.indexOf(binaryTerm.getInstance1());
//        if (prevIndex >= 0) {
//          nonNullIndices.remove(prevIndex);
//          nonNullInstances.remove(prevIndex);
//          i--;
//        }
//        nonNullInstances.add(binaryTerm.getInstance1());
//      }
//
//      // create formulas according to the nonNullIndices
//      for (int notNullIndex : nonNullIndices) {
//        // create a new formula
//        Formula formula = initFormula.clone();
//        formula.setTimeStamp(timeStamp); // explicitly set its start time
//        
//        // all conditions before this != null condition are discarded
//        for (int i = 0; i < notNullIndex; i++) {
//          formula.getConditionList().remove(0);
//        }
//        
//        // substitue the != null condition with = null condition
//        BinaryConditionTerm binaryTerm = (BinaryConditionTerm) formula.getConditionList().get(0).getConditionTerms().get(0);
//        BinaryConditionTerm nullTerm = new BinaryConditionTerm(
//            binaryTerm.getInstance1(), BinaryConditionTerm.Comparator.OP_EQUAL, binaryTerm.getInstance2());
//        formula.getConditionList().remove(0);
//        formula.getConditionList().add(0, new Condition(nullTerm));
//        prepFormulas.add(formula);
//      }
//    }
//    
//    return prepFormulas;
//  }
//  
//  //XXX
//  private static List<Formula> prepArrayIndexOutOfBoundsException(int excepLineNo, 
//      BBorInstInfo initInfoItem, ExecutionOptions execOptions, CallStack callStack, boolean forward) {
//    
//    // should be a "TRUE" Formula to begin with
//    Formula initFormula = new Formula(forward);
//
//    MethodMetaData methData = initInfoItem.methData;
//    int index = methData.getLastInstructionIndexForLine(excepLineNo);
//    if (index >= 0) {
//      // go through exception line
//      int orCondIndex = -1;
//      boolean started = false;
//      SSAInstruction[] methodInsts = methData.getcfg().getInstructions();
//      for (int i = index; i >= 0; i--) {
//        if (methodInsts[i] == null) {
//          continue;
//        }
//        
//        int currLine = methData.getLineNumber(i);
//        if (currLine == excepLineNo) {
//          if (!started && !(methodInsts[i] instanceof SSAArrayReferenceInstruction)) {
//            continue;
//          }
//          started = true;
//          
//          BBorInstInfo instInfo = initInfoItem.executor.new BBorInstInfo(initInfoItem.currentBB, 
//              initInfoItem.startingBB, initInfoItem.skipToBB, initFormula, initFormula, Formula.NORMAL_SUCCESSOR, 
//              initInfoItem.previousBB, initInfoItem.previousInfo, methData, 
//              initInfoItem.callSites, initInfoItem.workList, initInfoItem.executor);
//          initFormula = initInfoItem.executor.getInstructionHandler().handle(execOptions, null, 
//              initFormula, methodInsts[i], instInfo, callStack, 0 /* do enter invocation */);
//          
//          // merge all to form the or condition
//          List<Condition> conditionsToRemove = new ArrayList<Condition>();
//          List<Condition> conditionsList = initFormula.getConditionList();
//          for (int j = 0; j < conditionsList.size(); j++) {
//            Condition condition = conditionsList.get(j);
//            ConditionTerm term = condition.getConditionTerms().get(0);
//            
//            if (condition.getConditionTerms().size() == 1 && term instanceof BinaryConditionTerm) {
//              BinaryConditionTerm binaryTerm = (BinaryConditionTerm) term;
//              ConditionTerm changedTerm = null;
//              if (binaryTerm.getComparator().equals(BinaryConditionTerm.Comparator.OP_GREATER_EQUAL) && 
//                  binaryTerm.getInstance2().isAtomic() && binaryTerm.getInstance2().getValue().equals("#!0")) {
//                changedTerm = new BinaryConditionTerm(
//                    binaryTerm.getInstance1(), BinaryConditionTerm.Comparator.OP_SMALLER, binaryTerm.getInstance2());
//              }
//              else if (binaryTerm.getComparator().equals(BinaryConditionTerm.Comparator.OP_SMALLER) && 
//                       !binaryTerm.getInstance2().isBounded() && binaryTerm.getInstance2().getLastRefName().equals("length")) {
//                changedTerm = new BinaryConditionTerm(
//                    binaryTerm.getInstance1(), BinaryConditionTerm.Comparator.OP_GREATER_EQUAL, binaryTerm.getInstance2());
//              }
//            
//              if (changedTerm != null) {
//                if (orCondIndex == -1) {
//                  condition.getConditionTerms().remove(0);
//                  condition.getConditionTerms().add(changedTerm);
//                  orCondIndex = j;
//                }
//                else if (j != orCondIndex) {
//                  conditionsList.get(orCondIndex).getConditionTerms().add(changedTerm);
//                  conditionsToRemove.add(condition);
//                }
//              }
//            }
//          }
//          initFormula.getConditionList().removeAll(conditionsToRemove);
//        }
//        else {
//          break;
//        }
//      }
//    }
//    
//    List<Formula> prepFormulas = new ArrayList<Formula>();
//    prepFormulas.add(initFormula);
//    return prepFormulas;
//  }
}
