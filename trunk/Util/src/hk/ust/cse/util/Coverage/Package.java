package hk.ust.cse.util.Coverage;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Package {
  public Package(String name, double lineCoverage, double branchCoverage, double complexity) {
    m_name            = name;
    m_lineCoverage    = lineCoverage;
    m_branchCoverage  = branchCoverage;
    m_complexity      = complexity;
    
    m_classesInPkg    = new ArrayList<Class>();
    m_htClassesMap    = new Hashtable<String, Class>();
  }
  
  public void addInClass(Class cls) {
    if (cls != null) {
      m_classesInPkg.add(cls);
      m_htClassesMap.put(cls.getName(), cls);
    }
  }
  
  public List<Class> getAllClasses() {
    return m_classesInPkg;
  }

  public Class getClass(String className) {
    return m_htClassesMap.get(className);
  }

  public String getName() {
    return m_name;
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

  private final String m_name;
  private final double m_lineCoverage;
  private final double m_branchCoverage;
  private final double m_complexity;
  private List<Class> m_classesInPkg;
  private Hashtable<String, Class> m_htClassesMap;
}
