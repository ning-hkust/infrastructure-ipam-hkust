package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.Solver.ISolverLoader;
import hk.ust.cse.YicesWrapper.YicesWrapper;

public class YicesLoaderLib implements ISolverLoader {
  
  public SOLVER_COMP_PROCESS check(String input) {
    // call YicesWrapper directly
    boolean result = YicesWrapper.check(input);
    String output  = YicesWrapper.getLastOutput();
    String errMsg  = YicesWrapper.getLastErrorMsg();

    if (output.length() > 0) {       // SMT Check finished
      // return satisfactory or not
      return (result) ? SOLVER_COMP_PROCESS.SAT : SOLVER_COMP_PROCESS.UNSAT;
    }
    else if (errMsg.length() > 0) {       // SMT Check throws error
      System.out.println("SMT Check error: " + errMsg);
      return SOLVER_COMP_PROCESS.ERROR;
    }
    else {       // SMT Check timeout
      System.out.println("SMT Check timeout!");
      return SOLVER_COMP_PROCESS.TIMEOUT;
    }
  }
  
  public SOLVER_COMP_PROCESS checkInContext(int ctx, String input) {
    // call YicesWrapper directly
    boolean result = YicesWrapper.checkInContext(ctx, input);
    String output  = YicesWrapper.getLastOutput();
    String errMsg  = YicesWrapper.getLastErrorMsg();

    if (output.length() > 0) {       // SMT Check finished
      // return satisfactory or not
      return (result) ? SOLVER_COMP_PROCESS.SAT : SOLVER_COMP_PROCESS.UNSAT;
    }
    else if (errMsg.length() > 0) {       // SMT Check throws error
      System.out.println("SMT Check error: " + errMsg);
      return SOLVER_COMP_PROCESS.ERROR;
    }
    else {       // SMT Check timeout
      System.out.println("SMT Check timeout!");
      return SOLVER_COMP_PROCESS.TIMEOUT;
    }
  }
  
  public int createContext() {
    return YicesWrapper.createContext();
  }
  
  public void deleteContext(int ctx) {
    YicesWrapper.deleteContext(ctx);
  }
  
  public void pushContext(int ctx) {
    YicesWrapper.pushContext(ctx);
  }
  
  public void popContext(int ctx) {
    YicesWrapper.popContext(ctx);
  }
  
  public String getLastOutput() {
    return YicesWrapper.getLastOutput();
  }

  public String getLastInput() {
    return YicesWrapper.getLastInput();
  }
}
