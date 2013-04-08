package hk.ust.cse.Prevision.VirtualMachine;

import hk.ust.cse.Prevision.PathCondition.Formula;

import java.util.ArrayList;
import java.util.List;

public class ExecutionResult {
  
  public ExecutionResult() {
    m_overLimit       = false;
    m_reachMaximum    = false;
    m_satisfiables    = new ArrayList<Formula>();
    m_notSatisfiables = new ArrayList<Formula>();
  }

  public void addSatisfiable(Formula satisfiable) {
    m_satisfiables.add(satisfiable);
  }
  
  public void addNotSatisfiable(Formula notSatisfiable) {
    m_notSatisfiables.add(notSatisfiable);
  }
  
  public void setOverLimit(boolean overLimit) {
    m_overLimit = overLimit;
  }
  
  public void setReachMaximum(boolean reachMaximum) {
    m_reachMaximum = reachMaximum;
  }
  
  public boolean isOverLimit() {
    return m_overLimit;
  }
  
  public boolean isReachMaximum() {
    return m_reachMaximum;
  }
  
  public boolean isSatisfiable() {
    return m_satisfiables.size() > 0;
  }
  
  public List<Formula> getSatisfiables() {
    return m_satisfiables;
  }
  
  public List<Formula> getNotSatisfiables() {
    return m_notSatisfiables;
  }
  
  // return the first satisfiable precondition if exists
  public Formula getFirstSatisfiable() {
    Formula precond = null;
    if (m_satisfiables.size() > 0) {
      precond = m_satisfiables.get(0);
    }
    return precond;
  }
  
  public void clearAllSatNonSolverData() {
    for (Formula sat : m_satisfiables) {
      sat.clearNonSolverData();
    }
  }
  
  public void clearAllSatSolverData() {
    for (Formula sat : m_satisfiables) {
      sat.clearSolverData();
    }
  }
  
  public void clearAllNotSatNonSolverData() {
    for (Formula notSat : m_notSatisfiables) {
      notSat.clearNonSolverData();
    }
  }
  
  public void clearAllNotSatSolverData() {
    for (Formula notSat : m_notSatisfiables) {
      notSat.clearSolverData();
    }
  }
  
  private boolean         m_overLimit;
  private boolean         m_reachMaximum;
  private List<Formula>   m_satisfiables;
  private List<Formula>   m_notSatisfiables;
}
