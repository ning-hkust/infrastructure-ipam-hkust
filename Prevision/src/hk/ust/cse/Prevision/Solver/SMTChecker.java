package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm;
import hk.ust.cse.Prevision.PathCondition.BinaryConditionTerm.Comparator;
import hk.ust.cse.Prevision.PathCondition.Condition;
import hk.ust.cse.Prevision.PathCondition.ConditionTerm;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;
import hk.ust.cse.Prevision.Solver.SolverLoader.SOLVER_RESULT;
import hk.ust.cse.Prevision.Solver.Z3.Z3Input;
import hk.ust.cse.Prevision.Solver.Z3.Z3Loader;
import hk.ust.cse.Prevision.Solver.Z3.Z3Result;
import hk.ust.cse.Prevision.VirtualMachine.Instance;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;

public class SMTChecker {
  public enum SOLVERS {Z3}
  
  public SMTChecker(SOLVERS solverType) {
    m_solverType = solverType;
    switch (solverType) {
    case Z3:
      m_solverLoader = new Z3Loader();
      break;
    default:
      m_solverLoader = new Z3Loader();
      break;
    }
  }
  
  // a fast simple check
  public SOLVER_RESULT simpleCheck(Formula formula) {
    String simpleCheckResult = SimpleChecker.simpleCheck(formula, null, false);
    return simpleCheckResult.startsWith("sat") ? SOLVER_RESULT.SAT : SOLVER_RESULT.UNSAT;
  }
  
  public SOLVER_RESULT smtCheck(Formula formula, boolean checkSatOnly, boolean retrieveModel, 
      boolean retrievePatialModel, boolean keepUnboundField, boolean retrieveUnsatCore, boolean usePredefObjectValues) {
    
    clearSolverData();
    
    Object context = null;
    SOLVER_RESULT smtCheckResult = null;
    try {
      // use the field values of the predefined static final objects
      if (!checkSatOnly && usePredefObjectValues) {
        addUsePredefinedObjValuesTerms(formula);
      }

      // generate a neutral input first
      NeutralInput neutralInput = new NeutralInput(formula, keepUnboundField, retrieveModel, retrieveUnsatCore);
      
      // create a fresh context
      context = m_solverLoader.createContext();
      
      // generate a solver input from neutral input
      m_lastSolverInput = createNewSolverInput(context, neutralInput);
      
      // start checking: 1) simple check, 2) full check
      boolean finishedChecking = false;

      // a fast simple check
      if (!finishedChecking) {
        String simpleCheckResult = SimpleChecker.simpleCheck(formula, m_lastSolverInput, retrieveUnsatCore);
        if (simpleCheckResult.startsWith("unsat")) {
          smtCheckResult = SOLVER_RESULT.UNSAT;
          
          m_lastSolverOutput = simpleCheckResult;
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput);
          finishedChecking = true;
        }
      }

      // full SMT check
      if (!finishedChecking) {
        smtCheckResult = m_solverLoader.check(m_lastSolverInput);
        
        // save output object
        m_lastSolverOutput = m_solverLoader.getLastOutput();
        if (!checkSatOnly) {
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput);
        }
      }
      
    } catch (StackOverflowError e) {
      System.err.println("Stack overflowed when generating SMT solver input, skip!");
      smtCheckResult = SOLVER_RESULT.STACK_OVERFLOW;
    } catch (Exception e) {
      if (e.getMessage() != null) {
        System.err.println(e.getMessage());
      }
      else {
        e.printStackTrace();
      }
      smtCheckResult = SOLVER_RESULT.ERROR;
    } finally {
      if (context != null) {
        m_solverLoader.deleteContext(context);
      }
    }
    
    // save the solver SMT check result
    m_lastSMTCheckResult = smtCheckResult;
    
    return smtCheckResult;
  }
  
  public SOLVER_RESULT smtCheckInContext(Object ctxStorage, Formula ctxFormula, boolean checkSatOnly, boolean retrieveModel, 
      boolean retrievePatialModel, boolean keepUnboundField, boolean retrieveUnsatCore, boolean usePredefObjectValues) {
    
    clearSolverData();
    
    SOLVER_RESULT smtCheckResult = null;
    try {
      // use the field values of the predefined static final objects
      if (!checkSatOnly && usePredefObjectValues) {
        addUsePredefinedObjValuesTerms(ctxFormula);
      }

      // generate a neutral input first
      NeutralInput neutralInput = new NeutralInput(ctxFormula, keepUnboundField, retrieveModel, retrieveUnsatCore);
      
      // generate a solver input from neutral input
      m_lastSolverInput = createNewSolverInput(ctxStorage, neutralInput);
      
      // start checking: 1) simple check, 2) full check
      boolean finishedChecking = false;

      // a fast simple check
      if (!finishedChecking) {
        String simpleCheckResult = SimpleChecker.simpleCheck(ctxFormula, m_lastSolverInput, retrieveUnsatCore);
        if (simpleCheckResult.startsWith("unsat")) {
          smtCheckResult = SOLVER_RESULT.UNSAT;
          
          m_lastSolverOutput = simpleCheckResult;
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput);
          finishedChecking = true;
        }
      }

      // full SMT check
      if (!finishedChecking) {
        smtCheckResult = m_solverLoader.checkInContext(ctxStorage, m_lastSolverInput);
        
        // save output object
        m_lastSolverOutput = m_solverLoader.getLastOutput();
        if (!checkSatOnly) {
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput);
        }
      }
      
    } catch (StackOverflowError e) {
      System.err.println("Stack overflowed when generating SMT solver input, skip!");
      smtCheckResult = SOLVER_RESULT.STACK_OVERFLOW;
    } catch (Exception e) {
      if (e.getMessage() != null) {
        System.err.println(e.getMessage());
      }
      else {
        e.printStackTrace();
      }
      smtCheckResult = SOLVER_RESULT.ERROR;
    }
    
    // save the solver SMT check result
    m_lastSMTCheckResult = smtCheckResult;
    
    return smtCheckResult;
  }
  
  // check the additional conditions
  public SOLVER_RESULT smtCheckInContext(Object ctxStorage, Formula ctxFormula, NeutralInput ctxInput, 
      List<Condition> conditions, boolean checkSatOnly, boolean retrieveModel, boolean retrievePatialModel, 
      boolean keepUnboundField, boolean retrieveUnsatCore, boolean usePredefObjectValues) {
    
    clearSolverData();
    
    SOLVER_RESULT smtCheckResult = null;
    try {
      // generate a neutral input first
      NeutralInput neutralInput = new NeutralInput(ctxFormula, ctxInput, conditions);
      
      // generate a solver input from neutral input
      m_lastSolverInput = createNewSolverInput(ctxStorage, neutralInput);
      
      // find the assertions of the conditions
      Assertion[] assertions = new Assertion[conditions.size()];
      for (int i = 0; i < assertions.length; i++) {
        assertions[i] = neutralInput.getConditionAssertionMapping().get(conditions.get(i));
      }
      
      // check in context
      smtCheckResult = m_solverLoader.checkInContext(ctxStorage, m_lastSolverInput, assertions);
      
      // save output object
      m_lastSolverOutput = m_solverLoader.getLastOutput();
      if (!checkSatOnly) {
        m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput);
      }

    } catch (Exception e) {
      e.printStackTrace();
      smtCheckResult = SOLVER_RESULT.ERROR;
    }

    // save the solver SMT check result
    m_lastSMTCheckResult = smtCheckResult;
    
    return smtCheckResult;
  }
  
  public List<Condition> findLastUnsatCoreConditions() {
    List<Condition> unsatCoreConds = new ArrayList<Condition>();

    List<Assertion> unsatCoreAssertions = findLastUnsatCoreExpressions();
    for (Assertion unsatCoreAssertion : unsatCoreAssertions) {
      List<Condition> unsatCoreConditions = 
          m_lastSolverInput.getNeutralInput().getAssertionCondsMapping().get(unsatCoreAssertion);
      if (unsatCoreConditions != null && unsatCoreConditions.size() > 0) {
        Condition unsatCore = unsatCoreConditions.get(0); // get the first one
        unsatCoreConds.add(unsatCore);
      }
    }
    return unsatCoreConds;
  }
  
  private List<Assertion> findLastUnsatCoreExpressions() {
    List<Assertion> unsatCoreAssertions = new ArrayList<Assertion>();

    List<Integer> unsatCoreIds = m_lastSolverResult.getUnsatCoreIds();
    if (unsatCoreIds != null && unsatCoreIds.size() > 0) {
      for (Integer unsatCoreId : unsatCoreIds) {
        Assertion unsatCoreAssertion = 
            m_lastSolverInput.getNeutralInput().getAssertions().get(unsatCoreId - 1);
        unsatCoreAssertions.add(unsatCoreAssertion);
      }
    }
    return unsatCoreAssertions;
  }
  
  // add equal to predefined static final object's integer field value terms
  private void addUsePredefinedObjValuesTerms(Formula formula) {
    
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
  
  private SolverInput createNewSolverInput(Object context, NeutralInput neutralInput) throws Exception {
    SolverInput solverInput = null;
    switch (m_solverType) {
    case Z3:
      if (context instanceof Solver) {
        context = ((Z3Loader) m_solverLoader).getSolverContext((Solver) context);
      }
      solverInput = new Z3Input((Context) context, neutralInput);
      break;
    default:
      break;
    }
    return solverInput;
  }
  
  private SolverResult createNewSolverResult(SOLVER_RESULT result, Object output, SolverInput solverInput) {
    SolverResult solverResult = null;
    switch (m_solverType) {
    case Z3:
      solverResult = new Z3Result(result, output, (Z3Input) solverInput);
      break;
    default:
      break;
    }
    return solverResult;
  }
  
  public Object createContext() {
    return m_solverLoader.createContext();
  }
  
  public void deleteContext(Object ctx) {
    m_solverLoader.deleteContext(ctx);
  }
  
  public void clearSolverData() {
    m_lastSolverInput    = null;
    m_lastSolverOutput   = null;
    m_lastSolverResult   = null;
    m_lastSMTCheckResult = null;
  }

  public SolverInput getLastSolverInput() {
    return m_lastSolverInput;
  }
  
  public Object getLastSolverOutput() {
    return m_lastSolverOutput;
  }
  
  public SolverResult getLastResult() {
    return m_lastSolverResult;
  }
  
  public SOLVER_RESULT getLastSMTCheckResult() {
    return m_lastSMTCheckResult;
  }
  
  public SolverLoader getSolverLoader() {
    return m_solverLoader;
  }
  
  private SolverInput         m_lastSolverInput;
  private Object              m_lastSolverOutput;
  private SolverResult        m_lastSolverResult;
  private SOLVER_RESULT      m_lastSMTCheckResult;
  
  private final SOLVERS      m_solverType;
  private final SolverLoader m_solverLoader;
}
