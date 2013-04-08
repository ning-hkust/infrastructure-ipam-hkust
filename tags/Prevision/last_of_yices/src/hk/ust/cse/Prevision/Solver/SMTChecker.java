package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.PathCondition.Formula.SMT_RESULT;
import hk.ust.cse.Prevision.Solver.ICommand.TranslatedCommand;
import hk.ust.cse.Prevision.Solver.Yices.YicesCommand;
import hk.ust.cse.Prevision.Solver.Yices.YicesLoaderExe;
import hk.ust.cse.Prevision.Solver.Yices.YicesLoaderLib;
import hk.ust.cse.Prevision.Solver.Yices.YicesResult;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SMTChecker {
  public enum SOLVERS {YICES, YICES_BIN, Z3}
  
  public SMTChecker(SOLVERS solver) {
    m_solverType = solver;
    switch (solver) {
    case YICES:
      m_solverLoader = new YicesLoaderLib();
      m_command      = new YicesCommand();
      break;
    case YICES_BIN:
      m_solverLoader = new YicesLoaderExe();
      m_command      = new YicesCommand();
      break;
    case Z3:
      m_solverLoader = new YicesLoaderLib();
      m_command      = new YicesCommand();
      break;
    default:
      m_solverLoader = new YicesLoaderLib();
      m_command      = new YicesCommand();
      break;
    }
  }
  
  // a fast simple check
  public SMT_RESULT simpleCheck(Formula formula) {
    String simpleCheckResult = SimpleChecker.simpleCheck(formula, null, false);
    return simpleCheckResult.startsWith("sat") ? SMT_RESULT.SAT : SMT_RESULT.UNSAT;
  }
  
  public SMT_RESULT smtCheck(Formula formula, boolean checkSatOnly, boolean retrieveModel, 
      boolean retrievePatialModel, boolean keepUnboundedField, boolean retrieveUnsatCore, boolean usePredefObjectValues) {
    
    SMT_RESULT smtCheckResult = null;
    try {
      // use the field values of the predefined static final objects
      if (!checkSatOnly && usePredefObjectValues) {
        addUsePredefObjectValuesTerms(formula);
      }
      
      // generate SMT Solver inputs
      m_lastCmd = m_command.translateToCommand(formula, keepUnboundedField, retrieveUnsatCore);
      
      boolean finishedChecking = false;

      // a fast simple check
      if (!finishedChecking) {
        String simpleCheckResult = SimpleChecker.simpleCheck(formula, m_lastCmd, retrieveUnsatCore);
        if (simpleCheckResult.startsWith("unsat")) { // try simpleChecker first
          smtCheckResult = SMT_RESULT.UNSAT;
          
          m_lastSolverInput  = m_lastCmd.command;
          m_lastSolverOutput = simpleCheckResult;
          m_lastSolverResult = createNewSolverResult();
          m_lastSolverResult.parseOutput(m_lastSolverOutput, formula, m_lastCmd);
          finishedChecking = true;
        }
      }

      // full SMT check
      if (!finishedChecking) {
        ISolverLoader.SOLVER_COMP_PROCESS solverResult = m_solverLoader.check(m_lastCmd.command);
        switch (solverResult) {
        case SAT:
          smtCheckResult = SMT_RESULT.SAT;
          break;
        case UNSAT:
          smtCheckResult = SMT_RESULT.UNSAT;
          break;
        case ERROR:
          smtCheckResult = SMT_RESULT.ERROR;
          System.err.println(m_solverLoader.getLastOutput());
          //System.out.println(m_lastCmd.command);
          break;
        case TIMEOUT:
          smtCheckResult = SMT_RESULT.TIMEOUT;
          break;
        }
        
        // save SMT solver input and output right away, because s_solverLoader only  
        // keeps the last one, so it might change from time to time
        m_lastSolverInput  = m_solverLoader.getLastInput();
        m_lastSolverOutput = m_solverLoader.getLastOutput();
        
        if (!checkSatOnly) {
          // may take non-trivial time due to removeUselessFuncIntrp() when there are many array operations
          m_lastSolverResult = createNewSolverResult();
          m_lastSolverResult.parseOutput(m_lastSolverOutput, formula, m_lastCmd);
          if (retrieveModel && m_lastSolverResult.isSatisfactory()) {
            m_lastSolverResult.parseOutputModel(m_lastSolverOutput, formula, m_lastCmd, retrievePatialModel);
          }
        }
      }
    } catch (StackOverflowError e) {
      System.err.println("Stack overflowed when generating SMT statements, skip!");
      smtCheckResult = SMT_RESULT.STACK_OVERFLOW;
    } catch (Exception e) {
      if (e.getMessage() != null) {
        System.err.println(e.getMessage());
      }
      else {
        e.printStackTrace();
      }
      smtCheckResult = SMT_RESULT.ERROR;
    }
    
    // save the solver SMT check result
    m_lastSMTCheckResult = smtCheckResult;
    
    return smtCheckResult;
  }
  
  public Object[] smtChecksInContext(int ctx, Formula ctxFormula, List<List<Condition>> ctxConditions, 
      boolean checkSatOnly, boolean retrieveModel, boolean retrievePatialModel, 
      boolean keepUnboundedField, boolean retrieveUnsatCore, boolean usePredefObjectValues) {
    
    // results to return
    SMT_RESULT formulaCheckResult    = null;
    List<SMT_RESULT> smtCheckResults = new ArrayList<SMT_RESULT>();
    List<AbstractSolverResult> solverResults = new ArrayList<AbstractSolverResult>();
    
    int useCtx = -1;
    if (ctxFormula != null) {
      try {
        
        // use the field values of the predefined static final objects
        if (!checkSatOnly && usePredefObjectValues) {
          addUsePredefObjectValuesTerms(ctxFormula);
        }
        
        // generate SMT Solver inputs
        m_lastCmd = m_command.translateToCommand(ctxFormula, keepUnboundedField, retrieveUnsatCore);
        
        boolean finishedChecking = false;

        // a fast simple check
        if (!finishedChecking) {
          String simpleCheckResult = SimpleChecker.simpleCheck(ctxFormula, m_lastCmd, retrieveUnsatCore);
          if (simpleCheckResult.startsWith("unsat")) { // try simpleChecker first
            formulaCheckResult = SMT_RESULT.UNSAT;
            
            m_lastSolverInput  = m_lastCmd.command;
            m_lastSolverOutput = simpleCheckResult;
            m_lastSolverResult = createNewSolverResult();
            m_lastSolverResult.parseOutput(m_lastSolverOutput, ctxFormula, m_lastCmd);
            finishedChecking = true;
          }
        }

        // full SMT check
        if (!finishedChecking) {
          // create a new context
          useCtx = ctx < 0 ? m_solverLoader.createContext() : ctx;
          
          ISolverLoader.SOLVER_COMP_PROCESS solverResult = m_solverLoader.checkInContext(useCtx, m_lastCmd.command);
          switch (solverResult) {
          case SAT:
            formulaCheckResult = SMT_RESULT.SAT;
            break;
          case UNSAT:
            formulaCheckResult = SMT_RESULT.UNSAT;
            break;
          case ERROR:
            formulaCheckResult = SMT_RESULT.ERROR;
            System.out.println(m_lastCmd.command);
            break;
          case TIMEOUT:
            formulaCheckResult = SMT_RESULT.TIMEOUT;
            break;
          }
          
          // push the current context
          m_solverLoader.pushContext(useCtx);
          
          // save SMT solver input and output right away, because s_solverLoader only  
          // keeps the last one, so it might change from time to time
          m_lastSolverInput  = m_solverLoader.getLastInput();
          m_lastSolverOutput = m_solverLoader.getLastOutput();
          
          if (!checkSatOnly) {
            // may take non-trivial time due to removeUselessFuncIntrp() when there are many array operations
            m_lastSolverResult = createNewSolverResult();
            m_lastSolverResult.parseOutput(m_lastSolverOutput, ctxFormula, m_lastCmd);
            if (retrieveModel && m_lastSolverResult.isSatisfactory()) {
              m_lastSolverResult.parseOutputModel(m_lastSolverOutput, ctxFormula, m_lastCmd, retrievePatialModel);
            }
          }
        }
      } catch (StackOverflowError e) {
        System.err.println("Stack overflowed when generating SMT statements, skip!");
        formulaCheckResult = SMT_RESULT.STACK_OVERFLOW;
      }
    }
      
    // check each context conditions
    if ((formulaCheckResult == SMT_RESULT.SAT || ctx >= 0) && ctxConditions != null) {
      for (int i = 0, size = ctxConditions.size(); i < size; i++) {
        StringBuilder additionalCmds = new StringBuilder();
        for (Condition condition : ctxConditions.get(i)) {
          String command = m_command.translateToCommand(condition, keepUnboundedField, retrieveUnsatCore);
          if (command.length() > 0) {
            if (!command.contains("%%UnboundField%%")) {
              additionalCmds.append(command);
            }
          }
        }
        additionalCmds.append(m_command.getCheckCommand());
        
        // restore context
        useCtx = useCtx < 0 ? ctx : useCtx;
        m_solverLoader.popContext(useCtx);
        m_solverLoader.pushContext(useCtx);
        
        // check in context
        SMT_RESULT smtCheckResult = null;
        ISolverLoader.SOLVER_COMP_PROCESS solverResult2 = m_solverLoader.checkInContext(useCtx, additionalCmds.toString());
        switch (solverResult2) {
        case SAT:
          smtCheckResult = SMT_RESULT.SAT;
          break;
        case UNSAT:
          smtCheckResult = SMT_RESULT.UNSAT;
          break;
        case ERROR:
          smtCheckResult = SMT_RESULT.ERROR;
          System.out.println(m_lastCmd.command);
          break;
        case TIMEOUT:
          smtCheckResult = SMT_RESULT.TIMEOUT;
          break;
        }
        smtCheckResults.add(smtCheckResult);
        
        // save ISolverResult
        AbstractSolverResult lastSolverResult = null;
        if (!checkSatOnly) {
          // may take non-trivial time due to removeUselessFuncIntrp() when there are many array operations
          try {
            lastSolverResult = createNewSolverResult();
            lastSolverResult.parseOutput(m_solverLoader.getLastOutput(), ctxFormula, m_lastCmd);
            if (retrieveModel && m_lastSolverResult.isSatisfactory()) {
              lastSolverResult.parseOutputModel(m_solverLoader.getLastOutput(), ctxFormula, m_lastCmd, retrievePatialModel);
            }
          } catch (Exception e) {}
        }
        solverResults.add(lastSolverResult);
      }
    }
    
    // delete the context only if it is create by this method
    if (ctx < 0 && useCtx >= 0) {
      m_solverLoader.deleteContext(useCtx);
    }
    
    // save the solver SMT check result
    m_lastSMTCheckResult = formulaCheckResult;
    
    return new Object[] {formulaCheckResult, smtCheckResults, solverResults};
  }
  
  // add equal to predefined static final object's integer field value terms
  private void addUsePredefObjectValuesTerms(Formula formula) {
    
    // collect all instances with int type
    HashSet<Instance> intInstances = new HashSet<Instance>();
    for (Condition condition : formula.getConditionList()) {
      List<ConditionTerm> terms = condition.getConditionTerms();
      for (ConditionTerm term : terms) {
        Instance[] instances = term.getInstances();
        for (Instance instance : instances) {
          if (instance.getLastReference() != null && "I".equals(instance.getLastRefType())) {
            intInstances.add(instance);
          }
        }
      }
    }
    
    // add conditions
    for (Instance intInstance : intInstances) {
     Instance declInstance = intInstance.getLastReference().getDeclaringInstance();

      Class<?> declClass = null;
      if (declInstance != null && declInstance.getLastReference() != null) {
        String declClassName = declInstance.getLastRefType();
        declClass = Utils.findClass(declClassName);
      }
      
      if (declClass != null) {
        Constructor<?>[] ctors = Utils.getPublicCtors(declClass);
        
        // if there are public constructors, we do not use the static final field values
        if (ctors.length == 0) {
          String intFieldName = intInstance.getLastRefName();
          Field intField = Utils.getInheritedField(declClass, intFieldName);

          if (intField != null) {
            boolean accessible = intField.isAccessible();
            intField.setAccessible(true);
            
            HashSet<Integer> possibleIntValues = new HashSet<Integer>();
            Field[] fields = declClass.getDeclaredFields();
            for (Field field : fields) {
              try {
                if (Modifier.isStatic(field.getModifiers()) && 
                    Modifier.isFinal(field.getModifiers()) && declClass.equals(field.getType())) {
    
                  Object fieldObject = field.get(null);
                  Object intFieldValue = intField.get(fieldObject);
                  if (intFieldValue instanceof Integer) {
                    possibleIntValues.add((Integer) intFieldValue);
                  }
                }
              } catch (Exception e) {}
            }
            intField.setAccessible(accessible);
            
            if (possibleIntValues.size() > 0) {
              List<ConditionTerm> terms = new ArrayList<ConditionTerm>();
              for (Integer possibleIntValue : possibleIntValues) {
                BinaryConditionTerm term = new BinaryConditionTerm(
                    intInstance, Comparator.OP_EQUAL, new Instance("#!" + possibleIntValue, "I", null));
                terms.add(term);
              }
              formula.getConditionList().add(new Condition(terms));
            }
          }
        }
      }
    }
  }
  
  public int createContext() {
    return m_solverLoader.createContext();
  }
  
  public void deleteContext(int ctx) {
    m_solverLoader.deleteContext(ctx);
  }
  
  public List<String> findLastUnsatCoreCmds() {
    List<String> unsatCoreCmds = new ArrayList<String>();

    List<Integer> unsatCoreIds = m_lastSolverResult.getUnsatCoreIds();
    if (unsatCoreIds != null && unsatCoreIds.size() > 0) {
      for (Integer unsatCoreId : unsatCoreIds) {
        String unsatCoreCmd = m_lastCmd.assertCmds.get(unsatCoreId - 1);
        unsatCoreCmds.add(unsatCoreCmd);
      }
    }
    return unsatCoreCmds;
  }
  
  public List<Condition> findLastUnsatCoreConditions() {
    List<Condition> unsatCoreConds = new ArrayList<Condition>();

    List<String> unsatCoreCmds = findLastUnsatCoreCmds();
    for (String unsatCoreCmd : unsatCoreCmds) {
      List<Condition> unsatCoreConditions = m_lastCmd.assertCmdCondsMapping.get(unsatCoreCmd);
      if (unsatCoreConditions != null && unsatCoreConditions.size() > 0) {
        Condition unsatCore = unsatCoreConditions.get(0); // get the first one
        unsatCoreConds.add(unsatCore);
      }
    }
    return unsatCoreConds;
  }
  
  private AbstractSolverResult createNewSolverResult() {
    AbstractSolverResult solverResult = null;
    switch (m_solverType) {
    case YICES:
      solverResult = new YicesResult();
      break;
    case Z3:
      solverResult = new YicesResult();
      break;
    default:
      solverResult = new YicesResult();
      break;
    }
    return solverResult;
  }
  
  public void clearSolverData() {
    m_lastSolverInput  = null;
    m_lastSolverOutput = null;
    m_lastSolverResult = null;
  }
  
  public String getLastSolverOutput() {
    return m_lastSolverOutput;
  }

  public String getLastSolverInput() {
    return m_lastSolverInput;
  }
  
  public AbstractSolverResult getLastResult() {
    return m_lastSolverResult;
  }
  
  public SMT_RESULT getLastSMTCheckResult() {
    return m_lastSMTCheckResult;
  }
  
  public TranslatedCommand getLastTranslatedCommand() {
    return m_lastCmd;
  }
  
  private String              m_lastSolverInput;
  private String              m_lastSolverOutput;
  private AbstractSolverResult m_lastSolverResult;
  private SMT_RESULT         m_lastSMTCheckResult;
  private TranslatedCommand   m_lastCmd;
  private final SOLVERS       m_solverType;
  private final ISolverLoader m_solverLoader;
  private final ICommand      m_command;
}
