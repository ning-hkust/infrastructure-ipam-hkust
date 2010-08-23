package postCreationTests;

import java.io.IOException;

import edu.mit.csail.pag.objcap.tests.Sample1;
import edu.mit.csail.pag.objcap.tests.Sample1Helper;
import edu.mit.csail.pag.randoopHelper.ObjCaptureWrapperCreator;
import edu.mit.csail.pag.utils.Files;
import junit.framework.TestCase;

public class WrapperTests extends TestCase{

	public void testWrapSimpleObjects() throws IOException {
		String output = ObjCaptureWrapperCreator.createClass(Sample1.class);
		System.out.println(output);
	
	
	}
	public void testWrapSimpleObjectsAndCompile() throws IOException {
		Class<?> clazz = Sample1.class;
		createClassFile(clazz);
	
	}
	private void createClassFile(Class<?> clazz) throws IOException {
		String output = ObjCaptureWrapperCreator.createClass(clazz);
		Files.writeToFile(output, "/tmp/" +clazz.getSimpleName() + "Helper"+ ".java");
		System.out.println(output);
	}
	
	public void testSample1() throws IOException {
		Sample1 sh = Sample1Helper.method0();
	}
}
