package hk.ust.cse.Prevision_PseudoImpl;

public class StringBuffer {

  public StringBuffer() {
    count = 0;
    value = new char[16];
  }
  
  public StringBuffer(int i) {
    count = 0;
    value = new char[i];
  }
  
  public Object append(Object o) {
    return this;
  }
  
  public int capacity() {
    return value.length;
  }
  
  public String toString() {
    return "";
  }
  
  private int count;
  private char[] value;
}
