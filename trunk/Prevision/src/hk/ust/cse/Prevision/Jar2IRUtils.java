package hk.ust.cse.Prevision;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.AbstractMap.SimpleEntry;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.drivers.PDFCallGraph;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.io.FileProvider;
import com.ibm.wala.util.warnings.WalaException;

public class Jar2IRUtils {
  
  public static ClassHierarchy getClassHierarchy(String appJar) throws IOException, WalaException{
    if (s_classHierarchyCache == null) {
      s_classHierarchyCache = new Hashtable<String, ClassHierarchy>();
    }
    
    // try to find the ClassHierarchy object from cache
    ClassHierarchy cha = s_classHierarchyCache.get(appJar);
    
    if (cha == null) {
      if (PDFCallGraph.isDirectory(appJar)) {
        appJar = PDFCallGraph.findJarFiles(new String[] { appJar });
      }

      // Build an AnalysisScope which represents the set of classes to analyze.  In particular,
      // we will analyze the contents of the appJar jar file and the Java standard libraries.
      AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, FileProvider
          .getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));

      // Build a class hierarchy representing all classes to analyze.  This step will read the class
      // files and organize them into a tree.
      cha = ClassHierarchy.make(scope);
      
      // save into cache
      s_classHierarchyCache.put(appJar, cha);
    }
    return cha;
  }
  
  /**
   * find method reference according to method name and line number
   */
  public static MethodReference getMethodReference(String jarFile, String methodName, int nLine) throws IOException{
    try {
      ClassHierarchy cha = getClassHierarchy(jarFile);
      return getMethodReference(cha, methodName, nLine);
    } catch (WalaException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  /**
   * find method reference according to method name and line number
   */
  public static MethodReference getMethodReference(ClassHierarchy cha, String methodName, int nLine) {
    MethodReference mr = null;
    Iterator<IClass> classes = cha.iterator();
    while (classes.hasNext() && mr == null) {
      IClass aClass = (IClass) classes.next();

      // class name matches?
      String declaringClass = aClass.getName().toString();
      declaringClass = Utils.getClassTypeJavaStr(declaringClass);
      if (!methodName.startsWith(declaringClass)) {
        continue;
      }
      Iterator<IMethod> methods = aClass.getAllMethods().iterator();
      while (methods.hasNext()) {
        IMethod method = methods.next();
        // method name matches?
        String methName = declaringClass + "." + method.getName().toString();
        if (methName.equals(methodName)) {
          if (isMethodAtLine(method, nLine)) {
            mr = method.getReference();
            break;
          }
          else {
            System.out.println("Method " + methName + " found, but cannot verify line number");
          }
        }
      }
    }
    
    return mr;
  }
  
  private static boolean isMethodAtLine(IMethod method, int lineNo) {
    SimpleEntry<Integer, Integer> ret = getMethodLineNo(method);
    if (lineNo >= ret.getKey().intValue() && 
        lineNo <= ret.getValue().intValue()) {
      return true;
    }
    else {
      return false;
    }
  }
  
  // get the beginning and ending line number of the method
  private static SimpleEntry<Integer, Integer> getMethodLineNo(IMethod method) {
    SimpleEntry<Integer, Integer> ret = null;
      
    try {
      ShrikeBTMethod btMethod = (ShrikeBTMethod) method;
      IInstruction[] insts = btMethod.getInstructions();
      if (insts != null && insts.length > 0) {
        int bc1 = btMethod.getBytecodeIndex(0);
        int bc2 = btMethod.getBytecodeIndex(insts.length - 1);
        int lineno1 = btMethod.getLineNumber(bc1);
        int lineno2 = btMethod.getLineNumber(bc2);
        ret = new SimpleEntry<Integer, Integer>(Integer.valueOf(lineno1), Integer.valueOf(lineno2));
      }
      else {
        ret = new SimpleEntry<Integer, Integer>(Integer.valueOf(-1), Integer.valueOf(-1));
      }
    } catch (Exception e) {
      ret = new SimpleEntry<Integer, Integer>(Integer.valueOf(-1), Integer.valueOf(-1));
    }
    return ret;
  }

  private static Hashtable<String, ClassHierarchy> s_classHierarchyCache;
}
