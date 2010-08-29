package hk.ust.cse.Prevision.Solver;


import java.util.Hashtable;

public interface ISolverLoader {
  public enum SOLVER_COMP_PROCESS {SAT, UNSAT, ERROR, TIMEOUT}
  
  public abstract SOLVER_COMP_PROCESS check(String input,
      Hashtable<String, SMTVariable> defFinalVarMap);

  public abstract String getLastOutput();

  public abstract String getLastInput();

  public abstract ISolverResult getLastResult();
}
