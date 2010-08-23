package randoop.ocat;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import randoop.PrimitiveOrStringOrNullDecl;
import randoop.Sequence;
import randoop.main.GenInputsAbstract;
import randoop.util.PrimitiveTypes;
import randoop.util.Randomness;

public final class CapturedObjectSequences {
	private static String prevErrorMsg = "";
	private static String root = "";

	private CapturedObjectSequences() {
		throw new IllegalStateException("no instance");
	}

	public static Set<Sequence> loadCapturedObjects(
			String capturedObjectPathoo, String className) {
		if (PrimitiveTypes.isPrimitiveOrStringTypeName(className)) {
			Class<?> clazz = PrimitiveTypes.getBoxedType(className);
			if (clazz != null)
				className = clazz.getName();
		}

		if (className.equals("java.lang.Boolean")) {
			Set<Sequence> retval = new LinkedHashSet<Sequence>();

			retval.add(Sequence.create(new PrimitiveOrStringOrNullDecl(
					boolean.class, Randomness.nextRandomBool())));

			return retval;

		}

		String capturedObjectPath = capturedObjectPathoo + "/"
				+ className.replaceAll("\\.", "/");
		capturedObjectPath = capturedObjectPath.replaceAll("//", "/");

		if (capturedObjectPath == "")
			return null;

		if (capturedObjectPath.charAt(capturedObjectPath.length() - 1) != '\\'
				&& capturedObjectPath.charAt(capturedObjectPath.length() - 1) != '/')
			capturedObjectPath = capturedObjectPath + "/";

		File folder = new File(capturedObjectPath);

		if (!folder.exists()) {
			System.out.println("ERROR: The folder of captured classes ["
					+ capturedObjectPath
					+ "] does not exist (loadCapturedObjects).");
			return null;
			// System.exit(-1);
		}

		Set<Sequence> retval = loadCapturedObjects4Class(folder,
				capturedObjectPath, className);

		return retval;
	}

	public static Set<Sequence> loadCapturedObjects(
			List<String> capturedObjectPath) {
		if (capturedObjectPath.size() == 0)
			return null;

		Set<Sequence> retval = new LinkedHashSet<Sequence>();
		for (String capath : capturedObjectPath) {
			if (capath.charAt(capath.length() - 1) != '\\'
					&& capath.charAt(capath.length() - 1) != '/')
				capath = capath + "/";

			File folder = new File(capath);

			if (!folder.exists()) {
				System.out.println("ERROR: The folder of captured classes ["
						+ capath + "] does not exist (loadCapturedObjects).");
				return null;
				// System.exit(-1);
			}

			try {
				root = folder.getCanonicalPath();

				retval.addAll(loadCapturedObjects4All(folder, capath));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return retval;
	}

	public static Set<Sequence> loadCapturedObjects4All(File f,
			String capturedObjectPath) throws IOException {
		if (f.list() == null)
			return null;

		Set<Sequence> retval = new LinkedHashSet<Sequence>();

		File[] files = f.listFiles();
		// Extract all the java file;
		boolean thisfolderchecked = false;
		for (int i = 0; i < files.length; i++) {
			if (files[i].getName().contains("hash")) {
				if (!thisfolderchecked)
				{
					if (!isTestClass(files[i].getCanonicalPath()))
						return retval;
				}

				thisfolderchecked = true;
				retval.addAll(loadCapturedObjects(files[i], capturedObjectPath,
						null));
			} else if (files[i].isDirectory()) {
				retval.addAll(loadCapturedObjects4All(files[i],
						capturedObjectPath));
			}

		}
		return retval;
	}

	private static boolean isTestClass(String capath) {
		String primitives[] = { "float", "double", "long", "short", "byte",
				"char", "boolean", "int", "java", "sun" };

		String tmp = capath.substring(root.length() + 1);

		for (String prim : primitives) {
			if (tmp.startsWith(prim))
				return true;
		}

		for (String tcls : GenInputsAbstract.testclass) {
			int idxl = tcls.lastIndexOf(".");
			tcls = tcls.substring(idxl + 1);
			if (tmp.contains(tcls))
			{
        // System.out.println("Loaded: " + capath);
				return true;
			}
		}
		return false;
	}

	// traverse only one folder
	public static Set<Sequence> loadCapturedObjects4Class(File f,
			String capturedObjectPath, String className) {
		if (f.list() == null)
			return null;

		Set<Sequence> retval = new LinkedHashSet<Sequence>();

		File[] files = f.listFiles();
		// Extract all the java file;
		for (int i = 0; i < files.length; i++) {
			if (files[i].getName().contains("hash")) {
				retval.addAll(loadCapturedObjects(files[i], capturedObjectPath,
						className));
			}
		}
		return retval;
	}

	public static Set<Sequence> loadCapturedObjects(File file,
			String capturedObjectPath, String className) {
		Set<Sequence> retval = new LinkedHashSet<Sequence>();
		String fileName = file.getAbsoluteFile().toString();

		// if classname is null, capturedObjectPath includes all class names.
		if (className == null) {
			className = fileName.substring(capturedObjectPath.length());
			className = className.replace('\\', '.');
			className = className.replace('/', '.');
			className = className.replace(".zip", "");
			className = className.trim();

			int separator = className.lastIndexOf('.');
			// String FileName = ClassName.substring(separator + 1);
			if (separator < 0) {
				System.out.println("NotParcedClassName:" + className);
				System.exit(-1);
			}
			className = className.substring(0, separator);
		}

		try {
      // Serializer.loadObjectFromFile(fileName);
			CapturedObjectDecl co = new CapturedObjectDecl(className, fileName);
			Sequence seq = Sequence.create(co);
			retval.add(seq);
		} catch (IllegalArgumentException e) {
			if (!prevErrorMsg.equals(e.getMessage())) {
				System.out.println(e.getMessage());
				prevErrorMsg = e.getMessage();
			}
			// e.printStackTrace();
			return retval;
		}

		return retval;

	}
}
