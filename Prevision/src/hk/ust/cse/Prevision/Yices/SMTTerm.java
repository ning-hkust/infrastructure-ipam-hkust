package hk.ust.cse.Prevision.Yices;

public class SMTTerm {
  public enum Operator {
    OP_EQUAL, 
    OP_INEQUAL, 
    OP_GREATER, 
    OP_GREATER_EQUAL, 
    OP_SMALLER, 
    OP_SMALLER_EQUAL}
  
  public SMTTerm(SMTVariable var1, Operator op, SMTVariable var2) {
    m_var1 = var1;
    m_var2 = var2;
    m_op   = op;
  }

  public String toString() {
    String var1Str = m_var1.toYicesExprString(0);
    String var2Str = m_var2.toYicesExprString(0);
    switch (m_op) {
    case OP_EQUAL:
      return var1Str + " == " + var2Str;
    case OP_INEQUAL:
      return var1Str + " != " + var2Str;
    case OP_GREATER:
      return var1Str + " > " + var2Str;
    case OP_GREATER_EQUAL:
      return var1Str + " >= " + var2Str;
    case OP_SMALLER:
      return var1Str + " < " + var2Str;
    case OP_SMALLER_EQUAL:
      return var1Str + " <= " + var2Str;
    default:
      return "Unknown SMTTerm!";
    }
  }

  public String toYicesExprString() {
    String var1Str = m_var1.toYicesExprString(0);
    String var2Str = m_var2.toYicesExprString(0);
    switch (m_op) {
    case OP_EQUAL:
      return "(= " + var1Str + " " + var2Str + ")";
    case OP_INEQUAL:
      return "(/= " + var1Str + " " + var2Str + ")";
    case OP_GREATER:
      return "(> " + var1Str + " " + var2Str + ")";
    case OP_GREATER_EQUAL:
      return "(>= " + var1Str + " " + var2Str + ")";
    case OP_SMALLER:
      return "(< " + var1Str + " " + var2Str + ")";
    case OP_SMALLER_EQUAL:
      return "(<= " + var1Str + " " + var2Str + ")";
    default:
      return "Unknown SMTTerm!";
    }
  }

  public SMTVariable getVar1() {
    return m_var1;
  }

  public SMTVariable getVar2() {
    return m_var2;
  }

  public Operator getOp() {
    return m_op;
  }

  public void setVar1(SMTVariable newVar1) {
    m_var1 = newVar1;
  }

  public void setVar2(SMTVariable newVar2) {
    m_var2 = newVar2;
  }

  public boolean equals(Object o) {
    if (o == null) {
      return false;
    }
    
    if (!(o instanceof SMTTerm)) {
      return false;
    }
    
    SMTTerm term = (SMTTerm)o;
    return (m_var1.equals(term.getVar1()) && 
            m_var2.equals(term.getVar2()) && 
            m_op.equals(term.getOp()));
  }

  private SMTVariable m_var1;
  private SMTVariable m_var2;
  private Operator   m_op;
}
