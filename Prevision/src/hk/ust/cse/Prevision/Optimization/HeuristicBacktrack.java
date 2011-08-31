package hk.ust.cse.Prevision.Optimization;

import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.Executor.BBorInstInfo;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Stack;

import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public class HeuristicBacktrack {

  public HeuristicBacktrack(SMTChecker smtChecker) {
    m_smtChecker       = smtChecker;
    m_backTracking     = false;
    m_continueInMethod = false;
    m_watchList        = new Hashtable<BBorInstInfo, Object[]>();
  }
  
  @SuppressWarnings("unchecked")
  public void backtrack(Stack<BBorInstInfo> workList, int curInvokeDepth, int maxInvokeDepth) {
    // find unsat core, only the latest condition is retrieved
    List<Condition> unsatCores = findUnsatCores();
    if (unsatCores.size() > 0) {
      if (!m_backTracking && !m_continueInMethod) {
        Enumeration<BBorInstInfo> keys = m_watchList.keys();
        while (keys.hasMoreElements()) {
          // save unsat core condition to every current invocation
          Object[] unsatCoresData = m_watchList.get(keys.nextElement());
          if (unsatCoresData.length > 0 /* not a dummy place holder */) {
            ((List<Condition>) unsatCoresData[1]).addAll(unsatCores);
          }
        }

        System.out.println("Heuristic Backtracing: found a backtrackable unsat core: " + unsatCores + ", backtracking...");
        m_backTracking = true;
      }
      
      // try to backtrack current workList
      boolean continueInMethod  = false;
      boolean backTrackFinished = false;
      long eariestSet = findUnsatCoreEarliestSet();
      while (!workList.empty() && !backTrackFinished) {
        BBorInstInfo next = workList.peek();
        if (eariestSet < next.postCond.getTimeStamp()) {
          backTrackFinished = false;
        }
        else {
          // check if the current invocation target is backtrack-able
          backTrackFinished = true;
          SSAInstruction inst = next.currentBB.getLastInstructionIndex() >= 0 ? 
                                          next.currentBB.getLastInstruction() : null;
          if (inst != null && inst instanceof SSAInvokeInstruction && curInvokeDepth < maxInvokeDepth) {
            Object[] unsatCoresData = m_watchList.get(next);
            if (unsatCoresData != null && unsatCoresData.length > 0) { //  invocation does in watch list, also not a dummy place holder
              boolean affectable = false;
              List<Condition> prevUnsatCores      = (List<Condition>) unsatCoresData[1];
              List<Condition> prevCondsOnOrBefore = (List<Condition>) unsatCoresData[0];
              HashSet<Long> prevCondsOnOrBeforeTimes = new HashSet<Long>();
              for (Condition prevCondOnOrBefore : prevCondsOnOrBefore) {
                prevCondsOnOrBeforeTimes.add(prevCondOnOrBefore.getTimeStamp());
              } 
              for (int i = 0, size = prevUnsatCores.size(); i < size && !affectable; i++) {
                Condition unsatCoreCond = prevUnsatCores.get(i);
                long instance1Time = unsatCoreCond.getConditionTerms().get(0).getInstance1().getSetValueTime();
                long instance2Time = unsatCoreCond.getConditionTerms().get(0).getInstance2().getSetValueTime();
                if ((instance1Time > next.postCond.getTimeStamp() || instance2Time > next.postCond.getTimeStamp()) && 
                    prevCondsOnOrBeforeTimes.contains(unsatCoreCond.getTimeStamp())) {
                  affectable       = true;
                  continueInMethod = true;
                }
                else if ((instance1Time == Long.MIN_VALUE || instance2Time == Long.MIN_VALUE) && 
                         !prevCondsOnOrBeforeTimes.contains(unsatCoreCond.getTimeStamp())) { // may change assignment in invocation
                  affectable       = true;
                  continueInMethod = true;
                }
              }
              // we will continue back track inside the method
              backTrackFinished = affectable;
            }
          }
        }
        
        if (!backTrackFinished) {
          workList.pop();
          System.out.println("Backtracked " + next.currentBB);

          // since we backtracked, the current invocation no longer under watch list
          removeFromWatchList(next);
        }
      }
      m_backTracking     = !backTrackFinished;
      m_continueInMethod = continueInMethod;
    }
  }
  
  public boolean isBacktracking() {
    return m_backTracking;
  }
  
  public boolean isContinueInMethod() {
    return m_continueInMethod;
  }
  
  public void addToWatchList(BBorInstInfo infoItem, Formula precond, int prevCondListSize) {
    if (!m_watchList.containsKey(infoItem) || m_watchList.get(infoItem).length > 0) {
      List<Condition> condsOnOrBefore = new ArrayList<Condition>(precond.getConditionList());
      m_watchList.put(infoItem, new Object[] {condsOnOrBefore, new ArrayList<Condition>()});
      
      SSAInstruction inst = infoItem.currentBB.getLastInstructionIndex() >= 0 ? 
                            infoItem.currentBB.getLastInstruction() : null;
      if (inst != null && inst instanceof SSAInvokeInstruction && !((SSAInvokeInstruction) inst).isStatic()) {
        condsOnOrBefore.remove(prevCondListSize); // remove the receiver != null condition
      }
    }
  }
  
  public void removeFromWatchList(BBorInstInfo infoItem) {
    m_watchList.remove(infoItem);
  }
  
  public void clearWatchList() {
    Enumeration<BBorInstInfo> keys = m_watchList.keys();
    while (keys.hasMoreElements()) {
      m_watchList.put(keys.nextElement(), new Object[0] /* dummy place holder */);
    }
  }
  
  private List<Condition> findUnsatCores() {
    List<Condition> unsatCores = new ArrayList<Condition>();

    List<Integer> unsatCoreIds                 = m_smtChecker.getLastResult().getUnsatCoreIds();
    List<String> assertCmds                    = m_smtChecker.getLastTranslatedCommand().assertCmds;
    Hashtable<String, List<Condition>> mapping = m_smtChecker.getLastTranslatedCommand().assertCmdCondsMapping;
    
    if (unsatCoreIds != null && unsatCoreIds.size() > 0) {
      for (Integer integer : unsatCoreIds) {
        String unsatCoreCmd = assertCmds.get(integer - 1);
        List<Condition> unsatCoreConditions = mapping.get(unsatCoreCmd);
        if (unsatCoreConditions != null && unsatCoreConditions.size() > 0) {
          Condition unsatCore = null;
          for (Condition unsatCoreCondition : unsatCoreConditions) {
            if (unsatCore == null || unsatCoreCondition.getTimeStamp() > unsatCore.getTimeStamp()) {
              unsatCore = unsatCoreCondition; // get the latest one
            }
          }
          unsatCores.add(unsatCore);
        }
      }
    }
    return unsatCores;
  }
  
  private long findUnsatCoreEarliestSet() {

    List<Integer> unsatCoreIds                 = m_smtChecker.getLastResult().getUnsatCoreIds();
    List<String> assertCmds                    = m_smtChecker.getLastTranslatedCommand().assertCmds;
    Hashtable<String, List<Condition>> mapping = m_smtChecker.getLastTranslatedCommand().assertCmdCondsMapping;
    
    // we only deal with the simplest case
    long earliest = Long.MAX_VALUE;
    if (unsatCoreIds.size() == 1) {
      String unsatCoreCmd = assertCmds.get(unsatCoreIds.get(0) - 1);
      List<Condition> unsatCoreConditions = mapping.get(unsatCoreCmd);
      if (unsatCoreConditions != null && unsatCoreConditions.size() > 0) {
        for (Condition unsatCoreCondition : unsatCoreConditions) {
          long time1 = unsatCoreCondition.getConditionTerms().get(0).getInstance1().getLatestSetValueTime();
          long time2 = unsatCoreCondition.getConditionTerms().get(0).getInstance2().getLatestSetValueTime();
          long condTime = time1 > time2 ? time1 : time2;
          if (condTime < earliest) {
            earliest = condTime;
          }
        }
      }
    }
    return earliest;
  }
  
  private final SMTChecker                  m_smtChecker;
  private boolean                           m_backTracking;
  private boolean                           m_continueInMethod;
  private Hashtable<BBorInstInfo, Object[]> m_watchList;
}
