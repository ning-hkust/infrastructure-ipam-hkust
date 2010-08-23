package randoop;

import java.io.FileWriter;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import randoop.main.GenInputsAbstract;
import randoop.ocat.OCATGlobals;
import randoop.util.CollectionsExt;
import randoop.util.ProgressDisplay;
import randoop.util.ReflectionExecutor.TimeoutExceeded;
import utilMDE.Invisible;
import utilMDE.Option;
import utilMDE.UtilMDE;
import cov.Branch;
import cov.Coverage;
import cov.CoverageAtom;

public class SequenceGeneratorStats implements Serializable {

	private static final long serialVersionUID = 1L;

	@Invisible
	@Option("Output verbose statistics during generation.")
	public static boolean verbose_stats = false;

	@Invisible
	@Option("Output result data into a csv file format.")
	public static FileWriter csvout = null;
	
	protected final Map<String, Integer> exceptionTypes;

	public final Set<Branch> branchesCovered;

	private final Map<StatementKind, StatsForMethod> methodStats;
	
	private class ClassCoverage{
		public String className ="";
		public long branchTotal = 0;
		public long branchCovered = 0;
		public ClassCoverage(String className) {
			super();
			this.className = className;
		}
		
	}
	
	private final Map<String, ClassCoverage> classStats;
	
	private final StatsForMethod globalStats;

	private List<StatName> keys = new ArrayList<StatName>();

	public static final StatName STAT_BRANCHTOT = new StatName(
			"TOTAL NUMBER OF BRANCHES IN METHOD", "Brtot",
			"Total number of branches in method", false);

	public static final StatName STAT_BRANCHCOV = new StatName("BRANCHES",
			"Brcov", "Number of branches covered in method", true);

	public static final StatName STAT_BRANCHCOVP = new StatName("BRANCHES%",
			"Brcov%", "Percent of branches covered", true);
	
	public static final StatName STAT_SELECTED = new StatName("SELECTED",
			"Select", "Selected method to create new sequence.", verbose_stats);

	private static final StatName STAT_DID_NOT_FIND_INPUT_ARGUMENTS = new StatName(
			"DID NOT FIND SEQUENCE ARGUMENTS",
			"NoArgs",
			"Did not create a new sequence: could not find components that create sequence argument types.",
			verbose_stats);

	private static final StatName STAT_DID_NOT_FIND_INPUT_ARGUMENTS_CYCLE = new StatName(
			"DID NOT FIND SEQUENCE ARGUMENTS DUE TO A CYCLE",
			"NoArgsC",
			"Did not create a new sequence: could not find components that create sequence argument types due to a dependency cycle between the ctors.",
			verbose_stats);

	private static final StatName STAT_DISCARDED_SIZE = new StatName(
			"DISCARDED (EXCEEDS SIZE LIMIT)",
			"TooBig",
			"Did not create a new sequence: sequence exceeded maximum allowed size.",
			verbose_stats);

	private static final StatName STAT_DISCARDED_REPEATED = new StatName(
			"DISCARDED (ALREADY CREATED SEQUENCE)", "Repeat",
			"Did not create a new sequence: sequence was already created.",
			verbose_stats);
	
	public static final StatName STAT_NOT_DISCARDED = new StatName(
			"DID NOT DISCARD", "NewCase", "Created a new test input.", true );

	public static final StatName STAT_NOT_DISCARDED_MAX = new StatName(
			"DID NOT DISCARD MAX", "SelOKMax", "Created a new test input.", false);

	private static final StatName STAT_SEQUENCE_STOPPED_EXEC_BEFORE_LAST_STATEMENT = new StatName(
			"STAT_SEQUENCE_STOPPED_EXEC_BEFORE_LAST_STATEMENT",
			"Abort",
			"Execution outcome 1 (of 3): stopped before last statement (due to exception or contract violation).",
			verbose_stats);

	private static final StatName STAT_SEQUENCE_EXECUTED_NORMALLY = new StatName(
			"STAT_SEQUENCE_EXECUTED_NORMALLY",
			"NoEx",
			"Execution outcome 2 (of 3): executed to the end and threw no exceptions.",
			verbose_stats);

	private static final StatName STAT_SEQUENCE_OTHER_EXCEPTION_LAST_STATEMENT = new StatName(
			"STAT_SEQUENCE_OTHER_EXCEPTION_LAST_STATEMENT",
			"Excep",
			"Execution outcome 3 (or 3): threw an exception when executing last statement.",
			verbose_stats);

	private static final StatName STAT_SEQUENCE_ADDED_TO_COMPONENTS = new StatName(
			"STAT_SEQUENCE_ADDED_TO_COMPONENTS", "Comp",
			"Post-execution outcome 1 (of 1): Added sequence to components.",
			verbose_stats);

	private static final StatName STAT_STATEMENT_EXECUTION_TIME = new StatName(
			"STAT_STATEMENT_EXECUTION_TIME", "Time",
			"Milliseconds spent executing statement (across all sequences).",
			verbose_stats);

	private static final StatName STAT_STATEMENT_EXCEPTION_OTHER = new StatName(
			"STAT_STATEMENT_EXCEPTION_OTHER", "OthEx",
			"Times statement threw non-VM exception (across all sequences).",
			verbose_stats);

	private static final StatName STAT_STATEMENT_EXCEPTION_RESOURCE_EXHAUSTION = new StatName(
			"STAT_STATEMENT_EXCEPTION_RESOURCE_EXHAUSTION",
			"VmEx",
			"Times statement threw VM exception, e.g. stack overflow (across all sequences).",
			verbose_stats);

	private static final StatName STAT_STATEMENT_EXCEPTION_TIMEOUT_EXCEEDED = new StatName(
			"STAT_STATEMENT_EXCEPTION_TIMEOUT_EXCEEDED",
			"Killed",
			"Times statement killed because it exceeded time allowed (across all sequences).",
			verbose_stats);

	private static final StatName STAT_STATEMENT_NORMAL = new StatName(
			"STAT_STATEMENT_NORMAL", "NoEx",
			"Times statement executed normally (across all sequences).",
			verbose_stats);

	
	public static final StatName STAT_ELAPSED_SECOND = new StatName(
			"STAT_ELAPSED_SECOND", "ElpTime",
			"Seconds spent generating sequence (across all sequences).", true /*
																				 * always
																				 * printable
																				 */);

	public double getClassCoverage(String className)
	{
		ClassCoverage clscov = classStats.get(className);
		if (clscov == null)
		{
			//System.out.println("No coverage data for " + className);
			return 1;
		}
		return clscov.branchCovered/clscov.branchTotal;
	}
	
	/*
	public long getClassNumInstances(String className)
	{
		ClassCoverage clscov = classStats.get(className);
		if (clscov == null)
		{
			//System.out.println("No coverage data for " + className);
			return 0;
		}
		return clscov.instaces;
	}
	
	public boolean addClassNumInstances(String className)
	{
		ClassCoverage clscov = classStats.get(className);
		if (clscov == null)
		{
			//System.out.println("No coverage data for " + className);
			return false;
		}
		clscov.instaces += 1;
		classStats.put(clscov.className, clscov);
		return true;
	}*/
	
	public SequenceGeneratorStats(List<StatementKind> statements,
			List<Class<?>> coverageClasses) {
		this.methodStats = new LinkedHashMap<StatementKind, StatsForMethod>();
		this.classStats = new LinkedHashMap<String, ClassCoverage>();
		this.globalStats = new StatsForMethod(new DummyStatement("Total"));
		for (StatementKind s : statements) {
			addStatement(s);
		}
		this.exceptionTypes = new LinkedHashMap<String, Integer>();
		this.branchesCovered = new LinkedHashSet<Branch>();
		addStats();

		// Setup STAT_BRANCHTOT for the coverage classes.
		for (Class<?> cls : coverageClasses) {
			ClassCoverage clscov = new ClassCoverage(cls.getName());
			Set<CoverageAtom> atoms = Coverage.getBranches(cls);
			if (atoms == null) continue;
			assert atoms != null : cls.toString();
			for (CoverageAtom ca : atoms) {
				Member member = Coverage.getMemberContaining(ca);
				if (member == null) {
					// Atom does not belong to method or constructor.
					// Add only to global stats.
					globalStats.addToCount(STAT_BRANCHTOT, 1);
					clscov.branchTotal += 1;
					continue;
				} 

				if (member instanceof Method) {
					// Atom belongs to a method.
					// Add to method stats (and implicitly, global stats).
					Method method = (Method) member;
					addToCount(RMethod.getRMethod(method), STAT_BRANCHTOT, 1);
					clscov.branchTotal += 1;
					continue;
				}

				// Atom belongs to a constructor.
				// Add to constructor stats (and implicitly, global stats).
				assert member instanceof Constructor<?> : member.toString();
				Constructor<?> cons = (Constructor<?>) member;
				addToCount(RConstructor.getRConstructor(cons), STAT_BRANCHTOT,
						1);
				clscov.branchTotal += 1;
			}
			
			if (clscov.branchTotal != 0)
				classStats.put(clscov.className, clscov);

		}
	}

	public StatsForMethod addStatement(StatementKind s) {
		if (s == null)
			throw new IllegalArgumentException("s cannot be null.");
		StatsForMethod st = new StatsForMethod(s);
		addKeys(st);
		this.methodStats.put(s, st);
		return st;
	}

	public boolean containsStatement(StatementKind statement) {
		return methodStats.containsKey(statement);
	}

	private void addKeys(StatsForMethod st) {
		for (StatName key : keys) {
			st.addKey(key);
		}
	}

	public StatsForMethod getGlobalStats() {
		return globalStats;
	}

	public StatsForMethod getStatsForStatement(StatementKind statement) {
		if (!containsStatement(statement)) {
			addStatement(statement);
		}
		StatsForMethod retval = methodStats.get(statement);
		if (retval == null)
			throw new IllegalArgumentException("No stats for statement:"
					+ statement + " Only:" + Globals.lineSep
					+ CollectionsExt.toStringInLines(methodStats.keySet()));
		return retval;
	}

	public void addToCount(StatName key, long value) {
		globalStats.addToCount(key, value);
	}

	public void addToCount(StatementKind statement, StatName key, long value) {
		if (!containsStatement(statement)) {
			addStatement(statement);
		}
		globalStats.addToCount(key, value);
		StatsForMethod s = methodStats.get(statement);
		if (s == null)
			throw new IllegalArgumentException("No stats for statement:"
					+ statement + " Only:" + Globals.lineSep
					+ CollectionsExt.toStringInLines(methodStats.keySet()));
		s.addToCount(key, value);
	}

	public void addKey(StatName newKey) {
		keys.add(newKey);
		globalStats.addKey(newKey);
		for (StatementKind s : methodStats.keySet()) {
			StatsForMethod st = methodStats.get(s);
			if (st == null)
				throw new IllegalArgumentException("No stats for statement:"
						+ s + " Only:" + Globals.lineSep
						+ CollectionsExt.toStringInLines(methodStats.keySet()));
			st.addKey(newKey);
		}
	}

	public String toStringGlobal() {
		return globalStats.toString();
	}

	public String keyExplanationString() {
		return globalStats.keyExplanationString();
	}

	@Override
	public String toString() {
		StringBuilder b = new StringBuilder();
		int counter = 0;
		for (Map.Entry<StatementKind, StatsForMethod> entry : methodStats
				.entrySet()) {
			if (counter++ % 5 == 0) {
				b.append(getTitle());
			}
			b.append(entry.getValue().toString());
			b.append(Globals.lineSep);
		}
		b.append(getTitle());
		b.append(globalStats.toString());
		b.append(Globals.lineSep);
		return b.toString();
	}

	public String getTitle() {
		return globalStats.getTitle();
	}

	public void addSeparator() {
		addKey(StatsForMethod.getSeparator());
	}

	public transient ProgressDisplay progressDisplay; // XXX make sure things
														// are ok when
														// deserializing.

	/** Kills the progress-display thread. */
	public void stopProgressDisplay() {
		if (!GenInputsAbstract.noprogressdisplay) {
			if (progressDisplay != null) {
				progressDisplay.shouldStop = true;
			}
		}
	}

	/** Starts the progress-display thread. */
	public void startProgressDisplay() {
		if (!GenInputsAbstract.noprogressdisplay) {
			progressDisplay = new ProgressDisplay(this,
					ProgressDisplay.Mode.MULTILINE, GenInputsAbstract.progressinterval, 200, csvout);
			progressDisplay.display();
			progressDisplay.start();
		}
	}

	public void printLegend() {
		System.out.println("STATISTICS KEY:");
		System.out.println(keyExplanationString());
	}

	private void addStats() {

		addKey(STAT_SELECTED);
		addKey(STAT_BRANCHTOT);
		addKey(STAT_BRANCHCOV);
		addKey(STAT_BRANCHCOVP);
		if (verbose_stats)
			addSeparator();
		addKey(STAT_DID_NOT_FIND_INPUT_ARGUMENTS);
		addKey(STAT_DID_NOT_FIND_INPUT_ARGUMENTS_CYCLE);
		addKey(STAT_DISCARDED_SIZE);
		addKey(STAT_DISCARDED_REPEATED);
		if (verbose_stats)
			addSeparator();
		addKey(STAT_NOT_DISCARDED);
		addKey(STAT_NOT_DISCARDED_MAX);
		addSeparator();
		addKey(STAT_SEQUENCE_STOPPED_EXEC_BEFORE_LAST_STATEMENT);
		addKey(STAT_SEQUENCE_EXECUTED_NORMALLY);
		addKey(STAT_SEQUENCE_OTHER_EXCEPTION_LAST_STATEMENT);
		if (verbose_stats)
			addSeparator();
		addKey(STAT_SEQUENCE_ADDED_TO_COMPONENTS);
		if (verbose_stats)
			addSeparator();
		addKey(STAT_STATEMENT_EXECUTION_TIME);
		if (verbose_stats)
			addSeparator();
		addKey(STAT_STATEMENT_NORMAL);
		addKey(STAT_STATEMENT_EXCEPTION_RESOURCE_EXHAUSTION);
		addKey(STAT_STATEMENT_EXCEPTION_OTHER);
		addKey(STAT_STATEMENT_EXCEPTION_TIMEOUT_EXCEEDED);
		if (verbose_stats)
			addSeparator();
		globalStats.addKey(STAT_ELAPSED_SECOND);
	}

	public void checkStatsConsistent() {
		StatsForMethod globalStats = getGlobalStats();
		if (globalStats.getCount(STAT_SELECTED) != globalStats
				.getCount(STAT_NOT_DISCARDED)
				+ globalStats.getCount(STAT_DID_NOT_FIND_INPUT_ARGUMENTS)
				+ globalStats.getCount(STAT_DID_NOT_FIND_INPUT_ARGUMENTS_CYCLE)
				+ globalStats.getCount(STAT_DISCARDED_REPEATED)
				+ globalStats.getCount(STAT_DISCARDED_SIZE)) {
			throw new BugInRandoopException();
		}
	}

	public void updateStatistics(ExecutableSequence es,
			Set<Branch> coveredBranches) {
		boolean coverageIncreased = false;
		// Update coverage information.
		for (Branch ca : coveredBranches) {
			ClassCoverage clscov = classStats.get(ca.className);
			assert(clscov != null);
			
			// This branch was already counted.
			if (branchesCovered.contains(ca))
				continue;

			coverageIncreased = true;
			branchesCovered.add(ca);
			
			long covp = 0;
			if (globalStats.getCount(STAT_BRANCHTOT) != 0)
			  covp = (long)( globalStats.getCount(STAT_BRANCHCOV)* 1000 / globalStats.getCount(STAT_BRANCHTOT) ); 
			globalStats.setCount(STAT_BRANCHCOVP, covp);
			
			Member member = Coverage.getMemberContaining(ca);
			if (member == null) {
				// Atom does not belong to method or constructor.
				// Add only to global stats.
				globalStats.addToCount(STAT_BRANCHCOV, 1);
				clscov.branchCovered += 1;
				classStats.put(clscov.className, clscov);
				continue;
			}

			if (member instanceof Method) {
				// Atom belongs to a method.
				// Add to method stats (and implicitly, global stats).
				Method method = (Method) member;
				addToCount(RMethod.getRMethod(method), STAT_BRANCHCOV, 1);
				clscov.branchCovered += 1;
				classStats.put(clscov.className, clscov);
				continue;
			}

			// Atom belongs to a constructor.
			// Add to constructor stats (and implicitly, global stats).
			assert member instanceof Constructor<?> : member.toString();
			Constructor<?> cons = (Constructor<?>) member;
			addToCount(RConstructor.getRConstructor(cons), STAT_BRANCHCOV, 1);
			clscov.branchCovered += 1;
			classStats.put(clscov.className, clscov);
		}

		if (!coverageIncreased && OCATGlobals.JJ_reduction && OCATGlobals.JJ_covbase) {
			es.sequence.redundantByCov = true;
		}

		for (int i = 0; i < es.sequence.size(); i++) {
			StatementKind statement = es.sequence.getStatementKind(i);

			ExecutionOutcome o = es.getResult(i);

			if (!(statement instanceof RMethod || statement instanceof RConstructor)) {
				continue;
			}

			if (o instanceof NotExecuted) {
				// We don't record this fact (it's not interesting at the
				// statement-level, because
				// a statement not being executed is unrelated to the statement.
				// (It's often due to a previous statement throwing an
				// exception).
				continue;
			}

			addToCount(statement, STAT_STATEMENT_EXECUTION_TIME, o
					.getExecutionTime());

			if (o instanceof NormalExecution) {
				addToCount(statement, STAT_STATEMENT_NORMAL, 1);
				continue;
			}

			assert o instanceof ExceptionalExecution;
			ExceptionalExecution exc = (ExceptionalExecution) o;

			Class<?> exceptionClass = exc.getException().getClass();
			Integer count = exceptionTypes.get(exceptionClass);
			exceptionTypes.put(exceptionClass.getPackage().toString() + "."
					+ exceptionClass.getSimpleName(), count == null ? 1 : count
					.intValue() + 1);

			if (exc.getException() instanceof StackOverflowError
					|| exc.getException() instanceof OutOfMemoryError) {
				addToCount(statement,
						STAT_STATEMENT_EXCEPTION_RESOURCE_EXHAUSTION, 1);

			} else if (exc.getException() instanceof TimeoutExceeded) {
				addToCount(statement,
						STAT_STATEMENT_EXCEPTION_TIMEOUT_EXCEEDED, 1);
			} else {
				addToCount(statement, STAT_STATEMENT_EXCEPTION_OTHER, 1);
			}
		}

		StatementKind statement = es.sequence.getLastStatement();
		// if(statement instanceof MethodCall && !statement.isVoidMethod()) {
		// MethodCall sm = ((MethodCall)statement);
		// statement = MethodCall.getDefaultStatementInfo(sm.getMethod());
		// }
		if (es.hasNonExecutedStatements()) {
			addToCount(statement,
					STAT_SEQUENCE_STOPPED_EXEC_BEFORE_LAST_STATEMENT, 1);
			return;
		}

		ExecutionOutcome o = es.getResult(es.sequence.size() - 1);

		if (o instanceof ExceptionalExecution) {
			addToCount(statement, STAT_SEQUENCE_OTHER_EXCEPTION_LAST_STATEMENT,
					1);
			return;
		}

		assert o instanceof NormalExecution;
		addToCount(statement, STAT_SEQUENCE_EXECUTED_NORMALLY, 1);
	}

	public void statStatementSelected(StatementKind statement) {
		addToCount(statement, STAT_SELECTED, 1);
	}

	public void statStatementRepeated(StatementKind statement) {
		addToCount(statement, STAT_DISCARDED_REPEATED, 1);
	}

	public void statStatementToBig(StatementKind statement) {
		addToCount(statement, STAT_DISCARDED_SIZE, 1);
	}

	public void statStatementNoArgs(StatementKind statement) {
		addToCount(statement, STAT_DID_NOT_FIND_INPUT_ARGUMENTS, 1);
	}

	public void statStatementNoArgsCycle(StatementKind statement) {
		addToCount(statement, STAT_DID_NOT_FIND_INPUT_ARGUMENTS_CYCLE, 1);
	}

	public void statStatementNotDiscarded(StatementKind statement) {
		addToCount(statement, STAT_NOT_DISCARDED, 1);
		long curCnt = getStatsForStatement(statement).getCount(STAT_NOT_DISCARDED);
		globalStats.SetMaxCount(STAT_NOT_DISCARDED_MAX, curCnt);
		//curCnt = globalStats.getCount(STAT_NOT_DISCARDED_MAX);
	}

	public void printStatistics() {
		// TODO make this printout optional - it's too overwhelming
		System.out.println(Globals.lineSep + "Stats:" + Globals.lineSep
				+ toString());

		System.out.println(Globals.lineSep + "Exceptions thrown:");
		for (Map.Entry<String, Integer> e : exceptionTypes.entrySet()) {
			System.out.println("   " + UtilMDE.rpad(e.getValue().toString(), 8)
					+ " of " + e.getKey().toString());
		}
	}

}
