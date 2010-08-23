package randoop.util;

import java.io.PrintStream;

import utilMDE.Invisible;
import utilMDE.Option;

/**
 * Executes the code of a ReflectionCode object.
 * 
 * This class maintains an "executor" thread. Code is executed on that thread.
 * If the code takes longer than the specified timeout, the thread is killed and
 * a ReflectionExecutor.TimeoutExceeded exception is reported.
 * 
 */
public final class ReflectionExecutor {

	// Milliseconds after which an executing thread will be forcefully stopped.
	// Default is arbitrary; can be changed via setter method.

	@Invisible
	@Option("Milliseconds after which a statement (e.g. method call) is stopped forcefully. Only meaningfull with --usethreads.")
	public static long timeout = 5000;

	@Invisible
	@Option("Executing tested code in a separate thread (lets Randoop detect and kill nonterminating or long-running tests")
	public static boolean usethreads = true;

	public static class TimeoutExceeded extends RuntimeException {
		private static final long serialVersionUID = -5314228165430676893L;
	}

	public static Throwable executeReflectionCode(ReflectionCode code,
			PrintStream out) {
		if (usethreads) {
			return executeReflectionCodeThreaded(code, out);
		} else {
			return executeReflectionCodeUnThreaded(code, out);
		}
	}

	/**
	 * Executes code.runReflectionCode(). If no exception is thrown, returns
	 * null. Otherwise, returns the exception thrown.
	 * 
	 * @param code
	 * @param out
	 *            stream to print message to or null if message is to be
	 *            ignored.
	 */
	@SuppressWarnings("deprecation")
	public static Throwable executeReflectionCodeThreaded(ReflectionCode code,
			PrintStream out) {

    // altered by Ning
    // RunnerThread runnerThread = new RunnerThread(null);
    // runnerThread.setup(code);

		try {
      RunnerThread runnerThread = new RunnerThread(null);
      runnerThread.setup(code);

			// Start the test.
			runnerThread.start();

			// If test doesn't finish in time, suspend it.
			if (code
					.toString()
					.contains(
							"edu.mit.csail.pag.objcap.util.Serializer.loadObjectFromFile"))
				runnerThread.join(2 *60 * 1000);
			else
				runnerThread.join(timeout);

			if (!runnerThread.runFinished) {
				if (Log.isLoggingOn()) {
					Log.log("Exceeded max wait: aborting test input.");
				}

				// runnerThread.interrupt();
				runnerThread.stop();// We use this deprecated method because
				// it's the only way to
				// stop a thread no matter what it's doing.
				return new ReflectionExecutor.TimeoutExceeded();
			}

			return runnerThread.exceptionThrown;

		} catch (java.lang.InterruptedException e) {
			throw new IllegalStateException(
					"A RunnerThread thread shouldn't be interrupted by anyone! "
							+ "(this may be a bug in Randoop; please report it.)");
		}
	}

	/**
	 * without threads.
	 */
	public static Throwable executeReflectionCodeUnThreaded(
			ReflectionCode code, PrintStream out) {
		try {
			code.runReflectionCode();
			return null;
		} catch (ThreadDeath e) {// can't stop these guys
			throw e;
		} catch (ReflectionCode.NotCaughtIllegalStateException e) {// exception
			// in
			// randoop
			// code
			throw e;
		} catch (Throwable e) {
			if (e instanceof java.lang.reflect.InvocationTargetException)
				e = e.getCause();

			if (out != null) {
				out.println("Exception thrown:" + e.toString());
				out.println("Message: " + e.getMessage());
				out.println("Stack trace: ");
				e.printStackTrace(out);
			}
			return e;
		}
	}
}
