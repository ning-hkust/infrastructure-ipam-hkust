package randoop.main;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import randoop.SequenceCollection;
import randoop.util.Randomness;
import randoop.util.Reflection;
import randoop.util.Util;
import utilMDE.Invisible;
import utilMDE.Option;
import utilMDE.Options;

public abstract class GenInputsAbstract extends CommandHandler {
	@Option("Check java.lang.Object contracts, e.g. equals(Object) is reflexive, hashCode() throws no exceptions, etc.")
	public static boolean check_object_contracts = true;

	@Invisible
	@Option("Create helper sequences.")
	public static boolean helpers = false;

	@Invisible
	@Option("Name of a file containing a serialized list of sequences.")
	public static List<String> componentfile_ser = new ArrayList<String>();

	@Invisible
	@Option("Name of a file containing a textual list of sequences.")
	public static List<String> componentfile_txt = new ArrayList<String>();

	// Set in main method. Component sequences to help bdgen.
	public static SequenceCollection components;

	@Invisible
	@Option("Print to the given file source files annotated with coverage information.")
	public static String covreport = null;

	@Invisible
	@Option("Output components (serialized, GZIPPED) to the given file. Suggestion: use a .gz suffix in file name.")
	public static String output_components = null;

	@Invisible
	@Option("(For regression testing) Output covered branches to the given text file.")
	public static String regression_test_branches = null;

	@Invisible
	@Option("Output a SequenceGenerationStats object to the given file.")
	public static String output_stats = null;

	@Invisible
	@Option("The name of a file containing the list of coverage-instrumented classes.")
	public static String coverage_instrumented_classes = null;

	@Invisible
	@Option("Output a DataFlowInput object to the given file.")
	public static String branchdir = null;

	@Invisible
	@Option("Output an ordered DataFlowInput (slower, for regression testing, meaningful only if --branchdir is given too).")
	public static boolean ordered_dfin = false;

	@Option("Specify the fully-qualified name of a class under test.")
	public static List<String> testclass = new ArrayList<String>();

	@Option("Specify the name of a file that contains a list of classes under test. Each"
			+ "class is specified by its fully qualified name on a separate line.")
	public static String classlist = null;

	@Option("Specify the name of a file that contains a list of methods under test. Each"
			+ "method is specified on a separate line.")
	public static String methodlist = null;

	@Option("Used to determine when to stop test generation. Generation stops when "
			+ "either the time limit (--timelimit=int) OR the input limit (--inputlimit=int) is reached. "
			+ "Note that the number of tests output may be smaller than then number of inputs "
			+ "created, because redundant and illegal inputs may be discarded.")
	public static int inputlimit = 1000000;

	@Option("Used to determine when to stop test generation. Generation stops when "
			+ "either the time limit (--timelimit=int) OR the input limit (--inputlimit=int) is reached.")
	public static int timelimit = 10000;

	@Option("Maximum number of tests to write to each JUnit file.")
	public static int testsperfile = 500;

	@Option("Name of the JUnit file containing Randoop-generated tests.")
	public static String junit_classname = "RandoopTest";

	@Option("Name of the directory to which JUnit files should be written.")
	public static String junit_output_dir = null;

	@Option("The random seed to use in the generation process")
	public static int randomseed = (int) Randomness.SEED;

	@Option("Do not generate tests with more than <int> statements")
	public static int maxsize = 200;

	@Option("Forbid Randoop to use null as input to methods. IMPORTANT: even if "
			+ "this option is set to true, null is only used if there is no non-null values "
			+ "available.") 
	public static boolean forbid_null = true;

	@Option("Use null once for each method ")
	public static boolean null_once = true;

	@Option("Use null with the given frequency. [TODO explain]")
	public static double null_ratio = 0.01;

	@Invisible
	@Option("Display progress every <int> seconds.")
	public static int progressinterval = 1;

	@Invisible
	@Option("Do not display progress.")
	public static boolean noprogressdisplay = false;

	@Invisible
	@Option("Minimize testclasses cases.")
	public static boolean minimize = true;

	@Invisible
	@Option("Create a file containing experiment results.")
	public static String experiment = null;

	@Invisible
	@Option("Do not do online redundancy checks.")
	public static boolean noredundancychecks = false;

	@Invisible
	@Option("Create sequences but never execute them.")
	public static boolean dontexecute = false;

	@Invisible
	@Option("Clear the active set when it reaches <int> inputs")
	public static int clear = Integer.MAX_VALUE;

	@Invisible
	@Option("Do not do online illegal.")
	public static boolean offline = false;

	@Invisible
	@Option("Do not exercise methods that match regular expresssion <string>")
	public static Pattern omitmethods = null;

	@Invisible
	@Option("Generate inputs but do not check any contracts")
	public static boolean dont_check_contracts = false;

	@Invisible
	@Option("TODO document.")
	public static boolean weighted_inputs = false;

	@Invisible
	@Option("TODO document.")
	public static boolean no_args_statement_heuristic = true;

	@Invisible
	@Option("Only generate inputs, do not test for errors.")
	public static boolean dontcheckcontracts;

	@Invisible
	@Option("Use heuristic that may randomly repeat a method call several times.")
	public static boolean repeat_heuristic = false;

	@Option("Run Randoop but do not create JUnit tests (used in research experiments).")
	public static boolean dont_output_tests = false;

	@Option("The name of a file containing the list of coverage-instrumented classes. JJ")
	public static String call_information = null;

	public GenInputsAbstract(String command, String pitch, String commandGrammar, String where,
			String summary, List<String> notes, String input, String output, String example,
			Options options) {
		super(command, pitch, commandGrammar, where, summary, notes, input, output, example,
				options);
	}

	List<Class<?>> findClassesFromArgs(Options printUsageTo) {
		List<Class<?>> classes = new ArrayList<Class<?>>();
		try {
			if (classlist != null) {
				File classListingFile = new File(classlist);
				classes.addAll(Reflection.loadClassesFromFile(classListingFile));
			}
			classes.addAll(Reflection.loadClassesFromList(testclass));
		} catch (Exception e) {
			String msg = Util.toNColsStr("ERROR while reading list of classes to test: "
					+ e.getMessage(), 70);
			System.out.println(msg);
			System.exit(1);
		}
		return classes;
	}
}
