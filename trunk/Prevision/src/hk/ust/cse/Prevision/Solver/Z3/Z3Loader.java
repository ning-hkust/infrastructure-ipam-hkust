package hk.ust.cse.Prevision.Solver.Z3;

import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;
import hk.ust.cse.Prevision.Solver.SolverInput;
import hk.ust.cse.Prevision.Solver.SolverLoader;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Params;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;

public class Z3Loader extends SolverLoader {
  
  static {
    try {
      s_context = new Context();
    } catch (Z3Exception e) {
      e.printStackTrace();
    }
  }
  
  public SOLVER_RESULT check(SolverInput input) {
    assert(input instanceof Z3Input);

    SOLVER_RESULT result = null;
    try {
      List<BoolExpr> hardTrackers = new ArrayList<BoolExpr>();
      List<BoolExpr> softTrackers = new ArrayList<BoolExpr>();
      Solver solver = prepareSolver((Z3Input) input, hardTrackers, softTrackers);
      result = performCheck(solver, hardTrackers, softTrackers, (Z3Input) input);
      solver.dispose();
    } catch (Z3Exception e) {
      m_lastOutput = e.getMessage();
      result = SOLVER_RESULT.ERROR;
    }
    return result;
  }

  public SOLVER_RESULT checkInContext(Object ctxStorage, SolverInput input) {
    assert(ctxStorage instanceof Solver);

    SOLVER_RESULT result = null;
    try {
      List<BoolExpr> hardTrackers = new ArrayList<BoolExpr>();
      List<BoolExpr> softTrackers = new ArrayList<BoolExpr>();
      Solver solver = prepareSolver((Solver) ctxStorage, (Z3Input) input, hardTrackers, softTrackers);
      result = performCheck(solver, hardTrackers, softTrackers, (Z3Input) input);
    } catch (Z3Exception e) {
      m_lastOutput = e.getMessage();
      result = SOLVER_RESULT.ERROR;
    }
    return result;
  }
  
  // only check the particular assertions in the given context
  public SOLVER_RESULT checkInContext(Object ctxStorage, SolverInput input, Assertion[] assertions) {
    assert(ctxStorage instanceof Solver);

    SOLVER_RESULT result = null;
    try {
      // retrieve BoolExprs to check
      List<BoolExpr> assertionExprs = new ArrayList<BoolExpr>();
      for (Assertion assertion : assertions) {
        assertionExprs.add(((Z3Input) input).getAssertionMapping2().get(assertion));
      }

      List<BoolExpr> hardTrackers = new ArrayList<BoolExpr>();
      List<BoolExpr> softTrackers = new ArrayList<BoolExpr>();
      Solver solver = prepareSolver((Solver) ctxStorage, (Z3Input) input, 
          assertionExprs, ((Z3Input) input).getPreferAssertionExprs(), hardTrackers, softTrackers);
      result = performCheck(solver, hardTrackers, softTrackers, (Z3Input) input);
    } catch (Z3Exception e) {
      m_lastOutput = e.getMessage();
      result = SOLVER_RESULT.ERROR;
    }
    return result;
  }
  
  private SOLVER_RESULT performCheck(Solver solver, 
      List<BoolExpr> hardTrackers, List<BoolExpr> softTrackers, Z3Input input) {
    
    m_lastInput           = input;
    m_lastOutput          = null;
    SOLVER_RESULT result = null;
    boolean retrieveModel = input.getNeutralInput().retrieveModel();
    boolean retrieveUnsat = input.getNeutralInput().retrieveUnsatCore();
    
    try {
      // prepare trackers
      List<BoolExpr> trackers = new ArrayList<BoolExpr>();
      trackers.addAll(hardTrackers);
      trackers.addAll(softTrackers);
      Set<BoolExpr> softTrackerSet = new HashSet<BoolExpr>(softTrackers);
      
      // check until satisfied or unsatisfied due to hard constraints only
      while (result == null) {
        // perform SMT check
        Status status = solver.check(trackers.toArray(new BoolExpr[trackers.size()]));
        
        switch (status) {
        case SATISFIABLE:
          m_lastOutput = retrieveModel ? solver.getModel() : null;
          result = SOLVER_RESULT.SAT;
          break;
        case UNSATISFIABLE:
          List<BoolExpr> unsatCoreList = new ArrayList<BoolExpr>();
          for (Expr expr : solver.getUnsatCore()) {
            unsatCoreList.add((BoolExpr) expr);
          }
          Collection<BoolExpr> unsatSoftTrackers = Utils.intersect(unsatCoreList, softTrackerSet);
          if (unsatSoftTrackers.size() == 0) { // unsatisfied due to hard constraints only
            m_lastOutput = retrieveUnsat ? unsatCoreList.toArray(new BoolExpr[unsatCoreList.size()]) : null;
            result = SOLVER_RESULT.UNSAT;
          }
          else {
            trackers.removeAll(unsatSoftTrackers); // relax the unsatisfiable soft constraints
          }
          break;
        case UNKNOWN:
          String reason = solver.getReasonUnknown();
          if (reason.equals("canceled") || 
              reason.equals("smt tactic failed to show goal to be sat/unsat")) {
            m_lastOutput = "TIMEOUT";
            result = SOLVER_RESULT.TIMEOUT;
          }
          else {
            m_lastOutput = reason;
            result = SOLVER_RESULT.UNKNOWN;
          }
          break;
        default:
          m_lastOutput = "ERROR";
          result = SOLVER_RESULT.ERROR;
          break;
        }
      }
      
    } catch (Z3Exception e) {
      m_lastOutput = e.getMessage();
      result = SOLVER_RESULT.ERROR;
    }
    
    return result;
  }
  
  public Object createContext() {
    return s_context;
  }
  
  public void deleteContext(Object ctx) {
    assert(ctx instanceof Context);
    ((Context) ctx).dispose();
  }

  // we use Solver in Z3
  public Object createContextStorage() {
    Solver solver = null;
    try {
      solver = s_context.mkSolver();
    } catch (Z3Exception e) {e.printStackTrace();}
    return solver;
  }

  // we use Solver in Z3
  public void deleteContextStorage(Object ctxStorage) {
    assert(ctxStorage instanceof Solver);
    try {
      ((Solver) ctxStorage).dispose();
    } catch (Exception e) {e.printStackTrace();}
  }
  
  // we use Solver in Z3
  public void pushContextStorage(Object ctxStorage) {
    assert(ctxStorage instanceof Solver);
    try {
      ((Solver) ctxStorage).push();
    } catch (Exception e) {e.printStackTrace();}
  }
  
  // we use Solver in Z3
  public void popContextStorage(Object ctxStorage) {
    assert(ctxStorage instanceof Solver);
    try {
      if (((Solver) ctxStorage).getNumScopes() > 0) {
        ((Solver) ctxStorage).pop();
      }
    } catch (Exception e) {e.printStackTrace();}
  }
  
  public Context getSolverContext(Solver solver) {
    Context ctx = null;
    try {
      Field field = Utils.getInheritedField(Solver.class, "m_ctx");
      boolean accessible = field.isAccessible();
      field.setAccessible(true);
      ctx = (Context) field.get(solver);
      field.setAccessible(accessible);
    } catch (Exception e) {}
    return ctx;
  }
  
  private Solver prepareSolver(Z3Input input, List<BoolExpr> hardTrackers, List<BoolExpr> softTrackers) throws Z3Exception {
    Solver solver = input.getContext().mkSolver();
    return prepareSolver(solver, input, hardTrackers, softTrackers);
  }

  private Solver prepareSolver(Solver solver, Z3Input input, 
      List<BoolExpr> hardTrackers, List<BoolExpr> softTrackers) throws Z3Exception {
    return prepareSolver(solver, input, input.getAssertionExprs(), 
        input.getPreferAssertionExprs(), hardTrackers, softTrackers);
  }

  // only add the particular assertions
  private Solver prepareSolver(Solver solver, Z3Input input, List<BoolExpr> hardAssertions, 
      List<BoolExpr> softAssertions, List<BoolExpr> hardTrackers, List<BoolExpr> softTrackers) throws Z3Exception {
    
    // set default timeout for solver
    Params params = input.getContext().mkParams();
    params.add(":timeout", 10000);
    solver.setParameters(params);

    int trackerIdFrom = hardAssertions.size() > 0 ? input.getAssertionExprs().indexOf(hardAssertions.get(0)) : -1;
    trackerIdFrom = trackerIdFrom < 0 ? input.getAssertionExprs().size() : trackerIdFrom;
    
    // add hard assertions
    for (int i = 0, size = hardAssertions.size(); i < size; i++) {
      if (input.getNeutralInput().retrieveUnsatCore()) {
        BoolExpr tracker = input.getContext().mkBoolConst("tracker_" + (trackerIdFrom + i));
        solver.add(input.getContext().mkImplies(tracker, hardAssertions.get(i)));
        hardTrackers.add(tracker);
      }
      else {
        solver.add(hardAssertions.get(i));
      }
    }
    
    // add soft assertions
    for (int i = 0, size = softAssertions.size(); i < size; i++) {
      if (input.getNeutralInput().retrieveUnsatCore()) {
        BoolExpr tracker = input.getContext().mkBoolConst("tracker_" + (trackerIdFrom + hardTrackers.size() + i));
        solver.add(input.getContext().mkImplies(tracker, softAssertions.get(i)));
        softTrackers.add(tracker);
      }
      else {
        solver.add(softAssertions.get(i));
      }
    }
    return solver;
  }
  
  private static Context s_context;
}
