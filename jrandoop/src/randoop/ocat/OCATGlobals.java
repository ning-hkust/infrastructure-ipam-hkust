package randoop.ocat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import utilMDE.Invisible;
import utilMDE.Option;

public class OCATGlobals {

		@Option("Create input objects by needs")
		public static boolean JJ_bottomtop_prim = false;

		@Option("Create input objects by needs")
		public static boolean JJ_bottomtop_noninput = false;

		@Option("Create input objects by needs")
		public static boolean JJ_bottomtop_comp = false;
			
		@Option("Use a new approche which is based on coverage by JJ")
		public static boolean JJ_covbase = false;
		
		@Option("Weight for class")
		public static int cov_class_weight = 3;

		@Option("Weight for method")
		public static int cov_method_weight = 2;

		@Option("Weight for selected method")
		public static int sel_method_weight = 1;

		@Option("Do not use an object filter")
		public static boolean JJ_nofilter = false;

		@Option("Use a HeapMatcher")
		public static boolean JJ_heapmatcher = false;
		
		@Option("Use a new approche which is a exact and compatitable type in the same probablity during choosing inputs by JJ")
		public static boolean JJ_exactcomp = false;

		@Option("Use a new approches which adds protect access level methods by JJ")
		public static boolean JJ_protect = false;

		@Option("Use a new approch which adds making array function by JJ")
		public static boolean JJ_array = false;

		// @Option("Use a new approch which includes static methods to be tested by
		// JJ")
		// public static boolean JJ_static = false;

		@Option("Use a new approch which uses Adaptive Random Testing technique")
		public static boolean JJ_ARTJJ = false;

		@Option("Use a new approch which uses Adaptive Random Testing technique")
		public static boolean JJ_ARTJJPure = false;
		
		@Option("Use a new approch which uses Adaptive Random Testing technique")
		public static boolean JJ_ARTOO = false;
		
		@Option("Use a new approch which reduce the number of test cases")
		public static boolean JJ_reduction = false;

//		@Option("fixCache")
//		public static boolean JJ_fixCache = false;

		@Option("Number of candidates for an input argument.")
		public static int nCandidates = 10;

		@Option("Used to determine when to stop test generation. "
				+ "Generation stops when reducted test cases are reached this option. ")
		public static int reductionlimit = 1000000;

		@Invisible
		@Option("Stop if it archeives 100% coverage rate")
		public static boolean stop_if100coverate = false;

		@Option("Target Exception Name. Generating test cases to occur a specific target")
		public static String targetException = null;
		
		@Invisible
		@Option("StackTraceElement 1")
		public static String stackTrace1 = null;
		@Option("StackTraceElement 2")
		public static String stackTrace2 = null;
		@Option("StackTraceElement 3")
		public static String stackTrace3 = null;
		@Option("StackTraceElement 4")
		public static String stackTrace4 = null;
		@Option("StackTraceElement 5")
		public static String stackTrace5 = null;
		public static boolean stopon1Crash = false;

		@Option("Prioritized Methods")
		public static List<String> prioritizedMethods = new ArrayList<String>();
		public static double priorityRate = 0.3;		
}
