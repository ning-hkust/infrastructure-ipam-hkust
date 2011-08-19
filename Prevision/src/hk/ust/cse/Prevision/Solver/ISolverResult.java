package hk.ust.cse.Prevision.Solver;


import hk.ust.cse.Prevision.PathCondition.ConditionTerm;

import java.util.List;

public interface ISolverResult {

  public abstract void parseOutput(String output);

  public abstract boolean isSatisfactory();

  public abstract String getOutputStr();

  public abstract List<Integer> getUnsatCoreIds();

  
  public abstract List<ConditionTerm> getSatModel();

}
