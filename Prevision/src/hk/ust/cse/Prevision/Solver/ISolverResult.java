package hk.ust.cse.Prevision.Solver;


import java.util.Hashtable;
import java.util.List;

public interface ISolverResult {

  public abstract void parseOutput(String output,
      Hashtable<String, SMTVariable> defFinalVarMap);

  public abstract boolean isSatisfactory();

  public abstract String getOutputStr();

  public abstract List<SMTTerm> getSatModel();

}
