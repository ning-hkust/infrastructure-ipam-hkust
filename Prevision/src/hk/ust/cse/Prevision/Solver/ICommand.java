package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public interface ICommand {
  public class TranslatedCommand {
    public TranslatedCommand() {
      assertCmds            = new ArrayList<String>();
      assertCmdCondsMapping = new Hashtable<String, List<Condition>>();
      nameInstanceMapping   = new Hashtable<String, Instance>();
    }
    
    public String                             command;
    public List<String>                       assertCmds;
    public Hashtable<String, List<Condition>> assertCmdCondsMapping;
    public Hashtable<String, Instance>        nameInstanceMapping;
  }
  
  public abstract TranslatedCommand translateToCommand(Formula formula, 
      MethodMetaData methData, boolean keepUnboundedField, boolean retrieveUnsatCore);
}
