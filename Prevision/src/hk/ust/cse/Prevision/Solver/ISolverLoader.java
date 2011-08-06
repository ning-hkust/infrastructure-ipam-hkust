package hk.ust.cse.Prevision.Solver;



public interface ISolverLoader {
  public enum SOLVER_COMP_PROCESS {SAT, UNSAT, ERROR, TIMEOUT}
  
  public abstract SOLVER_COMP_PROCESS check(String input);

  public abstract String getLastOutput();

  public abstract String getLastInput();

  public abstract ISolverResult getLastResult();
}
