package edu.mit.csail.pag.utils;

import java.io.*;
import java.util.*;

public final class Files {
    private Files() {
        throw new IllegalStateException("no instances");
    }

    // Deletes all files and subdirectories under dir.
    // Returns true if all deletions were successful.
    // If a deletion fails, the method stops attempting to delete and returns false.
    public static boolean deleteDir(File dir) {
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success)
                    return false;
            }
        }

        // The directory is now empty so delete it
        return dir.delete();
    }

    public static List<String> findFilesInDir(File dir, String startsWith, String endsWith) {
        if (!dir.isDirectory())
            throw new IllegalArgumentException("not a directory: " + dir.getAbsolutePath());
        File currentDir = dir;
        List<String> retval = new ArrayList<String>();
        for (String fileName : currentDir.list()) {
            if (fileName.startsWith(startsWith) && fileName.endsWith(endsWith))
                retval.add(fileName);
        }
        return retval;
    }

    public static void writeToFile(String s, File file) throws IOException {
        writeToFile(s, file, false);
    }

    public static void writeToFile(String s, String fileName) throws IOException {
        writeToFile(s, fileName, false);
    }

    public static void writeToFile(String s, File file, Boolean append) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(file, append));
        try {
            writer.append(s);
        } finally {
            writer.close();
        }
    }

    public static void writeToFile(String s, String fileName, Boolean append) throws IOException {
        writeToFile(s, new File(fileName), append);
    }

    public static void writeToFile(List<String> lines, String fileName) throws IOException {
        writeToFile(CollectionsExt.toStringInLines(lines), fileName);
    }

    public static void writeToFile(List<String> lines, String fileName, boolean b) throws IOException {
        writeToFile(CollectionsExt.toStringInLines(lines), fileName, b);

    }

    /**
     * Reads the whole file. Does not close the reader. Returns the list of lines.
     */
    public static List<String> readWhole(BufferedReader reader) throws IOException {
        List<String> result = new ArrayList<String>();
        String line = reader.readLine();
        while (line != null) {
            result.add(line);
            line = reader.readLine();
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Reads the whole file. Returns the list of lines.
     */
    public static List<String> readWhole(String fileName) throws IOException {
        return readWhole(new File(fileName));
    }

    /**
     * Reads the whole file. Returns the list of lines.
     */
    public static List<String> readWhole(File file) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(file));
        try {
            return readWhole(in);
        } finally {
            in.close();
        }
    }

    /**
     * Reads the whole file. Returns the list of lines. Does not close the stream.
     */
    public static List<String> readWhole(InputStream is) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(is));
        return readWhole(in);
    }

    /**
     * Reads the whole file. Returns one big String.
     */
    public static String getFileContents(File file) throws IOException {
        StringBuilder result = new StringBuilder();
        Reader in = new BufferedReader(new FileReader(file));
        try {
            int c;
            while ((c = in.read()) != -1) {
                result.append((char) c);
            }
            in.close();
            return result.toString();
        } finally {
            in.close();
        }
    }

    /**
     * Reads the whole file. Returns one big String.
     */
    public static String getFileContents(String path) throws IOException {
        return getFileContents(new File(path));
    }

    public static LineNumberReader getFileReader(String fileName) {
        return getFileReader(new File(fileName));
    }

    public static LineNumberReader getFileReader(File fileName) {
        LineNumberReader reader;
        try {
            reader = new LineNumberReader(new BufferedReader(new FileReader(fileName)));
        } catch (FileNotFoundException e1) {
            throw new IllegalStateException("File was not found " + fileName + " " + e1.getMessage());
        }
        return reader;
    }

    public static String addProjectPath(String string) {
        return System.getProperty("user.dir") + File.separator + string;
    }

    public static boolean deleteFile(String path) {
        File f = new File(path);
        return f.delete();
    }

    /**
     * Reads a single long from the file. Returns null if the file does not exist.
     * 
     * @throws IllegalStateException
     *             is the file contains not just 1 line or if the file contains something.
     */
    public static Long readLongFromFile(File file) {
        if (!file.exists())
            return null;
        List<String> lines;
        try {
            lines = readWhole(file);
        } catch (IOException e) {
            throw new IllegalStateException("Problem reading file " + file + " ", e);
        }
        if (lines.size() != 1)
            throw new IllegalStateException("Expected exactly 1 line in " + file + " but found " + lines.size());
        try {
            return Long.valueOf(lines.get(0));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Expected a number (type long) in " + file + " but found " + lines.get(0));
        }
    }

    // Process only files under dir
    public static List<File> recursiveFindFilesInDir(File dir, String startsWith, String endsWith) {
        List<File> retval = new ArrayList<File>();
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                retval.addAll(recursiveFindFilesInDir(new File(dir, children[i]), startsWith, endsWith));
            }
        } else {
            if (dir.getPath().startsWith(startsWith) && dir.getPath().endsWith(endsWith))
                retval.add(dir);
        }

        return retval;
    }

    public static List<File> recursiveFindFilesWithPattern(File dir, String includeFileNamePattern) {
        List<File> retval = new ArrayList<File>();
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                retval.addAll(recursiveFindFilesWithPattern(new File(dir, children[i]), includeFileNamePattern));
            }
        } else {
            if (dir.getName().matches(includeFileNamePattern)) {
                retval.add(dir);
            }
        }

        return retval;
    }

}
