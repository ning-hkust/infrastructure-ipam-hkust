package edu.mit.csail.pag.objcap;

public class FlushObjectThread extends Thread {
	/**
	 * Should we make it thread safe?
	 */
	public void run() {
		System.out.println("[Shutdown thread] Shutting down");
		ObjCapture.writeObjTreeToFile();
		System.out.println("[Shutdown thread] Shutdown complete");
	}
}
