package edu.mit.csail.pag.objcap.instrumentation;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

import edu.mit.csail.pag.objcap.ObjCapture;
import edu.mit.csail.pag.objcap.util.Logger;
import edu.mit.csail.pag.objcap.util.Util;

public class Instrumentation implements Opcodes {
	public void transformDir(File srcDir, File descDir) throws IOException {
		assert (srcDir != null);
		assert (descDir != null);

		if (!srcDir.exists()) {
			Logger.fatal(srcDir + " does not exist!");
			return;
		}

		if (srcDir.isFile()) {
			transformFile(srcDir, descDir);
			return;
		}

		File[] subFiles = srcDir.listFiles();
		if (subFiles == null) {
			return;
		}

		for (int i = 0; i < subFiles.length; i++) {
			String subFileName = subFiles[i].getAbsolutePath();
			String addedOnFileName = subFileName.substring(srcDir.getAbsolutePath().length());
			if (subFiles[i].isFile()) {
				File descFile = new File(descDir, addedOnFileName);
				Logger.info("Transformming : " + subFiles[i] + " -> " + descFile);
				transformFile(subFiles[i], descFile);
			} else if (subFiles[i].isDirectory()) {
				transformDir(subFiles[i], new File(descDir, addedOnFileName));
			}
		}
	}

	private void transformFile(File in, File out) throws IOException {
		if (in == null || out == null) {
			return;
		}

		// create the directory for the out
		if (out.getParentFile() != null) {
			out.getParentFile().mkdirs();
		}

		// ### FIXME: is there more reliable way to check if it is a class file
		// or a jar file
		if (in.getName().endsWith(".class")) {
			transformClassFile(in, out);
		} else if (in.getName().endsWith(".jar")) {
			transformJarFile(in, out);
		} else {
			Util.copyFile(in, out);
		}
	}

	/**
	 * Transform a jar file
	 * 
	 * @param in
	 * @param out
	 * @throws IOException
	 */
	private void transformJarFile(File in, File out) throws IOException {
		// jar file
		JarFile inJar = new JarFile(in);

		// jar output stream
		JarOutputStream outJarStream = new JarOutputStream(new FileOutputStream(out));

		// get all jar entries
		Enumeration<JarEntry> entries = inJar.entries();
		while (entries.hasMoreElements()) {
			// get a jar entry and input stream
			JarEntry entry = entries.nextElement();
			InputStream inJarStream = inJar.getInputStream(entry);

			// add the jar entry
			outJarStream.putNextEntry(new JarEntry(entry.getName()));
			if (entry.isDirectory()) {
				// do nothing for directory
			} else if (entry.getName().endsWith(".class")) {
				transformClassStream(inJarStream, outJarStream);
				// a jar file inside jar?
			}
			// else if (entry.getName().endsWith(".jar")) {
			// transformJarStream(inJarStream, outJarStream);
			// }
			else {
				Util.copyStream(inJarStream, outJarStream);
			}

			// close the inJar stream
			inJarStream.close();
		}

		outJarStream.close();
		inJar.close();
	}

	/**
	 * Transform a class file
	 * 
	 * @param classFile
	 * @param transformedClassFile
	 * @throws IOException
	 */
	private void transformClassFile(File classFile, File transformedClassFile) throws IOException {
		FileInputStream classFileInputStream = new FileInputStream(classFile);

		Logger.println("Instrumeting  : " + classFile + " -> " + transformedClassFile);
		FileOutputStream fout = new FileOutputStream(transformedClassFile);
		transformClassStream(classFileInputStream, fout);
		classFileInputStream.close();
		fout.close();
	}

	/**
	 * Transform class stream
	 * 
	 * @param classFileInputStream
	 * @param fout
	 * @throws IOException
	 */
	private void transformClassStream(InputStream classFileInputStream, OutputStream fout)
			throws IOException {
		byte[] transformcledClassByte = treeAPITransform(classFileInputStream);
		fout.write(transformcledClassByte);
	}

	public byte[] treeAPITransform(InputStream classIn) throws IOException {
		ClassReader cr = new ClassReader(classIn);
		return treeAPITransform(cr);
	}

	public byte[] treeAPITransform(byte[] classIn) throws IOException {
		ClassReader cr = new ClassReader(classIn);
		return treeAPITransform(cr);
	}

	@SuppressWarnings("unchecked")
	public byte[] treeAPITransform(ClassReader cr) throws IOException {
		ClassNode cn = new ClassNode();
		cr.accept(cn, ClassReader.SKIP_FRAMES);

		// this is the main transformer
		transformClassNode(cn);
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
		
		cn.accept(cw);

		if (false) {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			CheckClassAdapter.verify(new ClassReader(cw.toByteArray()), false, pw);
			// TestCase.assertTrue(sw.toString(), sw.toString().length() == 0);
		}

		return cw.toByteArray();
	}

	/**
	 * Main transformer
	 * 
	 * @param cn
	 * @return
	 */
	private boolean transformClassNode(ClassNode cn) {
		if (!Util.shouldInstrumentThisClass(cn.name)) {
			return false;
		}

		// skip enum related ones
		if (cn.toString().indexOf("enum") != -1) {
			return false;
		}

		// No instrumentation for the interface
		if ((cn.access & ACC_INTERFACE) != 0) {
			return false;
		}

		// No instrumentation for the enum type
		if ((cn.access & ACC_ENUM) != 0) {
			return false;
		}

		// Skip inner classtransformClassNode
		if (false && cn.name.indexOf('$') != -1) {
			return false;
		}

		Logger.println("transforming  " + cn.name);

		Hashtable<String, FieldNode> allFieldTypeTable = new Hashtable<String, FieldNode>();

		// ## Let's make a final filed a non final
		for (FieldNode fn : (List<FieldNode>) cn.fields) {
			fn.access &= ~ACC_FINAL;
			allFieldTypeTable.put(fn.name, fn);
		}
		
		//cn.fields.add(new FieldNode(ACC_PRIVATE & ACC_STATIC, ObjCapture.callcountfield, "I",
		//		null, new Integer(0)));
		
		return instrumentEachMethod(cn);
	}

	private boolean instrumentEachMethod(ClassNode cn) {
		boolean bWithReceiver = true; //save receiver objects?
		boolean foundMainMethod = false;
		//Type classNodeObjectType = Type.getObjectType(cn.name);

		for (MethodNode mn : (List<MethodNode>) cn.methods) {

			// get instruction sets
			InsnList insns = mn.instructions;
			if (insns.size() == 0) {
				continue;
			}

			// get types from method description
			Type[] types = Type.getArgumentTypes(mn.desc);

			Type[] selectedTypes = SelectTypes.selectTypes(types);
			if (selectedTypes.length == 0) {
				if (cn.fields.size() == 0)
					continue;
			}


			// Ignore constructors
			if ("<init>".equals(mn.name) || "<clint>".equals(mn.name)) {
				continue;
			}

			if ((mn.access & ACC_ABSTRACT) != 0) { // no abstract
				continue;
			}
			
			if ((mn.access & ACC_STATIC) != 0 ) {
				// static 
				continue;
			}

			if (false && (mn.access & ACC_STATIC) != 0 && !"main".equals(mn.name)) {
				// static and not main
				continue;
			}
			
			//FIXME: this is temporal solution, we need to prevent recursive call permanently
			if ("toString".equals(mn.name)) continue;
			if ("hashCode".equals(mn.name)) continue;
			if ("next".equals(mn.name)) continue;

			if (!bWithReceiver)
			{
				if ("iterator".equals(mn.name)) continue;
				if ("hasNext".equals(mn.name)) continue;
				if ("entryIterator".equals(mn.name)) continue;
				if ("entrySet".equals(mn.name)) continue;
				if ("clone".equals(mn.name)) continue;
				if ("writeObject".equals(mn.name)) continue;
				if ("get".equals(mn.name)) continue;
				if ("getReadMethod".equals(mn.name)) continue;
			}
			/*
			if ("getValue".equals(mn.name) && selectedTypes.length == 0) {
				// static and not main
				continue;
			}*/			

			/*if ((mn.access & ACC_PUBLIC) == 0) { // no non-public
				continue;
			}*/

			Logger.info("Instrumenting " + mn.name + ":" + mn.desc);

			// Static void main?
			if ("main".equals(mn.name) && "([Ljava/lang/String;)V".equals(mn.desc)
					&& (mn.access & ACC_PUBLIC) != 0 && (mn.access & ACC_STATIC) != 0) {
				Logger.info("We found main for " + cn.name + "!");

				// mark that we find the main
				// we need to write objects in the memory to a file
				foundMainMethod = true;
			}

			// If it is main, we need to find all ret point and add flush code
			if (foundMainMethod && false) { // it will take care of by shutdown
				// hook
				/*
				 * Static analysis part for main method. First find where is the
				 * return point
				 */
				List<AbstractInsnNode> retInsertPointList = new ArrayList<AbstractInsnNode>();

				Iterator<AbstractInsnNode> j = insns.iterator();
				while (j.hasNext()) {
					AbstractInsnNode in = j.next();

					int op = in.getOpcode();
					if (in instanceof LabelNode) {
						continue; // do nothing with label
					}

					if ((op >= IRETURN && op <= RETURN) /* || op == ATHROW */) {
						retInsertPointList.add(in.getPrevious());
						continue;
					}
				}
				// Add flush part for all return points in main
				for (AbstractInsnNode retPoint : retInsertPointList) {
					InsnList il = new InsnList();
					/*
					 * INVOKESTATIC
					 * edu/mit/csail/pag/objcap/ObjCapture.writeObjTreeToFile()V
					 */
					il.add(new MethodInsnNode(INVOKESTATIC, "edu/mit/+csail/pag/objcap/ObjCapture",
							"writeObjTreeToFile", "()V"));
					insns.insert(retPoint, il);
				}
			}

			// instrument and add ObjectAll method with an object array
			InsnList il;
			
			if (bWithReceiver)
				il = insertAddObjectMethodWithReceiver(cn, mn, selectedTypes);
			else
				il = insertAddObjectMethod(cn, mn, selectedTypes);
			insns.insert(il);
		} // for (MethodNode mn : (List<MethodNode>) cn.methods)

		return foundMainMethod;
	}

	
	// to save receiver object
	private InsnList insertAddObjectMethodWithReceiver(ClassNode cn, MethodNode mn, Type[] selectedTypes) {

		// Add objectStore with an Object array
		InsnList il = new InsnList();

		// keep local type size
		int localVarIndex = 0;

		
		Type[] savingTypes;
		if ((cn.access & ACC_STATIC) == 1) { 
			savingTypes = selectedTypes;
			System.out.println("STATIC " + cn.name);
		} else {
			// If it is not static
			// add receiver object
			savingTypes = new Type[selectedTypes.length + 1];
			for (int i = 0; i < selectedTypes.length; i++)
				savingTypes[i + 1] = selectedTypes[i];
			savingTypes[0] = Type.getObjectType(cn.name);

			// keep local type size
			Type classNodeObjectType = Type.getObjectType(cn.name);
			localVarIndex += classNodeObjectType.getSize();
			System.out.println("getin:" + cn.name);			
		}	
		
		int arraySize = savingTypes.length;
		
		// create array for objects
		// array length = arg num + 1 (for this)
		il.add(getInstForIConst(arraySize));
		il.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));

		int startindex = 0;
		if ((mn.access & ACC_STATIC) == 0 && true) {
			il.add(new InsnNode(DUP));
			il.add(getInstForIConst(0));

			il.add(new VarInsnNode(ALOAD, 0));
			// il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Object",
			// "valueOf",
			// "(A)Ljava/lang/Object;"));
			il.add(new InsnNode(AASTORE));
			startindex = 1;
		}

		for (int t = startindex; t < savingTypes.length; t++) {
			Logger.info("Type object to instrument: " + savingTypes[t] + ": "
					+ savingTypes[t].getClassName());

			addObjectArrayElement(t, il, savingTypes[t], localVarIndex, cn);
			// size of type can be different
			localVarIndex += savingTypes[t].getSize();
		}


		il.add(new MethodInsnNode(INVOKESTATIC, "edu/mit/csail/pag/objcap/ObjCapture", "storeAll",
				"([Ljava/lang/Object;)V"));

		

		
		return il;
	}
	/**/
	private InsnList insertAddObjectMethod(ClassNode cn, MethodNode mn,
		Type[] selectedTypes) {

		// Add objectStore with an Object array
		InsnList il = new InsnList();
	
		// keep local type size
		int localVarIndex = 0;
	
		// If it is not static
		if ((mn.access & ACC_STATIC) == 0) {
			// keep local type size
			Type classNodeObjectType = Type.getObjectType(cn.name);
			localVarIndex += classNodeObjectType.getSize();
		}
	
		int arraySize = selectedTypes.length;
		// create array for objects
		// array length = arg num + 1 (for this)
		il.add(getInstForIConst(arraySize));
		il.add(new TypeInsnNode(ANEWARRAY, "java/lang/Object"));
	
		for (int t = 0; t < selectedTypes.length; t++) {
			Logger.info("Type object to instrument: " + selectedTypes[t] + ": "
					+ selectedTypes[t].getClassName());
	
			addObjectArrayElement(t, il, selectedTypes[t], localVarIndex, cn);
			// size of type can be different
			localVarIndex += selectedTypes[t].getSize();
		}
	
	
		il.add(new MethodInsnNode(INVOKESTATIC,
				"edu/mit/csail/pag/objcap/ObjCapture", "storeAll",
				"([Ljava/lang/Object;)V"));
	
		return il;
	}

	/**
	 * 
	 * @param iConst
	 * @return
	 */
	private AbstractInsnNode getInstForIConst(int iConst) {
		switch (iConst) {
		case 0:
			return new InsnNode(ICONST_0);
		case 1:
			return new InsnNode(ICONST_1);
		case 2:
			return new InsnNode(ICONST_2);
		case 3:
			return new InsnNode(ICONST_3);
		case 4:
			return new InsnNode(ICONST_4);
		case 5:
			return new InsnNode(ICONST_5);
		default:
			return new IntInsnNode(BIPUSH, iConst);
		}
	}

	private void addObjectArrayElement(int index, InsnList il, Type type, int argLocation,
			ClassNode cn) {
		il.add(new InsnNode(DUP));
		il.add(getInstForIConst(index));

		if (type.equals(Type.BOOLEAN_TYPE)) {
			il.add(new VarInsnNode(ILOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Boolean", "valueOf",
					"(Z)Ljava/lang/Boolean;"));
			// typeClassName = Boolean.class.getCanonicalName();
		} else if (type.equals(Type.CHAR_TYPE)) {
			il.add(new VarInsnNode(ILOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Character", "valueOf",
					"(C)Ljava/lang/Character;"));
			// typeClassName = Character.class.getCanonicalName();
		} else if (type.equals(Type.BYTE_TYPE)) {
			il.add(new VarInsnNode(ILOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Byte", "valueOf",
					"(B)Ljava/lang/Byte;"));
			// typeClassName = Boolean.class.getCanonicalName();
		} else if (type.equals(Type.SHORT_TYPE)) {
			il.add(new VarInsnNode(ILOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Short", "valueOf",
					"(S)Ljava/lang/Short;"));
			// typeClassName = Short.class.getCanonicalName();
		} else if (type.equals(Type.INT_TYPE)) {
			il.add(new VarInsnNode(ILOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Integer", "valueOf",
					"(I)Ljava/lang/Integer;"));
			// typeClassName = Integer.class.getCanonicalName();
		} else if (type.equals(Type.FLOAT_TYPE)) {
			il.add(new VarInsnNode(FLOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Float", "valueOf",
					"(F)Ljava/lang/Float;"));
			// typeClassName = Float.class.getCanonicalName();
		} else if (type.equals(Type.LONG_TYPE)) {
			il.add(new VarInsnNode(LLOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Long", "valueOf",
					"(J)Ljava/lang/Long;"));
			// typeClassName = Long.class.getCanonicalName();
		} else if (type.equals(Type.DOUBLE_TYPE)) {
			il.add(new VarInsnNode(DLOAD, argLocation));
			il.add(new MethodInsnNode(INVOKESTATIC, "java/lang/Double", "valueOf",
					"(D)Ljava/lang/Double;"));
			// typeClassName = Double.class.getCanonicalName();
		} else {
			il.add(new VarInsnNode(ALOAD, argLocation));
		}

		il.add(new InsnNode(AASTORE));
	}

	public static void main(String args[]) throws IOException {
		if (args.length < 2) {
			usage();
			return;
		}

		Instrumentation instrument = new Instrumentation();
		instrument.parseArgs(args, 2);
		instrument.transformDir(new File(args[0]), new File(args[1]));
	}

	public void parseArgs(String[] args, int argStartNo) throws IOException {
		Logger.verbose = false;
		for (int i = argStartNo; i < args.length; i++) {
			if (args[i].equals("-verbose")) {
				Logger.verbose = true;
			}
		}
	}

	public static void usage() {
		System.out.println("transform <src> <desc> <options>");
		System.out.println("Options:");
		System.out.println("  -verbose (prints information)");
		System.exit(0);
	}
}
