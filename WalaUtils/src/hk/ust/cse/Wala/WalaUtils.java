package hk.ust.cse.Wala;

import hk.ust.cse.util.Utils;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;

public class WalaUtils {
  
  public static List<String> getSubClasses(WalaAnalyzer walaAnalyzer, String className) {
    IClassHierarchy classHierarchy = walaAnalyzer.getClassHierarchy();
    List<String> subClassList = new ArrayList<String>();

    // find class first
    IClass targetClass = null;
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

    if (targetClass != null) {
      Collection<IClass> subClasses = null;
      if (targetClass.isInterface()) {
        subClasses = classHierarchy.getImplementors(targetClass.getReference());
      }
      else {
        subClasses = classHierarchy.getImmediateSubclasses(targetClass);
      }

      // add to result
      if (subClasses != null) {
        Iterator<IClass> allSubClasses = subClasses.iterator();
        while (allSubClasses.hasNext()) {
          IClass subClass = (IClass) allSubClasses.next();

          String subClassName = subClass.getName().toString();
          subClassList.add(Utils.getClassTypeJavaStr(subClassName, false));
        }
      }
    }
    return subClassList;
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
}
