package hk.ust.cse.util.Coverage;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Method {
  
  public Method(String name, String signature, double lineCoverage, 
      double branchCoverage, Class parentClass) {
    m_name            = name;
    m_signature       = signature;
    m_lineCoverage    = lineCoverage;
    m_branchCoverage  = branchCoverage;
    m_parentClass     = parentClass;
    
    m_linesInMethod   = new ArrayList<Line>();
    m_htLinesMap      = new Hashtable<Integer, Line>();
  }
  
  public void addInLine(Line line) {
    if (line != null) {
      m_linesInMethod.add(line);
      m_htLinesMap.put(Integer.valueOf(line.getLineNumber()), line);
    }
  }
  
  public List<Line> getAllLines() {
    return m_linesInMethod;
  }

  public Line getLine(int nLine) {
    return m_htLinesMap.get(Integer.valueOf(nLine));
  }
  
  public String getName() {
    return m_name;
  }

  public String getSignature() {
    return m_signature;
  }

  public double getLineCoverage() {
    return m_lineCoverage;
  }

  public double getBranchCoverage() {
    return m_branchCoverage;
  }

  public Class getParentClass() {
    return m_parentClass;
  }

  private final String m_name;
  private final String m_signature;
  private final double m_lineCoverage;
  private final double m_branchCoverage;
  private final Class  m_parentClass;
  private List<Line>   m_linesInMethod;
  private Hashtable<Integer, Line> m_htLinesMap;
}
