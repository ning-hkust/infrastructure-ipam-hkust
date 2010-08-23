package randoop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import randoop.main.GenInputsAbstract;
import randoop.ocat.OCATGlobals;
import randoop.util.ArrayListSimpleList;
import randoop.util.Log;
import randoop.util.ObjectDistance;
import randoop.util.PrimitiveTypes;
import randoop.util.Randomness;
import randoop.util.Reflection;
import randoop.util.SimpleList;
import randoop.util.Timer;
import randoop.util.Reflection.Match;
import utilMDE.Invisible;
import utilMDE.Option;
import utilMDE.Pair;
import cov.Branch;
import cov.Coverage;
import cov.CoverageAtom;

/**
 * Randoop's forward, component-based generator.
 */
public class ForwardGenerator {

	@Option("Print detailed statistics after generation.")
	public static boolean print_stats = false;

	@Invisible
	@Option("When branch coverage fails to increase for the given number of seconds (>0), stop generation.")
	public static int stop_when_plateau = -1;

	@Option("To force cobertura coverage data, input the gap of number of test cases. "
			+ " e.g. '500' will force to genenerate coverage data every 500 test cases")
	public static int coberturaForce = -1;

	private static final int NEW_ROUND_THRESHOLD = 100;

	private static final int ROUNDS = -1;

	public final Set<Sequence> allSequences;

	public final SequenceCollection components;

	private final List<ExecutableSequence> regressionSequences;

	private final Timer timer = new Timer();

	private final long timeMillis;

	private final int maxSequences;

	private final int maxReductedSeqs;

	// For testing purposes only. If Globals.randooptestrun==false then the
	// array
	// is never populated or queried. This set contains the same set of
	// components as the set "allsequences" above, but stores them as
	// strings obtained via the toCodeString() method.
	private final List<String> allsequencesAsCode = new ArrayList<String>();

	// For testing purposes only [[comment]]
	private final List<Sequence> allsequencesAsList = new ArrayList<Sequence>();

	public final Map<CoverageAtom, Set<Sequence>> branchesToCoveringSeqs = new LinkedHashMap<CoverageAtom, Set<Sequence>>();

	private static Set<Class<?>> instrumentedClassesCached = null;

	private CallInfo callinfo;

	public Set<Class<?>> getInstrumentedClasses() {
		if (instrumentedClassesCached == null) {
			instrumentedClassesCached = new LinkedHashSet<Class<?>>();
			for (Class<?> c : covClasses) {
				if (Coverage.isInstrumented(c))
					instrumentedClassesCached.add(c);
			}
		}
		return instrumentedClassesCached;
	}

	public final Set<CoverageAtom> branchesCovered = new LinkedHashSet<CoverageAtom>();

	protected ObjectCache objectCache = new ObjectCache();

	public void setObjectCache(ObjectCache newCache) {
		if (newCache == null)
			throw new IllegalArgumentException();
		this.objectCache = newCache;
	}

	public ExecutionVisitor executionVisitor;

	public List<StatementKind> statements;

	// JJ
	public List<StatementKind> constructors;
	public Map<Class<?>, List<RConstructor>> mapConstructors;
	public List<StatementKind> methods;

	public SequenceGeneratorStats stats;

	public List<Class<?>> covClasses;
	public List<Class<?>> classes;

	private boolean targetExceptionOccured = false;
	private boolean targetExceptionStasfied[] = { false, false, false, false,
			false };
	private List<StatementKind> prioritizedMethods;

	// private ArrayListSimpleList<StatementWithWeight> statementsWeight;

	// Sequence construction statistics
	// ================================

	public ForwardGenerator(List<StatementKind> statements,
			List<Class<?>> coverageClasses, long timeMillis, int maxSequences,
			int maxReductedSeqs, SequenceCollection startingcomponents,
			CallInfo callinfo, List<Class<?>> classes) {

		this.callinfo = callinfo;
		this.timeMillis = timeMillis;
		this.maxSequences = maxSequences;
		this.maxReductedSeqs = maxReductedSeqs;
		this.regressionSequences = new ArrayList<ExecutableSequence>();
		this.statements = statements;
		if (coverageClasses == null)
			this.covClasses = new ArrayList<Class<?>>();
		else
			this.covClasses = new ArrayList<Class<?>>(coverageClasses);
		if (startingcomponents == null)
			this.components = new SequenceCollection(SeedSequences
					.defaultSeeds());
		else
			this.components = startingcomponents;
		this.executionVisitor = new DummyVisitor();
		this.allSequences = new LinkedHashSet<Sequence>();
		this.stats = new SequenceGeneratorStats(statements, covClasses);
		this.classes = classes;

		// JJ : separate constructors and methods
		constructors = new ArrayList<StatementKind>();
		methods = new ArrayList<StatementKind>();
		mapConstructors = new HashMap<Class<?>, List<RConstructor>>();
		for (StatementKind sk : this.statements) {
			if (sk instanceof RMethod) {
				methods.add((RMethod) sk);
			} else if (sk instanceof RConstructor) {
				constructors.add((RConstructor) sk);

				Class<?> ccc = ((RConstructor) sk).getConstructor()
						.getDeclaringClass();
				List<RConstructor> lll;
				if (mapConstructors.containsKey(ccc))
					lll = mapConstructors.get(ccc);
				else
					lll = new ArrayList<RConstructor>();
				lll.add((RConstructor) sk);
				mapConstructors.put(ccc, lll);
			} else if (false)
				;
		}
	}

	public Set<Sequence> allSequences() {
		return Collections.unmodifiableSet(this.allSequences);
	}

	public void processSequence(ExecutableSequence seq) {

		if (seq.hasNonExecutedStatements()) {
			seq.sequence.clearAllActiveFlags();
			return;
		}

		regressionSequences.add(seq);

		if (seq.hasObservation(ContractViolation.class)) {
			seq.sequence.clearAllActiveFlags();
			return;
		}

		if (!seq.isNormalExecution()) {
			seq.sequence.clearAllActiveFlags();
			return;
		}

		objectCache.setActiveFlags(seq);
	}

	public void checkTargetException(ExecutableSequence seq) {
		// 1. check whether the exception mode is on
		// 2. check this sequence threw an exception
		if (OCATGlobals.targetException != null && seq.throwsException()) {
			// 3. if the exception is the target exception
			// System.out.println(seq);
			String output = "";
			int idx = seq.sequence.size() - 1;

			if (seq.sequence.executionResults.get(idx) instanceof randoop.NotExecuted)
				return;

			ExceptionalExecution eo = (ExceptionalExecution) seq.sequence.executionResults
					.get(idx);

			Throwable tw = eo.getException();
			String exceptionClassName = tw.getClass().getCanonicalName();

			if (exceptionClassName.equals(OCATGlobals.targetException)) {
				StackTraceElement[] ste = tw.getStackTrace();

				boolean matched = true;
				String targetste[] = { OCATGlobals.stackTrace1,
						OCATGlobals.stackTrace2, OCATGlobals.stackTrace3,
						OCATGlobals.stackTrace4, OCATGlobals.stackTrace5 };

				for (int i = 0; i < targetste.length; i++) {
					if (targetste[i] != null) {
						if (!targetste[i].equals(ste[i].toString()))
							matched = false;
					} else {
						matched = false;
						break;
					}

				}

				if (matched) {
					// 4. set the stop flag
					output += "" + "\r\n";
					output += "//###Type0 Exception Pefectly Matched " + "\r\n";
					output += "//# Exception class: " + tw.getClass() + "\r\n";
					for (int i = 0; i < targetste.length; i++)
						output += "//# StackTrace" + i + ": " + targetste[i]
								+ "\r\n";
					targetExceptionOccured = true;
					output += "!PERFECT!";
				} else {
					boolean onematched = false;
					output += "" + "\r\n";
					output += "//@@@ the same exception occured: "
							+ tw.getClass() + "\r\n";

					for (int i = 0; i < ste.length; i++) {
						output += "//@ StackTrace" + i + ": " + ste[i] + "\r\n";
						for (int j = 0; j < targetste.length; j++) {
							if (ste[i].toString().equals(targetste[j])) {
								onematched = true;
								output += "//###Type" + (j + 1) + " matched: "
										+ targetste[j] + "\r\n";
								targetExceptionStasfied[j] = true;
							}
						}
					}

					if (!onematched) {
						output += "//@@@ only the same exception has been occured."
								+ "\r\n";
					} else {
						if (OCATGlobals.stopon1Crash)
							targetExceptionOccured = true;
						output += "//@@@ exception has been found." + "\r\n";
					}

					output += "" + "\r\n";
				}

			}
			// eo.additionalInfo = output;
			System.out.println(output);
		}
	}

	public List<ExecutableSequence> getSelectedSequences() {
		return this.regressionSequences;
	}

	// JJ
	public void coberturalForceData() {
		if (coberturaForce <= 0)
			return;
		if (allSequences.size() % coberturaForce != 0)
			return;
		if (allSequences.size() < 1)
			return;

		try {
			System.out.println("\nCobertura starts saving data.");

			// force
			String className = "net.sourceforge.cobertura.coveragedata.ProjectData";
			String methodName = "saveGlobalProjectData";
			Class saveClass = Class.forName(className);
			java.lang.reflect.Method saveMethod = saveClass.getDeclaredMethod(
					methodName, new Class[0]);
			saveMethod.invoke(null, new Object[0]);

			System.out.println("\nCobertura is saving data.");
			while (true) {
				File lockf = new File("cobertura.ser.lock");
				Thread.sleep(1000);
				if (!lockf.exists())
					break;
				System.out.println("cobertura.ser.lock is existings!");
			}
			System.out.println("\nCobertura saves data!");

			// copy
			File src = new File("cobertura.ser");
			File dst = new File(String.format("coberturax%06d.ser",
					allSequences.size()));
			if (src.exists()) {
				InputStream in = new FileInputStream(src);
				OutputStream out = new FileOutputStream(dst);

				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;

				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				in.close();
				out.close();
			}

		} catch (Exception e) {
			System.out.println("Cobertura Writing Error!!: " + e.getMessage());
			e.printStackTrace();
			// coberturaForce = -1;
		}
	}

	protected boolean stop() {

		if (coberturaForce > 0)
			coberturalForceData();

		int countSkippedSeqs = 0;
		if (OCATGlobals.JJ_reduction)
			for (Sequence p : allSequences) {
				if (p.redundantByCov || p.redundantBySeq)
					countSkippedSeqs++;
			}

		boolean stopTargetException = OCATGlobals.targetException != null
				&& targetExceptionOccured;

		return (stop_when_plateau > 0 && stats.getGlobalStats().getCount(
				SequenceGeneratorStats.STAT_BRANCHTOT) == 0)
				|| (stop_when_plateau > 0 && stats.progressDisplay.lastCovIncrease > stop_when_plateau)
				|| (timer.getTimeElapsedMillis() >= timeMillis)
				|| (allSequences.size() >= maxSequences)
				|| (allSequences.size() - countSkippedSeqs >= maxReductedSeqs || stopTargetException);
	}

	protected void onStartOfExploration() {
		timer.startTiming();
	}

	/**
	 * Creates and executes new sequences in a loop, using the sequences in s.
	 * New sequences are themselves added to s. Stops when timer says it's
	 * testtime to stop.
	 */
	public void explore() {

		Log.log(this.statements);

		stats.printLegend();

		onStartOfExploration();

		stats.startProgressDisplay();

		int repeatedFailedAttemptsToGenerateNewComponent = 0;
		int numRounds = 0;
		Set<Sequence> newComponents = new LinkedHashSet<Sequence>();
		// Set<Branch> OldCoveredBranches = new LinkedHashSet<Branch>();
		// Set<Branch> TotalCoveredBranches = new LinkedHashSet<Branch>();
		// List<StatementKind> fullCoveredMethods = new
		// ArrayList<StatementKind>();

		while (!stop()) {

			// it is always true if ROUNDS is -1
			if (numRounds > ROUNDS
					|| repeatedFailedAttemptsToGenerateNewComponent > NEW_ROUND_THRESHOLD) {
				if (numRounds < ROUNDS)
					System.out.println("@@@@@ NEW ROUND");
				for (Sequence s : newComponents) {
					components.add(s);
				}
				newComponents = new LinkedHashSet<Sequence>();
				repeatedFailedAttemptsToGenerateNewComponent = 0;
				numRounds++;
			}

			if (components.size() % GenInputsAbstract.clear == 0)
				components.clear();

			Sequence newsequence = createNewUniqueSequence(null);

			if (newsequence == null) {
				repeatedFailedAttemptsToGenerateNewComponent++;
				continue;
			}

			if (GenInputsAbstract.dontexecute) {
				this.components.add(newsequence);
				continue;
			}

			ExecutableSequence eSeq = new ExecutableSequence(newsequence);

      Set<Branch> coveredBranches = new LinkedHashSet<Branch>();
      Set<Class<?>> classes = getInstrumentedClasses();
      Coverage.clearCoverage(classes);

      try {
        eSeq.execute(executionVisitor);
      } catch (java.lang.IllegalArgumentException e) {
        System.out.println("eSeq.execute Exception:" + e);
        e.printStackTrace();
        System.err.flush();
        continue;
      } catch (Exception e) {
        System.out.println("eSeq.execute Exception:" + e);
        e.printStackTrace();
        System.err.flush();
        continue;
      } catch (OutOfMemoryError e) { // altered by Ning
        System.out.println("eSeq.execute Exception:" + e);
        e.printStackTrace();
        System.err.flush();
        continue;
      }

      for (CoverageAtom ca : Coverage.getCoveredAtoms(classes)) {
        assert ca instanceof Branch;
        coveredBranches.add((Branch) ca);
      }

      // Update branch-to-seqs map.
      for (CoverageAtom br : coveredBranches) {
        branchesCovered.add(br);
        Set<Sequence> ss = branchesToCoveringSeqs.get(br);
        if (ss == null) {
          ss = new LinkedHashSet<Sequence>();
          branchesToCoveringSeqs.put(br, ss);
        }
        ss.add(newsequence);
      }

      if (Log.isLoggingOn() && eSeq.hasNonExecutedStatements())
        Log.logLine("Sequence after execution: " + Globals.lineSep
            + eSeq.toString());
      if (Log.isLoggingOn())
        Log.logLine("Branches covered:" + Globals.lineSep
            + coveredBranches);

      // altered by Ning
      String codeString = eSeq.toCodeString();
      if (codeString != null && codeString.length() > 500000) {
        eSeq.clearAllResults();
        continue;
      }

      stats.updateStatistics(eSeq, coveredBranches);

      processSequence(eSeq);

      checkTargetException(eSeq);

      if (GenInputsAbstract.offline) {
        newsequence.setAllActiveFlags();
        continue;
      }

      if (newsequence.hasActiveFlags()) {
        newComponents.add(newsequence);
        repeatedFailedAttemptsToGenerateNewComponent = 0;
      } else {
        repeatedFailedAttemptsToGenerateNewComponent++;
      }

      if (Log.isLoggingOn())
        Log.logLine("allSequences.size()=" + allSequences.size());

      if (OCATGlobals.stop_if100coverate && coveredBranches.size() != 0)
        if (stats.getGlobalStats().getCount(
            SequenceGeneratorStats.STAT_BRANCHCOV) == stats
            .getGlobalStats().getCount(
                SequenceGeneratorStats.STAT_BRANCHTOT)) {
          stats.progressDisplay.display();
          System.out
              .println("Stopped since it achieved 100% coverage");
          break;
        }

      // altered by Ning, clear execution results after sequence is generated
      eSeq.clearAllResults();

    } // End of generation loop.

		stats.stopProgressDisplay();

		if (print_stats)
			stats.printStatistics();

		if (OCATGlobals.targetException != null) {
			int countFrames = 0;
			if (OCATGlobals.stackTrace1 != null)
				countFrames++;
			if (OCATGlobals.stackTrace2 != null)
				countFrames++;
			if (OCATGlobals.stackTrace3 != null)
				countFrames++;
			if (OCATGlobals.stackTrace4 != null)
				countFrames++;
			if (OCATGlobals.stackTrace5 != null)
				countFrames++;

			System.out.println("");
			System.out.print("!!!!Exception Results");
			if (targetExceptionOccured) {
				System.out
						.println("(!OCCURED!):perfectly matched exception has been occured");
			} else {
				boolean none = true;
				String strframes = "";
				for (int i = 0; i < targetExceptionStasfied.length; i++) {
					if (targetExceptionStasfied[i]) {
						none = false;
						strframes += ("frame" + (i + 1) + ", ");
					}
				}

				if (none)
					System.out.print("(!NONE!):none of frame is matched");
				else
					System.out.print("(!OCCURED!):" + strframes
							+ " are occured ");
			}
			System.out.println(" among " + countFrames + " frames.");
		}

	}

	// JJ
	/*
	 * • constructor selection is based on class coverage and the number of
	 * class instance. • method selection is based on class coverage and method
	 * coverage.
	 */

	// Constructor Selection = alpha * class coverage + beta * the max number of
	// all class instances/the number of the class instances.
	// Method Selection = seta * class coverage + gamma * method coverage + zeta
	// * maxselected / STAT_NOT_DISCARDED
	Map<Class<?>, List<Class<?>>> subTypes = new HashMap<Class<?>, List<Class<?>>>();

	private List<Class<?>> findCompatibleClasses(Class<?> cls) {
		List<Class<?>> lcls = null;
		if (subTypes.containsKey(cls)) {
			lcls = subTypes.get(cls);
		} else {

			List<Class<?>> lc = new ArrayList<Class<?>>();
			for (Class<?> c : classes) {
				if (Reflection.canBeUsedAs(c, cls))
					lc.add(c);

			}
			subTypes.put(cls, lc);
			lcls = lc;
		}

		return lcls;
	}

	@SuppressWarnings("unchecked")
	private StatementKind selectTargetConstructor(Class<?> cls) {
		// constructor.getDeclaringClass();
		List<RConstructor> lsk;

		// select constructors of corresponding class from the input classes
		/*
		 * for (StatementKind sk : this.constructors) { if (cls ==
		 * ((RConstructor) sk).getConstructor().getDeclaringClass()) {
		 * lsk.add(sk); } }
		 */

		lsk = mapConstructors.get(cls);
		if (lsk == null) {
			lsk = new ArrayList<RConstructor>();

			// if there is no constructor for a class
			// find compatible classes
			if (OCATGlobals.JJ_bottomtop_comp) {
				List<Class<?>> lcls = findCompatibleClasses(cls);

				if (lcls != null)
					if (lcls.size() != 0) {
						for (Class<?> ccc : lcls) {
							List<RConstructor> lll = mapConstructors.get(ccc);
							if (lll != null)
								lsk.addAll(lll);
						}
					}
			}

			// if there is no constructor for a class
			// find it from the general classes.
			if (OCATGlobals.JJ_bottomtop_noninput) {
				Constructor[] cont;
				cont = Reflection.getConstructorsOrdered(cls);

				for (Constructor<?> co : cont) {
					RConstructor mc = RConstructor.getRConstructor(co);
					lsk.add(mc);
				}

				mapConstructors.put(cls, lsk);
			}

			mapConstructors.put(cls, lsk);
		}

		if (lsk.size() == 0)
			return null;

		return Randomness.randomMember(lsk);
	}

	// JJ
	private StatementKind selectTargetStatementBasedonCoverage() {
		// ratio 2:8 = constructors and methods
		List<StatementKind> statList;

		if (false) {
			// this is might not be helpful for bottom_up approach
			if (Randomness.nextRandomInt(100) < 10)
				statList = this.constructors;
			else
				statList = this.methods;
		} else
			statList = statements;

		// if (statementsWeight == null || Randomness.nextRandomInt(5) == 1) {
		ArrayListSimpleList<StatementWithWeight> sfmList = new ArrayListSimpleList<StatementWithWeight>();
		for (StatementKind sk : statList) {

			Set<StatementKind> calles = null;
			if (callinfo != null)
				calles = callinfo.getCalles(sk);

			StatementWithWeight sfm = new StatementWithWeight(sk, stats,
					calles, objectCache);
			sfmList.add(sfm);
		}
		// statementsWeight = sfmList;
		// }

		StatementWithWeight sfm = Randomness.randomMemberWeighted(sfmList);
		return sfm.getStatement();
	}

	// JJ: specified method has more possibility to be selected
	private StatementKind selectTargetStatementWithTargetLists() {
		// ratio 2:8 = constructors and methods
		List<StatementKind> statList;

		if (Randomness.nextRandomInt(100) < (OCATGlobals.priorityRate * 100)) {
			// select a pure random
			return Randomness.randomMember(this.statements);
		}

		if (this.prioritizedMethods == null) {
			this.prioritizedMethods = new ArrayList<StatementKind>();
			for (String strPM : OCATGlobals.prioritizedMethods) {
				for (StatementKind sk : this.statements) {
					if (sk.toString().contains(strPM)) {
						// System.out.println("UREKA!");
						this.prioritizedMethods.add(sk);
					}
				}
			}
		}

		StatementKind pmethod = Randomness
				.randomMember(this.prioritizedMethods);

		return pmethod;
	}

	/**
	 * Tries to create and execute a new sequence. If the sequence is new (not
	 * already in the specivied sequence manager), then it is executed and added
	 * to the manager's sequences. If the sequence created is already in the
	 * manager's sequences, this method has no effect.
	 */
	private Sequence createNewUniqueSequence(Class<?> cls) {

		if (Log.isLoggingOn())
			Log.logLine("-------------------------------------------");

		StatementKind statement = null;
		Sequence newSequence = null;

		if (cls == null) {
			// Select a StatementInfo
			// JJ: Prevent 100% coverage methods to be selected
			if (OCATGlobals.JJ_covbase) {
				assert (covClasses.size() > 0);

				if (Randomness.nextRandomBool())
					statement = selectTargetStatementBasedonCoverage();
				else
					// pure random. The original approach
					statement = Randomness.randomMember(this.statements);

			} else if (OCATGlobals.prioritizedMethods.size() > 0) {
				statement = selectTargetStatementWithTargetLists();
			} else { // altered by Ning
			  statement = Randomness.randomMember(this.statements);  // the original approach in randoop
			}
		} else {
			// bottom top
			statement = selectTargetConstructor(cls);

		}

		if (statement == null)
			return null;

		stats.statStatementSelected(statement); // increase a selected count

		if (Log.isLoggingOn())
			Log.logLine("Selected statement: " + statement.toString());

		InputsAndSuccessFlag sequences = selectInputs(statement, components,
				cls);

		// assert (statement.getInputTypes().size() ==
		// sequences.sequences.size());

		if (!sequences.success) {
			if (Log.isLoggingOn())
				Log.logLine("Failed to find inputs for statement.");
			stats.statStatementNoArgs(statement);
			return null;
		}

		newSequence = Sequence.create(statement, sequences.sequences);

		// With .1 probability, do a primitive value heuristic.
		if (GenInputsAbstract.repeat_heuristic
				&& Randomness.nextRandomInt(10) == 0) {
			int times = Randomness.nextRandomInt(100);
			newSequence = newSequence.repeatLast(times);
			// if (Log.isLoggingOn())
			// Log.log(">>>" + times + newSequence.toCodeString());
		}

		// Heuristic: if parameterless statement, subsequence inputs
		// will all be redundant, so just remove it from list of
		// statements.
		if (GenInputsAbstract.no_args_statement_heuristic
				&& statement.getInputTypes().size() == 0) {
			statements.remove(statement);
		}

		// If sequence is larger than size limit, try again.
		if (newSequence.size() > GenInputsAbstract.maxsize) {
			if (Log.isLoggingOn())
				Log.logLine("Sequence discarded because size "
						+ newSequence.size() + " exceeds maximum allowed size "
						+ GenInputsAbstract.maxsize);
			stats.statStatementToBig(statement);
			return null;
		}

		randoopConsistencyTests(newSequence);

		if (this.allSequences.contains(newSequence)) {
			if (Log.isLoggingOn())
				Log
						.logLine("Sequence discarded because the same sequence was previously created.");
			stats.statStatementRepeated(statement);
			return null;
		}

    this.allSequences.add(newSequence);
		for (Pair<Sequence, Variable> p : sequences.sequences) {
			p.a.lastTimeUsed = java.lang.System.currentTimeMillis();
		}

		randoopConsistencyTest2(newSequence);

		if (Log.isLoggingOn()) {
			Log.logLine("Successfully created new unique sequence:\n"
					+ newSequence.toCodeString());
		}
		// System.out.println("###" + statement.toStringVerbose() + "###" +
		// statement.getClass());
		stats.statStatementNotDiscarded(statement);

    if (cls == null)
      stats.checkStatsConsistent();

		return newSequence;
	}
	
  private void randoopConsistencyTest2(Sequence newSequence) {
		// Testing code.
		if (Globals.randooptestrun) {
			this.allsequencesAsCode.add(newSequence.toCodeString());
			this.allsequencesAsList.add(newSequence);
		}
	}

	private void randoopConsistencyTests(Sequence newSequence) {
		// Testing code.
		if (Globals.randooptestrun) {
			String code = newSequence.toCodeString();
			if (this.allSequences.contains(newSequence)) {
				if (!this.allsequencesAsCode.contains(code)) {
					throw new IllegalStateException(code);
				}
			} else {
				if (this.allsequencesAsCode.contains(code)) {
					int index = this.allsequencesAsCode.indexOf(code);
					StringBuilder b = new StringBuilder();
					Sequence co = this.allsequencesAsList.get(index);
					co.equals(newSequence);
					b.append("new component:" + Globals.lineSep + ""
							+ newSequence.toString() + "" + Globals.lineSep
							+ "as code:" + Globals.lineSep + "" + code
							+ Globals.lineSep);
					b
							.append("existing component:"
									+ Globals.lineSep
									+ ""
									+ this.allsequencesAsList.get(index)
											.toString()
									+ ""
									+ Globals.lineSep
									+ "as code:"
									+ Globals.lineSep
									+ ""
									+ this.allsequencesAsList.get(index)
											.toCodeString());
					throw new IllegalStateException(b.toString());
				}
			}
		}
	}

	public InputsAndSuccessFlag selectInputs(StatementKind statement,
			SequenceCollection components, Class<?> cls) {

		List<Class<?>> inputClasses = statement.getInputTypes();
		List<Pair<Sequence, Variable>> ret = new ArrayList<Pair<Sequence, Variable>>();

		// JJ: JJ_static
		boolean isNotStatic = (statement instanceof RMethod)
				&& !((RMethod) statement).isStatic();
		// if (Globals.JJ_static)
		// isNotStatic = false;

		for (int i = 0; i < inputClasses.size(); i++) {
			Class<?> inarg = inputClasses.get(i);

			// TODO check if this ever happens.
			if (!Reflection.isVisible(inarg))
				return new InputsAndSuccessFlag(false, ret);

			// when it is the first argument, if it is not static then it is
			// receiver.
			// boolean isReceiver = (i == 0 && isNotStatic);
			boolean isReceiver = (i == 0 && (statement instanceof RMethod) && (!((RMethod) statement)
					.isStatic()));

			// TODO: why only use exact type here? what about using
			// getMatchType()?
			SimpleList<Sequence> lseq = components.getSequencesForType(inarg,
					false);

			if (GenInputsAbstract.helpers && lseq.size() == 0) {
				// Try to get from helper sequences.
				lseq = HelperSequenceCreator.createSequence(inarg, components);
				if (lseq.size() > 0) {
					Log.logLine("helper sequence:");
					Log.log(lseq.get(0));
				}
			}

			if (lseq.size() == 0) {
				// JJ: If it is an array, find element's type and make an
				// new array
				if (OCATGlobals.JJ_array)
					lseq = MakeArrayFromElementType(inarg);
			}

			if (lseq.size() == 0) {
				// JJ: bottom top
				// if there is no feedback for this input type, try to create an
				// input.
				if (OCATGlobals.JJ_bottomtop_comp
						|| OCATGlobals.JJ_bottomtop_noninput
						|| OCATGlobals.JJ_bottomtop_prim) {

					if (inarg.isArray() && false) {
						lseq = MakeArrayFromElementType(inarg);
					} else if (cls == null) // prevent call
					// createNewUniqueSequence
					// recursively again and again
					{
						Sequence sq = createNewUniqueSequence(inarg);
						if (sq != null) {
							ArrayListSimpleList<Sequence> aseq = new ArrayListSimpleList<Sequence>();
							aseq.add(sq);
							lseq = aseq;
						}
					}
				}
			}

			if (lseq.size() == 0) {
				if (isReceiver || GenInputsAbstract.forbid_null) {
					return new InputsAndSuccessFlag(false, null);
				} else {
					if (!isReceiver) {
						if (Log.isLoggingOn()) {
							Log.logLine("Will use null as " + i + "-th input");
							StatementKind st = PrimitiveOrStringOrNullDecl
									.nullOrZeroDecl(inarg);
							Sequence seq = new Sequence().extend(st,
									new ArrayList<Variable>());
							ret.add(new Pair<Sequence, Variable>(seq, seq
									.getLastVariable()));
						}
						continue;
					}
				}
			}

			assert (lseq.size() != 0);

			// null?
			double nullRatio = GenInputsAbstract.null_ratio;
			assert nullRatio >= 0 && nullRatio <= 1;
			boolean useNull = Randomness.randomBoolFromDistribution(
					1 - nullRatio, nullRatio);
			if (!isReceiver && useNull) {

				boolean isnullused = false;
				if (GenInputsAbstract.null_once) {
					if (statement instanceof RMethod) {
						isnullused = ((RMethod) statement).isNullUsed(i);
						((RMethod) statement).setNullFlag(i);
					} else if (statement instanceof RConstructor) {
						isnullused = ((RConstructor) statement).isNullUsed(i);
						((RConstructor) statement).setNullFlag(i);
					}
				}

				if (!isnullused) {
					StatementKind st = PrimitiveOrStringOrNullDecl
							.nullOrZeroDecl(inarg);
					Sequence seq = new Sequence().extend(st,
							new ArrayList<Variable>());
					ret.add(new Pair<Sequence, Variable>(seq, seq
							.getLastVariable()));
					continue;
				}
			}

			// choose a variable and sequence
			Pair<Sequence, Variable> psv = null;
			/*
			 * if (Globals.JJ_ARTJJ && statement instanceof RMethod) if
			 * (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(inarg)) psv =
			 * chooseVariableNSequenceARTPrimitive(lseq, inarg, statement, i);
			 * else if (inarg.isArray()) psv =
			 * chooseVariableNSequenceARTArray(lseq, inarg, statement, i); else
			 * psv = chooseVariableNSequence(lseq, inarg); else psv =
			 * chooseVariableNSequence(lseq, inarg);
			 */

			if (OCATGlobals.JJ_ARTJJ && statement instanceof RMethod) {
				if (Randomness.nextRandomBool() || OCATGlobals.JJ_ARTJJPure) {

					// double tstart,tend, elp;
					// tstart=System.currentTimeMillis();
					// Primitive inputs!
					if (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(inarg))
						psv = chooseVariableNSequenceARTPrimitive(lseq, inarg,
								statement, i);

					// Array inputs!
					// else if (inarg.isArray())
					// psv = chooseVariableNSequenceARTArray(lseq, inarg,
					// statement, i);

					// Object inputs!
					else {
						psv = chooseVariableNSequenceARTObject(lseq, inarg,
								statement, i);

					}
					// tend=System.currentTimeMillis();
					// elp = (tend-tstart);
					// elp = elp;
				} else
					psv = chooseVariableNSequence(lseq, inarg);
			} else if (OCATGlobals.JJ_ARTOO && statement instanceof RMethod) {

				psv = chooseVariableNSequenceARTObject(lseq, inarg, statement,
						i);
			} else
				psv = chooseVariableNSequence(lseq, inarg);

			if (psv == null)
				return new InputsAndSuccessFlag(false, null);

			if (i == 0
					&& isNotStatic
					&& psv.a.getCreatingStatement(psv.b) instanceof PrimitiveOrStringOrNullDecl)
				return new InputsAndSuccessFlag(false, null);

			ret.add(psv);
		}

		return new InputsAndSuccessFlag(true, ret);
	}
	
	private Pair<Sequence, Variable> chooseVariableNSequenceARTObject(
			SimpleList<Sequence> lseq, Class<?> inarg, StatementKind statement,
			int argIndex) {

		List<Pair<Object, HashSet<Variable>>> inputCenterValues = ((RMethod) statement)
				.getTypeCenterValues();
		// Object centerVal = inputCenterValues.get(argIndex).a;

		Variable varFurthest = null;
		double distFurthest = 0.0;
		Object objFurthest = null;
		HashSet<Variable> setUsedVals = inputCenterValues.get(argIndex).b;

		if (Log.isLoggingOn())
			Log.logLine("Selection start for " + statement.toString()
					+ " argument type = " + inarg);

		if (setUsedVals != null)
			for (int i = 0; i < lseq.size(); i++) {
				Sequence seq = lseq.get(i);

				List<Variable> possibleVariables = seq.getVariablesOfType(
						inarg, getMatchType());

				for (Variable var : possibleVariables) {
					// check outval has been chosen
					if (((RMethod) statement).isUsedValue(var, argIndex))
						continue;

					Object valFromSequence = var.getValue();

					double dist_tot = 0;
					for (Variable usedVal : setUsedVals) {
						// Object retVal =
						// ((NormalExecution)exeOut).getRuntimeValue();
						// Object centerVal = inputCenterValues.get(argIndex).a;

						double dist = 0;
						if (OCATGlobals.JJ_ARTJJ) {
							// tmp = ObjectDistance.GetDistanceObjectSimple(
							// usedVal.getValue(), outVal);
							if (inarg.isArray())
								dist = ObjectDistance.GetDistanceObjectArray(
										usedVal.getValue(), valFromSequence);
							else
								dist = ObjectDistance.GetDistanceObjectSimple(
										usedVal.getValue(), valFromSequence);
						} else if (OCATGlobals.JJ_ARTOO)
							dist = ObjectDistance.GetDistanceObjectARTOO(
									usedVal.getValue(), valFromSequence);

						if (Log.isLoggingOn() && false) {
							if (usedVal != null)
								if (usedVal.getValue() != null)
									Log.log(usedVal.getValue().getClass()
											+ " ("
											+ usedVal.getValue().getClass()
											+ ") vs ");
								else
									Log.log("null vs ");

							if (valFromSequence != null)
								Log.log(valFromSequence.getClass() + " ("
										+ valFromSequence + ")");
							else
								Log.log("null");

							Log.logLine(" = " + dist);
						}
						dist_tot += dist;

					}
					if (ObjectDistance.compare(dist_tot, distFurthest) >= 0) {
						distFurthest = dist_tot;
						varFurthest = var;
						objFurthest = valFromSequence;
					}
				}

			}

		if (varFurthest == null) {
			Pair<Sequence, Variable> psv = chooseVariableNSequence(lseq, inarg);
			if (statement instanceof RMethod)
				((RMethod) statement).updateCenterValue(psv.b,
						psv.b.getValue(), argIndex);

			return psv;
		}

		if (Log.isLoggingOn()) {
			if (objFurthest != null)

				Log.logLine("Selected = " + objFurthest.getClass() + "("
						+ objFurthest + ")");
			else
				Log.logLine("Selected = null");
		}

		Sequence chosenSeq = null;
		chosenSeq = varFurthest.sequence;
		if (false && !(objFurthest instanceof Integer))
			System.out.println("\nNewval = " + objFurthest + " Furthest = "
					+ distFurthest + " statement="
					+ statement.toParseableString() + ", " + argIndex);

		if (varFurthest == null) {
			throw new BugInRandoopException("type: " + inarg + ", sequence: "
					+ chosenSeq);
		}

		if (statement instanceof RMethod)
			((RMethod) statement).updateCenterValue(varFurthest, objFurthest,
					argIndex);

		return new Pair<Sequence, Variable>(chosenSeq, varFurthest);
	}

	private Pair<Sequence, Variable> chooseVariableNSequenceARTArray(
			SimpleList<Sequence> lseq, Class<?> inarg, StatementKind statement,
			int argIndex) {

		List<Pair<Object, HashSet<Variable>>> inputCenterValues = ((RMethod) statement)
				.getTypeCenterValues();
		// Object centerVal = inputCenterValues.get(argIndex).a;

		HashSet<Sequence> unUsedSequences = new HashSet<Sequence>();
		for (int i = 0; i < lseq.size(); i++) {
			Sequence seq = lseq.get(i);

			List<Variable> possibleVariables = seq.getVariablesOfType(inarg,
					getMatchType());
			List<Variable> unUsedVariables = new ArrayList<Variable>();

			for (Variable var : possibleVariables) {
				// check outval has been chosen
				if (((RMethod) statement).isUsedValue(var, argIndex))
					continue;

				unUsedVariables.add(var);
				unUsedSequences.add(seq);
			}
		}

		// if unUsedVariables = null then make array
		if (OCATGlobals.JJ_array)
			if (unUsedSequences.size() == 0)
				unUsedSequences.addAll(MakeArrayFromElementType(inarg)
						.toJDKList());

		if (unUsedSequences.size() == 0)
			return null;

		Sequence chosenSeq = null;
		// TODO (JJ) : ARRAY selection - change ART from pure random.

		chosenSeq = Randomness.randomSetMember(unUsedSequences);

		// Now, find values that satisfy the constraint set.
		Match m = getMatchType();

		Variable randomVariable = chosenSeq.randomVariableForTypeLastStatement(
				inarg, m);

		if (randomVariable == null && m == Match.EXACT_TYPE) {
			m = Match.COMPATIBLE_TYPE;
			randomVariable = chosenSeq.randomVariableForTypeLastStatement(
					inarg, m);
		}

		if (randomVariable == null) {
			throw new BugInRandoopException("type: " + inarg + ", sequence: "
					+ chosenSeq);
		}

		if (false)
			System.out.println("\nNewval = " + randomVariable.getClass()
					+ " statement=" + statement.toParseableString() + ", "
					+ argIndex);

		if (statement instanceof RMethod)
			((RMethod) statement).updateCenterValue(randomVariable, null, // why
					// it
					// is
					// null???
					argIndex);

		return new Pair<Sequence, Variable>(chosenSeq, randomVariable);
	}

	private Match getMatchType() {
		// Now, find values that satisfy the constraint set.
		// JJ: Exact Type and Compatible_type
		Match m = Match.COMPATIBLE_TYPE;
		if (OCATGlobals.JJ_exactcomp) {
			if (Randomness.nextRandomBool())
				m = Match.COMPATIBLE_TYPE;
			else
				m = Match.EXACT_TYPE;
		}
		return m;
	}

	private Pair<Sequence, Variable> chooseVariableNSequenceARTPrimitive(
			SimpleList<Sequence> lseq, Class<?> inarg, StatementKind statement,
			int argIndex) {
		assert (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(inarg));

		List<Pair<Object, HashSet<Variable>>> inputCenterValues = ((RMethod) statement)
				.getTypeCenterValues();
		Object centerVal = inputCenterValues.get(argIndex).a;

		Variable varFurthest = null;
		double distFurthest = 0.0;
		Object objFurthest = null;
		for (int i = 0; i < lseq.size(); i++) {
			Sequence seq = lseq.get(i);

			List<Variable> possibleVariables = seq.getVariablesOfType(inarg,
					getMatchType());

			for (Variable var : possibleVariables) {
				// check outval has been chosen
				if (((RMethod) statement).isUsedValue(var, argIndex))
					continue;

				Object outVal = var.getValue();

				assert (outVal != null);
				double dist = ObjectDistance.GetDistancePrimitive(centerVal,
						outVal);
				if (ObjectDistance.compare(dist, distFurthest) >= 0) {
					distFurthest = dist;
					varFurthest = var;
					objFurthest = outVal;
				}
			}

		}

		if (varFurthest == null) {
			Pair<Sequence, Variable> psv = chooseVariableNSequence(lseq, inarg);
			if (statement instanceof RMethod)
				((RMethod) statement).updateCenterValue(psv.b,
						psv.b.getValue(), argIndex);

			return psv;
		}

		Sequence chosenSeq = null;

		chosenSeq = varFurthest.sequence;
		if (false && !(objFurthest instanceof Integer))
			System.out.println("\nNewval = " + objFurthest + " center = "
					+ centerVal + " Furthest = " + distFurthest + " statement="
					+ statement.toParseableString() + ", " + argIndex);

		if (varFurthest == null) {
			throw new BugInRandoopException("type: " + inarg + ", sequence: "
					+ chosenSeq);
		}

		if (statement instanceof RMethod)
			((RMethod) statement).updateCenterValue(varFurthest, objFurthest,
					argIndex);

		return new Pair<Sequence, Variable>(chosenSeq, varFurthest);
	}

	private Pair<Sequence, Variable> chooseVariableNSequence(
			SimpleList<Sequence> lseq, Class<?> inarg) {
		Sequence chosenSeq = null;
		if (GenInputsAbstract.weighted_inputs) {
			chosenSeq = Randomness.randomMemberWeighted(lseq);
		} else {
			chosenSeq = Randomness.randomMember(lseq);
		}

		// Now, find values that satisfy the constraint set.
		Match m = getMatchType();

		Variable randomVariable = chosenSeq.randomVariableForTypeLastStatement(
				inarg, m);

		if (randomVariable == null && m == Match.EXACT_TYPE) {
			m = Match.COMPATIBLE_TYPE;
			randomVariable = chosenSeq.randomVariableForTypeLastStatement(
					inarg, m);
		}

		if (randomVariable == null) {
			throw new BugInRandoopException("type: " + inarg + ", sequence: "
					+ chosenSeq);
		}

		return new Pair<Sequence, Variable>(chosenSeq, randomVariable);
	}

	private SimpleList<Sequence> MakeArrayFromElementType(Class<?> inputType) {
		// JJ: If it is an array, find element's type and make an new
		// It doesn't consider a multi-dimensional array.
		if (!inputType.isArray())
			return new ArrayListSimpleList<Sequence>();

		Class<?> compType = inputType.getComponentType();

		if (false)
			if (PrimitiveTypes.isBoxedOrPrimitiveOrStringType(compType))
				System.out.println("Primitive : " + compType.toString());
			else
				System.out.println("Not Primitive : " + compType.toString());

		SimpleList<Sequence> seqCompType = components.getSequencesForType(
				compType, false);

		if (seqCompType.size() == 0)
			return seqCompType;

		Sequence concatSeq = Sequence.concatenate(seqCompType.toJDKList());

		List<Variable> possibleVariables = concatSeq.getVariablesOfType(
				compType, getMatchType());

		if (possibleVariables.isEmpty())
			return new ArrayListSimpleList<Sequence>();

		int SizeOfArr = Randomness.nextRandomInt(possibleVariables.size() * 2) + 1;

		List<Variable> inputs = new ArrayList<Variable>();
		for (int i = 0; i < SizeOfArr; i++) {
			Variable rVariable = Randomness.randomMember(possibleVariables);
			inputs.add(rVariable);
		}
		concatSeq = concatSeq.extend(new ArrayDeclaration(inputType
				.getComponentType(), SizeOfArr), inputs);

		assert concatSeq != null;
		ArrayListSimpleList<Sequence> lt = new ArrayListSimpleList<Sequence>();
		/*
		 * for (int i = 0; i < l.size(); i++) { lt.add(l.get(i)); }
		 */
		lt.add(concatSeq);

		assert (lt != null);
		return lt;
	}

}
