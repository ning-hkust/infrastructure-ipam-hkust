package hk.ust.cse.Prevision.Solver;


import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.Hashtable;
import java.util.List;

public interface ISolverResult {

  public abstract void parseOutput(String output, Hashtable<String, Instance> nameInstanceMapping);

  public abstract boolean isSatisfactory();

  public abstract String getOutputStr();

  public abstract List<Integer> getUnsatCoreIds();

  public abstract List<BinaryConditionTerm> getSatModel();

}
