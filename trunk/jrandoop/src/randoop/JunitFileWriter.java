package randoop;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import randoop.ocat.OCATGlobals;
import randoop.util.CollectionsExt;
import randoop.util.Log;

/**
 * Outputs a collection of sequences as Java files, using the JUnit framework,
 * with one method per sequence.
 */
public class JunitFileWriter {

	// The class of the main JUnit suite, and the prefix of the subsuite names.
	private String junitDriverClassName;

	// The package name of the main JUnit suite
	private String packageName;

	// The directory where the JUnit files should be written to.
	private String dirName;

	public static boolean includeParseableString = false;

	private int testsPerFile;
	private boolean bObjectCaptured = false;

	private Map<String, List<List<ExecutableSequence>>> createdSequencesAndClasses = new LinkedHashMap<String, List<List<ExecutableSequence>>>();

	public JunitFileWriter(String junitDirName, String packageName,
			String junitDriverClassName, int testsPerFile,
			boolean bObjectCaptured) {
		this.dirName = junitDirName;
		this.packageName = packageName;
		this.junitDriverClassName = junitDriverClassName;
		this.testsPerFile = testsPerFile;
		this.bObjectCaptured = bObjectCaptured;
	}

	/**
	 * Creates Junit tests for the faults. Output is a set of .java files.
	 */
	public List<File> createJunitTestFiles(List<ExecutableSequence> sequences,
			String junitTestsClassName) {
		if (sequences.size() == 0) {
			System.out
					.println("No sequences given to createJunitFiles. No Junit class created.");
			return new ArrayList<File>();
		}

		// Create the output directory.
		File dir = getDir();
		if (!dir.exists()) {
			boolean success = dir.mkdirs();
			if (!success) {
				throw new Error("Unable to create directory: "
						+ dir.getAbsolutePath());
			}
		} else {
			// JJ
			File[] files = dir.listFiles();
			for (int i = 0; i < files.length; i++) {
				if (files[i].toString().contains("RandoopTest"))
					files[i].delete();
			}
		}

		List<File> ret = new ArrayList<File>();
		List<List<ExecutableSequence>> subSuites = CollectionsExt
				.<ExecutableSequence> chunkUp(
						new ArrayList<ExecutableSequence>(sequences),
						testsPerFile);
		for (int i = 0; i < subSuites.size(); i++) {
			ret.add(writeSubSuite(subSuites.get(i), i, junitTestsClassName));
		}
		createdSequencesAndClasses.put(junitTestsClassName, subSuites);
		return ret;
	}

	/**
	 * Creates Junit tests for the faults. Output is a set of .java files.
	 * 
	 * the default junit class name is the driver class name + index
	 */
	public List<File> createJunitTestFiles(List<ExecutableSequence> sequences) {
		return createJunitTestFiles(sequences, junitDriverClassName);
	}

	/** create both the test files and the drivers for convinience * */
	public List<File> createJunitFiles(List<ExecutableSequence> sequences,
			List<Class<?>> allClasses) {
		List<File> ret = new ArrayList<File>();
		ret.addAll(createJunitTestFiles(sequences));
		ret.add(writeDriverFile(allClasses));
		return ret;
	}

	/** create both the test files and the drivers for convinience * */
	public List<File> createJunitFiles(List<ExecutableSequence> sequences) {
		List<File> ret = new ArrayList<File>();
		ret.addAll(createJunitTestFiles(sequences));
		ret.add(writeDriverFile());
		return ret;
	}

	private File writeSubSuite(List<ExecutableSequence> sequencesForOneFile,
			int i, String junitTestsClassName) {
		String className = junitTestsClassName + i;
		File file = new File(getDir(), className + ".java");
		PrintStream out = createTextOutputStream(file);

		boolean bCheckException = false;

		try {
			outputPackageName(out);
			out.println();
			out.println("import junit.framework.*;");
			if (bObjectCaptured)
				out.println("import edu.mit.csail.pag.objcap.util.*;");

			out.println();
			out.println("public class " + className + " extends TestCase {");
			out.println();
			writeMain(out, className);
			out.println();
			int testCounter = 1;
			for (ExecutableSequence fault : sequencesForOneFile) {
				if (includeParseableString) {
					out.println("/*");
					out.println(fault.sequence.toString());
					out.println("*/");
				}

				bCheckException = false;
				if (OCATGlobals.targetException != null
						&& OCATGlobals.targetException.length() > 0
						&& fault.isNormalExecution())
				{
					bCheckException = true;
				}

				out.println("  public void test" + testCounter++
						+ "() throws Throwable {");
				out.println();
				if (bCheckException)
					out.println("    try {");
				out.println();
				out.println(indent(fault.toCodeString()));
				out.println();

				if (bCheckException) {
					out.println("    } catch (Exception e) {");
					out
							.println("      // Check this exception is a target exception");
					out.println("      RandoopTest.checkException(e);");
					out.println("    }");
				}
				out.println("  }");
				out.println();
			}
			out.println("}");
		} finally {
			if (out != null)
				out.close();
		}

		return file;
	}

	private String indent(String codeString) {
		if (codeString == null)
			return "";
		StringBuilder indented = new StringBuilder();
		String[] lines = codeString.split(Globals.lineSep);
		for (String line : lines) {
			indented.append("    " + line + Globals.lineSep);
		}
		return indented.toString();
	}

	private void writeMain(PrintStream out, String className) {
		out.println("  // Runs all the tests in this file.");
		out.println("  public static void main(String[] args) {");
		out
				.println("    junit.textui.TestRunner.run(" + className
						+ ".class);");
		out.println("  }");
	}

	private void writeCheckException(PrintStream out) {
		if (OCATGlobals.targetException == null)
			return;
		if (OCATGlobals.targetException.length() <= 0)
			return;

		out.println("  // check the target exception has been occured.");
		out
				.println("  public static void checkException(Exception exception) {");
		out.println("    // 1. check whether the exception mode is on");
		out.println("    // 2. check this sequence threw an exception");
		out.println("    String output = \"\";");
		out
				.println("    String exceptionClassName = exception.getClass().getCanonicalName();");
		out.println("    if (exceptionClassName.equals(\""
				+ OCATGlobals.targetException + "\") {");
		out
				.println("        StackTraceElement[] ste = exception.getStackTrace();");
		out.println("");
		out.println("        boolean matched = true;");
		out.println("        String targetste[] = {\""
				+ OCATGlobals.stackTrace1 + "\",");
		out.println("           \"" + OCATGlobals.stackTrace2 + "\", \""
				+ OCATGlobals.stackTrace3 + "\",");
		out.println("           \"" + OCATGlobals.stackTrace4 + "\", \""
				+ OCATGlobals.stackTrace5 + "\" };");
		out.println("");
		out.println("        for (int i = 0; i < targetste.length; i++) {");
		out.println("            if (targetste[i] != null) {");
		out
				.println("                if (!targetste[i].equals(ste[i].toString()))");
		out.println("                    matched = false;");
		out.println("                } else {");
		out.println("                    matched = false;");
		out.println("                    break;");
		out.println("                }");
		out.println("            }");
		out.println("");
		out.println("        if (matched) {");
		out.println("            output += \"\" + \"\\r\\n\";");
		out
				.println("            output += \"###Type0 Exception Pefectly Matched \" + \"\\r\\n\";");
		out
				.println("            output += \"# Exception class: \" + exception.getClass() + \"\\r\\n\";");
		out.println("            for (int i = 0; i < targetste.length; i++)");
		out
				.println("                output += \"# StackTrace\" + i + \": \" + targetste[i]\"\\r\\n\";");
		out.println("        } else {");
		out.println("            boolean onematched = false;");
		out.println("            output += \"\" + \"\\r\\n\";");
		out
				.println("            output += \"@@@ the same exception occured: \"");
		out.println("            + exception.getClass() + \"\\r\\n\";");
		out.println("");
		out.println("            for (int i = 0; i < ste.length; i++) {");
		out
				.println("                output += \"@ StackTrace\" + i + \": \" + ste[i] + \"\\r\\n\";");
		out
				.println("                for (int j = 0; j < targetste.length; j++) {");
		out
				.println("                    if (ste[i].toString().equals(targetste[j])) {");
		out.println("                        onematched = true;");
		out
				.println("                        output += \"###Type\" + (j + 1) + \" matched: \"");
		out.println("                        + targetste[j] + \"\\r\\n\";");
		out.println("                    }");
		out.println("                }");
		out.println("            }");
		out.println("");
		out.println("            if (!onematched) {");
		out
				.println("                output += \"@@@ only the same exception has been occured, but the location is different.\\r\\n\";");
		out.println("            } else");
		out
				.println("                output += \"@@@ exception has been found.\" + \"\\r\\n\";");
		out.println("");
		out.println("            output += \"\" + \"\\r\\n\";");
		out.println("        }");
		out.println("        System.out.println(output);");
		out.println("    }");
		out.println("  }");
	}

	private void outputPackageName(PrintStream out) {
		boolean isDefaultPackage = packageName.length() == 0;
		if (!isDefaultPackage)
			out.println("package " + packageName + ";");
	}

	public File writeDriverFile() {
		return writeDriverFile(Collections.<Class<?>> emptyList(),
				junitDriverClassName);
	}

	public File writeDriverFile(List<Class<?>> allClasses) {
		return writeDriverFile(allClasses, junitDriverClassName);
	}

	/**
	 * Creates Junit tests for the faults. Output is a set of .java files.
	 * 
	 * @param allClasses
	 *            List of all classes of interest (this is a workaround for emma
	 *            missing problem: we want to compute coverage over all classes,
	 *            not just those that happened to have been touched during
	 *            execution. Otherwise, a bad suite can report good coverage.
	 *            The trick is to insert code that will load all those classes;
	 */
	public File writeDriverFile(List<Class<?>> allClasses,
			String driverClassName) {
		File file = new File(getDir(), driverClassName + ".java");
		PrintStream out = createTextOutputStream(file);
		try {
			outputPackageName(out);
			out.println("import junit.framework.*;");
			out.println("import junit.textui.*;");
			out.println("");
			out.println("public class " + driverClassName
					+ " extends TestCase {");
			out.println("");
			out.println("  public static void main(String[] args) {");
			out.println("    TestRunner runner = new TestRunner();");
			out
					.println("    TestResult result = runner.doRun(suite(), false);");
			out.println("    if (! result.wasSuccessful()) {");
			out.println("      System.exit(1);");
			out.println("    }");
			out.println("  }");
			out.println("");
			out.println("  public " + driverClassName + "(String name) {");
			out.println("    super(name);");
			out.println("  }");
			out.println("");
			out.println("  public static Test suite() {");
			out.println("    TestSuite result = new TestSuite();");
			for (String junitTestsClassName : createdSequencesAndClasses
					.keySet()) {
				int numSubSuites = createdSequencesAndClasses.get(
						junitTestsClassName).size();
				for (int i = 0; i < numSubSuites; i++)
					out.println("    result.addTest(new TestSuite("
							+ junitTestsClassName + i + ".class));");
			}
			out.println("    return result;");
			out.println("  }");
			writeCheckException(out);
			out.println("");
			out.println("}");
		} finally {
			if (out != null)
				out.close();
		}
		return file;
	}

	private File getDir() {
		File dir = null;
		if (dirName == null || dirName.length() == 0)
			dir = new File(System.getProperty("user.dir"));
		else
			dir = new File(dirName);
		if (packageName == null)
			return dir;
		packageName = packageName.trim(); // Just in case.
		if (packageName.length() == 0)
			return dir;
		String[] split = packageName.split("\\.");
		for (String s : split) {
			dir = new File(dir, s);
		}
		return dir;
	}

	private PrintStream createTextOutputStream(File file) {
		try {
			return new PrintStream(file);
		} catch (IOException e) {
			Log.out
					.println("Exception thrown while creating text print stream:"
							+ file.getName());
			e.printStackTrace();
			System.exit(1);
			return null;// make compiler happy
		}
	}
}
