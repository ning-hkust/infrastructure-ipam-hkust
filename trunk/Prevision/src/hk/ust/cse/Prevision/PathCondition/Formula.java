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
    m_timeStamp      = System.nanoTime(); // time stamp for this formula
  }

  public Formula(List<Condition> conditions, 
      Hashtable<String, Hashtable<String, Reference>> refMap, 
      Hashtable<String, Hashtable<String, Integer>> defMap) {
    
    // remember, each Formula instance should own a unique
    // instance of conditions and varMap!
    m_conditionList  = conditions;
    m_abstractMemory = new AbstractMemory(refMap, defMap);
    m_timeStamp      = System.nanoTime(); // time stamp for this formula
  }
  
  public Formula(List<Condition> conditions, AbstractMemory absMemory) {
    // remember, each Formula instance should own a unique
    // instance of conditions and refMap!
    m_conditionList  = conditions;
    m_abstractMemory = absMemory;
    m_timeStamp      = System.nanoTime(); // time stamp for this formula
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
    m_lastSolverInput    = smtChecker.getLastSolverInput();
    m_lastSolverOutput   = smtChecker.getLastSolverOutput();
    m_lastSMTCheckResult = smtChecker.getLastSMTCheckResult();
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
  
  public SMT_RESULT getLastSMTCheckResult() {
    return m_lastSMTCheckResult;
  }

  public List<Condition> getConditionList() {
    return m_conditionList;
  }
  
  public AbstractMemory getAbstractMemory() {
    return m_abstractMemory;
  }
  
  public long getTimeStamp() {
    return m_timeStamp;
  }
  
  public Hashtable<String, Hashtable<String, Reference>> getRefMap() {
    return m_abstractMemory.getRefMap();
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
    
    Formula cloneFormula = new Formula(cloneCondList, cloneAbsMemory);
    cloneFormula.m_timeStamp = m_timeStamp; // since it is only a clone, keep the time stamp
    return cloneFormula;
  }
  
  //private Boolean                            m_contradicted; 
  private long                               m_timeStamp;
  private List<Condition>                    m_conditionList;
  private AbstractMemory                     m_abstractMemory;
  private Hashtable<ISSABasicBlock, Integer> m_visitedRecord;
  private String                             m_lastSolverInput;
  private String                             m_lastSolverOutput;
  private SMT_RESULT                        m_lastSMTCheckResult;
  private List<SMTTerm>                      m_lastSatModel;
}
