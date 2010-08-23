package randoop.util;

import java.io.FileWriter;
import java.io.IOException;

import randoop.Globals;
import randoop.SequenceGeneratorStats;
import utilMDE.UtilMDE;
import randoop.SequenceGeneratorStats;


/**
 * Taken and modified from Daikon.FileIOProgress (or something like that).
 */
public class ProgressDisplay extends Thread {

	private int progresswidth = 170;

	public static enum Mode {
		SINGLE_LINE_OVERWRITE, MULTILINE, NO_DISPLAY
	}

	private Mode outputMode;

	private long progressIntervalMillis = 1000;

	private SequenceGeneratorStats stats;

	private FileWriter csvout = null;

	public ProgressDisplay(SequenceGeneratorStats stats, Mode outputMode,
			int progressIntervalSeconds, int progressWidth, FileWriter csvout) {
		this.stats = stats;
		this.progressIntervalMillis = progressIntervalSeconds * 1000;
		this.outputMode = outputMode;
		this.progresswidth = progressWidth;
		this.csvout = csvout;
		setDaemon(true);
	}

	private int queries = 0;

	public String message() {
		StringBuilder b = new StringBuilder();
		if (queries++ % 10 == 0)
			b.append(stats.getTitle());
		b.append(stats.toStringGlobal());
		return b.toString();
	}

	/** 
	 * Clients should set this variable instead of calling Thread.stop(), which
	 * is deprecated. Typically a client calls "display()" before setting this.
	 */
	public boolean shouldStop = false;

	@Override
	public void run() {
		while (true) {
			if (shouldStop) {
				clear();
				return;
			}
			display();
			updateLastBranchCov();
			try {
				sleep(progressIntervalMillis);
				stats.addToCount(stats.STAT_ELAPSED_SECOND, progressIntervalMillis / 1000);
			} catch (InterruptedException e) {
				// hmm
			}
		}
	}

	public int lastCovIncrease = 0;

	public int lastNumBranches = 0;
	
	public long prvcov=0,prvtc=0;

	private void updateLastBranchCov() {
		if (stats.branchesCovered.size() > lastNumBranches) {
			lastCovIncrease = 0;
			lastNumBranches = stats.branchesCovered.size();
		} else {
			lastCovIncrease++;
		}
	}

	/** Clear the display; good to do before printing to System.out. * */
	public void clear() {
		if (progressIntervalMillis == -1)
			return;
		// "display("");" is wrong becuase it leaves the timestamp and writes
		// spaces across the screen.
		String status = UtilMDE.rpad("", progresswidth - 1);
		System.out.print("\r" + status);
		System.out.print("\r"); // return to beginning of line
		System.out.flush();
	}

	/**
	 * Displays the current status. Call this if you don't want to wait until
	 * the next automatic display.
	 */
	public void display() {
		if (progressIntervalMillis == -1)
			return;
		display(message());

		long testCases = stats.getGlobalStats().getCount(SequenceGeneratorStats.STAT_NOT_DISCARDED);
		long cov = stats.getGlobalStats().getCount(SequenceGeneratorStats.STAT_BRANCHCOV);
		long tot = stats.getGlobalStats().getCount(SequenceGeneratorStats.STAT_BRANCHTOT);
		long sec = stats.getGlobalStats().getCount(SequenceGeneratorStats.STAT_ELAPSED_SECOND);
		double coverage = ((double)cov / tot) * 100;
		String covs = String.format("%.1f", coverage);
		
		String s = cov + "," + covs + "," + testCases + "," + sec + ",\n";

		if (csvout != null)
			try {
				if (prvcov != cov)
				csvout.write(s);
				csvout.flush();
			} catch (IOException e) {
				e.printStackTrace();
				System.exit(1);
			}
			
			prvcov = cov;
			prvtc = testCases;
	}

	/** Displays the given message. * */
	public void display(String message) {
		if (progressIntervalMillis == -1)
			return;
		String status = message;
		System.out.print((this.outputMode == Mode.SINGLE_LINE_OVERWRITE ? "\r" : Globals.lineSep)
				+ status);
		System.out.flush();
		// System.out.println (status);

		// if (Log.loggingOn) {
		// Log.log("Free memory: "
		// + java.lang.Runtime.getRuntime().freeMemory());
		// Log.log("Used memory: "
		// + (java.lang.Runtime.getRuntime().totalMemory() - java.lang.Runtime
		// .getRuntime().freeMemory()));
		// }
	}
}
