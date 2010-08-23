package cov;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import utilMDE.Option;
import utilMDE.Options;
import utilMDE.Options.ArgException;

/**
 * A program that performs a number of utility functions. The program's first
 * argument is the command, and the rest of the arguments are parameters to the
 * command.
 * 
 * Commands:
 * 
 * union
 * 
 * Requires >= 2 --in options, --out option. Unions the branches contained in
 * the input files. Outputs the results to the output file.
 * 
 */
public class CovUtil {

	@Option("The command to execute.")
	public static String comm = null;

	@Option("Input file (may repeat, depending on command).")
	public static List<String> in = new ArrayList<String>();

	@Option("Output file (what's in it depends on command).")
	public static String out = null;

	public static void main(String[] args) {

		// Parse options and ensure that a scratch directory was specified.
		Options options = new Options(CovUtil.class);
		try {
			options.parse(args);
		} catch (ArgException e) {
			throw new Error(e);
		}
		if (comm == null) {
			throw new Error("--comm option expected.");
		}

		if (comm.equals("union")) {
			union();
		} else {
			throw new Error("Invalid command: " + comm);
		}

	}

	public static void union() {

		if (in.size() < 2)
			throw new Error("Expected at least two inputs.");
		if (out == null) {
			throw new Error("Expected option --out");
		}

		Set<Branch> branches = new LinkedHashSet<Branch>();
		for (String s : in) {
			branches.addAll(Branch.readFromFile(s));
		}

		Branch.writeToFile(branches, out, false);
	}

}
