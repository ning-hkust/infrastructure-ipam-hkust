package edu.mit.csail.pag.randoopHelper;

import java.io.IOException;

import edu.mit.csail.pag.objcap.ObjCapture;


public class ObjCaptureWrapperCreator {
	
	public static boolean verbose = true;

	public static String createClass(Class<?> clazz) throws IOException {
		StringBuilder sb = new StringBuilder();
		String newClassName = clazz.getSimpleName() + "Helper";
		sb.append("package " + clazz.getPackage().getName() +";\n");
		sb.append("import java.io.IOException;\n");
		sb.append("import edu.mit.csail.pag.objcap.ObjCapture;\n");
		sb.append("import " + clazz.getName()+ ";\n");
		sb.append("public class " + newClassName + " {\n");
		int objectCount = ObjCapture.getObJectCount(clazz);
		for(int i = 0; i< objectCount; i++) {
			sb.append(createMethodForClass(clazz, i) + "\n");
		}
		sb.append("}");
		return sb.toString();
	}

	private static String createMethodForClass(Class<?> clazz, int i) {
		StringBuilder sb = new StringBuilder();
		sb.append("  public static " + clazz.getSimpleName() + " method" + i + "() throws IOException {\n");
		sb.append("    return ("+clazz.getSimpleName() + ") ObjCapture.getObjectAtIndex("+clazz.getSimpleName()+ ".class, "+i+");\n");
		sb.append("  }");
		return sb.toString();
	}
}
