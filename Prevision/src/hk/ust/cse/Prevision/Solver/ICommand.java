package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Wala.MethodMetaData;

public interface ICommand {
  public abstract String translateToCommand(Formula formula, MethodMetaData methData, boolean keepUnboundedField);
}
