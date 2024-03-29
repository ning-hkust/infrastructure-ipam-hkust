package edu.mit.csail.pag.objcap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.XStreamException;

import edu.mit.csail.pag.objcap.util.Serializer;
import edu.mit.csail.pag.objcap.util.Util;
import edu.mit.csail.pag.utils.EqualsMethodMatcher;
import edu.mit.csail.pag.utils.Pair;
import edu.mit.csail.pag.utils.StateMatcher;

/**
 * ObjCapture main interface
 * 
 * @author hunkim
 * 
 */
public class ObjCapture {
	public final static String callcountfield = "objCaptureCallCount";
	private static int storeCount = 0;

	static int MAX_STORE_COUNT = 0;
	static int callcount = 0;

	private static Hashtable<Class<?>, Pair<List<Object>, StateMatcher>> objTable = new Hashtable<Class<?>, Pair<List<Object>, StateMatcher>>();

	/**
	 * Add shutdown hooks
	 */
	static {
		try {
			Runtime.getRuntime().addShutdownHook(new FlushObjectThread());
			System.out.println("[Main thread] Shutdown hook added");
		} catch (Throwable t) {
			System.out.println("[Main thread] Could not add Shutdown hook");
		}
	}

	/** Store array of objects */
	public static void storeAll(Object[] objects) {
		if (objects == null)
			return;
		if (objects.length == 0)
			return;
		/*
		 * int callcount = 0; boolean bThereisField = false;
		 * 
		 * 
		 * try { Field fcallcount =
		 * objects[0].getClass().getField(callcountfield); callcount = (Integer)
		 * fcallcount.getInt(objects[0]); callcount++; bThereisField = true;
		 * }catch (NoSuchFieldException e){ callcount = 0; } catch
		 * (IllegalArgumentException e) { callcount = 0; } catch
		 * (IllegalAccessException e) { callcount = 0; }
		 */

		callcount++;

		// if this object is recursively called through instrumented 'storeAll'
		// object, then don't save
		if (callcount < 2)
			for (Object obj : objects) {
				store(obj);
			}

		if (callcount > 0)
			callcount--;

		// save
	}

	/**
	 * Store object into memory. After saving more than MAX_STORE_COUNT, the
	 * objects in the memory will be stored in a file
	 * 
	 * @param obj
	 */
	public static void store(Object obj) {
		// Nothing to save
		if (obj == null || obj.getClass() == null) {
			return;
		}

		Pair<List<Object>, StateMatcher> objPair = objTable.get(obj.getClass());
		if (objPair == null) {
			objPair = new Pair<List<Object>, StateMatcher>(new ArrayList<Object>(),
					new EqualsMethodMatcher());
			// new HeapMatcher());
			objTable.put(obj.getClass(), objPair);
		}
		List lst = objPair.fst;
		StateMatcher sm = objPair.snd;

		boolean addok = false;

		// addok=true;

		if (!sm.contains(obj)) {
			addok = true;
			if (sm.size() < 500)
				sm.add(obj);
		}
		/**/

		if (addok) {
			// FIXME: since it saves object refereces, they save the same object
			// even if instances of objects are different. It should save a
			// clone of an object instead of a reference.
			// temporarily fix it, I make MAX_STORE_COUNT = 0to save immediately
			// into file.
			lst.add(obj);
			// Logger.info("Storing " + obj.getClass().getCanonicalName() + " "
			// + obj);

			// checking the saved value
			/*
			 * Pair<List<Object>, StateMatcher> zz =
			 * objTable.get(obj.getClass()); Logger.info("Storing " +
			 * obj.getClass().getCanonicalName() + " " +
			 * zz.fst.get(zz.fst.size()-1));
			 */

			// increase the object count and store objects to file
			if (storeCount++ >= MAX_STORE_COUNT) {
				writeObjTreeToFile();
				storeCount = 0;
			}

		}
		// else
		// Logger.info("Already stored " + obj.getClass().getCanonicalName() +
		// " " + obj);

	}

	/**
	 * Write current objects in the table to file
	 * 
	 * TODO: make it concurrent program so that it won't affect the performance
	 * of the subject program
	 */
	public static void writeObjTreeToFile() {
		Set<Class<?>> keySet = objTable.keySet();

		// Logger.info("Writing " + keySet.size() + " objects into a file.");

		for (Class<?> clazz : keySet) {
			try {
				writeObjectSetToFile(clazz, objTable.get(clazz).fst);

				// remove all sets in the table after dumping them to file
				// Note that, we dont clear statematcher part
				objTable.get(clazz).fst.clear();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// remove all sets in the table after dumping them to file
		// objTable = new Hashtable<Class<?>, Pair<List<Object>,
		// StateMatcher>>();
	}

	/**
	 * Write one object into a file
	 * 
	 * @param qualifiedClassName
	 * @param objSet
	 * @throws IOException
	 */
	private static void writeObjectSetToFile(Class<?> clazz, List<Object> objSet)
			throws IOException {// throws
		// IOException
		// {
		ZipOutputStream out = null;

		File outputFile = getOutoutFileName(clazz);

		// Make sure the output directory is created
		File outDir = outputFile.getParentFile();
		if (outDir.exists()) {
			outDir.delete();
		}
		outDir.mkdirs();

		boolean bzip = false;
		if (bzip) {
			// copy existing files to new zip
			if (outputFile.exists()) {
				// get a temp file
				File tempFile = File.createTempFile(outputFile.getName(), null);
				// delete it, otherwise you cannot rename your existing zip to
				// it.
				tempFile.delete();

				boolean renameOk = outputFile.renameTo(tempFile);
				if (!renameOk) {
					throw new RuntimeException("could not rename the file "
							+ outputFile.getAbsolutePath() + " to " + tempFile.getAbsolutePath());
				}

				ZipInputStream zin = new ZipInputStream(new FileInputStream(tempFile));
				out = new ZipOutputStream(new FileOutputStream(outputFile));

				ZipEntry entry = null;
				while ((entry = zin.getNextEntry()) != null) {
					String name = entry.getName();
					// Add ZIP entry to output stream.
					out.putNextEntry(new ZipEntry(name));
					Util.copyStream(zin, out);
				}

				// Close the streams
				zin.close();
			} else {
				out = new ZipOutputStream(new FileOutputStream(outputFile));
			}

			for (Object obj : objSet) {
				ZipEntry zipEntry = new ZipEntry("hash_" + obj.hashCode() + "_at_"
						+ new Date().getTime());
				try {
					out.putNextEntry(zipEntry);
					Serializer.storeObject(obj, out);
				} catch (ZipException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			out.close();
		} else {
			if (outputFile.exists())
				outputFile.delete();
			outputFile.mkdir();

			for (Object obj : objSet) {
				if (obj == null)
					continue; 
				
				// create file to save
				File instrumentedFile = null;
				try {
				  instrumentedFile = new File(outputFile, "/hash_" + obj.hashCode());
				} catch (Exception e) {
          // may be hashCode() thrown an exception, save next one
				  continue;
        }
				
				if (instrumentedFile.exists())
					instrumentedFile.delete();
				// File instrumentedFile = new File(outputFile, "\\hash_" +
				// obj.hashCode() + "_at_"
				// + new Date().getTime());
				// System.out.println("Writing " + instrumentedFile + " " +
				// obj.toString());
				Writer outt = new FileWriter(instrumentedFile);

				try {
					Serializer.storeObject(obj, outt);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				outt.close();
			}

		}
	}

	/**
	 * Get file name from
	 * 
	 * @param clazz
	 * @return
	 */
	private static File getOutoutFileName(Class<?> clazz) {
	  return getOutoutFileName(null, clazz);
	}
	
  /**
   * Get file name from
   * 
   * @param clazz
   * @return
   */
  private static File getOutoutFileName(String objCapturePath, Class<?> clazz) {
    String className = clazz.getCanonicalName();
    if (className == null)
    {
      //System.out.println("!!!!!!!!" + className + " " + clazz.getName());
      className = clazz.getName(); 
    }
    
    if (objCapturePath == null) {
      //String objCapturePath = System.getenv("OBJ_CAPTURE_DIR");
      objCapturePath = System.getProperty("OBJ_CAPTURE_DIR");
    }

    if (objCapturePath == null) {
      // objCapturePath = Util.getTmpDirectory() + "/" + "ObjCapture";
      objCapturePath = System.getProperty("user.dir") + "/" + "Captured";
    }

    // System.out.format("Object Capture Path ='%s', ", objCapturePath);

    String slashedClassName = Util.transClassNameDotToSlash(className);
    // File outputFile = new File(objCapturePath + "/" + slashedClassName +
    // ".zip");

    File outputFile = new File(objCapturePath + "/" + slashedClassName);
    return outputFile;
  }

	/**
	 * Read instances from objects
	 * 
	 * @param objQualifiedName
	 * @return
	 * @throws IOException
	 */
	public static Set<Object> get(Class<?> clazz, int maxObjectCount) throws IOException {
	  return get(null, clazz, maxObjectCount);
	}
	
	 /**
   * Read instances from objects
   * 
   * @param objQualifiedName
   * @return
   * @throws IOException
   */
  public static Set<Object> get(String objCapturePath, Class<?> clazz, int maxObjectCount) throws IOException {

    if (clazz == null && maxObjectCount <= 0) {
      return null;
    }

    File zipFileName = getOutoutFileName(objCapturePath, clazz);

    if (!zipFileName.exists() || !zipFileName.isFile()) {
      return null;
    }

    Set<Object> retSet = new HashSet<Object>();
    ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFileName));

    while (true) {
      ZipEntry entry = zin.getNextEntry();
      if (entry == null) {
        break;
      }

      // load object from zip input stream
      Object obj = Serializer.loadObject(zin);
      retSet.add(obj);

      if (retSet.size() >= maxObjectCount) {
        break;
      }

    }

    zin.close();

    return retSet;
  }

  /**
   * Read instances from a folder (not zipped)
   */
  public static Set<Object> getFromFolder(Class<?> clazz, int maxObjectCount)
      throws IOException {
    return getFromFolder(null, clazz, maxObjectCount);
  }
  
  /**
   * Read instances from a folder (not zipped)
   */
  public static Set<Object> getFromFolder(String objCapturePath, Class<?> clazz, int maxObjectCount)
      throws IOException {

    if (clazz == null && maxObjectCount <= 0) {
      return null;
    }

    File folder = getOutoutFileName(objCapturePath, clazz);

    if (!folder.exists() || !folder.isDirectory()) {
      return null;
    }

    Set<Object> retSet = new HashSet<Object>();
    String files[] = folder.list(new FilenameFilter() {
      @Override
      public boolean accept(File arg0, String arg1) {
        return arg1.startsWith("hash_");
      }
    });

    for (int i = 0; i < files.length; i++) {
      FileReader reader = new FileReader(folder.getAbsolutePath() + "/" + files[i]);
      try {
        Object obj = Serializer.loadObject(reader);
        retSet.add(obj);
      } catch (Exception e) {
        // do nothing, try next one
      }
      reader.close();

      if (retSet.size() >= maxObjectCount) {
        break;
      }
    }

    return retSet;
  }
  
  /**
   * Read instances from a folder (not zipped), include file paths
   */
  public static Set<SimpleEntry<Object, String>> getFromFolderWithFilePath(Class<?> clazz, int maxObjectCount)
      throws IOException {
    return getFromFolderWithFilePath(null, clazz, maxObjectCount);
  }
  
  /**
   * Read instances from a folder (not zipped), include file paths
   */
  public static Set<SimpleEntry<Object, String>> getFromFolderWithFilePath(
      String objCapturePath, Class<?> clazz, int maxObjectCount) throws IOException {

    if (clazz == null && maxObjectCount <= 0) {
      return null;
    }

    File folder = getOutoutFileName(objCapturePath, clazz);

    if (!folder.exists() || !folder.isDirectory()) {
      return null;
    }

    Set<SimpleEntry<Object, String>> retSet = new HashSet<SimpleEntry<Object, String>>();
    String files[] = folder.list(new FilenameFilter() {
      @Override
      public boolean accept(File arg0, String arg1) {
        return arg1.startsWith("hash_");
      }
    });

    for (int i = 0; i < files.length; i++) {
      String filePath = folder.getAbsolutePath() + "/" + files[i];
      FileReader reader = new FileReader(filePath);
      try {
        Object obj = Serializer.loadObject(reader);
        retSet.add(new SimpleEntry<Object, String>(obj, filePath));
      } catch (Exception e) {
        System.err.println("Exception \"" + e.getClass().getName() + "\" occurred when loading: " + filePath);
        // try next one
      }
      reader.close();

      if (retSet.size() >= maxObjectCount) {
        break;
      }
    }

    return retSet;
  }

  /**
   * Read instances from a zipped file
   */
  public static Set<Object> getFromZipFile(String zipFilePath, Class<?> clazz, int maxObjectCount)
      throws IOException {

    Set<Object> objs = null;
    Set<SimpleEntry<Object, String>> objsWithPath = getFromZipFileWithFilePath(zipFilePath, clazz, maxObjectCount);
    if (objsWithPath != null) {
      objs = new HashSet<Object>();
      for (SimpleEntry<Object, String> obj : objsWithPath) {
        objs.add(obj.getKey());
      }
    }
    return objs;
  }

  /**
   * Read instances from a zipped file, include file paths
   */
  public static Set<SimpleEntry<Object, String>> getFromZipFileWithFilePath(
      String zipFilePath, Class<?> clazz, int maxObjectCount) throws IOException {

    if (clazz == null && maxObjectCount <= 0) {
      return null;
    }

    // get the detail folder name
    String className = clazz.getCanonicalName();
    if (className == null) {
      className = clazz.getName(); 
    }
    String slashedClassName = Util.transClassNameDotToSlash(className);

    Set<SimpleEntry<Object, String>> objects = new HashSet<SimpleEntry<Object, String>>();
    try {
      ZipFile zipFile = new ZipFile(zipFilePath);
      Enumeration<?> allObjectFiles = zipFile.entries();
    
      // for de-serializing
      XStream xstream = new XStream();

      // read each object
      boolean inGoodFolder = false;
      boolean gotInBefore  = false;
      char[] buff = new char[20971520];
      while (allObjectFiles.hasMoreElements() && objects.size() < maxObjectCount) {
        ZipEntry entry = (ZipEntry) allObjectFiles.nextElement();

        if (entry.isDirectory() && entry.getName().startsWith(slashedClassName + "/")) {
          inGoodFolder = true;
        }
        else if (entry.isDirectory()) {
          inGoodFolder = false;
        }
        
        if (inGoodFolder && !entry.isDirectory() && entry.getName().startsWith(slashedClassName + "/hash_")) {
          gotInBefore = true;
          
          // big xml files usually leads to jvm crash
          if (entry.getSize() > 1048576) {
            continue;
          }
  
          // read object list
          BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
          int read = reader.read(buff, 0, buff.length);
          reader.close();
  
          // de-serialize object
          try {
            Object obj = xstream.fromXML(new String(buff, 0, read));
            if (obj != null) {
              objects.add(new SimpleEntry<Object, String>(obj, entry.getName()));
            }
          } catch (XStreamException e) {
            System.err.println("Error occured when de-serializing object: " + entry.getName());
          } catch (Exception e) {}
        }
        else if (gotInBefore) {
          break;
        }
      }
    } catch (Exception e) {e.printStackTrace();}
    System.out.println("Read " + objects.size() + " object from " + slashedClassName);
    
    return objects;
  }
  
  public static Set<String> classesInZipFile(String zipFilePath) {
    HashSet<String> classesInZipFile = new HashSet<String>();
    try {
      ZipFile zipFile = new ZipFile(zipFilePath);
      Enumeration<?> allObjectFiles = zipFile.entries();
      while (allObjectFiles.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) allObjectFiles.nextElement();
        
        String lastClassName = null;
        if (!entry.isDirectory() && (lastClassName == null || !entry.getName().startsWith(lastClassName + "/hash_"))) {
          int index = entry.getName().lastIndexOf("/hash_");
          if (index >= 0) {
            lastClassName = entry.getName().substring(0, index);
            classesInZipFile.add(lastClassName);
          }
        }
      }
    } catch (Exception e) {e.printStackTrace();}
  
    return classesInZipFile;
  }

  /**
   * Read instance from a zipped file
   */
  public static Object getObjectFromZipFile(String zipFilePath, String objPath)
      throws IOException {

    Object obj = null;
    try {
      ZipFile zipFile = new ZipFile(zipFilePath);
      Enumeration<?> allObjectFiles = zipFile.entries();
    
      // for de-serializing
      XStream xstream = new XStream();

      // read each object
      char[] buff = new char[20971520];
      while (allObjectFiles.hasMoreElements()) {
        ZipEntry entry = (ZipEntry) allObjectFiles.nextElement();

        if (!entry.isDirectory() && entry.getName().equals(objPath)) {
          // big xml files usually leads to jvm crash
          if (entry.getSize() > 1048576) {
            break;
          }
  
          // read object list
          BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
          int read = reader.read(buff, 0, buff.length);
          reader.close();
  
          // de-serialize object
          try {
            obj = xstream.fromXML(new String(buff, 0, read));
          } catch (XStreamException e) {
            System.err.println("Exception occured when de-serializing object: " + entry.getName());
          }
          
          break;
        }
      }
    } catch (Exception e) {e.printStackTrace();}
  
    return obj;
  }

	/**
	 * Read instances from objects
	 * 
	 * @param objQualifiedName
	 * @return
	 * @throws IOException
	 */
	public static Object getObjectAtIndex(Class<?> clazz, int index) throws IOException {
		if (clazz == null) {
			return null;
		}

		File zipFileName = getOutoutFileName(clazz);

		if (!zipFileName.exists() || !zipFileName.isFile()) {
			return null;
		}

		ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFileName));

		for (int i = 0; i <= index; i++) {
			ZipEntry entry = zin.getNextEntry();
			if (entry == null) {
				return null;
			}
		}

		// load object from zip input stream
		Object obj = Serializer.loadObject(zin);
		zin.close();
		return obj;
	}

	/**
	 * Get stored object count
	 * 
	 * @param objQualifiedName
	 * @return
	 * @throws IOException
	 */
	public static int getObJectCount(Class<?> clazz) throws IOException {
		int objectCount = 0;
		if (clazz == null) {
			return objectCount;
		}

		File zipFileName = getOutoutFileName(clazz);

		if (!zipFileName.exists() || !zipFileName.isFile()) {
			return objectCount;
		}

		ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFileName));

		while (zin.getNextEntry() != null) {
			objectCount++;
		}
		zin.close();
		return objectCount;
	}

	protected void finalize() {
		System.out.println("Finalizing....");
		writeObjTreeToFile();
	}
}
