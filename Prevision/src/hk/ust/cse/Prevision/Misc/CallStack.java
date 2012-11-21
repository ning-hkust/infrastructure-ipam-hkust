package hk.ust.cse.Prevision.Misc;

import java.util.ArrayList;
import java.util.List;

public class CallStack implements Cloneable {
  class StackTrace {
    public StackTrace(String methodNameOrSign, int lineNo) {
      m_methodNameOrSign  = methodNameOrSign;
      m_lineNo            = lineNo;
    }
    
    public String getMethodNameOrSign() {
      return m_methodNameOrSign;
    }
    
    public int getLineNo() {
      return m_lineNo;
    }
    
    // we need to find it from hashtable
    public boolean equals(Object o) {
      if (o == null || !(o instanceof StackTrace)) {
        return false;
      }

      StackTrace st = (StackTrace) o;
      return m_lineNo == st.m_lineNo
          && m_methodNameOrSign.equals(st.m_methodNameOrSign);
    }

    // we need to find it from hashtable
    public int hashCode() {
      return m_methodNameOrSign.hashCode();
    }

    private final String m_methodNameOrSign;
    private final int    m_lineNo;
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
  public void addStackTrace(String methodNameOrSign, int lineNo) {
    m_callStack.add(new StackTrace(methodNameOrSign, lineNo));
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
  
  public CallStack getInnerMostCallStack() {
    CallStack innerMostCallStack = new CallStack(m_callStack.size() == 1 ? m_isOutMostCall : false);
    innerMostCallStack.addStackTrace(m_callStack.get(m_callStack.size() - 1));
    return innerMostCallStack;
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
  
  public String getCurMethodNameAndLineNo() {
    if (m_callStack.size() > 0) {
      return m_callStack.get(0).getMethodNameOrSign() + ":" + m_callStack.get(0).getLineNo();
    }
    else {
      return null;
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
  
  public int getNextLineNo() {
    if (m_callStack.size() > 1) {
      return m_callStack.get(1).getLineNo();
    }
    else {
      return -1;
    }
  }
  
  public String getNextMethodNameAndLineNo() {
    if (m_callStack.size() > 1) {
      return m_callStack.get(1).getMethodNameOrSign() + ":" + m_callStack.get(1).getLineNo();
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
  
  public void setOutMostCall(boolean isOutMostCall) {
    m_isOutMostCall = isOutMostCall  ;
  }
  
  public String toString() {
    StringBuilder str = new StringBuilder();
    for (int i = 0, size = m_callStack.size(); i < size; i++) {
      str.append(m_callStack.get(i).getMethodNameOrSign() + ":");
      str.append(m_callStack.get(i).getLineNo());
      if (i != m_callStack.size() - 1) {
        str.append(" -> ");
      }
    }
    return str.toString();
  }
  
  public static CallStack fromString(String callStackStr) {
    CallStack cs = new CallStack(true);
    
    try {  
      String[] frames = callStackStr.split(" -> ");
      
      // parse each frame
      for (int i = 0; i < frames.length; i++) {
        int index            = frames[i].indexOf(":");
        String methNameOrSig = frames[i].substring(0, index);
        int lineNo           = Integer.parseInt(frames[i].substring(index + 1));
        cs.addStackTrace(methNameOrSig.intern(), lineNo);
      }
    } catch (Exception e) {
      e.printStackTrace();
      cs = null;
    }
    return cs;
  }
  
  public CallStack clone() {
    CallStack callStack = new CallStack(m_isOutMostCall);
    for (StackTrace st : m_callStack) {
      callStack.addStackTrace(st);
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
    for (StackTrace st : m_callStack) {
      hashCode += st.hashCode();
    }
    return hashCode;
  }

  private boolean m_isOutMostCall;
  private final List<StackTrace> m_callStack;
}
