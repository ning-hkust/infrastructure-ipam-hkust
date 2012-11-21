package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.PathCondition.Formula;

import java.util.ArrayList;
import java.util.List;

public class CallStackResult {

  public CallStackResult(CallStack callStack, ExecutionResult execResult) {
    m_callStack    = callStack;
    m_execResult   = execResult;
    m_satisfiables = new ArrayList<Formula>();
  }
  
  public void addSatisfiable(Formula satisfiable) {
    m_satisfiables.add(satisfiable);
  }
  
  public CallStack getCallStack() {
    return m_callStack;
  }
  
  public ExecutionResult getExecResult() {
    return m_execResult;
  }
  
  public List<Formula> getSatisfiables() {
    return m_satisfiables;
  }
  
  private final CallStack       m_callStack;
  private final ExecutionResult m_execResult;
  private final List<Formula>   m_satisfiables;
}
