package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Executor.BBorInstInfo;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.List;

import com.ibm.wala.ssa.SSAArrayReferenceInstruction;
import com.ibm.wala.ssa.SSACheckCastInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;

public class PrepInitFormula {

  static Formula prepInitFormula(BBorInstInfo initInfoItem, ExecutionOptions execOptions, CallStack callStack) {
    
    // get exception line number
    CallStack innerCallStack = execOptions.fullCallStack;
    while (innerCallStack.getDepth() > 1) {
      innerCallStack = innerCallStack.getInnerCallStack();
    }
    int excepLineNo = innerCallStack.getCurLineNo();
    
    switch (execOptions.exceptionType) {
    case CUSTOM:
      return new Formula();
    case NPE:
      return prepNullPointerException(excepLineNo, initInfoItem, callStack);
    case AIOBE:
      return prepArrayIndexOutOfBoundsException(excepLineNo, initInfoItem, callStack);
    default:
      return new Formula();
    }
  }
  
  private static Formula prepNullPointerException(int excepLineNo, BBorInstInfo initInfoItem, CallStack callStack) {
    
    // should be a "TRUE" Formula to begin with
    Formula initFormula = new Formula();

    MethodMetaData methData = initInfoItem.methData;
    int index = methData.getLastInstructionIndexForLine(excepLineNo);
    if (index >= 0) {
      // a dummy optAndStates
      ExecutionOptions execOptions = new ExecutionOptions(null, false);
      execOptions.finishedEnteringCallStack();
      
      // go through exception line
      int orCondIndex = -1;
      SSAInstruction[] methodInsts = methData.getcfg().getInstructions();
      for (int i = index; i >= 0; i--) {
        if (methodInsts[i] == null) {
          continue;
        }
        
        int currLine = methData.getLineNumber(i);
        if (currLine == excepLineNo) {
          int oriSize = initFormula.getConditionList().size();
          BBorInstInfo instInfo = initInfoItem.executor.new BBorInstInfo(initInfoItem.currentBB, 
              initInfoItem.isSkipToBB, initFormula, initFormula, Formula.NORMAL_SUCCESSOR, 
              initInfoItem.sucessorBB, initInfoItem.sucessorInfo, methData, 
              initInfoItem.callSites, initInfoItem.workList, initInfoItem.executor);
          initFormula = initInfoItem.executor.getInstructionHandler().handle(execOptions, null, 
              initFormula, methodInsts[i], instInfo, callStack, Integer.MAX_VALUE /* do not get into method */);
          
          if (!(methodInsts[i] instanceof SSAArrayReferenceInstruction) && 
              !(methodInsts[i] instanceof SSAGetInstruction) && 
              !(methodInsts[i] instanceof SSAInvokeInstruction) && 
              !(methodInsts[i] instanceof SSAPutInstruction) && 
              !(methodInsts[i] instanceof SSACheckCastInstruction)) {
            // we only need the reference assignments
            for (int j = oriSize; j < initFormula.getConditionList().size(); j++) {
              initFormula.getConditionList().remove(j--);
            }
          }
          
          // merge all to form the or condition
          List<Condition> conditionsToRemove = new ArrayList<Condition>();
          List<Condition> conditionsList = initFormula.getConditionList();
          for (int j = 0; j < conditionsList.size(); j++) {
            Condition condition = conditionsList.get(j);
            ConditionTerm term = condition.getConditionTerms().get(0);

            if (condition.getConditionTerms().size() == 1 && term.getComparator().equals(ConditionTerm.Comparator.OP_INEQUAL) && 
                term.getInstance2().isAtomic() && term.getInstance2().getValue().equals("null")) {
              ConditionTerm nullTerm = 
                new ConditionTerm(term.getInstance1(), ConditionTerm.Comparator.OP_EQUAL, term.getInstance2());
              if (orCondIndex == -1) {
                condition.getConditionTerms().remove(0);
                condition.getConditionTerms().add(nullTerm);
                // remove the previous conditions
                for (int k = 0; k < j; k++) {
                  conditionsToRemove.add(conditionsList.get(k));
                }
                orCondIndex = 0;
              }
              else if (j != orCondIndex) {
                conditionsList.get(orCondIndex).getConditionTerms().add(nullTerm);
                conditionsToRemove.add(condition);
              }
            }
          }
          initFormula.getConditionList().removeAll(conditionsToRemove);
        }
        else {
          break;
        }
      }
    }
    
    return initFormula;
  }
  
  //XXX
  private static Formula prepArrayIndexOutOfBoundsException(int excepLineNo, BBorInstInfo initInfoItem, CallStack callStack) {
    
    // should be a "TRUE" Formula to begin with
    Formula initFormula = new Formula();

    MethodMetaData methData = initInfoItem.methData;
    int index = methData.getLastInstructionIndexForLine(excepLineNo);
    if (index >= 0) {
      // a dummy optAndStates
      ExecutionOptions execOptions = new ExecutionOptions(null, false);
      execOptions.finishedEnteringCallStack();
      
      // go through exception line
      int orCondIndex = -1;
      boolean started = false;
      SSAInstruction[] methodInsts = methData.getcfg().getInstructions();
      for (int i = index; i >= 0; i--) {
        if (methodInsts[i] == null) {
          continue;
        }
        
        int currLine = methData.getLineNumber(i);
        if (currLine == excepLineNo) {
          if (!started && !(methodInsts[i] instanceof SSAArrayReferenceInstruction)) {
            continue;
          }
          started = true;
          
          BBorInstInfo instInfo = initInfoItem.executor.new BBorInstInfo(initInfoItem.currentBB, 
              initInfoItem.isSkipToBB, initFormula, initFormula, Formula.NORMAL_SUCCESSOR, 
              initInfoItem.sucessorBB, initInfoItem.sucessorInfo, methData, 
              initInfoItem.callSites, initInfoItem.workList, initInfoItem.executor);
          initFormula = initInfoItem.executor.getInstructionHandler().handle(execOptions, null, 
              initFormula, methodInsts[i], instInfo, callStack, 0 /* do enter invocation */);
          
          // merge all to form the or condition
          List<Condition> conditionsToRemove = new ArrayList<Condition>();
          List<Condition> conditionsList = initFormula.getConditionList();
          for (int j = 0; j < conditionsList.size(); j++) {
            Condition condition = conditionsList.get(j);

            if (condition.getConditionTerms().size() == 1) {
              ConditionTerm term = condition.getConditionTerms().get(0);
              ConditionTerm changedTerm = null;
              if (term.getComparator().equals(ConditionTerm.Comparator.OP_GREATER_EQUAL) && 
                  term.getInstance2().isAtomic() && term.getInstance2().getValue().equals("#!0")) {
                changedTerm = new ConditionTerm(term.getInstance1(), ConditionTerm.Comparator.OP_SMALLER, term.getInstance2());;
              }
              else if (term.getComparator().equals(ConditionTerm.Comparator.OP_SMALLER) && 
                       !term.getInstance2().isBounded() && term.getInstance2().getLastReference().getName().equals("length")) {
                changedTerm = new ConditionTerm(term.getInstance1(), ConditionTerm.Comparator.OP_GREATER_EQUAL, term.getInstance2());;
              }
            
              if (changedTerm != null) {
                if (orCondIndex == -1) {
                  condition.getConditionTerms().remove(0);
                  condition.getConditionTerms().add(changedTerm);
                  orCondIndex = j;
                }
                else if (j != orCondIndex) {
                  conditionsList.get(orCondIndex).getConditionTerms().add(changedTerm);
                  conditionsToRemove.add(condition);
                }
              }
            }
          }
          initFormula.getConditionList().removeAll(conditionsToRemove);
        }
        else {
          break;
        }
      }
    }
    
    return initFormula;
  }
}
