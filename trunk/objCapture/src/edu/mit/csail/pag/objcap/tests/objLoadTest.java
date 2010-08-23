package edu.mit.csail.pag.objcap.tests;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Date;
import java.util.zip.ZipOutputStream;

import edu.mit.csail.pag.objcap.util.Serializer;

public class objLoadTest {
	public static String pathName = ".";

	public static void main(String args[]) throws IOException {
		ZipOutputStream out = null;

		if (args.length > 0)
			pathName = args[0];
		else
			pathName = "./"; // \\edu\\mit\\csail\\pag\\objcap\\tests\\Sample1.zip

		if (pathName.charAt(pathName.length() - 1) != '\\'
				&& pathName.charAt(pathName.length() - 1) != '/')
			pathName = pathName + "/";

		File folder = new File(pathName);

		if (!folder.exists()) {
			System.out.println("The folder of captured classes does not exist.");
			System.exit(-1);
		}

		findAllSubFolders(folder);

		/*
		 * for (Object obj : objSet) { File instrumentedFile = new
		 * File(outputFile, "\\hash_" + obj.hashCode() + "_at_" + new
		 * Date().getTime()); System.out.println("Writing " + instrumentedFile);
		 * Writer outt = new FileWriter(instrumentedFile);
		 * 
		 * try { Serializer.storeObject(obj, outt); } catch (Exception e) { //
		 * TODO Auto-generated catch block e.printStackTrace(); } outt.close(); }
		 */

	}

	public static void findAllSubFolders(File f) {
		if (f.list() == null)
			return;

		File[] files = f.listFiles();
		// Extract all the java file;
		for (int i = 0; i < files.length; i++) {
			if (files[i].getName().contains("hash")) {
				String fname = files[i].getAbsoluteFile().toString();
				String ClassName = fname.substring(pathName.length());
				ClassName = ClassName.replace('/', '.');
				ClassName = ClassName.replace(".zip", "");
				
				int separator = ClassName.lastIndexOf('.');
				String FileName = ClassName.substring(separator + 1);
				ClassName = ClassName.substring(0, separator);
				
				File instrumentedFile = new File(fname);
				
				try {
					Reader outt = new FileReader(instrumentedFile);
					Object obj = Serializer.loadObject(outt);
					outt.close();
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				try {
					System.out.println(ClassName + " !! " + FileName);
				} catch (Exception e) {
					System.out.println(e.getMessage());
					e.printStackTrace();
				}
			} else if (files[i].isDirectory()) {
				findAllSubFolders(files[i]);
			}

		}

		// total = total + count;

	}
}
