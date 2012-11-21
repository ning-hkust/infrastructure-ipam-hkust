package hk.ust.cse.Prevision;

import hk.ust.cse.Prevision.WeakestPrecondition.BBorInstInfo;
import hk.ust.cse.Prevision.Solver.ISolverLoader;
import hk.ust.cse.Prevision.Solver.SMTStatement;
import hk.ust.cse.Prevision.Solver.SMTStatementList;
import hk.ust.cse.Prevision.Solver.SMTTerm;
import hk.ust.cse.Prevision.Solver.SMTVariable;
import hk.ust.cse.Prevision.Solver.SMTVariableMap;
import hk.ust.cse.Prevision.Solver.Yices.YicesLoaderLib;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import com.ibm.wala.ssa.ISSABasicBlock;

// Predicate instances are immutable
public class Predicate {
  public enum SMT_RESULT {SAT, UNSAT, ERROR, TIMEOUT, STACK_OVERFLOW}
  
  public static final int NORMAL_SUCCESSOR      = 0;
  public static final int EXCEPTIONAL_SUCCESSOR = 1;
  
  // a TRUE predicate
  public Predicate() {
    m_SMTStatements = new ArrayList<List<String>>();
    m_varMap        = new Hashtable<String, List<String>>();
    m_phiMap        = new Hashtable<String, String>();
    m_defMap        = new Hashtable<String, Integer>();
  }
  
  public Predicate(List<List<String>> SMTStatements,
      Hashtable<String, List<String>> varMap, Hashtable<String, String> phiMap, 
      Hashtable<String, Integer> defMap) {
    // remember, each Predicate instance should own a unique
    // instance of SMTStatments and varMap!
    m_SMTStatements = SMTStatements;
    m_varMap        = varMap;
    m_phiMap        = phiMap;
    m_defMap        = defMap;
  }

  public boolean isContradicted() {
    if (m_contradicted == null) {
      // only need to do it once, because
      // Predicate instances are immutable!
      simplify();
    }

    return m_contradicted.booleanValue();
  }

  public SMT_RESULT smtCheck() {
    // initialize ISolverLoader
    if (s_solverLoader == null) {
      //s_solverLoader = new YicesLoader2(1);
      s_solverLoader = new YicesLoaderLib();
    }
    
    // create SMTObjects and save into member variables
    m_SMTVariableMap    = new SMTVariableMap(m_varMap, 25);
    m_SMTStatementList  = new SMTStatementList(m_SMTStatements, m_SMTVariableMap.getAllVarMap());
    
    SMT_RESULT smtResult = null;
    try {
      // generate SMT Solver inputs
      StringBuilder solverCmd = new StringBuilder();
      solverCmd.append(m_SMTVariableMap.genYicesInput());
      solverCmd.append(m_SMTStatementList.genYicesInput(m_SMTVariableMap.getFinalVarMap()));
      
      if (isContradicted() /* try simplify() first */) {
        smtResult = SMT_RESULT.UNSAT;
        m_lastSolverOutput = "unsat\nProven contradicted by simplify().";
      }
      else {
        // check with an smt solver instance
        ISolverLoader.SOLVER_COMP_PROCESS solverResult = 
          s_solverLoader.check(solverCmd.toString(), m_SMTVariableMap.getDefFinalVarMap());
        switch (solverResult) {
        case SAT:
          smtResult = SMT_RESULT.SAT;
          break;
        case UNSAT:
          smtResult = SMT_RESULT.UNSAT;
          break;
        case ERROR:
          smtResult = SMT_RESULT.ERROR;
          break;
        case TIMEOUT:
          smtResult = SMT_RESULT.TIMEOUT;
          break;
        }
        
        // save smt solver input and output right away, because s_solverLoader only  
        // keeps the last one, so it might change from time to time
        m_lastSolverInput  = s_solverLoader.getLastInput();
        m_lastSolverOutput = s_solverLoader.getLastOutput();
        m_lastSatModel     = s_solverLoader.getLastResult().getSatModel();
      } 
    } catch (StackOverflowError e) {
      System.err.println("Stack overflowed when generating SMT statements, skip!");
      smtResult = SMT_RESULT.STACK_OVERFLOW;
    }
    
    // save the solver result
    m_lastSolverResult = smtResult;
    
    return smtResult;
  }

  // clear everything except solver data, they are taking too much memory!
  public void clearNonSolverData() {
    m_SMTStatements    = null;
    m_varMap           = null;
    m_phiMap           = null;
    m_defMap           = null;
    m_SMTStatementList = null;
    m_SMTVariableMap   = null;
    m_visitedRecord    = null;
  }

  // clear solver data
  public void clearSolverData() {
    m_lastSolverInput  = null;
    m_lastSolverOutput = null;
  }

  public String getLastSolverOutput() {
    if (s_solverLoader != null) {
      return m_lastSolverOutput;
    }
    else {
      return "";
    }
  }

  public String getLastSolverInput() {
    if (s_solverLoader != null) {
      return m_lastSolverInput;
    }
    else {
      return "";
    }
  }
  
  public List<SMTTerm> getLastSatModel() {
    if (s_solverLoader != null) {
      return m_lastSatModel;
    }
    else {
      return null;
    }
  }
  
  public SMT_RESULT getLastSolverResult() {
    if (s_solverLoader != null) {
      return m_lastSolverResult;
    }
    else {
      return null;
    }
  }

  public ISolverLoader getSolverLoader() {
    return s_solverLoader;
  }

  public SMTStatementList getSMTStatementList() {
    return m_SMTStatementList;
  }

  public SMTVariableMap getSMTVariableMap() {
    return m_SMTVariableMap;
  }

  public List<List<String>> getSMTStatements() {
    return m_SMTStatements;
  }

  public Hashtable<String, List<String>> getVarMap() {
    return m_varMap;
  }
  
  // is it efficient?
  @SuppressWarnings("unchecked")
  public Hashtable<String, List<String>> getVarMapClone() {
    Hashtable<String, List<String>> newVarMap = 
      (Hashtable<String, List<String>>) m_varMap.clone();

    Enumeration<String> keys = newVarMap.keys();
    while (keys.hasMoreElements()) {
      String key = (String) keys.nextElement();
      List<String> val = newVarMap.get(key);
      newVarMap.put(key, (List<String>) ((ArrayList<String>) val).clone());
    }

    return newVarMap;
  }
  
  public Hashtable<String, String> getPhiMap() {
    return m_phiMap;
  }

  public Hashtable<String, Integer> getDefMap() {
    return m_defMap;
  }

  @SuppressWarnings("unchecked")
  public Hashtable<String, String> getPhiMapClone() {
    return (Hashtable<String, String>) m_phiMap.clone();
  }
  
  @SuppressWarnings("unchecked")
  public Hashtable<String, Integer> getDefMapClone() {
    return (Hashtable<String, Integer>) m_defMap.clone();
  }

  public Hashtable<ISSABasicBlock, Integer> getVisitedRecord() {
    return m_visitedRecord;
  }

  @SuppressWarnings("unchecked")
  public void setVisitedRecord(Hashtable<ISSABasicBlock, Integer> lastRecord,
      BBorInstInfo newlyVisited) {
    if (lastRecord != null) {
      m_visitedRecord = (Hashtable<ISSABasicBlock, Integer>) lastRecord.clone();
    }
    else {
      m_visitedRecord = new Hashtable<ISSABasicBlock, Integer>();
    }
    
    // mark as visited
    Integer count = m_visitedRecord.get(newlyVisited.currentBB);
    if (count == null) {
      count = 0;
    }
    
    // add loop
    if (newlyVisited.sucessorBB != null && 
        newlyVisited.sucessorBB.getNumber() < newlyVisited.currentBB.getNumber()) {
      count++;
    }
    m_visitedRecord.put(newlyVisited.currentBB, count);
  }

  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    
    if (!(obj instanceof Predicate)) {
      return false;
    }
    
    Predicate predicate = (Predicate) obj;
    return m_SMTStatements.equals(predicate.getSMTStatements()) && 
           m_varMap.equals(predicate.getVarMap()) && 
           m_phiMap.equals(predicate.getPhiMap()) && 
           m_defMap.equals(predicate.getDefMap());
  }

  public int hashCode() {
    return m_SMTStatements.hashCode() + m_varMap.hashCode() + m_phiMap.hashCode() + m_defMap.hashCode();
  }

  private void simplify() {
    m_contradicted = false;
    
    List<SMTStatement> smtStatements = m_SMTStatementList.getSMTStatements();
    for (int i = 0, size = smtStatements.size(); i < size && !m_contradicted; i++) {
      if (smtStatements.get(i).getSMTTerms().size() != 1) {
        continue;
      }
      
      SMTTerm smtTerm = smtStatements.get(i).getSMTTerms().get(0);
      SMTTerm.Operator op = smtTerm.getOp();
      SMTVariable var1 = smtTerm.getVar1();
      SMTVariable var2 = smtTerm.getVar2();
      
      // simplify rule 1: 
      String var1Str = var1.toYicesExprString(0);
      String var2Str = var2.toYicesExprString(0);
      if (var1Str.equals(var2Str) && 
         (op == SMTTerm.Operator.OP_GREATER || 
          op == SMTTerm.Operator.OP_INEQUAL || 
          op == SMTTerm.Operator.OP_SMALLER)) {
        m_contradicted = true;
        continue;
      }
      
      // simplify rule 2: 
      List<String> key = new ArrayList<String>();
      key.add(var1Str);
      key.add(var2Str);
      
      Hashtable<List<String>, List<SMTTerm.Operator>> statements = 
        new Hashtable<List<String>, List<SMTTerm.Operator>>();
      List<SMTTerm.Operator> previousOps = statements.get(key);
      if (previousOps == null) {
        previousOps = new ArrayList<SMTTerm.Operator>();
        statements.put(key, previousOps);
      }
      
      for (int j = 0, size2 = previousOps.size(); j < size2 && !m_contradicted; j++) {
        SMTTerm.Operator previousOp = previousOps.get(j);
        
        if (previousOp == SMTTerm.Operator.OP_EQUAL && 
           (op == SMTTerm.Operator.OP_GREATER || 
            op == SMTTerm.Operator.OP_INEQUAL || 
            op == SMTTerm.Operator.OP_SMALLER)) {
          m_contradicted = true;
        }
        else if (previousOp == SMTTerm.Operator.OP_GREATER && 
                (op == SMTTerm.Operator.OP_EQUAL || 
                 op == SMTTerm.Operator.OP_SMALLER || 
                 op == SMTTerm.Operator.OP_SMALLER_EQUAL)) {
          m_contradicted = true;
        }     
        else if (previousOp == SMTTerm.Operator.OP_GREATER_EQUAL && 
                (op == SMTTerm.Operator.OP_SMALLER)) {
          m_contradicted = true;
        }
        else if (previousOp == SMTTerm.Operator.OP_INEQUAL && 
                (op == SMTTerm.Operator.OP_EQUAL)) {
          m_contradicted = true;
        }       
        else if (previousOp == SMTTerm.Operator.OP_SMALLER && 
                (op == SMTTerm.Operator.OP_EQUAL || 
                 op == SMTTerm.Operator.OP_GREATER || 
                 op == SMTTerm.Operator.OP_GREATER_EQUAL)) {
          m_contradicted = true;
        } 
        else if (previousOp == SMTTerm.Operator.OP_SMALLER_EQUAL && 
                (op == SMTTerm.Operator.OP_GREATER)) {
          m_contradicted = true;
        }
      }
      previousOps.add(op);
      
      // simplify rule 3: 
      // there is a bug in yices: when (assert (= #somestr null)) is input, yices will crash.
      if (var1Str.startsWith("#") && var2Str.equals("null") && op == SMTTerm.Operator.OP_EQUAL) {
        m_contradicted = true;
      }
    }
  }
  
  private Boolean                            m_contradicted;
  private List<List<String>>                 m_SMTStatements;
  private Hashtable<String, List<String>>    m_varMap;
  private Hashtable<String, String>          m_phiMap;
  private Hashtable<String, Integer>         m_defMap;
  private SMTStatementList                   m_SMTStatementList;
  private SMTVariableMap                     m_SMTVariableMap;
  private Hashtable<ISSABasicBlock, Integer> m_visitedRecord;
  private String                             m_lastSolverInput;
  private String                             m_lastSolverOutput;
  private SMT_RESULT                        m_lastSolverResult;
  private List<SMTTerm>                      m_lastSatModel;
  private static ISolverLoader               s_solverLoader;
}
