package hk.ust.cse.Prevision.Wala;

import java.io.IOException;
import java.lang.reflect.Method;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.StringStuff;

public class Jar2IR {
  
  /**
   * @param methodSig should be something like "java_cup.lexer.advance()V"
   * @throws IOException
   */
  public static IR getIR(WalaAnalyzer walaAnalyzer, String methodSig) {
    // Create a name representing the method whose IR we will visualize
    MethodReference mr = StringStuff.makeMethodReference(methodSig);

    return getIRFromMethodReference(walaAnalyzer, mr);
  }
  
  public static IR getIR(WalaAnalyzer walaAnalyzer, Method method) {
    // Create a name representing the method whose IR we will visualize
    MethodReference mr = Jar2IRUtils.getMethodReference(
        walaAnalyzer.getClassHierarchy(), method);
    
    return getIRFromMethodReference(walaAnalyzer, mr);
  }
  
  public static IR getIR(WalaAnalyzer walaAnalyzer, String methodName, int lineNo) {
    // Create a name representing the method whose IR we will visualize
    MethodReference mr = Jar2IRUtils.getMethodReference(
        walaAnalyzer.getClassHierarchy(), methodName, lineNo);
    
    return getIRFromMethodReference(walaAnalyzer, mr);
  }
  
  private static IR getIRFromMethodReference(WalaAnalyzer walaAnalyzer, MethodReference mr) {
    IR ir = null;
    if (mr != null) {
      // Resolve the method name into the IMethod, the canonical representation of the method information.
      IMethod m = walaAnalyzer.getClassHierarchy().resolveMethod(mr);
      if (m == null) {
        // Throws an exception here
        Assertions.UNREACHABLE("could not resolve " + mr);
      }
      
      // Build the IR and cache it.
      ir = walaAnalyzer.getAnalysisCache().getSSACache().findOrCreateIR(m, 
          Everywhere.EVERYWHERE, walaAnalyzer.getAnalysisOptions().getSSAOptions());

      if (ir == null) {
        // Throws an exception here
        Assertions.UNREACHABLE("Null IR for " + m);
      }

      //System.err.println(ir.toString());
    }
    return ir;
  }
}
