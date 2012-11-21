package hk.ust.cse.Wala;

import hk.ust.cse.util.LRUCache;
import hk.ust.cse.util.Utils;

import java.lang.reflect.Modifier;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.types.MethodReference;

public class WalaUtils {
  
  private static LRUCache s_classCache = new LRUCache(50);
  public static IClass getClass(WalaAnalyzer walaAnalyzer, String className) {
    className = Utils.getClassTypeJavaStr(className);
    
    IClass targetClass = (IClass) s_classCache.find(className);
    if (targetClass == null) {
      IClassHierarchy classHierarchy = walaAnalyzer.getClassHierarchy();
      Iterator<IClass> classes = classHierarchy.iterator();
      while (classes.hasNext()) {
        IClass aClass = (IClass) classes.next();

        // class name matches?
        String aClassName = aClass.getName().toString();
        aClassName = Utils.getClassTypeJavaStr(aClassName);
        if (className.equals(aClassName)) {
          targetClass = aClass;
          break;
        }
      }
      
      s_classCache.put(className, targetClass);
    }

    return targetClass;
  }
  
  public static List<String> getSubClasses(WalaAnalyzer walaAnalyzer, String className, 
      boolean sortByAccessibility, boolean transToJavaName) {
    // find class first
    IClass targetClass = getClass(walaAnalyzer, className);
    return getSubClasses(walaAnalyzer, targetClass, sortByAccessibility, transToJavaName);
  }
  
  public static List<String> getSubClasses(WalaAnalyzer walaAnalyzer, IClass targetClass, 
      boolean sortByAccessibility, boolean transToJavaName) {
    
    List<String> subClassList = new ArrayList<String>();

    List<IClass> subClasses = getSubClasses(walaAnalyzer, targetClass);
    if (subClasses != null) {
      Iterator<IClass> allSubClasses = subClasses.iterator();
      while (allSubClasses.hasNext()) {
        IClass subClass = (IClass) allSubClasses.next();

        String subClassName = subClass.getName().toString();
        subClassList.add(transToJavaName ? Utils.getClassTypeJavaStr(subClassName, false) : subClassName);
      }
    }
    
    // sort by accessibility
    if (sortByAccessibility) {
      sortSubClasses(subClassList);
    }
    
    return subClassList;
  }

  public static List<IClass> getSubClasses(WalaAnalyzer walaAnalyzer, IClass targetClass) {
    IClassHierarchy classHierarchy = walaAnalyzer.getClassHierarchy();
    List<IClass> subClassList = new ArrayList<IClass>();

    if (targetClass != null) {
      List<IClass> subClasses = new ArrayList<IClass>();
      if (targetClass.isInterface()) {
        subClasses.addAll(classHierarchy.getImplementors(targetClass.getReference()));
      }
      else {
        subClasses.addAll(classHierarchy.getImmediateSubclasses(targetClass));
        
        // get the sub-subclasses
        int lastSize = 0;
        while (subClasses.size() > lastSize) {
          Iterator<IClass> iter = subClasses.iterator();
          List<IClass> subSubClassList = new ArrayList<IClass>();
          for (int i = 0; iter.hasNext(); i++) {
            IClass subClass = iter.next();
            if (i >= lastSize) {
              Collection<IClass> subSubClasses = classHierarchy.getImmediateSubclasses(subClass);
              subSubClassList.addAll(subSubClasses);
            }
          }
          
          lastSize = subClasses.size();
          subClasses.addAll(subSubClassList);
        }
      }
      subClassList.addAll(subClasses);
    }
    
    return subClassList;
  }
  
  public static IR[] getImplementations(WalaAnalyzer walaAnalyzer, MethodReference mr, int maxToGet) {
    List<IR> targetIRs = new ArrayList<IR>();
    
    IR ir = Jar2IR.getIR(walaAnalyzer, mr.getSignature());
    if (ir == null) {
      String declClass = mr.getDeclaringClass().getName().toString();
      String methodSig = mr.getSignature();
      String methodSelector = methodSig.substring(methodSig.lastIndexOf('.'));
      
      List<String> subClasses = getSubClasses(walaAnalyzer, declClass, true, false);
      for (int i = 0, size = subClasses.size(); i < size && targetIRs.size() < maxToGet; i++) {
        String newMethodSig = Utils.getClassTypeJavaStr(subClasses.get(i), false) + methodSelector;
        IR ir2 = Jar2IR.getIR(walaAnalyzer, newMethodSig);
        if (ir2 != null) {
          targetIRs.add(ir2);
        }
      }
    }
    else {
      targetIRs.add(ir); // it is concrete, no need to find implementations
    }
    
    // remove redundant
    Utils.deleteRedundents(targetIRs);
    return targetIRs.toArray(new IR[targetIRs.size()]);
  }
  
  public static IR[] getOverrides(WalaAnalyzer walaAnalyzer, MethodReference mr, int maxToGet) {
    IR ir = Jar2IR.getIR(walaAnalyzer, mr.getSignature());
    if (ir == null) {// it is an abstract method, we will find its implementation methods
      return getImplementations(walaAnalyzer, mr, maxToGet);
    }
    else { // it is a concrete method, we will find all the methods that override it
      HashSet<IR> targetIRs = new HashSet<IR>();
      
      String declClass = mr.getDeclaringClass().getName().toString();
      String methodSig = mr.getSignature();
      String methodSelector = methodSig.substring(methodSig.lastIndexOf('.'));
      
      List<String> subClasses = getSubClasses(walaAnalyzer, declClass, true, false);
      for (int i = 0, size = subClasses.size(); i < size && targetIRs.size() < maxToGet; i++) {
        String newMethodSig = Utils.getClassTypeJavaStr(subClasses.get(i), false) + methodSelector;
        IR ir2 = Jar2IR.getIR(walaAnalyzer, newMethodSig);
        if (ir2 != null && !ir2.equals(ir)) {
          targetIRs.add(ir2);
        }
      }
      return targetIRs.toArray(new IR[targetIRs.size()]);
    }
  }
  
  public static String findFieldType(WalaAnalyzer walaAnalyzer, String objType, String fieldName) {
    String fieldTypeStr = "Unknown-Type";
    
    // get fieldType according to class name
    Class<?> fieldType = Utils.findClassFieldType(objType, fieldName);
    
    if (fieldType != null) {
      int arrayDim = 0;
      String fieldTypeJavaName = fieldType.getName();
      for (int i = 0, size = fieldTypeJavaName.length(); i < size; i++) {
        if (fieldTypeJavaName.charAt(i) != '[') {
          break;
        }
        else {
          arrayDim++;
        }
      }

      String innerTypeWala = null;
      String innerTypeStr = fieldType.getName().substring(arrayDim);
      if (innerTypeStr.length() == 1) { // e.g.[C, [B
        innerTypeWala = innerTypeStr;
      }
      else {
        innerTypeWala = Utils.toEncoding(innerTypeStr); // char, int -> C, I; reference type names unchanged
      }
      
      if (innerTypeWala.length() > 1) {
        // get the wala type string
        IClass cls = getClass(walaAnalyzer, innerTypeStr);
        innerTypeWala = cls.getName().toString();
      }
      
      // append [ to the beginning
      if (innerTypeWala != null) {
        fieldTypeStr = "";
        for (int i = 0; i < arrayDim; i++) {
          fieldTypeStr += '[';
        }
        fieldTypeStr += innerTypeWala;
      }
    }
    
    return fieldTypeStr;
  }
  
  public static SimpleEntry<IR[], CGNode[]> findInvocationTargets(
      WalaAnalyzer walaAnalyzer, CGNode caller, CallSiteReference callSite, int maxToGet) {
    
    // get target CGNodes
    CallGraph callGraph = walaAnalyzer.getCallGraph();
    CGNode[] targets = callGraph.getDispatchTargets(caller, callSite);
    
    // get target IRs
    List<IR> targetIRs       = new ArrayList<IR>();
    List<CGNode> targetNodes = new ArrayList<CGNode>();
    for (int i = 0; i < targets.length && targetIRs.size() < maxToGet; i++) {
      IR ir = Jar2IR.getIR(walaAnalyzer, targets[i].getMethod().getSignature());
      if (ir != null) {
        targetIRs.add(ir);
        targetNodes.add(targets[i]);
      }
    }
    
    int size = targetIRs.size();
    return new SimpleEntry<IR[], CGNode[]>(targetIRs.toArray(new IR[size]), targetNodes.toArray(new CGNode[size]));
  }
  
  private static void sortSubClasses(List<String> subClasses) {
    final Hashtable<String, Class<?>> subClassMap = new Hashtable<String, Class<?>>();
    for (String subClass : subClasses) {
      Class<?> cls = Utils.findClass(subClass);
      if (cls != null && !Modifier.isAbstract(cls.getModifiers()) && !cls.isInterface()) {
        subClassMap.put(subClass, cls);
      }
    }
    
    Collections.sort(subClasses, new java.util.Comparator<String>() {
      @Override
      public int compare(String o1, String o2) {
        Class<?> cls1 = subClassMap.get(o1);
        Class<?> cls2 = subClassMap.get(o2);
        boolean isPublic1 = cls1 != null && Modifier.isPublic(cls1.getModifiers());
        boolean isPublic2 = cls2 != null && Modifier.isPublic(cls2.getModifiers());
        return (isPublic1 ? 0 : 1) - (isPublic2 ? 0 : 1);
      }
    });
  }
}
