package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.AbstractMemory;
import hk.ust.cse.Prevision.VirtualMachine.Executor.BBorInstInfo;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.deprecated.SMTTerm;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;

public class Formula {
  public enum SMT_RESULT {SAT, UNSAT, ERROR, TIMEOUT, STACK_OVERFLOW}
  
  public static final int NORMAL_SUCCESSOR      = 0;
  public static final int EXCEPTIONAL_SUCCESSOR = 1;
  
  // a TRUE predicate
  public Formula() {
    m_conditionList  = new ArrayList<Condition>();
    m_abstractMemory = new AbstractMemory();
  }

  public Formula(List<Condition> conditions, 
      Hashtable<String, Hashtable<String, Reference>> refMap, 
      Hashtable<String, Hashtable<String, List<Reference>>> phiMap, 
      Hashtable<String, Hashtable<String, Integer>> defMap) {
    
    // remember, each Formula instance should own a unique
    // instance of conditions and varMap!
    m_conditionList = conditions;
    m_abstractMemory = new AbstractMemory(refMap, phiMap, defMap);
  }
  
  public Formula(List<Condition> conditions, AbstractMemory absMemory) {
    // remember, each Formula instance should own a unique
    // instance of conditions and refMap!
    m_conditionList  = conditions;
    m_abstractMemory = absMemory;
  }

  // clear everything except solver data, they are taking too much memory!
  public void clearNonSolverData() {
    m_conditionList  = null;
    m_abstractMemory = null;
    m_visitedRecord  = null;
  }

  // clear solver data
  public void clearSolverData() {
    m_lastSolverInput  = null;
    m_lastSolverOutput = null;
  }

  public void setSolverResult(SMTChecker smtChecker) {
    m_lastSolverInput  = smtChecker.getLastSolverInput();
    m_lastSolverOutput = smtChecker.getLastSolverOutput();
    m_lastSolverResult = smtChecker.getLastSolverResult();
  }
  
  public String getLastSolverOutput() {
    return m_lastSolverOutput;
  }

  public String getLastSolverInput() {
    return m_lastSolverInput;
  }
  
  //XXX
//  public List<SMTTerm> getLastSatModel() {
//    return m_lastSatModel;
//  }
  
  public SMT_RESULT getLastSolverResult() {
    return m_lastSolverResult;
  }

  public List<Condition> getConditionList() {
    return m_conditionList;
  }
  
  public AbstractMemory getAbstractMemory() {
    return m_abstractMemory;
  }
  
  public Hashtable<String, Hashtable<String, Reference>> getRefMap() {
    return m_abstractMemory.getRefMap();
  }
  
  public Hashtable<String, Hashtable<String, List<Reference>>> getPhiMap() {
    return m_abstractMemory.getPhiMap();
  }

  public Hashtable<String, Hashtable<String, Integer>> getDefMap() {
    return m_abstractMemory.getDefMap();
  }

  public Hashtable<ISSABasicBlock, Integer> getVisitedRecord() {
    return m_visitedRecord;
  }

  @SuppressWarnings("unchecked")
  public void setVisitedRecord(Hashtable<ISSABasicBlock, Integer> lastRecord, BBorInstInfo newlyVisited) {
    m_visitedRecord = (lastRecord != null) ? (Hashtable<ISSABasicBlock, Integer>) lastRecord.clone() : 
                                             new Hashtable<ISSABasicBlock, Integer>();
    if (newlyVisited != null) {
      // mark as visited
      Integer count = m_visitedRecord.get(newlyVisited.currentBB);
      count = (count == null) ? 0 : count;
      
      // add loop
      if (newlyVisited.sucessorBB != null && 
          newlyVisited.sucessorBB.getNumber() < newlyVisited.currentBB.getNumber()) {
        count++;
      }
      m_visitedRecord.put(newlyVisited.currentBB, count);
    }
  }

  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    
    if (!(obj instanceof Formula)) {
      return false;
    }
    
    Formula formula = (Formula) obj;
    return m_conditionList.equals(formula.getConditionList()) && 
           m_abstractMemory.equals(formula.getAbstractMemory());
  }

  public int hashCode() {
    return m_conditionList.hashCode() + m_abstractMemory.hashCode();
  }
  
  public Formula clone() {
    // makes sure the pointings are still correct
    Hashtable<Object, Object> cloneMap = new Hashtable<Object, Object>();
    return clone(cloneMap);
  }
  
  public Formula clone(Hashtable<Object, Object> cloneMap) {
    AbstractMemory cloneAbsMemory = m_abstractMemory.deepClone(cloneMap);
    List<Condition> cloneCondList = new ArrayList<Condition>();
    for (Condition condition : m_conditionList) {
      cloneCondList.add(condition.deepClone(cloneMap));
    }
    return new Formula(cloneCondList, cloneAbsMemory);
  }
  
  //private Boolean                            m_contradicted; 
  private List<Condition>                    m_conditionList;
  private AbstractMemory                     m_abstractMemory;
  private Hashtable<ISSABasicBlock, Integer> m_visitedRecord;
  private String                             m_lastSolverInput;
  private String                             m_lastSolverOutput;
  private SMT_RESULT                        m_lastSolverResult;
  private List<SMTTerm>                      m_lastSatModel;
}
