package hk.ust.cse.util.Python;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ScriptRunner {

  public ScriptRunner(String scriptFile) {
    m_scriptFile = scriptFile;
  }
  
  public int run(String[] args, boolean quiet) {
    int exitVal = -1;
    
    try {
      Runtime runtime = Runtime.getRuntime();
      
      // construct command and arguments
      StringBuilder cmd = new StringBuilder();
      cmd.append("cmd /c ");
      cmd.append((new File(m_scriptFile)).getAbsolutePath());
      cmd.append(" ");
      for (int i = 0; i < args.length; i++) {
        cmd.append(args[i]);
        cmd.append(" ");
      }
      Process proc = runtime.exec(cmd.toString());
      
      // wait for output
      BufferedReader input = new BufferedReader(new InputStreamReader(proc.getInputStream()));
      BufferedReader error = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

      String line = null;
      StringBuilder str = new StringBuilder();
      while((line = input.readLine()) != null) {
        if (!quiet) {
          System.out.println(line);
        }
        str.append(line);
        str.append("\n");
      }
      m_lastOutput = str.toString();
      
      str = new StringBuilder();
      while((line = error.readLine()) != null) {
        if (!quiet) {
          System.out.println(line);
        }
        str.append(line);
        str.append("\n");
      }
      m_lastError = str.toString();

      exitVal = proc.waitFor();
      if (!quiet) {
        System.out.println("Exited with error code: " + exitVal);
      }
      
    } catch(Exception e) {
      e.printStackTrace();
    }
    
    return exitVal;
  }
  
  public String getScriptFile() {
    return m_scriptFile;
  }
  
  public String getLastOutput() {
    return m_lastOutput;
  }
  
  public String getLastError() {
    return m_lastError;
  }
  
  private final String m_scriptFile;
  private String m_lastOutput;
  private String m_lastError;
}
