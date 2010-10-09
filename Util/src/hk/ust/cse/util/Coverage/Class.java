package hk.ust.cse.util.Coverage;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Class {
  public Class(String name, String srcFileName, double lineCoverage,
      double branchCoverage, double complexity, Package parentPkg) {
    m_name            = name;
    m_srcFileName     = srcFileName;
    m_lineCoverage    = lineCoverage;
    m_branchCoverage  = branchCoverage;
    m_complexity      = complexity;
    m_parentPkg       = parentPkg;
    
    m_methodsInClass  = new ArrayList<Method>();
    m_htMethodsMap    = new Hashtable<String, Method>();
  }
  
  public void addInMethod(Method method) {
    if (method != null) {
      m_methodsInClass.add(method);
      m_htMethodsMap.put(method.getName() + method.getSignature(), method);
    }
  }
  
  public List<Method> getAllMethods() {
    return m_methodsInClass;
  }

  public Method getMethod(String methodName, String methodSig) {
    return m_htMethodsMap.get(methodName + methodSig);
  }
  
  public String getName() {
    return m_name;
  }
  
  public String getSrcFileName() {
    return m_srcFileName;
  }

  public double getLineCoverage() {
    return m_lineCoverage;
  }

  public double getBranchCoverage() {
    return m_branchCoverage;
  }

  public double getComplexity() {
    return m_complexity;
  }

  public Package getParentPackage() {
    return m_parentPkg;
  }

  private final String m_name;
  private final String m_srcFileName;
  private final double m_lineCoverage;
  private final double m_branchCoverage;
  private final double m_complexity;
  private final Package m_parentPkg;
  private List<Method> m_methodsInClass;
  private Hashtable<String, Method> m_htMethodsMap;
}
