package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;



public abstract class SolverLoader {
  public enum SOLVER_RESULT {SAT, UNSAT, UNKNOWN, ERROR, TIMEOUT, STACK_OVERFLOW}
  
  public abstract SOLVER_RESULT check(SolverInput input);

  public abstract SOLVER_RESULT checkInContext(Object ctxStorage, SolverInput input);
  
  public abstract SOLVER_RESULT checkInContext(Object ctxStorage, SolverInput input, Assertion[] assertions);

  public abstract Object createContext();
  
  public abstract void deleteContext(Object ctx);

  public abstract Object createContextStorage();
  
  public abstract void deleteContextStorage(Object ctxStorage);
  
  public abstract void pushContextStorage(Object ctxStorage);
  
  public abstract void popContextStorage(Object ctxStorage);

  public SolverInput getLastInput() {
    return m_lastInput;
  }

  // output: 1) SAT + Model, 2) UNSAT + UnsatCore (Expr[]) 3) UNSAT + UnsatCore (String) 4) "TIMEOUT" / "UNKNOWN" / "ERROR"
  public Object getLastOutput() {
    return m_lastOutput;
  }

  protected SolverInput m_lastInput;
  protected Object      m_lastOutput;
}
