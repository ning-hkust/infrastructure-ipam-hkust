package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Executor.BBorInstInfo;
import hk.ust.cse.Prevision.VirtualMachine.Executor.GlobalOptionsAndStates;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.List;

import com.ibm.wala.ssa.SSAInstruction;

public class PrepInitFormula {

  static Formula prepInitFormula(BBorInstInfo initInfoItem, GlobalOptionsAndStates optAndStates, CallStack callStack) {
    
    // get exception line number
    CallStack innerCallStack = optAndStates.fullCallStack;
    while (innerCallStack.getDepth() > 1) {
      innerCallStack = innerCallStack.getInnerCallStack();
    }
    int excepLineNo = innerCallStack.getCurLineNo();
    
    switch (optAndStates.exceptionType) {
    case CUSTOM:
      return new Formula();
    case NPE:
      return prepNullPointerException(excepLineNo, initInfoItem, callStack);
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
      GlobalOptionsAndStates optAndStates = initInfoItem.executor.new GlobalOptionsAndStates(null, false);
      optAndStates.finishedEnteringCallStack(); // never enter invocation
      
      // go through exception line
      int orCondIndex = -1;
      SSAInstruction[] methodInsts = methData.getcfg().getInstructions();
      for (int i = index; i >= 0; i--) {
        if (methodInsts[i] == null) {
          continue;
        }
        
        int currLine = methData.getLineNumber(i);
        if (currLine == excepLineNo) {
          BBorInstInfo instInfo = initInfoItem.executor.new BBorInstInfo(initInfoItem.currentBB, 
              initInfoItem.isSkipToBB, initFormula, initFormula, Formula.NORMAL_SUCCESSOR, 
              initInfoItem.sucessorBB, initInfoItem.sucessorInfo, methData, 
              initInfoItem.callSites, initInfoItem.workList, initInfoItem.executor);
          initFormula = initInfoItem.executor.getInstructionHandler().handle(optAndStates, null, 
              initFormula, methodInsts[i], instInfo, callStack, Integer.MAX_VALUE /* do not get into method */);
          
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
                orCondIndex = j;
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
}
