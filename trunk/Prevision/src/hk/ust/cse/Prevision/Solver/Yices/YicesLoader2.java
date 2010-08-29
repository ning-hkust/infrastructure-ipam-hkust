package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.Solver.ISolverResult;
import hk.ust.cse.Prevision.Solver.SMTVariable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Hashtable;


// Deprecated, use YicesLoader instead
public class YicesLoader2 {

  static {
    // create extension
    if (System.getProperty("os.name").startsWith("Windows")) {
      s_yicesPath = "./yices/yices.exe";
    }
    else {
      s_yicesPath = "./yices/yices";
    }
  }

  public YicesLoader2(int nRestartInterval) {
    m_nCurrentCount    = 0;
    m_nRestartInterval = nRestartInterval;
  }
  
  public boolean check(String input, Hashtable<String, SMTVariable> defFinalVarMap) {
    // start process
    if (m_yicesProc == null) {
      m_yicesProc = startYicesProc();
    }
    else {
      try {
        m_yicesProc.exitValue();
        m_yicesProc = startYicesProc();
      } catch (IllegalThreadStateException e) {
        // the process is still running
        if (m_nCurrentCount >= m_nRestartInterval) {
          // we restart a new Yices process after some interval, that's because
          // the process's input stream may be blocked after some time
          m_yicesProc.destroy();
          m_yicesProc = startYicesProc();
        }
      }
    }
    m_nCurrentCount++;
    
    // there seems to be some problems if we use Buffered*Stream
    InputStream in   = m_yicesProc.getInputStream();
    InputStream err  = m_yicesProc.getErrorStream();
    OutputStream out = m_yicesProc.getOutputStream();

    try {
      byte[] inputBytes = input.getBytes();
      for (int nWrote = 0; nWrote < inputBytes.length;) {
        int nLeft = inputBytes.length - nWrote;
        int nWriteOnce = (nLeft > 4000) ? 4000 : nLeft;

        // input statements into yices process
        out.write(inputBytes, nWrote, nWriteOnce);
        out.flush();

        // before writing more, wait and see if anything returns
        if ((nWrote += nWriteOnce) < inputBytes.length) {
          Thread.sleep(20);
          if (in.available() > 0 || err.available() > 0) {
            break;
          }
        }
      }
      m_lastInput = input;

      // wait a while for calculation
      int nSlept = 0;
      while (nSlept < 1000 && in.available() <= 0 && err.available() <= 0) {
        Thread.sleep(10);
        nSlept += 10;
      }
      Thread.sleep(100);

      if (in.available() > 0) {       // SMT Check finished
        // get output
        int nRead = 0;
        char[] buff = new char[51200];
        while (in.available() > 0) {
          int c;
          if ((c = in.read()) != -1) {
            buff[nRead++] = (char) c;
          }
          else {
            break;
          }
        }

        // get output
        String output = new String(buff, 0, nRead);

        // parse and save result
        if (m_lastResult == null) {
          m_lastResult = new YicesResult();
        }
        m_lastResult.parseOutput(output, defFinalVarMap);

        // return satisfactory or not
        return m_lastResult.isSatisfactory();
      }
      else if (err.available() > 0) {       // SMT Check throws error
        // get error output
        int nRead = 0;
        char[] buff = new char[51200];
        while (err.available() > 0) {
          int c;
          if ((c = err.read()) != -1) {
            buff[nRead++] = (char) c;
          }
          else {
            break;
          }
        }

        // get output
        String errMsg = new String(buff, 0, nRead);
        
        System.out.println("SMT Check error: " + errMsg);
        return false;
      }
      else {       // SMT Check timeout
        System.out.println("SMT Check timeout!");
        return false;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }
  
  public String getLastOutput() {
    if (m_lastResult != null) {
      return m_lastResult.getOutputStr();
    }
    else {
      return "";
    }
  }

  public String getLastInput() {
    return m_lastInput;
  }

  public ISolverResult getLastResult() {
    return m_lastResult;
  }

  private Process startYicesProc() {
    try {
      m_nCurrentCount = 0; /* clear counter */
      return Runtime.getRuntime().exec(s_yicesPath);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }

  private Process       m_yicesProc;
  private String        m_lastInput;
  private ISolverResult m_lastResult;
  private int           m_nCurrentCount;
  private final int     m_nRestartInterval;
  private static final String s_yicesPath;
}
