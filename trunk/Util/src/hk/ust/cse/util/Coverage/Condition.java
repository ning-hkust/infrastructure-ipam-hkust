package hk.ust.cse.util.Coverage;

public class Condition {
  public Condition(int number, String type, String coverage, Line parentLine) {
    m_number      = number;
    m_type        = type;
    m_coverageStr = coverage;
    m_parentLine  = parentLine;
    
    // get coverage in double format
    double num    = Double.parseDouble(m_coverageStr.substring(0, m_coverageStr.length()-1));
    m_coverage    = num / 100.0;
  }

  public int getNumber() {
    return m_number;
  }

  public String getType() {
    return m_type; // jump or switch, or ...
  }

  public String getCoverageStr() {
    return m_coverageStr; // should be 0%, 50% or 100% for ordinary "jump" type
  }
  
  public double getCoverage() {
    return m_coverage;
  }
  
  public Line getParentLine() {
    return m_parentLine;
  }

  private final int     m_number;
  private final String  m_type;
  private final String  m_coverageStr;
  private final double  m_coverage;
  private final Line    m_parentLine;
}
