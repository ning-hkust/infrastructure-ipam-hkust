package hk.ust.cse.Prevision;

public class WeakestPreconditionResult {
  public enum SAT_RESULT {SAT, UNSAT, OVER_LIMIT, NOT_CHECKED}
  
  public WeakestPreconditionResult(SAT_RESULT satResult, Predicate satisfiable) {
    m_satResult   = satResult;
    m_satisfiable = satisfiable;
  }
  
  public void setResult(SAT_RESULT satResult, Predicate satisfiable) {
    m_satResult   = satResult;
    m_satisfiable = satisfiable;
  }
  
  public boolean isSatisfiable() {
    return m_satResult == SAT_RESULT.SAT;
  }
  
  public boolean isUnsatisfiable() {
    return m_satResult == SAT_RESULT.UNSAT;
  }
  
  public boolean isOverLimit() {
    return m_satResult == SAT_RESULT.OVER_LIMIT;
  }
  
  public boolean isNotChecked() {
    return m_satResult == SAT_RESULT.NOT_CHECKED;
  }
  
  public SAT_RESULT getSatResult() {
    return m_satResult;
  }
  
  public Predicate getSatisfiable() {
    return m_satisfiable;
  }
  
  private SAT_RESULT m_satResult;
  private Predicate  m_satisfiable;
}
