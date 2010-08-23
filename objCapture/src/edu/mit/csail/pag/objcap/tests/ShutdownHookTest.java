package edu.mit.csail.pag.objcap.tests;



// And finally a Main class which tests the two classes
// We let the sample thread run for 10 seconds and then
// force a Shutdown with System.exit(0). You may stop the
// program early by pressing CTRL-C.
public class ShutdownHookTest {
	public static void main(String[] args) {
		try {
			Runtime.getRuntime().addShutdownHook(new ShutdownThread());
			System.out.println("[Main thread] Shutdown hook added");
		} catch (Throwable t) {
			System.out.println("[Main thread] Could not add Shutdown hook");
		}
	}
}