package edu.mit.csail.pag.objcap.sample_subjects;

import java.io.File;
import java.util.Hashtable;

import edu.mit.csail.pag.objcap.ObjCapture;

public class Sample {
	public void test(Sample o, File x, java.util.Hashtable y) {
		System.out.println("Test");
	}

	public static void main(String args[]) {
		new Sample().test(new Sample(), new File("X"), new Hashtable());
	}

}
