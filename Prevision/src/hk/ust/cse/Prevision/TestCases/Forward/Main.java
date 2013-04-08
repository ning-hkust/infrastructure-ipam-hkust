package hk.ust.cse.Prevision.TestCases.Forward;

import hk.ust.cse.Prevision.InstructionHandlers.AbstractHandler;
import hk.ust.cse.Prevision.InstructionHandlers.CompleteForwardHandler;
import hk.ust.cse.Prevision.PathCondition.Formula;
import hk.ust.cse.Prevision.Solver.SMTChecker;
import hk.ust.cse.Prevision.TestCases.TestSuite;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionResult;
import hk.ust.cse.Prevision.VirtualMachine.Executor.ForwardExecutor;
import hk.ust.cse.Wala.WalaUtils;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Main {

  public static void main(String[] args) throws Exception {
    AbstractHandler instHandler = new CompleteForwardHandler();
    SMTChecker smtChecker = new SMTChecker(SMTChecker.SOLVERS.Z3);
    ForwardExecutor executor = new ForwardExecutor("./hk.ust.cse.Prevision.jar", null, instHandler, smtChecker);

    // load all test suites
    List<String> testSuiteNames = WalaUtils.getSubClasses(
        executor.getWalaAnalyzer(), "hk.ust.cse.Prevision.TestCases.TestSuite", false, false);
    List<TestSuite> testSuites = new ArrayList<TestSuite>();
    for (String testSuiteName : testSuiteNames) {
      if (testSuiteName.contains("Forward")) {
        Class<?> cls = Utils.findClass(testSuiteName);
        if (cls != null) {
          testSuites.add((TestSuite) cls.newInstance());
        }
      }
    }
    
    // run all test suites
    for (TestSuite testSuite : testSuites) {
      System.out.println("Running tests in test suite: " + testSuite.getClass().getName());
      for (String test : testSuite.getTests()) {
        try {
          Method optionsMethod = testSuite.getClass().getMethod(test + "_options", (Class<?>[]) null);
          Method resultsMethod = testSuite.getClass().getMethod(test + "_results", (Class<?>[]) null);
          ExecutionOptions execOptions = (ExecutionOptions) optionsMethod.invoke(testSuite, (Object[]) null);
          Object[] result = (Object[]) resultsMethod.invoke(testSuite, (Object[]) null);
          int targetSat    = (Integer) result[0];
          
          ExecutionResult execResult = executor.compute(execOptions, null);
          List<Formula> sats = execResult.getSatisfiables();
          if (sats.size() >= targetSat) {
            boolean ret = true;
            for (int i = 1; i < result.length && ret; i++) {
              ret &= sats.get(targetSat - 1).getLastSolverOutput().toString().contains((String) result[i]);
            }
            System.out.println((ret ? "Test passed: " : "Test failed: ") + test);
          }
          else {
            System.err.println("Test failed: " + test);
          }
        } catch (Exception e) {
          System.err.println("Test caused exception: " + test);
          e.printStackTrace();
        }
        System.out.println();
      }
      System.out.println("Completed tests in test suite: " + testSuite.getClass().getName() + "\n");
    }     
  }
}
