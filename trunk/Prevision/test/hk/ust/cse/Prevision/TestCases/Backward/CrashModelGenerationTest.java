package hk.ust.cse.Prevision.TestCases.Backward;

import static org.junit.Assert.assertTrue;
import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandlers.CompleteBackwardHandler;
import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.Misc.InvalidStackTraceException;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions.EXCEPTION_TYPE;
import hk.ust.cse.Prevision.VirtualMachine.Executor.BackwardExecutor;
import hk.ust.cse.Wala.CallGraph.CallGraphBuilder;
import hk.ust.cse.util.Utils;
import hk.ust.cse.util.DbHelper.DbHelperSqlite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import javax.naming.TimeLimitExceededException;

import org.junit.Test;

public class CrashModelGenerationTest {
  
  // can only be used to compute for one exception at a time as the target jarFile is different
  public CrashModelGenerationTest() throws Exception {
    m_dbName            = "./test_programs/CrashModel";
    m_filterMethodFile  = "./test_programs/filterMethods.txt";
    m_pseudoImplJarFile = "../Prevision_PseudoImpl/hk.ust.cse.Prevision_PseudoImpl.jar";
  }
  
  public void compute(int exceptionId, int frameIndex) {
    Connection conn = null;
    try {
      conn = DbHelperSqlite.openConnection(m_dbName);
      
      // load exception and initial executor
      initilize(conn, exceptionId);
      
      // start time
      long start = System.currentTimeMillis();

      m_satisfiables = computeAtFrame(frameIndex);
      
      // end time
      long end = System.currentTimeMillis();
      System.out.println("Total elapsed: " + (end - start) + "ms");

    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      DbHelperSqlite.closeConnection(conn);
    }
  }

  // load the exception and initial the execution
  private void initilize(Connection conn, int exceptionId) throws Exception {
    // load the exception to compute
    loadException(conn, exceptionId);
    String appJar = (String) m_exception[0];
    
    // initialize
    AbstractHandler instHandler = new CompleteBackwardHandler();
    instHandler.setMethodStepInFilters(m_filterMethodFile);
    SMTChecker smtChecker = new SMTChecker(SMTChecker.SOLVERS.Z3);
    //smtChecker.addSatModelProcessor(new UseRangeProcessor());
    //smtChecker.addSatModelProcessor(new LessTypeRestrict());
    
    m_executor = new BackwardExecutor(appJar, m_pseudoImplJarFile, instHandler, smtChecker);

    // load jar file, _removed.jar version is for faster call graph construction. Since it may 
    // be missing some classes (e.g. UnknownElement), we should use the full version in classloader
    appJar = appJar.endsWith("_removed.jar") ? appJar.substring(0, appJar.length() - 12) + ".jar" : appJar;
    Utils.loadJarFile(appJar);
  }
  
  private List<Formula> computeAtFrame(int frameIndex) throws Exception {
    CallStack callStack  = (CallStack) m_exception[9];
    while (callStack.getDepth() > frameIndex) {
      callStack = callStack.getInnerCallStack();
    }
    callStack.setOutMostCall(true);
    
    // log progress
    System.out.println("Computing for level " + frameIndex + "...");
    System.out.println("Call Stack: " + callStack.toString());
    
    // compute
    return compute(callStack);
  }

  private Object[] loadException(Connection conn, int exceptionId) throws Exception {
    m_exception = new Object[10];
    
    String sqlText = "Select * From Exception Where id = " + exceptionId;
    ResultSet rs = DbHelperSqlite.executeQuery(conn, sqlText);
    if (rs.next()) {
      // mandatory fields
      String jarFile          = rs.getString(4).replace("../experiments/reproducibility/", "./test_programs/targets/");
      String cgBuilder        = rs.getString(5);
      String excepType        = rs.getString(6);
      String outputDir        = rs.getString(7);
      
      // optional fields
      Integer maxDispTargets  = (Integer) rs.getObject(8);
      Integer maxRetrieve     = (Integer) rs.getObject(9);
      Integer maxSmtCheck     = (Integer) rs.getObject(10);
      Integer maxInvokeDepth  = (Integer) rs.getObject(11);
      Integer maxLoop         = (Integer) rs.getObject(12);

      String frames = rs.getString(13);
      CallStack callStack = null;
      if (frames != null && frames.length() > 0) {
        callStack = CallStack.fromString(frames);
      }
      
      m_exception[0] = jarFile;
      m_exception[1] = cgBuilder;
      m_exception[2] = excepType;
      m_exception[3] = outputDir;
      m_exception[4] = maxDispTargets;
      m_exception[5] = maxRetrieve;
      m_exception[6] = maxSmtCheck;
      m_exception[7] = maxInvokeDepth;
      m_exception[8] = maxLoop;
      m_exception[9] = callStack;
    }
    rs.close();

    return m_exception;
  }
  
  private List<Formula> compute(CallStack callStack) {
    List<Formula> satisfiables = new ArrayList<Formula>();
    try {
      // set symbolic execution properties
      int maxDispTargets  = (m_exception[4] != null) ? (Integer) m_exception[4] : 2;
      int maxRetrieve     = (m_exception[5] != null) ? (Integer) m_exception[5] : 1;
      int maxSmtCheck     = (m_exception[6] != null) ? (Integer) m_exception[6] : 1000;
      int maxInvokeDepth  = (m_exception[7] != null) ? (Integer) m_exception[7] : 10;
      int maxLoop         = (m_exception[8] != null) ? (Integer) m_exception[8] : 1;

      // prepare call graph builder
      CallGraphBuilder cgBuilder = CallGraphBuilder.ZeroOneCFA;
      if (m_exception[1] != null) {
        if (m_exception[1].equals("ZeroCFA")) {
          cgBuilder = CallGraphBuilder.ZeroCFA;
        }
        else if (m_exception[1].equals("ZeroOneCFA")) {
          cgBuilder = CallGraphBuilder.ZeroOneCFA;
        }
        else if (m_exception[1].equals("VanillaZeroOneCFA")) {
          cgBuilder = CallGraphBuilder.VanillaZeroOneCFA;
        }
        else if (m_exception[1].equals("ZeroContainerCFA")) {
          cgBuilder = CallGraphBuilder.ZeroContainerCFA;
        }
        else if (m_exception[1].equals("ZeroOneContainerCFA")) {
          cgBuilder = CallGraphBuilder.ZeroOneContainerCFA;
        }
        else if (m_exception[1].equals("VanillaZeroOneContainerCFA")) {
          cgBuilder = CallGraphBuilder.VanillaZeroOneContainerCFA;
        }
        else if (m_exception[1].equals("None")) {
          cgBuilder = null;
        }
      }
      
      // prepare exception type
      EXCEPTION_TYPE excepType = EXCEPTION_TYPE.CUSTOM;;
      if (m_exception[2].equals("NPE")) {
        excepType = EXCEPTION_TYPE.NPE;
      }
      else if (m_exception[2].equals("AIOBE")) {
        excepType = EXCEPTION_TYPE.AIOBE;
      }
      else {
        excepType = EXCEPTION_TYPE.CUSTOM;
      }
      
      // set options
      ExecutionOptions execOptions = new ExecutionOptions(callStack);
      execOptions.maxDispatchTargets     = maxDispTargets;
      execOptions.maxRetrieve            = maxRetrieve;
      execOptions.maxSmtCheck            = maxSmtCheck;
      execOptions.maxInvokeDepth         = maxInvokeDepth;
      execOptions.maxLoop                = maxLoop;
      execOptions.maxTimeAllow           = 100000;
      execOptions.addIRAsEntryPoint      = true;
      
      execOptions.exceptionType         = excepType;
      execOptions.callGraphBuilder      = cgBuilder;
//      execOptions.checkOnTheFly         = false;
//      execOptions.skipUselessBranches   = false;
//      execOptions.skipUselessMethods    = false;
//      execOptions.heuristicBacktrack    = false;
      
      m_executor.compute(execOptions, null);
    
    } catch (InvalidStackTraceException e) {
      System.err.println("Invalid Stack Trace!");
    } catch (TimeLimitExceededException e) {
      System.err.println("Time limit exceeded!");
    } catch (Exception e) {
      e.printStackTrace();
    } catch (OutOfMemoryError e) {
      System.err.println("Ran out of memory when computing for this call stack, skip!");
    }
    
    // get all satisfiables from executor
    if (m_executor.getExecResult() != null) {
      satisfiables.addAll(m_executor.getSatisfiables());
    }
    
    return satisfiables;
  }
  
  @Test public void test_acc4() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(21, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc28() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(22, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc35() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(23, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc48() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(24, 6);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc53() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(25, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc77() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(27, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc104() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(29, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_acc411() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(61, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant28820() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(1, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant33446() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(2, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant34722() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(3, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant34734() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(4, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant36733() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(5, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant38458() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(6, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant38622() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(7, 4);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant41422() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(8, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant43292() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(10, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant44689() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(11, 7);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant44790() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(12, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant49137() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(16, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant49755() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(17, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant49803() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(18, 4);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_ant50894() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(19, 6);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log29() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(32, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log10528() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(34, 13);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log10706() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(35, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log11570() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(36, 10);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log31003() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(37, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log40159() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(39, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log40212() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(40, 2);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log41186() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(41, 12);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log45335() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(44, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log46144() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(45, 4);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log46271() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(46, 6);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log47547() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(48, 1);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log47912() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(49, 3);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  @Test public void test_log47957() throws Throwable {
    CrashModelGenerationTest modelGenerator = new CrashModelGenerationTest();
    modelGenerator.compute(50, 6);
    assertTrue(modelGenerator.m_satisfiables.size() > 0);
  }
  
  private Object[]         m_exception;
  private BackwardExecutor m_executor;
  private List<Formula>    m_satisfiables;

  private final String m_dbName;
  private final String m_pseudoImplJarFile;
  private final String m_filterMethodFile;
}
