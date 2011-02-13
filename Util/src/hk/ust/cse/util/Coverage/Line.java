package hk.ust.cse.util.Coverage;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Line {

  public Line(int nLineNo, long nHits, boolean isBranch, String condCoverage, 
      Method parentMethod) {
    m_nLineNo           = nLineNo;
    m_nHits             = nHits;
    m_isBranch          = isBranch;
    m_conditionCoverage = condCoverage;
    m_parentMethod      = parentMethod;
    
    m_conditionsInLine  = new ArrayList<Condition>();
    m_htConditionsMap   = new Hashtable<Integer, Condition>();
    
    // get branch covered and branch count
    if (isBranch) {
      int nIndex1 = m_conditionCoverage.indexOf('(');
      int nIndex2 = m_conditionCoverage.indexOf('/');
      int nIndex3 = m_conditionCoverage.indexOf(')');
      
      m_conditionCovered = Integer.parseInt(m_conditionCoverage.substring(nIndex1 + 1, nIndex2));
      m_conditionCount   = Integer.parseInt(m_conditionCoverage.substring(nIndex2 + 1, nIndex3)); 
    }
    else {
      m_conditionCovered = -1;
      m_conditionCount   = -1;
    }
  }
  
  public void addInCondition(Condition condition) {
    if (condition != null) {
      m_conditionsInLine.add(condition);
      m_htConditionsMap.put(Integer.valueOf(condition.getNumber()), condition);
    }
  }
  
  public List<Condition> getAllConditions() {
    return m_conditionsInLine;
  }

  public Condition getCondition(int number) {
    return m_htConditionsMap.get(Integer.valueOf(number));
  }

  public int getLineNumber() {
    return m_nLineNo;
  }

  public long getHitCount() {
    return m_nHits;
  }

  public boolean isBranch() {
    return m_isBranch;
  }

  public String getConditionCoverage() {
    return m_conditionCoverage;
  }
  
  public int getConditionCovered() {
    return m_conditionCovered;
  }
  
  public int getConditionCount() {
    return m_conditionCount;
  }
  
  public Method getParentMethod() {
    return m_parentMethod;
  }

  private final int     m_nLineNo;
  private final long    m_nHits;
  private final int     m_conditionCovered;
  private final int     m_conditionCount;
  private final boolean m_isBranch;
  private final String  m_conditionCoverage;
  private final Method  m_parentMethod;
  private List<Condition> m_conditionsInLine;
  private Hashtable<Integer, Condition> m_htConditionsMap;
}
