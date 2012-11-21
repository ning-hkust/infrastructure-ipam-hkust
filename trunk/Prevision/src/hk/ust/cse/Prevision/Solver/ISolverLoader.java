package hk.ust.cse.Prevision.Solver;

public interface ISolverLoader {
  public enum SOLVER_COMP_PROCESS {SAT, UNSAT, ERROR, TIMEOUT}
  
  public abstract SOLVER_COMP_PROCESS check(String input);
  
  public abstract SOLVER_COMP_PROCESS checkInContext(int ctx, String input);

  public abstract int createContext();
  
  public abstract void deleteContext(int ctx);
  
  public abstract void pushContext(int ctx);
  
  public abstract void popContext(int ctx);
  
  public abstract String getLastOutput();

  public abstract String getLastInput();
}
