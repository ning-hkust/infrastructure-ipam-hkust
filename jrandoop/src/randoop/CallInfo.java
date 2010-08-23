package randoop;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import randoop.util.Files;

public class CallInfo {
	List<StatementKind> statements;

	HashMap<String, StatementKind> StringToStatementMap = new HashMap<String, StatementKind>();

	public HashMap<StatementKind, Set<StatementKind>> callMap;// key = caller,

	// value =
	// callee

	public CallInfo(File callInfoFile, List<StatementKind> model)
			throws IOException {
		callMap = new HashMap<StatementKind, Set<StatementKind>>();
		statements = model;
		loadClassesFromFile(callInfoFile);

		if (true) {
			printCallmap();
			printStatements();
		}
	}

	public void printStatements() throws IOException {
		File f = new File("statements.out");
		f.delete();
		FileWriter fw = new FileWriter(f);
		BufferedWriter writer = null;
		writer = new BufferedWriter(fw);

		for (StatementKind sk : statements) {
			writer.append(sk.toString());
			writer.newLine();
		}
		writer.close();
	}

	public void printCallmap() throws IOException {
		File f = new File("callhier.out");
		f.delete();
		FileWriter fw = new FileWriter(f);
		BufferedWriter writer = null;
		writer = new BufferedWriter(fw);

		for (StatementKind sk : callMap.keySet()) {
			writer.append(sk.toString());
			writer.newLine();
			Set<StatementKind> sks = callMap.get(sk);
			for (StatementKind sk2 : sks) {
				writer.append("-" + sk2.toString());
				writer.newLine();
			}
			writer.newLine();
		}
		writer.close();
	}

	public void add_set(StatementKind key, StatementKind value) {
		Set<StatementKind> v = new HashSet<StatementKind>();
		v.add(value);

		Set<StatementKind> ret = callMap.get(key);

		if (ret == null)
			ret = new HashSet<StatementKind>();

		ret.addAll(v);
		callMap.put(key, ret);
	}

	public void loadClassesFromFile(File callInfoFile) throws IOException {
		BufferedReader reader = null;
		try {
			reader = Files.getFileReader(callInfoFile);
			loadClassesFromReader(reader);
			return;
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	public void loadClassesFromReader(BufferedReader reader) {
		try {
			List<String> lines = Files.readWhole(reader);
			loadClassesFromLines(lines);
			return;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void loadClassesFromLines(List<String> lines) {
		int stage = 0;
		StatementKind key = null;
		try {
			for (String line : lines) {
				String trimmed = line.trim();
				if (trimmed.equals("") || trimmed.startsWith("#")) {
					stage = 0;
					continue;
				}

				if (stage == 0) {
					if (trimmed.charAt(0) == '-')
						continue;
					else {
						key = FindStatementByString(trimmed);
						if (key == null) {
							// System.out.println("Notfound : " + trimmed);
							continue;
						}

						stage = 1;
					}
				} else if (stage == 1) {
					if (trimmed.charAt(0) == '-') {
						StatementKind callee = FindStatementByString(trimmed
								.substring(1));
						if (callee != null)
							add_set(key, callee);
						// else
						// System.out.println("- Notfound : " +
						// trimmed.substring(1));
					} else
						stage = 0;
				} else {
					throw new Exception();
				}

			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}

	private StatementKind FindStatementByString(String methodname) {
		if (StringToStatementMap.containsKey(methodname))
			return StringToStatementMap.get(methodname);

		StatementKind ret = null;
		for (StatementKind sk : statements) {
			String sk_str = sk.toString();
			if (methodname.equals(sk_str)) {
				ret = sk;
				break;
			}
		}

		if (ret != null) {
			StringToStatementMap.put(methodname, ret);
			// System.out.println("Found " + methodname);
		} else if (false)
			System.out.println("No    " + methodname);

		return ret;
	}

	public Set<StatementKind> getCalles(StatementKind caller) {
		if (callMap.containsKey(caller))
			return (Set<StatementKind>) callMap.get(caller);

		return null;

	}
}
