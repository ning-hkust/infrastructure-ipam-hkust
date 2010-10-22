package hk.ust.cse.Prevision;

import hk.ust.cse.Prevision.Solver.ISolverLoader;
import hk.ust.cse.Prevision.Solver.SMTStatementList;
import hk.ust.cse.Prevision.Solver.SMTVariableMap;
import hk.ust.cse.Prevision.Solver.Yices.YicesLoader;
import hk.ust.cse.Prevision.WeakestPrecondition.BBorInstInfo;
import hk.ust.cse.Prevision.WeakestPrecondition.GlobalOptionsAndStates;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.AbstractMap.SimpleEntry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;

// Predicate instances are immutable
public class Predicate {
  public enum SMT_RESULT {SAT, UNSAT, ERROR, TIMEOUT, STACK_OVERFLOW}
  
  public static final int NORMAL_SUCCESSOR      = 0;
  public static final int EXCEPTIONAL_SUCCESSOR = 1;

  private static final String s_regExpInstStr = "(?:v[\\d]+ = )*([\\p{Alpha}]+[ ]*[\\p{Alpha}]+)(?:\\([\\w]+\\))*(?: <[ \\S]+)*";
  private static final Pattern s_instPattern  = Pattern.compile(s_regExpInstStr);
  
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

  public Predicate getPrecondtion(GlobalOptionsAndStates optionsAndStates, 
      CGNode method, SSAInstruction inst, BBorInstInfo instInfo, CallStack callStack, 
      int curInvokeDepth, List<SimpleEntry<String, Predicate>> usedPredicates) {
    try {
      Matcher matcher = s_instPattern.matcher(inst.toString());
      if (matcher.find()) {
        String instType = matcher.group(1).toString();
        if (instType != null && instType.length() > 0) {
          // a hack to handle checkcast
          if (instType.startsWith("checkcast")) {
            instType = "checkcast";
          }
          System.out.println("handling " + instType + "...");

          // eliminate spaces in instruction names
          instType = instType.replace(' ', '_');
          
          Predicate preCond = null;
          if (!instType.startsWith("invoke") || 
              (!optionsAndStates.isEnteringCallStack() && 
               curInvokeDepth >= optionsAndStates.maxInvokeDepth && 
              !instInfo.wp.isCallStackInvokeInst(instInfo, inst))) {
            // invoke handler for this instruction
            Method rmethod = InstHandler.class.getMethod("handle_" + instType,
                Predicate.class, SSAInstruction.class, BBorInstInfo.class);
            preCond = (Predicate) rmethod.invoke(null, this, inst, instInfo);
          }
          else {
            System.out.println("stepping into " + instType + "...");
            // invoke handler for this instruction
            Method rmethod = InstHandler.class.getMethod("handle_" + instType + "_stepin", 
                GlobalOptionsAndStates.class, CGNode.class, Predicate.class, SSAInstruction.class, 
                BBorInstInfo.class, CallStack.class, int.class, List.class);
            preCond = (Predicate) rmethod.invoke(null, optionsAndStates, method, 
                this, inst, instInfo, callStack, curInvokeDepth, usedPredicates);
          }
          return preCond;
        }
        else {
          System.err.println("Unknown instruction string: " + inst.toString());
          return null;
        }
      }
      else {
        System.err.println("Unknown instruction string: " + inst.toString());
        return null;
      }
    } catch (NoSuchMethodException e) {
      System.err.println("No Handler defined for instruction: " + inst.toString());
      return null;
    } catch (Exception e2) {
      e2.printStackTrace();
      return null;
    }
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
      s_solverLoader = new YicesLoader();
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
      
    } catch (StackOverflowError e) {
      System.err.println("Stack overflowed when generating SMT statements, skip!");
      smtResult = SMT_RESULT.STACK_OVERFLOW;
    }
    
    // save the solver result
    m_lastSolverResult = smtResult;
    
    return smtResult;
  }
  
  public void clearNonSolverData() {
    // clear everything except solver results, they are taking too much memory!
    m_SMTStatements    = null;
    m_varMap           = null;
    m_phiMap           = null;
    m_defMap           = null;
    m_SMTStatementList = null;
    m_SMTVariableMap   = null;
    m_visitedRecord    = null;
    
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
    m_contradicted = Boolean.valueOf(false);
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
  private static ISolverLoader               s_solverLoader;
}
