package hk.ust.cse.Prevision.Wala;

import hk.ust.cse.Prevision.Utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.IClassHierarchy;

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
}
