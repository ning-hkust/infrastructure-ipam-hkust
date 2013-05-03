package hk.ust.cse.Prevision.PathCondition;

import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.Solver.SolverInput;
import hk.ust.cse.Prevision.Solver.SolverLoader.SOLVER_RESULT;
import hk.ust.cse.Prevision.Solver.SolverResult;
import hk.ust.cse.Prevision.VirtualMachine.AbstractMemory;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.Prevision.VirtualMachine.Reference;
import hk.ust.cse.Prevision.VirtualMachine.Relation;
import hk.ust.cse.Prevision.VirtualMachine.Executor.AbstractExecutor.BBorInstInfo;
import hk.ust.cse.Wala.MethodMetaData;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;

public class Formula {
  public static final int NORMAL_SUCCESSOR      = 0;
  public static final int EXCEPTIONAL_SUCCESSOR = 1;
  
  // a TRUE predicate
  public Formula(boolean forward) {
    m_conditionList    = new ArrayList<Condition>();
    m_abstractMemory   = new AbstractMemory(forward);
    m_fieldAssignTimes = new Hashtable<String, List<Long>>();
    m_traversedPath    = new ArrayList<Object[]>();
    m_visitedRecord    = new Hashtable<ISSABasicBlock, Integer>();
    m_timeStamp        = System.nanoTime(); // time stamp for this formula
  }
  
  public Formula(Formula formula) {
    // remember, each Formula instance should own a unique
    // instance of conditions and varMap!
    m_conditionList    = formula.getConditionList();
    m_abstractMemory   = new AbstractMemory(formula.getRefMap(), formula.getDefMap(), formula.getRelationMap());
    m_fieldAssignTimes = formula.getFieldAssignTimes();
    m_traversedPath    = formula.getTraversedPath();
    m_visitedRecord    = formula.getVisitedRecord();
    m_timeStamp        = System.nanoTime(); // time stamp for this formula
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
    m_conditionList    = null;
    m_abstractMemory   = null;
    m_fieldAssignTimes = null;
    m_traversedPath    = null;
    m_visitedRecord    = null;
  }

  // clear solver data
  public void clearSolverData() {
    m_lastSolverInput  = null;
    m_lastSolverOutput = null;
    m_lastSolverResult = null;
  }

  public void setSolverResult(SMTChecker smtChecker) {
    m_lastSolverInput    = smtChecker.getLastSolverInput();
    m_lastSolverOutput   = smtChecker.getLastSolverOutput();
    m_lastSolverResult   = smtChecker.getLastResult();
    m_lastSMTCheckResult = smtChecker.getLastSMTCheckResult();
  }
  
  public void addFieldAssignTime(String fieldName, long time) {
    List<Long> times = m_fieldAssignTimes.get(fieldName);
    if (times == null) {
      times = new ArrayList<Long>();
      m_fieldAssignTimes.put(fieldName, times);
    }
    times.add(time);
  }
  
  public void addToTraversedPath(SSAInstruction traversed, MethodMetaData methData, String callSites) {
    m_traversedPath.add(new Object[] {traversed, methData, callSites});
  }
  
  // for phi instruction only
  public void addToTraversedPath(SSAInstruction traversed, MethodMetaData methData, String callSites, int phiVarID) {
    m_traversedPath.add(new Object[] {traversed, methData, callSites, phiVarID});
  }
  
  public Object getLastSolverOutput() {
    return m_lastSolverOutput;
  }

  public SolverInput getLastSolverInput() {
    return m_lastSolverInput;
  }
  
  public SolverResult getLastSolverResult() {
    return m_lastSolverResult;
  }
  
  public SOLVER_RESULT getLastSMTCheckResult() {
    return m_lastSMTCheckResult;
  }

  public List<Condition> getConditionList() {
    return m_conditionList;
  }
  
  public AbstractMemory getAbstractMemory() {
    return m_abstractMemory;
  }
  
  public Hashtable<String, List<Long>> getFieldAssignTimes() {
    return m_fieldAssignTimes;
  }
  
  public List<Long> getFieldAssignTimes(String fieldName) {
    return m_fieldAssignTimes.get(fieldName);
  }
  
  public List<Object[]> getTraversedPath() {
    return m_traversedPath;
  }
  
  public long getTimeStamp() {
    return m_timeStamp;
  }
  
  // currently should only be used in PrepInitFormula
  public void setTimeStamp(long timeStamp) {
    m_timeStamp = timeStamp;
  }
  
  public Hashtable<String, Hashtable<String, Reference>> getRefMap() {
    return m_abstractMemory.getRefMap();
  }

  public Hashtable<String, Hashtable<String, Integer>> getDefMap() {
    return m_abstractMemory.getDefMap();
  }
  
  public Hashtable<String, Relation> getRelationMap() {
    return m_abstractMemory.getRelationMap();
  }
  
  public Relation getRelation(String relationName) {
    if (relationName.startsWith("read_")) {
      relationName = relationName.substring(5, relationName.lastIndexOf("_"));
    }
    return m_abstractMemory.getRelation(relationName);
  }
  
  public Instance[] getReadRelationDomainValues(Instance readInstance) {
    String relName = readInstance.getLastReference().getReadRelName();
    long readTime  = readInstance.getLastReference().getReadRelTime();
    
    Relation relation = getRelation(relName);
    int index = relation.getIndex(readTime);
    
    return relation.getDomainValues().get(index);
  }
  
  public Relation addFieldRelation(String relationName, boolean forward, String[] domainTypes, String rangeType) {
    return m_abstractMemory.addFieldRelation(relationName, forward, domainTypes, rangeType);
  }

  public Hashtable<ISSABasicBlock, Integer> getVisitedRecord() {
    return m_visitedRecord;
  }

  @SuppressWarnings("unchecked")
  public void setVisitedRecord(Hashtable<ISSABasicBlock, Integer> lastRecord, BBorInstInfo newlyVisited, boolean forward) {
    m_visitedRecord = (lastRecord != null) ? (Hashtable<ISSABasicBlock, Integer>) lastRecord.clone() : 
                                             new Hashtable<ISSABasicBlock, Integer>();
    if (newlyVisited != null) {
      // mark as visited
      Integer count = m_visitedRecord.get(newlyVisited.currentBB);
      count = (count == null) ? 0 : count;
      
      // add loop
      if (newlyVisited.previousBB != null) {
        int previousLine = newlyVisited.methData.getLineNumber(newlyVisited.previousBB);
        int currentLine  = newlyVisited.methData.getLineNumber(newlyVisited.currentBB);
        
        if (forward) {
          if (previousLine > 0 && currentLine > 0 && previousLine > currentLine) {
            count++;
          }
          else if (previousLine <= 0 || currentLine <= 0 || previousLine == currentLine) {
            if (newlyVisited.previousBB.getNumber() > newlyVisited.currentBB.getNumber()) {
              count++;
            }
          }
        }
        else {
          if (previousLine > 0 && currentLine > 0 && previousLine < currentLine) {
            count++;
          }
          else if (previousLine <= 0 || currentLine <= 0 || previousLine == currentLine) {
            if (newlyVisited.previousBB.getNumber() < newlyVisited.currentBB.getNumber()) {
              count++;
            }
          }
        }
      }
      
      m_visitedRecord.put(newlyVisited.currentBB, count);
    }
  }

  public Formula clone() {
    // makes sure the pointings are still correct
    Hashtable<Object, Object> cloneMap = new Hashtable<Object, Object>();
    return clone(cloneMap);
  }
  
  @SuppressWarnings("unchecked")
  public Formula clone(Hashtable<Object, Object> cloneMap) {
    AbstractMemory cloneAbsMemory = m_abstractMemory.deepClone(cloneMap);
    List<Condition> cloneCondList = new ArrayList<Condition>();
    for (Condition condition : m_conditionList) {
      cloneCondList.add(condition.deepClone(cloneMap));
    }
    Formula cloneFormula = new Formula(cloneCondList, cloneAbsMemory);
    
    cloneFormula.m_traversedPath    = new ArrayList<Object[]>(m_traversedPath);
    cloneFormula.m_fieldAssignTimes = new Hashtable<String, List<Long>>();
    cloneFormula.m_visitedRecord    = (Hashtable<ISSABasicBlock, Integer>) m_visitedRecord.clone();
    Enumeration<String> keys = m_fieldAssignTimes.keys();
    while (keys.hasMoreElements()) {
      String fieldName = (String) keys.nextElement();
      cloneFormula.m_fieldAssignTimes.put(fieldName, new ArrayList<Long>(m_fieldAssignTimes.get(fieldName)));
    }
    
    cloneFormula.m_timeStamp = m_timeStamp; // since it is only a clone, keep the time stamp
    return cloneFormula;
  }
  
  //private Boolean                            m_contradicted; 
  private long                               m_timeStamp;
  private List<Condition>                    m_conditionList;
  private AbstractMemory                     m_abstractMemory;
  private Hashtable<String, List<Long>>      m_fieldAssignTimes;
  private List<Object[]>                     m_traversedPath;
  private Hashtable<ISSABasicBlock, Integer> m_visitedRecord;
  private SolverInput                        m_lastSolverInput;
  private Object                             m_lastSolverOutput;
  private SolverResult                       m_lastSolverResult;
  private SOLVER_RESULT                     m_lastSMTCheckResult;
}
