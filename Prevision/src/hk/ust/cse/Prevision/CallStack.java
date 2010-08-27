package hk.ust.cse.Prevision;

import java.util.ArrayList;
import java.util.List;

public class CallStack {
  class StackTrace {
    public StackTrace(String methodNameOrSign, int nLineNo) {
      m_methodNameOrSign  = methodNameOrSign;
      m_nLineNo           = nLineNo;
    }
    
    public String getMethodNameOrSign() {
      return m_methodNameOrSign;
    }
    
    public int getLineNo() {
      return m_nLineNo;
    }
    
    // we need to find it from hashtable
    public boolean equals(Object o) {
      if (o == null || !(o instanceof StackTrace)) {
        return false;
      }

      StackTrace st = (StackTrace) o;
      return m_nLineNo == st.m_nLineNo
          && m_methodNameOrSign.equals(st.m_methodNameOrSign);
    }

    // we need to find it from hashtable
    public int hashCode() {
      return m_methodNameOrSign.hashCode();
    }

    private final String m_methodNameOrSign;
    private final int    m_nLineNo;
  }

  public CallStack(boolean isOutMostCall) {
    m_callStack     = new ArrayList<StackTrace>();
    m_isOutMostCall = isOutMostCall;
  }
  
  /**
   * add a stack trace to inner call stack
   */
  public void addStackTrace(StackTrace stackTrace) {
    m_callStack.add(stackTrace);
  }

  /**
   * add a stack trace to inner call stack
   */
  public void addStackTrace(String methodNameOrSign, int nLineNo) {
    m_callStack.add(new StackTrace(methodNameOrSign, nLineNo));
  }

  public CallStack getInnerCallStack() {
    if (m_callStack.size() > 1) {
      CallStack innerCallStack = new CallStack(false);
      
      for (int i = 1; i < m_callStack.size(); i++) {
        innerCallStack.addStackTrace(m_callStack.get(i));
      }
      
      return innerCallStack;
    }
    else {
      return null;
    }
  }

  public String getCurMethodNameOrSign() {
    if (m_callStack.size() > 0) {
      return m_callStack.get(0).getMethodNameOrSign();
    }
    else {
      return null;
    }
  }

  public int getCurLineNo() {
    if (m_callStack.size() > 0) {
      return m_callStack.get(0).getLineNo();
    }
    else {
      return -1;
    }
  }

  public String getNextMethodNameOrSign() {
    if (m_callStack.size() > 1) {
      return m_callStack.get(1).getMethodNameOrSign();
    }
    else {
      return null;
    }
  }

  public int getDepth() {
    return m_callStack.size();
  }

  public boolean isOutMostCall() {
    return m_isOutMostCall;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();
    for (int i = 0; i < m_callStack.size(); i++) {
      str.append(m_callStack.get(i).getMethodNameOrSign() + ":");
      str.append(m_callStack.get(i).getLineNo());
      if (i != m_callStack.size() - 1) {
        str.append(" -> ");
      }
    }
    return str.toString();
  }
  
  public CallStack clone() {
    CallStack callStack = new CallStack(m_isOutMostCall);
    for (int i = 0, size = m_callStack.size(); i < size; i++) {
      callStack.addStackTrace(m_callStack.get(i));
    }
    return callStack;
  }

  // we need to find it from hashtable
  public boolean equals(Object o) {
    if (o == null || !(o instanceof CallStack)) {
      return false;
    }

    CallStack cs = (CallStack) o;
    return m_isOutMostCall == cs.m_isOutMostCall
        && m_callStack.equals(cs.m_callStack);
  }

  // we need to find it from hashtable
  public int hashCode() {
    int hashCode = 0;
    for (int i = 0, size = m_callStack.size(); i < size; i++) {
      hashCode += m_callStack.get(i).hashCode();
    }
    return hashCode;
  }

  private final List<StackTrace> m_callStack;
  private final boolean          m_isOutMostCall;
}
