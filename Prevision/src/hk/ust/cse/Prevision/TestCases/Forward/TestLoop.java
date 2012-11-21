package hk.ust.cse.Prevision.TestCases.Forward;

import hk.ust.cse.Prevision.Misc.CallStack;
import hk.ust.cse.Prevision.TestCases.TestSuite;
import hk.ust.cse.Prevision.VirtualMachine.ExecutionOptions;

public class TestLoop extends TestSuite {
  
  public void test1(int num) {
    for (int i = 0; i < num; i++) {
      if (i == 5) {
        System.out.println("Good");
      }
    }
  }
  
  public ExecutionOptions test1_options() {
    CallStack callStack = new CallStack(true);
    callStack.addStackTrace("hk.ust.cse.Prevision.TestCases.Forward.TestLoop.test1", 12);
    
    ExecutionOptions execOptions = new ExecutionOptions(callStack);
    execOptions.maxDispatchTargets  = 5;
    execOptions.maxRetrieve         = 1000;
    execOptions.maxSmtCheck         = 5000;
    execOptions.maxInvokeDepth      = 0;
    execOptions.maxLoop             = 6;
    
    return execOptions;
  }
  
  public Object[] test1_results() {
    return new Object[] {7, "(= num 6)", "(= Ljava/lang/System.out "};
  }
  
  public void test2(int num) {
    int i = 0;
    while (i < num) {
      if (i++ == 5) {
        System.out.println("Good");
      }
    }
  }
  
  public ExecutionOptions test2_options() {
    CallStack callStack = new CallStack(true);
    callStack.addStackTrace("hk.ust.cse.Prevision.TestCases.Forward.TestLoop.test2", 39);
    
    ExecutionOptions execOptions = new ExecutionOptions(callStack);
    execOptions.maxDispatchTargets  = 5;
    execOptions.maxRetrieve         = 1000;
    execOptions.maxSmtCheck         = 5000;
    execOptions.maxInvokeDepth      = 0;
    execOptions.maxLoop             = 3;
    
    return execOptions;
  }
  
  public Object[] test2_results() {
    return new Object[] {4, "(= num 3)"};
  }
  
  public void test3(int num) {
    int i = 0;
    do {
      if (i++ == 5) {
        System.out.println("Good");
      }
    } while (i < num);
  }
  
  public ExecutionOptions test3_options() {
    CallStack callStack = new CallStack(true);
    callStack.addStackTrace("hk.ust.cse.Prevision.TestCases.Forward.TestLoop.test3", 66);
    
    ExecutionOptions execOptions = new ExecutionOptions(callStack);
    execOptions.maxDispatchTargets  = 5;
    execOptions.maxRetrieve         = 1000;
    execOptions.maxSmtCheck         = 5000;
    execOptions.maxInvokeDepth      = 0;
    execOptions.maxLoop             = 5;
    
    return execOptions;
  }
  
  public Object[] test3_results() {
    return new Object[] {6, "(= num 6)", "(= Ljava/lang/System.out "};
  }
}
