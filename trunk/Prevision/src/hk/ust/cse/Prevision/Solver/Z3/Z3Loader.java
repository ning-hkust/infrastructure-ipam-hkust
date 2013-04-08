package hk.ust.cse.Prevision.Solver.Z3;

import hk.ust.cse.Prevision.Solver.NeutralInput.Assertion;
import hk.ust.cse.Prevision.Solver.NeutralInput.DefineConstant;
import hk.ust.cse.Prevision.Solver.SolverInput;
import hk.ust.cse.Prevision.Solver.SolverLoader;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.microsoft.z3.ArithExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Model;
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
      List<BoolExpr> trackers = new ArrayList<BoolExpr>();
      Solver solver = prepareSolver((Z3Input) input, trackers);
      result = performCheck(solver, trackers, (Z3Input) input, true);
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
      List<BoolExpr> trackers = new ArrayList<BoolExpr>();
      Solver solver = prepareSolver((Solver) ctxStorage, (Z3Input) input, trackers);
      result = performCheck(solver, trackers, (Z3Input) input, false);
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

      List<BoolExpr> trackers = new ArrayList<BoolExpr>();
      Solver solver = prepareSolver((Solver) ctxStorage, (Z3Input) input, assertionExprs, trackers);
      result = performCheck(solver, trackers, (Z3Input) input, false);
    } catch (Z3Exception e) {
      m_lastOutput = e.getMessage();
      result = SOLVER_RESULT.ERROR;
    }
    return result;
  }
  
  private SOLVER_RESULT performCheck(Solver solver, List<BoolExpr> trackers, Z3Input input, boolean smallValue) {
    m_lastInput           = input;
    m_lastOutput          = null;
    SOLVER_RESULT result = null;
    boolean retrieveModel = input.getNeutralInput().retrieveModel();
    boolean retrieveUnsat = input.getNeutralInput().retrieveUnsatCore();
    
    try {
      // perform SMT check
      Status status = solver.check(trackers.toArray(new BoolExpr[trackers.size()]));
      
      switch (status) {
      case SATISFIABLE:
        m_lastOutput = retrieveModel ? (smallValue ? retrieveModel(solver, input) : solver.getModel()) : null;
        result = SOLVER_RESULT.SAT;
        break;
      case UNSATISFIABLE:
        m_lastOutput = retrieveUnsat ? solver.getUnsatCore() : null;
        result = SOLVER_RESULT.UNSAT;
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
  
  private Solver prepareSolver(Z3Input input, List<BoolExpr> trackers) throws Z3Exception {
    Solver solver = input.getContext().mkSolver();
    return prepareSolver(solver, input, trackers);
  }

  private Solver prepareSolver(Solver solver, Z3Input input, List<BoolExpr> trackers) throws Z3Exception {
    return prepareSolver(solver, input, input.getAssertionExprs(), trackers);
  }

  // only add the particular assertions
  private Solver prepareSolver(Solver solver, 
      Z3Input input, List<BoolExpr> assertions, List<BoolExpr> trackers) throws Z3Exception {
    // set default timeout for solver
    Params params = input.getContext().mkParams();
    params.add(":timeout", 10000);
    solver.setParameters(params);

    int trackerIdFrom = input.getAssertionExprs().indexOf(assertions.get(0));
    trackerIdFrom = trackerIdFrom < 0 ? input.getAssertionExprs().size() : trackerIdFrom;
    
    // add assertions
    for (int i = 0, size = assertions.size(); i < size; i++) {
      if (input.getNeutralInput().retrieveUnsatCore()) {
        BoolExpr tracker = input.getContext().mkBoolConst("tracker_" + (trackerIdFrom + i));
        solver.assertAndTrack(assertions.get(i), tracker);
        trackers.add(tracker);
      }
      else {
        solver.add(assertions.get(i));
      }
    }
    return solver;
  }
  
  // if there are any integer numbers with model values > 500 or < -500, 
  // we will try to find interpretations to them within a [10, 10] range
  private Model retrieveModel(Solver solver, Z3Input input) throws Z3Exception {
    Model model = solver.getModel();
    
    // parse constant interpretations
    List<BoolExpr> rangeExprs = new ArrayList<BoolExpr>();
    List<DefineConstant> definedConstants = input.getNeutralInput().getDefineConstants();
    for (int i = 0, size = definedConstants.size(); i < size; i++) {
      DefineConstant constant = definedConstants.get(i);
      if (constant.value == null && (constant.type.equals("I") || 
          constant.type.equals("J") || constant.type.equals("S")) && 
         !constant.name.matches("v[\\d]+\\$1") /* not conversion helper */ ) { 

        try {
          ArithExpr expr = (ArithExpr) input.getDefinedConstants().get(constant.name);
          Expr interp = model.getConstInterp(expr);
          int value = Integer.parseInt(interp.toString());
          if (value > 500 || value < -500) {
            // create a smaller range [-10, 10]
            Context ctx = input.getContext();
            rangeExprs.add(ctx.mkAnd(ctx.mkLe(expr, ctx.mkInt(10)), 
                                     ctx.mkGe(expr, ctx.mkInt(-10))));
          }
        } catch (Exception e) {}
      }
    }
    
    // if added range limit, perform smt check again
    if (rangeExprs.size() > 0) {
      solver.reset();
      solver = prepareSolver(solver, input, new ArrayList<BoolExpr>());
      solver.add(rangeExprs.toArray(new BoolExpr[rangeExprs.size()]));
      
      Status status = solver.check();
      model = status == Status.SATISFIABLE ? solver.getModel() : model;
    }
    return model;
  }
  
  private static Context s_context;
}
