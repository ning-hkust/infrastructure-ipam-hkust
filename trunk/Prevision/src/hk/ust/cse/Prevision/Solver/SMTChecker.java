package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.Formula.SMT_RESULT;
import hk.ust.cse.Prevision.Solver.Yices.YicesCommand;
import hk.ust.cse.Prevision.Solver.Yices.YicesLoader;
import hk.ust.cse.Wala.MethodMetaData;

public class SMTChecker {
  public enum SOLVERS {YICES, Z3}
  
  public SMTChecker(SOLVERS solver) {
    switch (solver) {
    case YICES:
      m_solverLoader = new YicesLoader();
      m_command      = new YicesCommand();
      break;
    case Z3:
      m_solverLoader = new YicesLoader();
      m_command      = new YicesCommand();
      break;
    default:
      m_solverLoader = new YicesLoader();
      m_command      = new YicesCommand();
      break;
    }
  }
  
  public SMT_RESULT smtCheck(Formula formula, MethodMetaData methData /* for param name */, boolean keepUnboundedField) {    
    
    SMT_RESULT smtResult = null;
    try {
      // generate SMT Solver inputs
      String command = m_command.translateToCommand(formula, methData, keepUnboundedField);
      
      if (false /*simplify()*/ /* try simplify() first */) {
        smtResult = SMT_RESULT.UNSAT;
        m_lastSolverOutput = "unsat\nProven contradicted by simplify().";
      }
      else {
        // check with an smt solver instance
        ISolverLoader.SOLVER_COMP_PROCESS solverResult = m_solverLoader.check(command);
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
        m_lastSolverInput  = m_solverLoader.getLastInput();
        m_lastSolverOutput = m_solverLoader.getLastOutput();
        //m_lastSatModel     = s_solverLoader.getLastResult().getSatModel();
      } 
    } catch (StackOverflowError e) {
      System.err.println("Stack overflowed when generating SMT statements, skip!");
      smtResult = SMT_RESULT.STACK_OVERFLOW;
    }
    
    // save the solver result
    m_lastSolverResult = smtResult;
    
    return smtResult;
  }
  
  public void clearSolverData() {
    m_lastSolverInput  = null;
    m_lastSolverOutput = null;
  }
  
  public String getLastSolverOutput() {
    return m_lastSolverOutput;
  }

  public String getLastSolverInput() {
    return m_lastSolverInput;
  }
  
  public SMT_RESULT getLastSolverResult() {
    return m_lastSolverResult;
  }
  
//  private boolean simplify() {
//    boolean isContradicted = false;
//    
//    List<SMTStatement> smtStatements = m_SMTStatementList.getSMTStatements();
//    for (int i = 0, size = smtStatements.size(); i < size && !m_contradicted; i++) {
//      if (smtStatements.get(i).getSMTTerms().size() != 1) {
//        continue;
//      }
//      
//      SMTTerm smtTerm = smtStatements.get(i).getSMTTerms().get(0);
//      SMTTerm.Operator op = smtTerm.getOp();
//      SMTVariable var1 = smtTerm.getVar1();
//      SMTVariable var2 = smtTerm.getVar2();
//      
//      // simplify rule 1: 
//      String var1Str = var1.toYicesExprString(0);
//      String var2Str = var2.toYicesExprString(0);
//      if (var1Str.equals(var2Str) && 
//         (op == SMTTerm.Operator.OP_GREATER || 
//          op == SMTTerm.Operator.OP_INEQUAL || 
//          op == SMTTerm.Operator.OP_SMALLER)) {
//        isContradicted = true;
//        continue;
//      }
//      
//      // simplify rule 2: 
//      List<String> key = new ArrayList<String>();
//      key.add(var1Str);
//      key.add(var2Str);
//      
//      Hashtable<List<String>, List<SMTTerm.Operator>> statements = 
//        new Hashtable<List<String>, List<SMTTerm.Operator>>();
//      List<SMTTerm.Operator> previousOps = statements.get(key);
//      if (previousOps == null) {
//        previousOps = new ArrayList<SMTTerm.Operator>();
//        statements.put(key, previousOps);
//      }
//      
//      for (int j = 0, size2 = previousOps.size(); j < size2 && !m_contradicted; j++) {
//        SMTTerm.Operator previousOp = previousOps.get(j);
//        
//        if (previousOp == SMTTerm.Operator.OP_EQUAL && 
//           (op == SMTTerm.Operator.OP_GREATER || 
//            op == SMTTerm.Operator.OP_INEQUAL || 
//            op == SMTTerm.Operator.OP_SMALLER)) {
//          isContradicted = true;
//        }
//        else if (previousOp == SMTTerm.Operator.OP_GREATER && 
//                (op == SMTTerm.Operator.OP_EQUAL || 
//                 op == SMTTerm.Operator.OP_SMALLER || 
//                 op == SMTTerm.Operator.OP_SMALLER_EQUAL)) {
//          isContradicted = true;
//        }     
//        else if (previousOp == SMTTerm.Operator.OP_GREATER_EQUAL && 
//                (op == SMTTerm.Operator.OP_SMALLER)) {
//          isContradicted = true;
//        }
//        else if (previousOp == SMTTerm.Operator.OP_INEQUAL && 
//                (op == SMTTerm.Operator.OP_EQUAL)) {
//          isContradicted = true;
//        }       
//        else if (previousOp == SMTTerm.Operator.OP_SMALLER && 
//                (op == SMTTerm.Operator.OP_EQUAL || 
//                 op == SMTTerm.Operator.OP_GREATER || 
//                 op == SMTTerm.Operator.OP_GREATER_EQUAL)) {
//          isContradicted = true;
//        } 
//        else if (previousOp == SMTTerm.Operator.OP_SMALLER_EQUAL && 
//                (op == SMTTerm.Operator.OP_GREATER)) {
//          isContradicted = true;
//        }
//      }
//      previousOps.add(op);
//      
//      // simplify rule 3: 
//      // there is a bug in yices: when (assert (= #somestr null)) is input, yices will crash.
//      if (var1Str.startsWith("#") && var2Str.equals("null") && op == SMTTerm.Operator.OP_EQUAL) {
//        isContradicted = true;
//      }
//    }
//    
//    return isContradicted;
//  }
  
  private String      m_lastSolverInput;
  private String      m_lastSolverOutput;
  private SMT_RESULT m_lastSolverResult;
  private final ISolverLoader m_solverLoader;
  private final ICommand      m_command;
}
