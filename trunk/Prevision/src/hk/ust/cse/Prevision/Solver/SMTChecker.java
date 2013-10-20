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
  
  public static abstract class SatModelProcessor {
    public abstract List<ConditionTerm> process(List<ConditionTerm> satModel, Formula formula);
  }
  
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
    m_satModelProcessors = new ArrayList<SatModelProcessor>();
  }
  
  // a fast simple check
  public SOLVER_RESULT simpleCheck(Formula formula) {
    Object simpleCheckResult = SimpleChecker.simpleCheck(formula, null, false);
    return simpleCheckResult.equals("sat") ? SOLVER_RESULT.SAT : SOLVER_RESULT.UNSAT;
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
      boolean preferGeneralType  = retrieveModel;
      boolean preferSmallerValue = retrieveModel;
      m_lastNeutralInput = new NeutralInput(formula, keepUnboundField, 
          retrieveModel, retrieveUnsatCore, preferGeneralType, preferSmallerValue);
      
      // start checking: 1) simple check, 2) full check
      boolean finishedChecking = false;

      // a fast simple check
      if (!finishedChecking) {
        Object simpleCheckResult = SimpleChecker.simpleCheck(formula, m_lastNeutralInput, retrieveUnsatCore);
        if (simpleCheckResult instanceof Condition[] /*unsat*/) {
          smtCheckResult = SOLVER_RESULT.UNSAT;
          
          m_lastSolverOutput = simpleCheckResult;
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput, formula);
          finishedChecking = true;
        }
      }

      // full SMT check
      if (!finishedChecking) {
        // create a fresh context
        context = m_solverLoader.createContext();
        
        // generate a solver input from neutral input
        m_lastSolverInput = createNewSolverInput(context, m_lastNeutralInput);
        
        smtCheckResult = m_solverLoader.check(m_lastSolverInput);
        
        // save output object
        m_lastSolverOutput = m_solverLoader.getLastOutput();
        if (!checkSatOnly) {
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput, formula);
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
      boolean preferGeneralType  = retrieveModel;
      boolean preferSmallerValue = retrieveModel;
      m_lastNeutralInput = new NeutralInput(ctxFormula, keepUnboundField, 
          retrieveModel, retrieveUnsatCore, preferGeneralType, preferSmallerValue);
      
      // generate a solver input from neutral input
      m_lastSolverInput = createNewSolverInput(ctxStorage, m_lastNeutralInput);
      
      // start checking: 1) simple check, 2) full check
      boolean finishedChecking = false;

      // a fast simple check
      if (!finishedChecking) {
        Object simpleCheckResult = SimpleChecker.simpleCheck(ctxFormula, m_lastNeutralInput, retrieveUnsatCore);
        if (simpleCheckResult instanceof Condition[] /*unsat*/) {
          smtCheckResult = SOLVER_RESULT.UNSAT;
          
          m_lastSolverOutput = simpleCheckResult;
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput, ctxFormula);
          finishedChecking = true;
        }
      }

      // full SMT check
      if (!finishedChecking) {
        smtCheckResult = m_solverLoader.checkInContext(ctxStorage, m_lastSolverInput);
        
        // save output object
        m_lastSolverOutput = m_solverLoader.getLastOutput();
        if (!checkSatOnly) {
          m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput, ctxFormula);
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
      m_lastNeutralInput = new NeutralInput(ctxFormula, ctxInput, conditions);
      
      // generate a solver input from neutral input
      m_lastSolverInput = createNewSolverInput(ctxStorage, m_lastNeutralInput);
      
      // find the assertions of the conditions
      Assertion[] assertions = new Assertion[conditions.size()];
      for (int i = 0; i < assertions.length; i++) {
        assertions[i] = m_lastNeutralInput.getConditionAssertionMapping().get(conditions.get(i));
      }
      
      // check in context
      smtCheckResult = m_solverLoader.checkInContext(ctxStorage, m_lastSolverInput, assertions);
      
      // save output object
      m_lastSolverOutput = m_solverLoader.getLastOutput();
      if (!checkSatOnly) {
        m_lastSolverResult = createNewSolverResult(smtCheckResult, m_lastSolverOutput, m_lastSolverInput, ctxFormula);
      }

    } catch (Exception e) {
      e.printStackTrace();
      smtCheckResult = SOLVER_RESULT.ERROR;
    }

    // save the solver SMT check result
    m_lastSMTCheckResult = smtCheckResult;
    
    return smtCheckResult;
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
  
  private SolverResult createNewSolverResult(SOLVER_RESULT result, Object output, SolverInput solverInput, Formula formula) {
    SolverResult solverResult = null;
    switch (m_solverType) {
    case Z3:
      solverResult = new Z3Result(result, output, (Z3Input) solverInput);
      break;
    default:
      break;
    }
    
    // apply satModel processors
    List<ConditionTerm> satModel = solverResult.getSatModel();
    if (satModel != null && m_satModelProcessors.size() > 0) {
      for (SatModelProcessor satModelProcessor : m_satModelProcessors) {
        satModel = satModelProcessor.process(satModel, formula);
      }
      solverResult.setSatModel(satModel);
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
    m_lastNeutralInput   = null;
    m_lastSolverInput    = null;
    m_lastSolverOutput   = null;
    m_lastSolverResult   = null;
    m_lastSMTCheckResult = null;
  }

  public SolverInput getLastSolverInput() {
    return m_lastSolverInput;
  }

  public NeutralInput getLastNeutralInput() {
    return m_lastNeutralInput;
  }
  
  public void addSatModelProcessor(SatModelProcessor processor) {
    m_satModelProcessors.add(processor);
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

  private NeutralInput                  m_lastNeutralInput;
  private SolverInput                   m_lastSolverInput;
  private Object                        m_lastSolverOutput;
  private SolverResult                  m_lastSolverResult;
  private SOLVER_RESULT                m_lastSMTCheckResult;
  
  private final SOLVERS                m_solverType;
  private final SolverLoader            m_solverLoader;
  private final List<SatModelProcessor> m_satModelProcessors;
}
