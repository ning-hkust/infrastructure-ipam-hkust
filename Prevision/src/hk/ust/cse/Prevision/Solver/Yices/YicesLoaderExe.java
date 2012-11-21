package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.Solver.ISolverLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// YicesLoaderExe is much slower than YicesLoaderLib, but the advantage 
// of using binary instead of library is that we can set timeout
public class YicesLoaderExe implements ISolverLoader {

  static {
    // create extension
    if (System.getProperty("os.name").startsWith("Windows")) {
      s_yicesPath = "./yices/yices.exe";
    }
    else {
      s_yicesPath = "./yices/yices";
    }
  }

  public YicesLoaderExe() {
    m_timeout = 5; // default time: 5 seconds
  }
  
  public YicesLoaderExe(int timeout) {
    m_timeout = timeout;
  }
  
  @SuppressWarnings("deprecation")
  public SOLVER_COMP_PROCESS check(String input) {
    // start process
    m_yicesProc = startYicesProc();
    
    // create separate thread to pass command
    WriteCommandThread writeThread = new WriteCommandThread();
    
    // there seems to be some problems if we use Buffered*Stream
    InputStream in   = m_yicesProc.getInputStream();
    InputStream err  = m_yicesProc.getErrorStream();
    OutputStream out = m_yicesProc.getOutputStream();

    try {
      writeThread.setContent(in, err, out, input.getBytes());
      writeThread.start();
      
      int waitTime    = 0;
      int maxWaitTime = 4 * m_timeout * 1000;
      while (writeThread.isAlive() /* still writing commands */ && waitTime < maxWaitTime) {
        Thread.sleep(50);
        waitTime += 50;
      }
      
      // a block on flush() is detected (dont' know why it happens, but it may)
      if (writeThread.isAlive()) { // writing command never finish
        if (isYicesProcAlive()) {
          m_yicesProc.destroy();
        }
        writeThread.stop();
      }
      
      m_lastInput = input;

      // wait a while for calculation
      while (isYicesProcAlive() && in.available() <= 0 && err.available() <= 0) {
        Thread.sleep(10);
      }
      Thread.sleep(50);

      if (in.available() > 0) {       // SMT Check finished
        // get output
        int read = 0;
        char[] buff = new char[51200];
        while (in.available() > 0) {
          int c;
          if ((c = in.read()) != -1) {
            buff[read++] = (char) c;
          }
          else {
            break;
          }
        }

        // get output
        m_lastOutput = new String(buff, 0, read);
        
        // destroy process
        m_yicesProc.destroy();

        // return satisfactory or not
        if (m_lastOutput.startsWith("unknown")) {
          return SOLVER_COMP_PROCESS.TIMEOUT;
        }
        else if (m_lastOutput.startsWith("sat")) {
          return SOLVER_COMP_PROCESS.SAT;
        }
        else {
          return SOLVER_COMP_PROCESS.UNSAT;
        }
      }
      else if (err.available() > 0) {       // SMT Check throws error
        // get error output
        int read = 0;
        char[] buff = new char[51200];
        while (err.available() > 0) {
          int c;
          if ((c = err.read()) != -1) {
            buff[read++] = (char) c;
          }
          else {
            break;
          }
        }
        
        // destroy process
        m_yicesProc.destroy();
        
        // get output
        String errMsg = new String(buff, 0, read);
        System.out.println("SMT Check error: " + errMsg);
        return SOLVER_COMP_PROCESS.ERROR;
      }
      else {       // SMT Check timeout
        System.out.println("SMT Check timeout!");
        return SOLVER_COMP_PROCESS.TIMEOUT;
      }
    } catch (Exception e) {
      e.printStackTrace();
      
      // destroy process
      m_yicesProc.destroy();
      
      return SOLVER_COMP_PROCESS.ERROR;
    }
  }
  
  public String getLastOutput() {
    return m_lastOutput;
  }

  public String getLastInput() {
    return m_lastInput;
  }

  private Process startYicesProc() {
    try {
      return Runtime.getRuntime().exec(s_yicesPath + " --timeout=" + m_timeout);
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
  }
  
  private boolean isYicesProcAlive() {
    boolean isAlive = true;
    try {
      m_yicesProc.exitValue();
      isAlive = false;
    } catch (IllegalThreadStateException e) {
      isAlive = true;
    }
    return isAlive;
  }
  
  private static class WriteCommandThread extends Thread {
    
    public void setContent(InputStream in, InputStream err, OutputStream out, byte[] inputBytes) {
      m_inStream   = in;
      m_errStream  = err;
      m_outStream  = out;
      m_inputBytes = inputBytes;   
    }

    public void run() {
      try {
        for (int wrote = 0; wrote < m_inputBytes.length;) {
          int left = m_inputBytes.length - wrote;
          int writeOnce = (left > 4000) ? 4000 : left;
          
          // input statements into yices process
          m_outStream.write(m_inputBytes, wrote, writeOnce);
          m_outStream.flush();
          
          // before writing more, wait and see if anything returns
          if ((wrote += writeOnce) < m_inputBytes.length) {
            Thread.sleep(20);
            if (m_inStream.available() > 0 || m_errStream.available() > 0) {
              break;
            }
          }
        }
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }

    private byte[]       m_inputBytes;
    private InputStream  m_inStream;
    private InputStream  m_errStream;
    private OutputStream m_outStream;
  }

  private final int     m_timeout; /* in second */
  private Process       m_yicesProc;
  private String        m_lastInput;
  private String        m_lastOutput;
  private static final String s_yicesPath;
  @Override
  public SOLVER_COMP_PROCESS checkInContext(int ctx, String input) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public int createContext() {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override
  public void deleteContext(int ctx) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void pushContext(int ctx) {
    // TODO Auto-generated method stub
    
  }

  @Override
  public void popContext(int ctx) {
    // TODO Auto-generated method stub
    
  }
}
