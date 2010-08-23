package edu.mit.csail.pag.objcap.tests;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Random;
import java.util.Set;
import java.util.Stack;

import edu.mit.csail.pag.objcap.ObjCapture;

public class Sample1 {
	int x;

	File f = new File("/tmp");

	int y;
	
	private int prv=0;
	
	private Stack stk = null;

	public String toString() {
		return this.getClass() + " x: " + x + " y: " + y + " f " + f + " prv = " + prv;
	}

	public void foo(int x) {
		this.x = x;
		stk = new Stack();
		stk.push("Hi");
		stk.push("This is test stack");
		ObjCapture.store(this);
	}

	public static void main(String args[]) throws IOException {
		Random rand = new Random(new Date().getTime());
		Sample1 s1 = new Sample1();
		s1.foo(rand.nextInt());

		ObjCapture.writeObjTreeToFile();

		System.out.println("Reading captured objects");

		Set<Object> objSet = ObjCapture.get(Sample1.class, 100);
		for (Object obj : objSet) {
			System.out.println(obj);
		}

		System.out.println("Reading captured objects2");

		int objectCount = ObjCapture.getObJectCount(Sample1.class);
		System.out.println("We have " + objectCount + " objects");
		for (int i = 0; i < objectCount; i++) {
			Object obj = ObjCapture.getObjectAtIndex(Sample1.class, i);
			System.out.println(obj);
		}
	}
}
