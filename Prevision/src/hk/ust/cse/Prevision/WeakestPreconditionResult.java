package hk.ust.cse.Prevision;

import java.util.ArrayList;
import java.util.List;

public class WeakestPreconditionResult {
  
  public WeakestPreconditionResult() {
    m_overLimit    = false;
    m_reachMaximum = false;
    m_satisfiables = new ArrayList<Predicate>();
  }
  
  public void addSatisfiable(Predicate satisfiable) {
    m_satisfiables.add(satisfiable);
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
  
  // return the first satisfiable precondition if exists
  public Predicate getFirstSatisfiable() {
    Predicate precond = null;
    if (m_satisfiables.size() > 0) {
      precond = m_satisfiables.get(0);
    }
    return precond;
  }
  
  private boolean         m_overLimit;
  private boolean         m_reachMaximum;
  private List<Predicate> m_satisfiables;
}
