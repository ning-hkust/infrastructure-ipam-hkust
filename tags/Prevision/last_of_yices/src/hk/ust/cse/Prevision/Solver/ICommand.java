package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Instance;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public interface ICommand {
  public class TranslatedCommand {
    public TranslatedCommand() {
      assertCmds            = new ArrayList<String>();
      assertCmdCondsMapping = new Hashtable<String, List<Condition>>();
      nameInstanceMapping   = new Hashtable<String, Instance>();
      constInstanceMapping  = new Hashtable<String, Instance>();
      instanceNameMapping   = new Hashtable<Instance, String>();
    }
    
    public String                             command;
    public List<String>                       assertCmds;
    public Hashtable<String, List<Condition>> assertCmdCondsMapping;
    public Hashtable<String, Instance>        nameInstanceMapping;
    public Hashtable<String, List<long[]>>    typeRanges;
    public Hashtable<String, Instance>        constInstanceMapping;
    public Hashtable<Instance, String>        instanceNameMapping;
  }

  public abstract String            getCheckCommand();
  public abstract String            translateToCommand(Condition condition, boolean keepUnboundedField, boolean retrieveUnsatCore);
  public abstract TranslatedCommand translateToCommand(Formula formula, boolean keepUnboundedField, boolean retrieveUnsatCore);
}
