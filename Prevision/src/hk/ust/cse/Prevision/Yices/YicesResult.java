package hk.ust.cse.Prevision.Yices;

import hk.ust.cse.Prevision.Yices.SMTTerm.Operator;
import hk.ust.cse.Prevision.Yices.SMTVariable.VarCategory;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesResult {

  public void parseOutput(String output, Hashtable<String, SMTVariable> defFinalVarMap) {
    // save output first
    m_output = output;

    // split outputs
    String[] outLines = output.split(LINE_SEPARATOR);

    // analyze
    m_satModel = new ArrayList<SMTTerm>();
    if (outLines.length == 0) {
      m_bSatisfactory = false;
    }
    else if (!outLines[0].startsWith("sat")) {
      m_bSatisfactory = false;
    }
    else {
      m_bSatisfactory = true;
      for (int i = 1; i < outLines.length; i++) {
        if (outLines[i].length() > 0) {
          SMTTerm term = toSMTTerm(outLines[i], defFinalVarMap);
          if (term != null) {
            m_satModel.add(term);
          }
          else {
            System.err.println("Unable to analyze model line: " + outLines[i]);
          }
        }
      }
    }
  }

  public boolean isSatisfactory() {
    return m_bSatisfactory;
  }

  public String getOutputStr() {
    return Utils.unfilterChars(m_output);
  }

  public List<SMTTerm> getSatModel() {
    return m_satModel;
  }

  private SMTTerm toSMTTerm(String str, Hashtable<String, SMTVariable> defFinalVarMap) {
    Pattern pattern = Pattern.compile("^\\(= ([\\S]+) ([\\S]+)\\)$");
    Matcher matcher = null;
    if ((matcher = pattern.matcher(str)).find()) {
      SMTVariable var1 = defFinalVarMap.get(matcher.group(1));
      SMTVariable var2 = defFinalVarMap.get(matcher.group(2));
      
      // it is possible that var2 is a new value,
      // e.g. a new int or string(bit-vector) value
      if (var2 == null) {
        var2 = new SMTVariable(matcher.group(2), "#ConstantNumber", 
            VarCategory.VAR_CONST, null);
      }
      
      if (var1 != null) {
        return new SMTTerm(var1, Operator.OP_EQUAL, var2);
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }

  private String        m_output;
  private boolean       m_bSatisfactory;
  private List<SMTTerm> m_satModel;
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
