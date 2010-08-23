package edu.mit.csail.pag.objcap.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

/**
 * A utility class
 * 
 * @author hunkim
 */
public class Util {
	// TODO: need to make this path in configure file or something
	public static final int MAX_STACK_TRACE_TO_WRITE = 100;

	private static final String JAVA_LANG = "java.lang.";

	private static String tempdir = null;

	private static final List<String> nonTransformedPrefixes = Arrays
			.asList(new String[] { "java/", "com/sun/", "javax/", "sun/",
					"edu/mit/csail/pag/recrash/", "edu/mit/csail/pag/objcap/",
					"com/thoughtworks/xstream/", "org/xmlpull/" });

	public static String makeArgumentName(int argOrder) {
		if (argOrder == 0) {
			return "this";
		}

		return "arg_" + argOrder;
	}

	/**
	 * transform class name from a/b/c/ -> a.b.c. public GetClass() { }
	 * transform class name from a.b.c. -> a/b/c/
	 * 
	 * @param className
	 * @return
	 */
	@Deprecated
	public static String transClassName(String className) {
		if (className.indexOf('.') != -1) {
			return className.replace('.', '/');
		}

		if (className.indexOf('/') != -1) {
			return className.replace('/', '.');
		}

		return className;
	}

	public static String transClassNameDotToSlash(String name) {
		if (name == null)
			return null;
		return name.replace('.', '/');
	}

	public static String transClassNameSlashToDot(String name) {
		if (name == null)
			return null;
		return name.replace('/', '.');
	}

	public static String getShortClassName(String className) {
		if (className == null) {
			return null;
		}

		// Remove 'java.lang.'
		if (className.startsWith(JAVA_LANG)) {
			return className.substring(JAVA_LANG.length());
		}
		return className;
	}

	/**
	 * return system tmp directory
	 * 
	 * @return
	 */
	public static String getTmpDirectory() {
		if (tempdir != null) {
			return tempdir;
		}

		tempdir = System.getProperty("java.io.tmpdir");
		if (!(tempdir.endsWith("/") || tempdir.endsWith("\\"))) {
			tempdir = tempdir + System.getProperty("file.separator");
		}

		return tempdir;
	}

	public static boolean shouldInstrumentThisClass(String className) {
		for (String p : nonTransformedPrefixes) {
			if (className.startsWith(p)) {
				Logger
						.info("Skip, since it is part of nonTransformedPrefixes: "
								+ className);
				return false;
			}
		}

		return true;
	}

	/**
	 * remove java.lang part to make a short type name. If it includes "$"
	 * repace it to ".".
	 * 
	 * @param argumentTypeName
	 * @return
	 */
	public static String getShortTypeName(String argumentTypeName) {
		String ret = argumentTypeName.replace('$', '.');
		if (ret.startsWith("java.lang.")) {
			ret = ret.substring("java.lang.".length());
		}
		return ret;
	}

	/**
	 * Copy a given file to out
	 * 
	 * @param in
	 * @param out
	 * @return
	 * @throws IOException
	 */
	public static int copyFile(File inFile, File outFile) throws IOException {
		FileInputStream in = new FileInputStream(inFile);
		FileOutputStream out = new FileOutputStream(outFile);
		int size = copyStream(in, out);
		in.close();
		out.close();

		return size;
	}

	/**
	 * Copy a stream
	 * 
	 * @param in
	 * @param out
	 * @return
	 * @throws IOException
	 */
	public static int copyStream(InputStream in, OutputStream out)
			throws IOException {
		int size = 0;
		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;

		while ((len = in.read(buf)) > 0) {
			size += len;
			out.write(buf, 0, len);
		}

		return size;
	}
}
