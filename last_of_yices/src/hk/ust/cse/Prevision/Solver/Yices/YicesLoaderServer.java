package hk.ust.cse.Prevision.Solver.Yices;

import hk.ust.cse.Prevision.Solver.ISolverLoader;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Random;

public class YicesLoaderServer implements ISolverLoader {
  
  static {
    Runtime.getRuntime().addShutdownHook(new ShutdownThread());
    
    Random random = new Random();
    s_listenPort = 30000 + random.nextInt(30000);
    s_serverPort = 30000 + random.nextInt(30000);
  }
  
  public YicesLoaderServer() {
  }
  
  public SOLVER_COMP_PROCESS check(String input) {
    // start listening for responses
    if (s_listenThread == null || !isThreadListening()) {
      try {
        s_listenThread = new ListenThread(s_listenPort);
        s_listenThread.start();
        Thread.sleep(500);
        
        // wait for the port to be binded
        long waited = 0;
        while (!isThreadListening() && waited < 10000) {
          Thread.sleep(20);
          waited += 20;
        }
      } catch (InterruptedException e) {}
    }
    
    // start server process when necessary
    if (!isServerProcAlive()) {
      startServerProc();
    }
    
    // send out command
    m_lastInput = input;
    s_listenThread.resetLastReceived();
    sendCommand(input);

    long waited = 0;
    String response = s_listenThread.getLastReceived();
    while (response == null && waited < 10000) {
      try {
        Thread.sleep(20);
        waited += 20;
      } catch (InterruptedException e) {}
      
      response = s_listenThread.getLastReceived();
    }
    
    m_lastOutput = "";
    if (response != null) { // SMT Check finished
      if (response.startsWith("sat:")) {
        m_lastOutput = response.substring(4);
        return SOLVER_COMP_PROCESS.SAT;
      }
      else if (response.startsWith("unsat:")) {
        m_lastOutput = response.substring(6);
        return SOLVER_COMP_PROCESS.UNSAT;
      }
      else if (response.startsWith("error:")) {
        String errMsg = response.substring(6);
        System.out.println("SMT Check error: " + errMsg);
        return SOLVER_COMP_PROCESS.ERROR;
      }
      else {
        System.out.println("SMT Check error: " + response);
        return SOLVER_COMP_PROCESS.ERROR;
      }
    }
    else { // SMT Check timeout
      terminateServerProc(); // terminate computation server process
      
      System.out.println("SMT Check timeout!");
      return SOLVER_COMP_PROCESS.TIMEOUT;
    }
  }
  
  public String getLastOutput() {
    return m_lastOutput;
  }

  public String getLastInput() {
    return m_lastInput;
  }
  
  private static void startServerProc() {
    try {
      //Runtime.getRuntime().exec("cmd /c start yicesServer.cmd " + s_serverPort + " " + s_listenPort);
      Runtime.getRuntime().exec("cmd /c yicesServer.cmd " + s_serverPort + " " + s_listenPort);
      // wait for the command batch file to start the java process
      long waited = 0;
      while (!isServerProcAlive() && waited < 10000) {
        Thread.sleep(20);
        waited += 20;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void terminateServerProc() {
    // cannot use ordinary process destroy() to terminate
    if (isServerProcAlive()) {
      // send termination signal
      sendCommand("terminate!");
      
      // wait for termination
      long waited = 0;
      while (isServerProcAlive() && waited < 10000) {
        try {
          Thread.sleep(20);
          waited += 20;
        } catch (InterruptedException e) {}
      }
    }
  }
  
  private static boolean isServerProcAlive() {
    // cannot use ordinary process exitValue() to check
    return !isPortAvailable(s_serverPort);
  }
  
  private static boolean isThreadListening() {
    return !isPortAvailable(s_listenPort);
  }
  
  private static boolean isPortAvailable(int port) {
    // cannot use ordinary process exitValue() to check
    boolean available = false;
    try {
      DatagramSocket socket = new DatagramSocket(port);
      socket.close();
      available = true;
    } catch (Exception e) {}
    return available;
  }
  
  private static void sendCommand(String command) {
    DatagramSocket socket = null;
    try {
      socket = new DatagramSocket();
      DatagramPacket packet = new DatagramPacket(command.getBytes(), 
          command.getBytes().length, InetAddress.getByName("localhost"), s_serverPort);
      socket.send(packet);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (socket != null) {
        socket.close();
      }
    }
  }
  
  private static class ListenThread extends Thread {
    private ListenThread(int listenPort) {
      m_listenPort    = listenPort;
      m_receiveBuffer = new byte[51200];
      m_mainThread    = Thread.currentThread();
    }
    
    private void listen() {
      m_socket = null;
      try {
        m_socket = new DatagramSocket(m_listenPort);
        m_socket.setSoTimeout(5000);
        DatagramPacket packet = new DatagramPacket(m_receiveBuffer, m_receiveBuffer.length);
        
        m_lastReceived = null;
        while (m_mainThread.isAlive()) {
          try {
            m_socket.receive(packet);
          } catch (SocketTimeoutException e) {}
          if (packet.getLength() > 0) {
            m_lastReceived = new String(packet.getData(), 0, packet.getLength());
          }
        }

      } catch (Exception e) {
        //e.printStackTrace();
      } finally {
        closeSocket();
      }
    }
    
    public void run() {
      listen();
    }
    
    public void closeSocket() {
      if (m_socket != null) {
        m_socket.close();
      }
    }
    
    public void resetLastReceived() {
      m_lastReceived = null;
    }
    
    public String getLastReceived() {
      return m_lastReceived;
    }

    private DatagramSocket m_socket;
    private String         m_lastReceived;
    private final int      m_listenPort;
    private final byte[]   m_receiveBuffer;
    private Thread         m_mainThread;
  }
  
  private static class ShutdownThread extends Thread {
    private ShutdownThread() {
    }
    
    public void run() {
      terminateServerProc();
    }
  }
  
  private String m_lastInput;
  private String m_lastOutput;

  //private static Process      s_serverProc;
  private static ListenThread s_listenThread;
  
  private static int s_listenPort;
  private static int s_serverPort;
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
