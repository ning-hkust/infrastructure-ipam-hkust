package hk.ust.cse.Prevision;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class Summary {

  public Summary() {
    m_summary = new Hashtable<List<Object>, Predicate>();
  }

  public void putSummary(String methodNameOrSign, int nCurInvokeDepth,
      String valPrefix, Predicate postCond, Predicate preCond) {
    List<Object> key = new ArrayList<Object>();
    key.add(methodNameOrSign);
    key.add(nCurInvokeDepth);
    key.add(valPrefix);
    key.add(postCond);
    m_summary.put(key, preCond);
  }
  
  public Predicate getSummary(String methodNameOrSign, int nCurInvokeDepth,
      String valPrefix, Predicate postCond) {
    List<Object> key = new ArrayList<Object>();
    key.add(methodNameOrSign);
    key.add(nCurInvokeDepth);
    key.add(valPrefix);
    key.add(postCond);
    return m_summary.get(key);
  }

  private Hashtable<List<Object>, Predicate> m_summary;
}
