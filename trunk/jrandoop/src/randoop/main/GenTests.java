package randoop.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import randoop.BugInRandoopException;
import randoop.CallInfo;
import randoop.ContractCheckingVisitor;
import randoop.EqualsToItself;
import randoop.EqualsToNull;
import randoop.ExecutableSequence;
import randoop.ExecutionVisitor;
import randoop.ForwardGenerator;
import randoop.Globals;
import randoop.HashCodeReturnsNormally;
import randoop.JunitFileWriter;
import randoop.MultiVisitor;
import randoop.ObjectContract;
import randoop.RConstructor;
import randoop.RMethod;
import randoop.RegressionCaptureVisitor;
import randoop.SeedSequences;
import randoop.Sequence;
import randoop.SequenceCollection;
import randoop.SequenceGeneratorStats;
import randoop.StatementKind;
import randoop.ToStringReturnsNormally;
import randoop.ocat.CapturedObjectSequences;
import randoop.ocat.OCATGlobals;
import randoop.util.DefaultReflectionFilter;
import randoop.util.Log;
import randoop.util.Randomness;
import randoop.util.Reflection;
import randoop.util.ReflectionExecutor;
import randoop.util.SerializationHelper;
import utilMDE.Option;
import utilMDE.Options;
import utilMDE.Options.ArgException;
import cov.Branch;
import cov.Coverage;

public class GenTests extends GenInputsAbstract {

	private static final String command = "gentests";

	private static final String pitch = "Generates unit tests for a set of classes.";

	private static final String commandGrammar = "gentests OPTIONS";

	private static final String where = "At least one class is specified via `--testclass' or `--classlist'.";

	private static final String summary = "Attempts to generate JUnit tests that "
			+ "capture the behavior of the classes under test and/or find contract violations. "
			+ "Randoop generates tests using feedback-directed random test generation. ";

	private static final String input = "One or more names of classes to test. A class to test can be specified "
			+ "via the `--testclass=<CLASSNAME>' or `--classlist=<FILENAME>' options.";

	private static final String output = "A JUnit test suite (as one or more Java source files). The "
			+ "tests in the suite will pass when executed using the classes under test.";

	private static final String example = "java randoop.main.Main gentests --testclass=java.util.Collections "
			+ " --testclass=java.util.TreeSet";

	private static final List<String> notes;

	static {

		notes = new ArrayList<String>();
		notes
				.add("Randoop executes the code under test, with no mechanisms to protect your system "
						+ " from harm resulting from arbitrary code execution. If random execution of "
						+ "your code could have undesirable effects (e.g. deletion of files, opening network"
						+ " connections, etc.) make sure you execute Randoop in a sandbox machine.");
		notes
				.add("Randoop will only use methods from the classes that you specify for testing. "
						+ "If Randoop is not generating tests for a particular method, make sure that "
						+ "you are including classes for the types that the method requires. Otherwise,"
						+ " Randoop may fail to generate tests due to missing input parameters.");
		notes
				.add("Randoop is designed to be deterministic when the code under test is itself deterministic."
						+ " This means that two runs of Randoop will generate the same tests. To get variation "
						+ "across runs, use the --randomseed option.");

	}

	@Option("Signals that this is a run in the context of a system test. (Slower)")
	public static boolean system_test_run = false;
	
	@Option("The path of capured objects")
	public static List<String> capturedObjectPath = new ArrayList<String>();


	private static Options options = new Options(Globals.class, GenTests.class,
			GenInputsAbstract.class, Log.class, ReflectionExecutor.class,
			ForwardGenerator.class, SequenceGeneratorStats.class, OCATGlobals.class);

	public GenTests() {
		super(command, pitch, commandGrammar, where, summary, notes, input,
				output, example, options);
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean handle(String[] args) throws RandoopTextuiException {

		// RandoopSecurityManager randoopSecurityManager = new
		// RandoopSecurityManager(
		// RandoopSecurityManager.Status.OFF);
		// System.setSecurityManager(randoopSecurityManager);

		try {
			String[] nonargs = options.parse(args);
			if (nonargs.length > 0)
				throw new ArgException("Unrecognized arguments: "
						+ Arrays.toString(nonargs));
		} catch (ArgException ae) {
			System.out
					.println("ERROR while parsing command-line arguments (will exit): "
							+ ae.getMessage());
			System.exit(-1);
		}

		// Modifies plans and testtime fields according to user arguments.
		for (String str : args) {
			System.out.println(str + " ");
		}
		System.out.println("Time limit=" + timelimit
				+ " seconds,Test input limit=" + inputlimit
				+ " inputs, Reducted input limit =" + OCATGlobals.reductionlimit
				+ " inputs.");
		Randomness.reset(randomseed);

		// Find classes to test.
		if (classlist == null && methodlist == null && testclass.size() == 0) {
			System.out
					.println("You must specify some classes or methods to test.");
			System.out
					.println("Use the --classlist, --testclass, or --methodlist options.");
			System.exit(1);
		}
		List<Class<?>> classes = findClassesFromArgs(options);

		List<StatementKind> model = Reflection.getStatements(classes,
				new DefaultReflectionFilter(omitmethods));

		// Always add Object constructor (it's often useful).
		try {
			RConstructor cons = RConstructor.getRConstructor(Object.class
					.getConstructor());
			if (!model.contains(cons))
				model.add(cons);

			/*
			 * //(JJ) Always add all classes' constructor for (Class<?> cls :
			 * classes) { cons = RConstructor.getRConstructor(cls.ge); if
			 * (!model.contains(cons)) model.add(cons); }
			 */
		} catch (Exception e) {
			throw new BugInRandoopException(e); // Should never reach here!
		}

		if (methodlist != null) {
			Set<StatementKind> statements = new LinkedHashSet<StatementKind>();
			try {
				for (Member m : Reflection
						.loadMethodsAndCtorsFromFile(new File(methodlist))) {
					if (m instanceof Method) {
						statements.add(RMethod.getRMethod((Method) m));
					} else {
						assert m instanceof Constructor<?>;
						statements.add(RConstructor
								.getRConstructor((Constructor<?>) m));
					}
				}
			} catch (IOException e) {
				System.out.println("Error while reading method list file "
						+ methodlist);
				System.exit(1);
			}
			for (StatementKind st : statements) {
				if (!model.contains(st))
					model.add(st);
			}
		}

		if (model.size() == 0) {
			Log.out.println("There are no methods to testclasses. Exiting.");
			System.exit(1);
		}
		System.out.println("Found " + model.size()
				+ " testable methods/constructors.");

		List<Class<?>> covClasses = new ArrayList<Class<?>>();
		if (coverage_instrumented_classes != null) {
			File covClassesFile = new File(coverage_instrumented_classes);
			try {
				covClasses = Reflection.loadClassesFromFile(covClassesFile);
			} catch (IOException e) {
				throw new Error(e);
			}
			for (Class<?> cls : covClasses) {
				assert Coverage.isInstrumented(cls) : cls.toString();
				System.out.println("Will track branch coverage for " + cls);
			}
		}

		CallInfo callinfo = null;
		if (OCATGlobals.JJ_covbase) {
			if (call_information != null) {
				File callInfoFile = new File(call_information);
				try {
					callinfo = new CallInfo(callInfoFile, model);
				} catch (IOException e) {
					throw new Error(e);
				}

			}
		}

		// Initialize components.
		components = new SequenceCollection();
		if (!componentfile_ser.isEmpty()) {
			for (String onefile : componentfile_ser) {
				try {
					FileInputStream fileos = new FileInputStream(onefile);
					ObjectInputStream objectos = new ObjectInputStream(
							new GZIPInputStream(fileos));
					Set<Sequence> seqset = (Set<Sequence>) objectos
							.readObject();
					System.out.println("Adding " + seqset.size()
							+ " component sequences from file " + onefile);
					components.addAll(seqset);
				} catch (Exception e) {
					throw new Error(e);
				}
			}
		}
		if (!componentfile_txt.isEmpty()) {
			for (String onefile : componentfile_txt) {
				Set<Sequence> seqset = Sequence.readTextSequences(onefile);
				System.out.println("Adding " + seqset.size()
						+ " component sequences from file " + onefile);
				components.addAll(seqset);
			}
		}
		components.addAll(SeedSequences
				.objectsToSeeds(SeedSequences.primitiveSeeds));
		
		//JJ
		boolean bObjCap = (capturedObjectPath.size() > 0);

		if (bObjCap) {
		  int size = components.size();
			components.addAll (CapturedObjectSequences.loadCapturedObjects(capturedObjectPath));
			System.out.println("Total loaded captured objects: " + (components.size() - size));
		}

		// add some random values
		if (OCATGlobals.JJ_ARTJJ && false)
			components.addAll(SeedSequences.RandomSeeds(5));

		System.out.println("Total starting objects: " + components.size());
		
		// Generate inputs.
		ForwardGenerator explorer = new ForwardGenerator(model, covClasses,
				timelimit * 1000, inputlimit, OCATGlobals.reductionlimit, components,
				callinfo, classes);

		// Determine what visitors to install.
		List<ExecutionVisitor> visitors = new ArrayList<ExecutionVisitor>();
		visitors.add(new RegressionCaptureVisitor());
		if (check_object_contracts) {
			List<ObjectContract> contracts = new ArrayList<ObjectContract>();
			contracts.add(new EqualsToItself());
			contracts.add(new EqualsToNull());
			contracts.add(new ToStringReturnsNormally());
			contracts.add(new HashCodeReturnsNormally());
			ContractCheckingVisitor contractVisitor = new ContractCheckingVisitor(
					contracts);
			visitors.add(contractVisitor);
		}
		explorer.executionVisitor = new MultiVisitor(visitors
				.toArray(new ExecutionVisitor[0]));

		try {
			explorer.explore();
		} catch (Throwable e) {

			System.out.println("Throwable thrown while handling command:" + e);
			e.printStackTrace();
			System.err.flush();

		}

		// Print branch coverage.
		System.out.println();
		// DecimalFormat format = new DecimalFormat("#.###");

		if (regression_test_branches != null) {
			Comparator<Branch> branchComparator = new Comparator<Branch>() {
				public int compare(Branch o1, Branch o2) {
					return o1.toString().compareTo(o2.toString());
				}
			};
			Set<Branch> branches = new TreeSet<Branch>(branchComparator);
			branches.addAll(explorer.stats.branchesCovered);
			// Create a file with branches, sorted by their string
			// representation.
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new FileWriter(
						regression_test_branches));
				// Touch all covered branches (they may have been reset during
				// generation).
				for (Branch b : branches) {
					writer.append(b.toString());
					writer.newLine();
				}
				writer.close();
			} catch (IOException e) {
				throw new Error(e);
			}
		}

		if (branchdir != null) {

			// Output frontier branches with witness sequences.
			System.out.println();
			System.out.println("Writing DF input...");
			try {

				Class<?> dfin = Class.forName("randoop.DataFlowInput");
				Method initFrontiers = dfin.getDeclaredMethod("initDF",
						ForwardGenerator.class, String.class, boolean.class);
				initFrontiers.invoke(null, explorer, branchdir, true);
				System.out.println("done.");
			} catch (Exception e) {
				throw new Error(e);
			}

			System.out.println("done.");
		}

		if (output_components != null) {
			// Output component sequences.
			System.out.print("Serializing component sequences...");
			try {
				FileOutputStream fileos = new FileOutputStream(
						output_components);
				ObjectOutputStream objectos = new ObjectOutputStream(
						new GZIPOutputStream(fileos));
				Set<Sequence> components = explorer.components
						.getAllSequences();
				System.out.println(" (" + components.size() + " components) ");
				objectos.writeObject(components);
				objectos.close();
				fileos.close();
			} catch (Exception e) {
				throw new Error(e);
			}
		}

		if (covreport != null) {
			// Create a file with coverage (temporary code).
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new FileWriter(covreport));
				// Touch all covered branches (they may have been reset during
				// generation).
				for (Branch br : explorer.stats.branchesCovered) {
					Coverage.touch(br);
				}
				for (Class<?> cls : covClasses) {
					for (String s : Coverage.getCoverageAnnotatedSource(cls)) {
						writer.append(s);
						writer.newLine();
					}
				}
				writer.close();
			} catch (IOException e) {
				throw new Error(e);
			}
		}

		if (output_stats != null) {
			SerializationHelper.writeSerialized(output_stats, explorer.stats);
		}

		if (dont_output_tests)
			return true;

		//List<ExecutableSequence> errorRevealingSequences;
		int countSkippedSeqs;

		// Create JUnit files containing faults.
		// reductionbyCov
		if (OCATGlobals.JJ_reduction) {
			System.out.println();
			System.out.print("Coverage-based Reduction: Creating Junit tests ("
					+ explorer.getSelectedSequences().size() + " tests)...");
			List<ExecutableSequence> errorRevealingSequences1 = new ArrayList<ExecutableSequence>();
			countSkippedSeqs = 0;
			for (ExecutableSequence p : explorer.getSelectedSequences()) {
				if (!p.sequence.redundantByCov)
					errorRevealingSequences1.add(p);
				else
					countSkippedSeqs++;

			}

			System.out
					.println("Coverage-based Reduction: Skipped sequences ("
							+ countSkippedSeqs
							+ " tests)/Unskipped("
							+ (explorer.getSelectedSequences().size() - countSkippedSeqs)
							+ " tests)...");

			JunitFileWriter jfw1 = new JunitFileWriter(junit_output_dir + "_covred",
					Globals.junit_package_name, junit_classname, testsperfile, bObjCap);
			List<File> files1 = jfw1.createJunitFiles(errorRevealingSequences1);
			System.out.println();
			for (File f : files1) {
				System.out.println("Created file: " + f.getAbsolutePath());
			}

			// --------------------------------------------------------------------
			System.out.println();
			System.out.print("Sequence-based Reduction: Creating Junit tests ("
					+ explorer.getSelectedSequences().size() + " tests)...");
			List<ExecutableSequence> errorRevealingSequences2 = new ArrayList<ExecutableSequence>();
			countSkippedSeqs = 0;
			for (ExecutableSequence p : explorer.getSelectedSequences()) {
				if (!p.sequence.redundantBySeq)
					errorRevealingSequences2.add(p);
				else
					countSkippedSeqs++;

			}

			System.out
					.println("Sequence-based Reduction: Skipped sequences ("
							+ countSkippedSeqs
							+ " tests)/Unskipped("
							+ (explorer.getSelectedSequences().size() - countSkippedSeqs)
							+ " tests)...");

			JunitFileWriter jfw2 = new JunitFileWriter(junit_output_dir + "_seqred",
					Globals.junit_package_name, junit_classname, testsperfile, bObjCap);
			List<File> files2 = jfw2.createJunitFiles(errorRevealingSequences2);
			System.out.println();
			for (File f : files2) {
				System.out.println("Created file: " + f.getAbsolutePath());
			}

			// --------------------------------------------------------------------
			System.out.println();
			System.out.print("Both Reduction: Creating Junit tests ("
					+ explorer.getSelectedSequences().size() + " tests)...");
			List<ExecutableSequence> errorRevealingSequences3 = new ArrayList<ExecutableSequence>();
			countSkippedSeqs = 0;
			for (ExecutableSequence p : explorer.getSelectedSequences()) {
				if (p.sequence.redundantBySeq || p.sequence.redundantByCov)
					countSkippedSeqs++;
				else
					errorRevealingSequences3.add(p);
			}

			System.out
					.println("Both Reduction: Skipped sequences ("
							+ countSkippedSeqs
							+ " tests)/Unskipped("
							+ (explorer.getSelectedSequences().size() - countSkippedSeqs)
							+ " tests)...");

			JunitFileWriter jfw3 = new JunitFileWriter(junit_output_dir + "_bothred",
					Globals.junit_package_name, junit_classname, testsperfile, bObjCap);
			List<File> files3 = jfw3.createJunitFiles(errorRevealingSequences3);
			System.out.println();
			for (File f : files3) {
				System.out.println("Created file: " + f.getAbsolutePath());
			}
		}

		System.out.println();
		System.out.print("No Reduction: Creating Junit tests ("
				+ explorer.getSelectedSequences().size() + " tests)...");
		List<ExecutableSequence> errorRevealingSequences4 = new ArrayList<ExecutableSequence>();
		countSkippedSeqs = 0;
		for (ExecutableSequence p : explorer.getSelectedSequences()) {
			errorRevealingSequences4.add(p);
		}

		System.out.println("No Reduction: Skipped sequences ("
				+ countSkippedSeqs + " tests)/Unskipped("
				+ (explorer.getSelectedSequences().size() - countSkippedSeqs)
				+ " tests)...");

		JunitFileWriter jfw4 = new JunitFileWriter(junit_output_dir + "_nored", Globals.junit_package_name,
				junit_classname, testsperfile, bObjCap);
		List<File> files4 = jfw4.createJunitFiles(errorRevealingSequences4);
		System.out.println();
		for (File f : files4) {
			System.out.println("Created file: " + f.getAbsolutePath());
		}

		return true;
	}
}
