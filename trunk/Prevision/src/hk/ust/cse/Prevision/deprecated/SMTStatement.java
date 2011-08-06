package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.Solver.SMTVariable.VarCategory;

import java.util.Hashtable;
import java.util.List;

public class SMTStatement {
  public SMTStatement(List<SMTTerm> terms) {
    m_terms = terms;
  }
  
  public void finalizeSMTStatement(Hashtable<SMTVariable, SMTVariable> finalVarMap) {
    for (SMTTerm term : m_terms) {
      SMTVariable var1  = finalVarMap.get(term.getVar1());
      SMTVariable var2  = finalVarMap.get(term.getVar2());

      if (var1 != null) {
        term.setVar1(var1);
      }
      if (var2 != null) {
        term.setVar2(var2);
      }
      
      // necessary, because Yices cannot parse 1/0 as true and false
      if (var1 != null && var1.getVarType().equals("Z")) {
        if (term.getVar2().equals("#!0")) {
          term.setVar2(new SMTVariable("false", "#ConstantNumber",
              VarCategory.VAR_CONST, null));
        }
        else if (term.getVar2().equals("#!1")) {
          term.setVar2(new SMTVariable("true", "#ConstantNumber",
              VarCategory.VAR_CONST, null));
        }
      }
    }
  }

  public String toYicesExprString() {
    StringBuilder yicesStr = new StringBuilder();

    if (m_terms.size() == 1) {
      yicesStr.append(m_terms.get(0).toYicesExprString());
    }
    else {
      // SMTTerms are arranged with 'or'
      yicesStr.append("(or");
      for (SMTTerm term : m_terms) {
        yicesStr.append(" ");
        yicesStr.append(term.toYicesExprString());
      }
      yicesStr.append(")");
    }

    return yicesStr.toString();
  }
  
  public List<SMTTerm> getSMTTerms() {
    return m_terms;
  }

  private final List<SMTTerm> m_terms;
}
