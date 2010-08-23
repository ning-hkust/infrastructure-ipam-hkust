package edu.mit.csail.pag.objcap.tests;
import java.io.IOException;
import edu.mit.csail.pag.objcap.ObjCapture;
import edu.mit.csail.pag.objcap.tests.Sample1;
public class Sample1Helper {
	public static void main(String args[]) throws IOException {
		method0();
		method1();
		method2();
		method3();
	}	
	
  public static Sample1 method0() throws IOException {
    return (Sample1) ObjCapture.getObjectAtIndex(Sample1.class, 0);
  }
  public static Sample1 method1() throws IOException {
    return (Sample1) ObjCapture.getObjectAtIndex(Sample1.class, 1);
  }
  public static Sample1 method2() throws IOException {
    return (Sample1) ObjCapture.getObjectAtIndex(Sample1.class, 2);
  }
  public static Sample1 method3() throws IOException {
    return (Sample1) ObjCapture.getObjectAtIndex(Sample1.class, 3);
  }
}