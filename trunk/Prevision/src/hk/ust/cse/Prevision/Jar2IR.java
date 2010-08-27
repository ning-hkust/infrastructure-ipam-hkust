package hk.ust.cse.Prevision;

import java.io.IOException;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.strings.StringStuff;
import com.ibm.wala.util.warnings.WalaException;

public class Jar2IR {
  /**
   * @param appJar should be something like "c:/temp/testdata/java_cup.jar"
   * @param methodSig should be something like "java_cup.lexer.advance()V"
   * @throws IOException
   */
  public static IR getIR(String appJar, String methodSig, AnalysisCache cache) throws IOException {
    try {
      // Build a class hierarchy representing all classes to analyze.
      // This step will read the class files and organize them into a tree.
      ClassHierarchy cha = Jar2IRUtils.getClassHierarchy(appJar);

      // Create a name representing the method whose IR we will visualize
      MethodReference mr = StringStuff.makeMethodReference(methodSig);

      IR ir = null;
      if (mr != null) {
        // Resolve the method name into the IMethod, the canonical representation of the method information.
        IMethod m = cha.resolveMethod(mr);
        if (m == null) {
          // Throws an exception here
          Assertions.UNREACHABLE("could not resolve " + mr);
        }
        
        // Set up options which govern analysis choices.  In particular, we will use all Pi nodes when
        // building the IR.
        AnalysisOptions options = new AnalysisOptions();
        options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
        
        // Create an object which caches IRs and related information, reconstructing them lazily on demand.
        if (cache == null) {
          cache = new AnalysisCache();
        }
        
        // Build the IR and cache it.
        ir = cache.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, options.getSSAOptions());
  
        if (ir == null) {
          // Throws an exception here
          Assertions.UNREACHABLE("Null IR for " + m);
        }
  
        //System.err.println(ir.toString());
      }
      return ir;
    } catch (WalaException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public static IR getIR(String appJar, String methodName, int nLine, AnalysisCache cache) throws IOException {
    try {
      // Build a class hierarchy representing all classes to analyze.
      // This step will read the class files and organize them into a tree.
      ClassHierarchy cha = Jar2IRUtils.getClassHierarchy(appJar);
      
      // Create a name representing the method whose IR we will visualize
      MethodReference mr = Jar2IRUtils.getMethodReference(cha, methodName, nLine);
      
      IR ir = null;
      if (mr != null) {
        // Resolve the method name into the IMethod, the canonical representation of the method information.
        IMethod m = cha.resolveMethod(mr);
        if (m == null) {
          // Throws an exception here
          Assertions.UNREACHABLE("could not resolve " + mr);
        }
        
        // Set up options which govern analysis choices.  In particular, we will use all Pi nodes when
        // building the IR.
        AnalysisOptions options = new AnalysisOptions();
        options.getSSAOptions().setPiNodePolicy(SSAOptions.getAllBuiltInPiNodes());
        
        // Create an object which caches IRs and related information, reconstructing them lazily on demand.
        if (cache == null) {
          cache = new AnalysisCache();
        }
        
        // Build the IR and cache it.
        ir = cache.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, options.getSSAOptions());

        if (ir == null) {
          // Throws an exception here
          Assertions.UNREACHABLE("Null IR for " + m);
        }

        //System.err.println(ir.toString());
      }
      return ir;
    } catch (WalaException e) {
      e.printStackTrace();
      return null;
    }
  }
}
