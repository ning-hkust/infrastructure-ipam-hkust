package hk.ust.cse.YicesWrapper;


import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class YicesServer {
  
  public YicesServer(int listenPort, int respondPort) throws Exception {
    m_listenPort  = listenPort;
    m_respondPort = respondPort;
  }

  public void start() {
    DatagramSocket socket = null;
    try {
      socket = new DatagramSocket(m_listenPort);
      socket.setSoTimeout(5000);
      byte[] receiveBuffer  = new byte[10485760]; // 10M
      DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);

      boolean terminateFlag = false;
      while (!terminateFlag && isClientListening()) {
        socket.receive(packet);
        if (packet.getLength() > 0) {
          String command = new String(packet.getData(), 0, packet.getLength());

          // received an termination signal
          if (command.equals("terminate!")) {
            terminateFlag = true;
            continue;
          }
          
          try {
            // call YicesWrapper directly
            boolean result = YicesWrapper.check(command);
            String output  = YicesWrapper.getLastOutput();
            String errMsg  = YicesWrapper.getLastErrorMsg();

            String response = null;
            if (output.length() > 0) { // SMT Check finished
              response = (result ? "sat:" : "unsat:") + output;
            }
            else if (errMsg.length() > 0) { // SMT Check throws error
              response = "error:" + errMsg;
            }
            else { // SMT Check timeout
              response = "timeout:";
            }
            sendResponse(response);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (socket != null) {
        socket.close();
      }
    }
  }
  
  private boolean isClientListening() {
    return !isPortAvailable(m_respondPort);
  }
  
  private boolean isPortAvailable(int port) {
    // cannot use ordinary process exitValue() to check
    boolean available = false;
    try {
      DatagramSocket socket = new DatagramSocket(port);
      socket.close();
      available = true;
    } catch (Exception e) {}
    return available;
  }
  
  private void sendResponse(String response) {
    DatagramSocket socket = null;
    try {
      socket = new DatagramSocket();
      DatagramPacket packet = new DatagramPacket(response.getBytes(), 
          response.getBytes().length, InetAddress.getByName("localhost"), m_respondPort);
      socket.send(packet);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (socket != null) {
        socket.close();
      }
    }
  }
  
  public static void main(String[] args) throws Exception {
    YicesServer server = new YicesServer(Integer.parseInt(args[0]), Integer.parseInt(args[1]));
    server.start();
  }
  
  private final int m_listenPort;
  private final int m_respondPort;
}
