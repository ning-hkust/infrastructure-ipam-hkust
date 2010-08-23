package hk.ust.cse.Prevision.Yices;

import hk.ust.cse.YicesWrapper.YicesWrapper;

import java.util.Hashtable;

public class YicesLoader {
  public enum YICES_COMP_PROCESS {SAT, UNSAT, ERROR, TIMEOUT}
  
  public YICES_COMP_PROCESS check(String input, Hashtable<String, SMTVariable> defFinalVarMap) {
    // call YicesWrapper directly
    boolean result = YicesWrapper.check(input);
    String output  = YicesWrapper.getLastOutput();
    String errMsg  = YicesWrapper.getLastErrorMsg();

    if (output.length() > 0) {       // SMT Check finished
      // parse and save result
      if (m_lastResult == null) {
        m_lastResult = new YicesResult();
      }
      m_lastResult.parseOutput(output, defFinalVarMap);

      // return satisfactory or not
      return (result) ? YICES_COMP_PROCESS.SAT : YICES_COMP_PROCESS.UNSAT;
    }
    else if (errMsg.length() > 0) {       // SMT Check throws error
      System.out.println("SMT Check error: " + errMsg);
      return YICES_COMP_PROCESS.ERROR;
    }
    else {       // SMT Check timeout
      System.out.println("SMT Check timeout!");
      return YICES_COMP_PROCESS.TIMEOUT;
    }
  }
  
  public String getLastOutput() {
    return YicesWrapper.getLastOutput();
  }

  public String getLastInput() {
    return YicesWrapper.getLastInput();
  }

  public YicesResult getLastResult() {
    return m_lastResult;
  }
  
  private YicesResult m_lastResult;
}
