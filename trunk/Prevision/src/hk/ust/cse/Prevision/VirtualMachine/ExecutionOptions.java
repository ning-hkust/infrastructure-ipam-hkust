package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.CallStack;
import hk.ust.cse.Prevision.Summary;
import hk.ust.cse.Wala.CallGraph.CallGraphBuilder;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ExecutionOptions {
  public enum EXCEPTION_TYPE {CUSTOM, NPE, AIOBE};
  
  public ExecutionOptions(CallStack fullCallStack, boolean useSummary) {      
    // set call stack
    this.fullCallStack = fullCallStack;
    
    // initialize summary
    this.summary = useSummary ? new Summary() : null;

    // states
    this.m_enteringCallStack = true; // at the beginning, we are entering call stack
    
    // callback list
    m_afterSmtCheckCallbacks      = new ArrayList<Object[]>();
    m_afterOnTheFlyCheckCallbacks = new ArrayList<Object[]>();
  }

  public boolean isEnteringCallStack() {
    return m_enteringCallStack;
  }

  public void finishedEnteringCallStack() {
    m_enteringCallStack = false;
  }
  
  public void addAfterSmtCheckCallback(Method callBack, Object receiver) {
    m_afterSmtCheckCallbacks.add(new Object[] {callBack, receiver});
  }
  
  public void addAfterOnTheFlyCheckCallback(Method callBack, Object receiver) {
    m_afterOnTheFlyCheckCallbacks.add(new Object[] {callBack, receiver});
  }
  
  public void removeAfterSmtCheckCallback(Method callBack, Object receiver) {
    m_afterSmtCheckCallbacks.remove(new Object[] {callBack, receiver});
  }
  
  public void removeAfterOnTheFlyCheckCallback(Method callBack, Object receiver) {
    m_afterOnTheFlyCheckCallbacks.remove(new Object[] {callBack, receiver});
  }
  
  public List<Object[]> getAfterSmtCheckCallbacks() {
    return m_afterSmtCheckCallbacks;
  }
  
  public List<Object[]> getAfterOnTheFlyCheckCallback() {
    return m_afterOnTheFlyCheckCallbacks;
  }

  // global options
  public boolean   inclInnerMostLine       = false;
  public boolean   inclStartingInst        = false;
  public boolean   saveNotSatResults       = false;
  public boolean   checkOnTheFly           = true;
  public boolean   skipUselessBranches     = true;
  public boolean   skipUselessMethods      = true;
  public boolean   heuristicBacktrack      = true;
  public boolean   clearSatNonSolverData   = true;
  public int       maxDispatchTargets      = Integer.MAX_VALUE;
  public int       maxRetrieve             = 1;
  public int       maxSmtCheck             = 1000;
  public int       maxInvokeDepth          = 1;
  public int       maxLoop                 = 1;
  public int       startingInst            = -1;   // -1 if don't want to specify the starting instruction index
  public int[]     startingInstBranchesTo  = null; // null if don't want to specify the instructions that the starting instruction is branching to
  public EXCEPTION_TYPE exceptionType      = EXCEPTION_TYPE.CUSTOM;
  public CallGraphBuilder callGraphBuilder = CallGraphBuilder.ZeroOneCFA;
  
  public final CallStack fullCallStack;
  public final Summary   summary;

  // global states
  private boolean m_enteringCallStack;
  
  // call back functions
  private final List<Object[]> m_afterOnTheFlyCheckCallbacks;
  private final List<Object[]> m_afterSmtCheckCallbacks;
}
