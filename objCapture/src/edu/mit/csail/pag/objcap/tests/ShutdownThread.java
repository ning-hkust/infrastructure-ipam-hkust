package edu.mit.csail.pag.objcap.tests;

// The ShutdownThread is the thread we pass to the
// addShutdownHook method
public class ShutdownThread extends Thread {
	public void run() {
		System.out.println("[Shutdown thread] Shutting down");
		System.out.println("[Shutdown thread] Shutdown complete");
	}
}
