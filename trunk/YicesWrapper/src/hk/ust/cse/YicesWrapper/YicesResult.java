package hk.ust.cse.YicesWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesResult {
  
  private static final Pattern s_pattern1 = Pattern.compile("^\\(= ([\\S]+) ([\\S]+)\\)$");
  
  public void parseOutput(String output) {
    // save output first
    m_output = output;

    // split outputs
    String[] outLines = output.split(LINE_SEPARATOR);

    // analyze
    m_satModel = new ArrayList<String[]>();
    if (outLines.length == 0) {
      m_bSatisfactory = false;
    }
    else if (!outLines[0].startsWith("sat")) {
      m_bSatisfactory = false;
    }
    else {
      m_bSatisfactory = true;
      
      // analyze each model line
      for (int i = 1; i < outLines.length; i++) {
        if (outLines[i].length() > 0) {
          String[] term = toSMTTerm(outLines[i]);
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
    return m_output;
  }
  
  public List<String[]> getSatModel() {
    return m_satModel;
  }
  
  private String[] toSMTTerm(String str) {
    String[] smtTerm = null;
    
    Matcher matcher = null;
    if ((matcher = s_pattern1.matcher(str)).find()) {
      smtTerm = new String[] {matcher.group(1), matcher.group(2)};
    }
    return smtTerm;
  }

  private String         m_output;
  private boolean        m_bSatisfactory;
  private List<String[]> m_satModel;
  private static final String LINE_SEPARATOR = System.getProperty("line.separator");
}
