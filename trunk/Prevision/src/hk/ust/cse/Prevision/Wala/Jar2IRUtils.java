package hk.ust.cse.Prevision.Wala;

import hk.ust.cse.Prevision.Utils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.Hashtable;
import java.util.Iterator;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.examples.drivers.PDFCallGraph;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.shrikeBT.IInstruction;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.util.warnings.WalaException;

/**
 * Internal Utils class for Jar2IR and WalaAnalyzer
 */
class Jar2IRUtils {
  
  static ClassHierarchy getClassHierarchy(String appJar, AnalysisScope scope) throws IOException, WalaException{
    if (s_classHierarchyCache == null) {
      s_classHierarchyCache = new Hashtable<String, ClassHierarchy>();
    }
    
    // try to find the ClassHierarchy object from cache
    ClassHierarchy cha = s_classHierarchyCache.get(appJar);
    
    if (cha == null) {
      if (PDFCallGraph.isDirectory(appJar)) {
        appJar = PDFCallGraph.findJarFiles(new String[] { appJar });
      }

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
  static MethodReference getMethodReference(ClassHierarchy cha, String methodName, int lineNo) {
    MethodReference mr = null;
    
    // get class name
    String clsName = methodName.replace('.', '/');
    int index1 = clsName.lastIndexOf('/');
    int index2 = clsName.lastIndexOf('/', index1 - 1) + 1;
    clsName = clsName.substring(index2, index1);
    int clsLength = clsName.length();
    
    // find class
    Iterator<IClass> classes = cha.iterator();
    while (classes.hasNext() && mr == null) {
      IClass aClass = (IClass) classes.next();

      // filter
      Atom clsAtom = aClass.getName().getClassName();
      if (clsAtom.length() != clsLength || 
          clsAtom.getVal(clsLength-1) != clsName.charAt(clsLength-1) || 
          clsAtom.getVal(0) != clsName.charAt(0) || 
          !clsAtom.toString().equals(clsName)) {
        continue;
      }
      
      // class name matches?
      String declaringClass = aClass.getName().toString();
      declaringClass = Utils.getClassTypeJavaStr(declaringClass);
      if (!methodName.startsWith(declaringClass)) {
        continue;
      }
      Iterator<IMethod> methods = aClass.getAllMethods().iterator();
      while (methods.hasNext()) {
        IMethod aMethod = methods.next();
        // method name matches?
        String methName = declaringClass + "." + aMethod.getName().toString();
        if (methName.equals(methodName)) {
          if (isMethodAtLine(aMethod, lineNo)) {
            mr = aMethod.getReference();
            break;
          }
          else {
            //System.out.println("Method " + methName + " found, but cannot verify line number");
          }
        }
      }
    }
    
    return mr;
  }
  
  /**
   * find method reference according to method name and line number
   */
  static MethodReference getMethodReference(ClassHierarchy cha, Method method) {
    MethodReference mr = null;
    
    // get method name
    String methodName = method.getDeclaringClass().getName() + "." + method.getName();
    
    // get class name
    String clsName = methodName.replace('.', '/');
    int index1 = clsName.lastIndexOf('/');
    int index2 = clsName.lastIndexOf('/', index1 - 1) + 1;
    clsName = clsName.substring(index2, index1);
    int clsLength = clsName.length();
    
    // find class
    Iterator<IClass> classes = cha.iterator();
    while (classes.hasNext() && mr == null) {
      IClass aClass = (IClass) classes.next();

      // filter
      Atom clsAtom = aClass.getName().getClassName();
      if (clsAtom.length() != clsLength || 
          clsAtom.getVal(clsLength-1) != clsName.charAt(clsLength-1) || 
          clsAtom.getVal(0) != clsName.charAt(0) || 
          !clsAtom.toString().equals(clsName)) {
        continue;
      }
      
      // class name matches?
      String declaringClass = aClass.getName().toString();
      declaringClass = Utils.getClassTypeJavaStr(declaringClass);
      if (!methodName.startsWith(declaringClass)) {
        continue;
      }
      Iterator<IMethod> methods = aClass.getAllMethods().iterator();
      while (methods.hasNext()) {
        IMethod aMethod = methods.next();
        // method name matches?
        String methName = declaringClass + "." + aMethod.getName().toString();
        if (methName.equals(methodName)) {
          if (isParametersSame(aMethod, method)) {
            mr = aMethod.getReference();
            break;
          }
          else {
            System.out.println("Method " + methName + " found, but parameter types are different!");
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
  
  private static boolean isParametersSame(IMethod method1, Method method2) {
    boolean isSame = false;
    
    Class<?>[] paramTypes = method2.getParameterTypes();
    int baseIndex = method1.isStatic() ? 0 : 1;
    
    if ((method1.getNumberOfParameters() - baseIndex) == paramTypes.length) {
      isSame = true;
      for (int i = 0; i < paramTypes.length; i++) {
        TypeReference type1 = method1.getParameterType(i + baseIndex);
        Class<?> type2      = paramTypes[i];
        
        String type1Name = Utils.toPrimitiveType(Utils.getClassTypeJavaStr(type1.getName().toString()));
        String type2Name = Utils.toPrimitiveType(Utils.getClassTypeJavaStr(type2.getName()));
        if (!type1Name.equals(type2Name)) {
          isSame = false;
          break;
        }
      }
    }
    return isSame;
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
