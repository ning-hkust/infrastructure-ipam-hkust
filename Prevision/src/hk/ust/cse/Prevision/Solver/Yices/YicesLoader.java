package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.Solver.ISolverLoader;
import hk.ust.cse.Prevision.Solver.ISolverResult;
import hk.ust.cse.YicesWrapper.YicesWrapper;

public class YicesLoader implements ISolverLoader {
  
  public SOLVER_COMP_PROCESS check(String input) {
    // call YicesWrapper directly
    boolean result = YicesWrapper.check(input);
    String output  = YicesWrapper.getLastOutput();
    String errMsg  = YicesWrapper.getLastErrorMsg();

    // create a solver result
    if (m_lastResult == null) {
      m_lastResult = new YicesResult();
    }
    
    if (output.length() > 0) {       // SMT Check finished
      // parse and save result
      m_lastResult.parseOutput(output);

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
  
  public String getLastOutput() {
    return YicesWrapper.getLastOutput();
  }

  public String getLastInput() {
    return YicesWrapper.getLastInput();
  }

  public ISolverResult getLastResult() {
    return m_lastResult;
  }
  
  private ISolverResult m_lastResult;
}
