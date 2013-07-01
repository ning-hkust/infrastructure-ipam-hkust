package hk.ust.cse.Prevision.Optimization;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.TypeConditionTerm;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Executor.AbstractExecutor.BBorInstInfo;
import hk.ust.cse.Wala.Jar2IR;
import hk.ust.cse.Wala.WalaAnalyzer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;

public class HeuristicBacktrack {

  private static class InvocationWatchList {
    private static class WatchItem {
      public WatchItem(List<Condition> condsOnOrBefore) {
        this.condsOnOrBefore = condsOnOrBefore;
        this.unsatCores      = new ArrayList<List<Condition>>();
      }
      
      public final List<Condition> condsOnOrBefore; /* path conditions introduced within or before invocation */
      public final List<List<Condition>> unsatCores;
    }
    
    public InvocationWatchList() {
      m_watchList = new HashMap<BBorInstInfo, WatchItem>();
    }
    
    public void addInvocation(BBorInstInfo invocation, Formula precond) {
      if (!m_watchList.containsKey(invocation) || m_watchList.get(invocation) != null /* not dummy holder */) {
        List<Condition> condsOnOrBefore = new ArrayList<Condition>(precond.getConditionList());
        m_watchList.put(invocation, new WatchItem(condsOnOrBefore));
        
        SSAInstruction inst = invocation.currentBB.getLastInstructionIndex() >= 0 ? 
                              invocation.currentBB.getLastInstruction() : null;
        if (inst != null && inst instanceof SSAInvokeInstruction && 
            !((SSAInvokeInstruction) inst).isStatic() && condsOnOrBefore.size() > 0) {
          condsOnOrBefore.remove(condsOnOrBefore.size() - 1); // remove the receiver != null condition
        }
      }
    }

    public Set<BBorInstInfo> invocations() {
      return m_watchList.keySet();
    }
    
    public WatchItem getWatchItem(BBorInstInfo invocation) {
      return m_watchList.get(invocation);
    }
    
    public void removeWatchItem(BBorInstInfo invocation) {
      m_watchList.remove(invocation);
    }
    
    public void clear() {
      Set<BBorInstInfo> keys = m_watchList.keySet();
      for (BBorInstInfo key : keys) {
        m_watchList.put(key, null /* dummy place holder */);
      }
    }
    
    private final HashMap<BBorInstInfo, WatchItem> m_watchList;
  }
  
  public HeuristicBacktrack(SMTChecker smtChecker, WalaAnalyzer walaAnalyzer, DefAnalyzerWrapper defAnalyzer) {
    m_smtChecker       = smtChecker;
    m_walaAnalyzer     = walaAnalyzer;
    m_defAnalyzer      = defAnalyzer;
    m_backTracking     = false;
    m_continueInMethod = false;
    m_watchList        = new InvocationWatchList();
  }
  
  public void backtrack(Stack<BBorInstInfo> workList, int curInvokeDepth, int maxInvokeDepth) {
    // find unsat core conditions, only the latest conditions in each set are retrieved
    List<Condition> unsatCores = findUnsatCores();
    if (unsatCores.size() == 0) {
      return;
    }
    
    if (!m_backTracking && !m_continueInMethod) {
      Set<BBorInstInfo> invocations = m_watchList.invocations();
      for (BBorInstInfo invocation : invocations) {
        // save the new unsat core conditions to every current invocation
        InvocationWatchList.WatchItem watchItem = m_watchList.getWatchItem(invocation);
        if (watchItem != null /* not a dummy place holder */) {
          watchItem.unsatCores.add(unsatCores);
        }
      }

      System.out.println("Heuristic Backtracing: found a backtrackable unsat core: " + unsatCores + ", backtracking...");
      m_backTracking = true;
    }
    
    // try to backtrack current workList
    boolean continueInMethod  = false;
    boolean backTrackFinished = false;
    long eariestLastSet = findUnsatCoreEarliestLastSet();
    while (!workList.empty() && !backTrackFinished) {
      BBorInstInfo nextBasicBlock = workList.peek();
      
      if (eariestLastSet < nextBasicBlock.formula.getTimeStamp()) {
        // no matter what we do in nextBasicBlock, at least one full set of unsat core conditions can be fully set
        backTrackFinished = false;
      }
      else {
        // check if the current invocation target is backtrack-able
        backTrackFinished = true;
        SSAInstruction inst = nextBasicBlock.currentBB.getLastInstructionIndex() >= 0 ? 
                              nextBasicBlock.currentBB.getLastInstruction() : null;
        if (inst != null && inst instanceof SSAInvokeInstruction && curInvokeDepth < maxInvokeDepth) {
          InvocationWatchList.WatchItem watchItem = m_watchList.getWatchItem(nextBasicBlock);
          IR targetIR = getTargetIR(nextBasicBlock, (SSAInvokeInstruction) inst);
          
          if (watchItem != null && targetIR != null) { // invocation does in watch list, also not a dummy place holder                  
            List<List<Condition>> prevUnsatCores = watchItem.unsatCores;
            List<Condition> prevCondsOnOrBefore  = watchItem.condsOnOrBefore;
            HashSet<Long> prevCondsOnOrBeforeTimes = new HashSet<Long>();
            for (Condition prevCondOnOrBefore : prevCondsOnOrBefore) {
              prevCondsOnOrBeforeTimes.add(prevCondOnOrBefore.getTimeStamp());
            }
            
            boolean affectable = false;
            long invocationTime = nextBasicBlock.formula.getTimeStamp();
            for (int i = 0, size = prevUnsatCores.size(); i < size && !affectable; i++) {
              if (isAffectable(prevUnsatCores.get(i), invocationTime, prevCondsOnOrBeforeTimes, targetIR)) {
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
        System.out.println("Backtracked " + nextBasicBlock.currentBB);

        // since we backtracked, the current invocation no longer under watch list
        m_watchList.removeWatchItem(nextBasicBlock);
      }
    }
    m_backTracking     = !backTrackFinished;
    m_continueInMethod = continueInMethod;
  }
  
  private IR getTargetIR(BBorInstInfo basicBlock, SSAInvokeInstruction invokeInst) {
    IR ir = null;
    if (basicBlock.target != null && basicBlock.target[0].equals(invokeInst) && basicBlock.target[1] != null) {
      ir = (IR) basicBlock.target[1];
    }
    else {
      String methodSig = invokeInst.getDeclaredTarget().getSignature();
      ir = Jar2IR.getIR(m_walaAnalyzer, methodSig);
    }
    return ir;
  }
  
  private boolean isAffectable(List<Condition> unsatCore, 
      long invocationTime, HashSet<Long> prevCondsOnOrBeforeTimes, IR targetIR) {
    
    // get times
    List<Instance> instance1s = new ArrayList<Instance>();
    List<Instance> instance2s = new ArrayList<Instance>();
    List<Long> instance1Times = new ArrayList<Long>();
    List<Long> instance2Times = new ArrayList<Long>();
    List<Integer> introduces  = new ArrayList<Integer>();
    
    for (int i = 0, size = unsatCore.size(); i < size; i++) {
      Condition unsatCoreCond = unsatCore.get(i); 
      Object[] instancesAndTimes = getInstancesAndTimes(unsatCoreCond); // get times
      
      int introduced = (!prevCondsOnOrBeforeTimes.contains(unsatCoreCond.getTimeStamp())) ? 1 : 
                           (unsatCoreCond.getTimeStamp() > invocationTime ? 0 : -1);
      instance1Times.add((Long) instancesAndTimes[0]);
      instance2Times.add((Long) instancesAndTimes[1]);
      instance1s.add((Instance) instancesAndTimes[2]);
      instance2s.add((Instance) instancesAndTimes[3]);
      introduces.add(introduced);
    }
    
    boolean affectable = false;
    if (unsatCore.size() == 1) {
      affectable = isAffectable1(instance1s.get(0), instance2s.get(0), 
          instance1Times.get(0), instance2Times.get(0), introduces.get(0), invocationTime, targetIR);
    }
    else {
      affectable = isAffectable2(instance1s, instance2s, 
          instance1Times, instance2Times, introduces, invocationTime, targetIR);
    }
    return affectable;
  }
  
  private Object[] getInstancesAndTimes(Condition unsatCoreCond) {
    // get times
    Instance instance1 = null;
    Instance instance2 = null;
    long instance1Time = Long.MAX_VALUE;
    long instance2Time = Long.MAX_VALUE;
    if (unsatCoreCond.getConditionTerms().get(0) instanceof BinaryConditionTerm) {
      BinaryConditionTerm binaryTerm = (BinaryConditionTerm) unsatCoreCond.getConditionTerms().get(0);
      instance1     = binaryTerm.getInstance1();
      instance2     = binaryTerm.getInstance2();
      instance1Time = instance1.isRelationRead() ? instance1.getLastReference().getReadRelTime() : instance1.getSetValueTime();
      instance2Time = instance2.isRelationRead() ? instance2.getLastReference().getReadRelTime() : instance2.getSetValueTime();
      instance1Time = instance1Time == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : instance1Time;
      instance2Time = instance2Time == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : instance2Time;
    }
    else {
      TypeConditionTerm typeTerm = (TypeConditionTerm) unsatCoreCond.getConditionTerms().get(0);
      instance1     = typeTerm.getInstance1();
      instance2     = instance1;
      instance1Time = instance1.isRelationRead() ? instance1.getLastReference().getReadRelTime() : instance1.getSetValueTime();
      instance2Time = instance1Time;
      instance1Time = instance1Time == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : instance1Time;
      instance2Time = instance2Time == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : instance2Time;
    }
    return new Object[] {instance1Time, instance2Time, instance1, instance2};
  }

  // introduced: 0: within invocation, 1: after invocation (in backward order), -1: before invocation (in backward order)
  private boolean isAffectable1(Instance instance1, Instance instance2, 
      long instance1Time, long instance2Time, int introduced, long invocationTime, IR targetIR) {
    boolean affectable = false;
    
    if (introduced == 0) { // condition introduced within invocation, may avoid introducing this condition in invocation 
      affectable = true;
    }
    else if (introduced == 1) { // condition introduced after (in backward order) invocation
      affectable = false;
    }
    else { // condition introduced before (in backward order) invocation
      if (instance1Time < invocationTime && instance2Time < invocationTime) { // if all instances set to concrete value after invocation
        affectable = false;
      }
      else {
        affectable = false;
        if (!affectable && instance1Time > invocationTime) {          
          String[] declFields = instance1.getDeclFieldNames();
          if (declFields.length == 0 || canAffectDeclFields(targetIR, instance1)) {
            affectable = true;
          }
        }
        if (!affectable && instance2Time > invocationTime) {
          String[] declFields = instance2.getDeclFieldNames();
          if (declFields.length == 0 || canAffectDeclFields(targetIR, instance2)) {
            affectable = true;
          }
        }
      }
    }
    return affectable;
  }

  // introduced: 0: within invocation, 1: after invocation (in backward order), -1: before invocation (in backward order)
  private boolean isAffectable2(List<Instance> instance1s, List<Instance> instance2s, List<Long> instance1Times, 
      List<Long> instance2Times, List<Integer> introduces, long invocationTime, IR targetIR) {
    
    boolean affectable = false;
    
    // divided by introduce time
    List<Object[]> before = new ArrayList<Object[]>();
    List<Object[]> within = new ArrayList<Object[]>();
    List<Object[]> after  = new ArrayList<Object[]>();
    for (int i = 0, size = introduces.size(); i < size; i++) {
      List<Object[]> addTo = introduces.get(i) < 0 ? before : introduces.get(i) == 0 ? within : after;
      addTo.add(new Object[] {instance1Times.get(i), instance2Times.get(i), instance1s.get(i), instance2s.get(i)});
    }
    
    if (within.size() > 0) { // condition introduced within invocation, may avoid introducing this condition in invocation 
      affectable = true;
    }
    else if (before.size() == 0) { // all conditions introduced after (in backward order) invocation
      affectable = false;
    }
    else if (after.size() == 0) { // all conditions introduced before (in backward order) invocation
      affectable = false;
    }
    else {
      for (int i = 0, size = before.size(); i < size && !affectable; i++) {
        long instance1Time = (long) before.get(i)[0];
        long instance2Time = (long) before.get(i)[1];
        Instance instance1 = (Instance) before.get(i)[2];
        Instance instance2 = (Instance) before.get(i)[3];
        if (instance1Time > invocationTime || instance2Time > invocationTime) {
          affectable = false;
          if (!affectable && instance1Time > invocationTime) {
            String[] declFields = instance1.getDeclFieldNames();
            if (declFields.length == 0 || canAffectDeclFields(targetIR, instance1)) {
              affectable = true;
            }
          }
          if (!affectable && instance2Time > invocationTime) {
            String[] declFields = instance2.getDeclFieldNames();
            if (declFields.length == 0 || canAffectDeclFields(targetIR, instance2)) {
              affectable = true;
            }
          }
        }
      }
    }
    return affectable;
  }
  
  private boolean canAffectDeclFields(IR ir, Instance instance) {
    boolean affectable = false;
    
    String[] declFields = instance.getDeclFieldNames();
    if (declFields.length > 0) {
      HashSet<String> fieldsSet = m_defAnalyzer.getMethodFieldNames(ir);
      for (int i = 0; i < declFields.length && !affectable; i++) {
        affectable = fieldsSet.contains(declFields[i]);
      }
    }
    return affectable;
  }
  
  public void addToWatchList(BBorInstInfo invocation, Formula precond) {
    m_watchList.addInvocation(invocation, precond);
  }
  
  public void removeFromWatchList(BBorInstInfo invocation) {
    m_watchList.removeWatchItem(invocation);
  }
  
  public void clearWatchList() {
    m_watchList.clear();
  }
  
  public boolean isBacktracking() {
    return m_backTracking;
  }
  
  public boolean isContinueInMethod() {
    return m_continueInMethod;
  }
  
  private List<Condition> findUnsatCores() {
    List<Condition> unsatCores = new ArrayList<Condition>();

    List<List<Condition>> unsatCore = m_smtChecker.getLastResult().getUnsatCore();
    if (unsatCore != null && unsatCore.size() > 0) {
      for (List<Condition> unsatCoreConditions : unsatCore) {
        Condition earliestCond = null;
        for (Condition unsatCoreCond : unsatCoreConditions) {
          if (earliestCond == null || unsatCoreCond.getTimeStamp() < earliestCond.getTimeStamp()) {
            earliestCond = unsatCoreCond; // get the earliest one
          }
        }
        unsatCores.add(earliestCond);
      }
    }
    return unsatCores;
  }
  
  // find the earliest of the latest set time
  private long findUnsatCoreEarliestLastSet() {
    long earliest = Long.MIN_VALUE;
    
    List<List<Condition>> unsatCore = m_smtChecker.getLastResult().getUnsatCore();
    for (List<Condition> unsatCoreConditions : unsatCore) {
      long earliestFullySetTime = Long.MAX_VALUE;
      for (Condition unsatCoreCondition : unsatCoreConditions) {
        if (unsatCoreCondition.getConditionTerms().get(0) instanceof BinaryConditionTerm) {
          BinaryConditionTerm binaryTerm = (BinaryConditionTerm) unsatCoreCondition.getConditionTerms().get(0);
          long time1 = binaryTerm.getInstance1().getLatestSetValueTime();
          long time2 = binaryTerm.getInstance2().getLatestSetValueTime();
          if (unsatCore.size() == 1) {
            time1 = time1 == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : time1;
            time2 = time2 == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : time2;
          }
          long condfullySetTime = time1 > time2 ? time1 : time2;
          if (condfullySetTime < earliestFullySetTime) {
            earliestFullySetTime = condfullySetTime;
          }
        }
        else {
          TypeConditionTerm typeTerm = (TypeConditionTerm) unsatCoreCondition.getConditionTerms().get(0);
          long condfullySetTime = typeTerm.getInstance1().getLatestSetValueTime();
          if (unsatCore.size() == 1) {
            condfullySetTime = condfullySetTime == Long.MIN_VALUE /* not set */ ? Long.MAX_VALUE : condfullySetTime;
          }
          if (condfullySetTime < earliestFullySetTime) {
            earliestFullySetTime = condfullySetTime;
          }
        }
      }
      if (earliestFullySetTime > earliest) {
        earliest = earliestFullySetTime;
      }
    }
    earliest = earliest == Long.MIN_VALUE ? Long.MAX_VALUE : earliest;
    
    return earliest;
  }
  
  private boolean                   m_backTracking;
  private boolean                   m_continueInMethod;
  private final SMTChecker          m_smtChecker;
  private final WalaAnalyzer        m_walaAnalyzer;
  private final DefAnalyzerWrapper  m_defAnalyzer;
  private final InvocationWatchList m_watchList;
}
