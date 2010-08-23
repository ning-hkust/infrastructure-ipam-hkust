package edu.mit.csail.pag.objcap.instrumentation;

import org.objectweb.asm.Type;

/**
 * We only store
 * 
 * @author hunkim
 * 
 */
public class SelectTypes {
	/**
	 * Get only necessary types
	 * @param types
	 * @return
	 */
	public static Type[] selectTypes(Type[] types) {
		return types;
	}
}
