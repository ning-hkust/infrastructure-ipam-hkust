package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.Hashtable;

public abstract class ConditionTerm {

  public abstract ConditionTerm replaceInstances(Hashtable<Instance, Instance> replaceMap);
  public abstract String toString();
  public abstract Instance[] getInstances();
  public abstract ConditionTerm deepClone(Hashtable<Object, Object> cloneMap);
}
