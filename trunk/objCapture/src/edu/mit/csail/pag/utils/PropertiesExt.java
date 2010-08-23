package edu.mit.csail.pag.utils;

import java.io.*;
import java.util.*;

public class PropertiesExt {
    public static String optionString(Properties p, String name, String defaultVal) {
        String s = p.getProperty(name);
        if (s == null) {
            System.out.println("WARNING: missing property " + name + " using default value " + defaultVal);
        }
        return s == null ? defaultVal : s;
    }

    public static boolean optionBoolean(Properties p, String name, boolean defaultVal) {
        String s = p.getProperty(name);
        if (s == null) {
            System.out.println("WARNING: missing property " + name + " using default value " + defaultVal);
        }
        return s == null ? defaultVal : Boolean.parseBoolean(s);
    }

    public static int optionInt(Properties p, String name, int defaultVal) {
        String s = p.getProperty(name);
        if (s == null) {
            System.out.println("WARNING: missing property " + name + " using default value " + defaultVal);
        }
        return s == null ? defaultVal : Integer.parseInt(s);
    }

    public static double optionDouble(Properties p, String name, double defaultVal) {
        String s = p.getProperty(name);
        if (s == null) {
            System.out.println("WARNING: missing property " + name + " using default value " + defaultVal);
        }
        return s == null ? defaultVal : Double.parseDouble(s);
    }

    public static String optionString(Properties p, String name) {
        String s = p.getProperty(name);
        if (s == null) {
            throw new IllegalArgumentException("WARNING: missing property " + name);

        }
        return s;
    }

    public static boolean optionBoolean(Properties p, String name) {
        String s = p.getProperty(name);
        if (s == null) {
            throw new IllegalArgumentException("WARNING: missing property " + name);
        }
        return Boolean.parseBoolean(s);
    }

    public static int optionInt(Properties p, String name) {
        String s = p.getProperty(name);
        if (s == null) {
            throw new IllegalArgumentException("WARNING: missing property " + name);
        }
        return Integer.parseInt(s);
    }

    public static double optionDouble(Properties p, String name) {
        String s = p.getProperty(name);
        if (s == null) {
            throw new IllegalArgumentException("WARNING: missing property " + name);
        }
        return Double.parseDouble(s);
    }

    public static Properties properties(String fileName, boolean verbose) throws FileNotFoundException, IOException {
        if (verbose)
            System.out.println("Loading options from " + fileName);
        Properties properties = new Properties();
        FileInputStream fis = new FileInputStream(fileName);
        try {
            properties.load(fis);
        } finally {
            fis.close();
        }
        if (verbose)
            System.out.println("Loading options done. " + properties.keySet().size() + " keys");
        return properties;
    }

    public static Properties properties(String fileName) throws FileNotFoundException, IOException {
        return properties(fileName, false);
    }
}
