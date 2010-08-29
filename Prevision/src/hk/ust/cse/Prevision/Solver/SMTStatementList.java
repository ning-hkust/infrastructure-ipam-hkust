package hk.ust.cse.Prevision.Solver;

import hk.ust.cse.Prevision.Solver.SMTTerm.Operator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;

public class SMTStatementList {

  public SMTStatementList(List<List<String>> smtStatementList,
      Hashtable<String, SMTVariable> allVarMap) {
    buildSMTStatementList(smtStatementList, allVarMap);
  }

  public String genYicesInput(Hashtable<SMTVariable, SMTVariable> finalVarMap) {
    // finalize variables in smtStatements
    finalizeAllSMTStatements(finalVarMap);
    
    // create Yices assert statements
    HashSet<String> asserted = new HashSet<String>();
    StringBuilder yicesStr = new StringBuilder();
    for (SMTStatement smtStatement : m_smtStatements) {
      String toAssertStr = smtStatement.toYicesExprString();
      if (toAssertStr.length() > 0 && !asserted.contains(toAssertStr)) {
        yicesStr.append("(assert ");
        yicesStr.append(toAssertStr);
        yicesStr.append(")\n");

        // avoid duplication
        asserted.add(toAssertStr);
      }
    }

    // check
    yicesStr.append("(check)\n");

    return yicesStr.toString();
  }
  
  private void finalizeAllSMTStatements(Hashtable<SMTVariable, SMTVariable> finalVarMap) {
    for (SMTStatement smtStatement : m_smtStatements) {
      smtStatement.finalizeSMTStatement(finalVarMap);
    }
  }

  private void buildSMTStatementList(List<List<String>> smtStatementList,
      Hashtable<String, SMTVariable> allVarMap) {
    m_smtStatements = new ArrayList<SMTStatement>();
    for (List<String> smtStatementTerms : smtStatementList) {
      List<SMTTerm> smtTerms = new ArrayList<SMTTerm>();
      for (int i = 0; i < smtStatementTerms.size(); i++) {
        String var1 = smtStatementTerms.get(i++);
        String op   = smtStatementTerms.get(i++);
        String var2 = smtStatementTerms.get(i);
        
        SMTTerm term = translateSMTStr(var1, op, var2, allVarMap);
        if (term != null) {
          smtTerms.add(term);
        }
        else {
          System.err.println("Unable to analyze term: " + var1 + " " + op + " " + var2);
        }
      }
      m_smtStatements.add(new SMTStatement(smtTerms));
    }
  }
  
  private SMTTerm translateSMTStr(String var1Str, String opStr, String var2Str,
      Hashtable<String, SMTVariable> allVarMap) {

    Operator op = null;
    if (opStr.equals("!=")) {
      op = Operator.OP_INEQUAL;
    }
    else if (opStr.equals("==")) {
      op = Operator.OP_EQUAL;
    }
    else if (opStr.equals(">")) {
      op = Operator.OP_GREATER;
    }
    else if (opStr.equals(">=")) {
      op = Operator.OP_GREATER_EQUAL;
    }
    else if (opStr.equals("<")) {
      op = Operator.OP_SMALLER;
    }
    else if (opStr.equals("<=")) {
      op = Operator.OP_SMALLER_EQUAL;
    }

    SMTVariable var1 = allVarMap.get(var1Str);
    SMTVariable var2 = allVarMap.get(var2Str);
    if (var1 != null && var2 != null && op != null) {
      return new SMTTerm(var1, op, var2);
    }
    else {
      return null;
    }
  }
  
  private List<SMTStatement> m_smtStatements;
}
