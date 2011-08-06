package hk.ust.cse.Prevision;

import java.util.ArrayList;
import java.util.List;

public class WeakestPreconditionResult {
  
  public WeakestPreconditionResult() {
    m_overLimit       = false;
    m_reachMaximum    = false;
    m_satisfiables    = new ArrayList<Predicate>();
    m_notSatisfiables = new ArrayList<Predicate>();
  }

  public void addSatisfiable(Predicate satisfiable) {
    m_satisfiables.add(satisfiable);
  }
  
  public void addNotSatisfiable(Predicate notSatisfiable) {
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
  
  public List<Predicate> getSatisfiables() {
    return m_satisfiables;
  }
  
  public List<Predicate> getNotSatisfiables() {
    return m_notSatisfiables;
  }
  
  // return the first satisfiable precondition if exists
  public Predicate getFirstSatisfiable() {
    Predicate precond = null;
    if (m_satisfiables.size() > 0) {
      precond = m_satisfiables.get(0);
    }
    return precond;
  }
  
  public void clearAllSatNonSolverData() {
    for (Predicate sat : m_satisfiables) {
      sat.clearNonSolverData();
    }
  }
  
  public void clearAllSatSolverData() {
    for (Predicate sat : m_satisfiables) {
      sat.clearSolverData();
    }
  }
  
  public void clearAllNotSatNonSolverData() {
    for (Predicate notSat : m_notSatisfiables) {
      notSat.clearNonSolverData();
    }
  }
  
  public void clearAllNotSatSolverData() {
    for (Predicate notSat : m_notSatisfiables) {
      notSat.clearSolverData();
    }
  }
  
  private boolean         m_overLimit;
  private boolean         m_reachMaximum;
  private List<Predicate> m_satisfiables;
  private List<Predicate> m_notSatisfiables;
}
