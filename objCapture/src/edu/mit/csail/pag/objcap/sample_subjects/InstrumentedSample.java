package edu.mit.csail.pag.objcap.sample_subjects;

import java.io.File;

import edu.mit.csail.pag.objcap.ObjCapture;

/*@Annotation*/

public class InstrumentedSample {
	public void test(InstrumentedSample o, File x, java.util.Hashtable y) {
		ObjCapture.storeAll(new Object[] { o, x, y });

	}

	public void main(String args[]) {
		ObjCapture.writeObjTreeToFile();
	}

}
