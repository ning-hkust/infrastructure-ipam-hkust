package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.Hashtable;
import java.util.List;

public interface ICommand {
  public abstract String translateToCommand(Formula formula, MethodMetaData methData, 
      List<String> assertCmds, Hashtable<String, List<Condition>> cmdConditionsMapping, 
      boolean keepUnboundedField, boolean retrieveUnsatCore);
}
