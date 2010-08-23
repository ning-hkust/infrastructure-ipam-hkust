package edu.mit.csail.pag.objcap;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

import edu.mit.csail.pag.objcap.util.Logger;
import edu.mit.csail.pag.objcap.util.Util;

public class Agent {

	/**
	 * Called when Java is invoked with -javaagent pointing to a jar with this
	 * class as premain agent.
	 * 
	 * @throws IOException
	 */
	public static void premain(String agentArgs, Instrumentation inst)
			throws IOException {

		final edu.mit.csail.pag.objcap.instrumentation.Instrumentation trans = new edu.mit.csail.pag.objcap.instrumentation.Instrumentation();

		if (agentArgs != null) {
			String[] args = agentArgs.split("(  *)|(, *)");
			trans.parseArgs(args, 0);
		}

		if (Logger.verbose) {
			System.out.format("In premain, agentargs ='%s', "
					+ "Instrumentation = '%s'\n", agentArgs, inst);
		}

		inst.addTransformer(new ClassFileTransformer() {

			public byte[] transform(ClassLoader loader, String className,
					Class<?> classBeingRedefined,
					ProtectionDomain protectionDomain, byte[] classfileBuffer)
					throws IllegalClassFormatException {

				if (!Util.shouldInstrumentThisClass(Util
						.transClassNameDotToSlash(className))) {
					return null;
				}

				try {
					return trans.treeAPITransform(classfileBuffer);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});
	}
}
