package hk.ust.cse.YicesWrapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;

public class YicesWrapper {
  // Yices Lite API Definitions
  public static native void   yicesl_set_verbosity(short l);
  public static native String yicesl_version();
  public static native void   yicesl_enable_type_checker(short flag);
  public static native void   yicesl_enable_log_file(String filename);
  public static native int    yicesl_mk_context();
  public static native void   yicesl_del_context(int ctx);
  public static native int    yicesl_read(int ctx, String cmd);
  public static native int    yicesl_inconsistent(int ctx);
  public static native String yicesl_get_last_error_message();
  public static native void   yicesl_set_output_file(String filename);
  
  static {
    // use an absolute path
    //String currDir = System.getProperty("user.dir");
    
    // load libraries according to os type and JVM architecture
    String osName  = System.getProperty("os.name");
    String jvmArch = System.getProperty("os.arch");
    
    if (osName.startsWith("Windows")) {
      String yicesLibName     = "libyices.dll";
      String yicesWrapperName = "hk_ust_cse_YicesWrapper_YicesWrapper.dll";
      if (jvmArch.contains("64")) {
        yicesLibName     = "libyices_64.dll";
        yicesWrapperName = "hk_ust_cse_YicesWrapper_YicesWrapper_64.dll";
      }
      // do not use loadLibrary(), since it only accepts library name
      // use load() with an absolute path to the library instead!
      URL yicesLib     = YicesWrapper.class.getClassLoader().getResource(yicesLibName);
      URL yicesWrapper = YicesWrapper.class.getClassLoader().getResource(yicesWrapperName);
      if (yicesLib != null && yicesWrapper != null) {
        System.load(yicesLib.getPath());
        System.load(yicesWrapper.getPath());
      }
      else {
        System.err.println("No Yices module available for platform: " + osName + " (" + jvmArch + ")");
      }
    }
    else if (osName.startsWith("Mac")) {
      String yicesWrapperName = "libhk_ust_cse_YicesWrapper_YicesWrapper.jnilib";
      if (jvmArch.contains("64")) {
        yicesWrapperName = "libhk_ust_cse_YicesWrapper_YicesWrapper_64.jnilib";
      }
      URL yicesWrapper = YicesWrapper.class.getClassLoader().getResource(yicesWrapperName);
      if (yicesWrapper != null) {
        System.load(yicesWrapper.getPath());
      }
      else {
        System.err.println("No Yices module available for platform: " + osName + " (" + jvmArch + ")");
      }
    }
    else {
      // we are using a hk_ust_cse_YicesWrapper_YicesWrapper.so that is 
      // statically linked against libyices.a. That's because linux might 
      // not have the right libgmp.so v4.1.2. Thus, we use a static libyices.a
      String yicesWrapperName = "hk_ust_cse_YicesWrapper_YicesWrapper.so";
      if (jvmArch.contains("64")) {
        yicesWrapperName = "hk_ust_cse_YicesWrapper_YicesWrapper_64.so";
      }
      URL yicesWrapper = YicesWrapper.class.getClassLoader().getResource(yicesWrapperName);
      if (yicesWrapper != null) {
        System.load(yicesWrapper.getPath());
      }
      else {
        System.err.println("No Yices module available for platform: " + osName + " (" + jvmArch + ")");
      }
    }
    
    // create temporary output file
    try {
      s_tempOutput = File.createTempFile("tmp", ".tmp");
    } catch (IOException e) {}
  }
  
  public static boolean check(String input) {
    // create a context
    int ctx = yicesl_mk_context();
    
    boolean result = checkInContext(ctx, input);
    
    // clean context
    yicesl_del_context(ctx);
    
    return result;
  }

  public static boolean checkInContext(int ctx, String input) {
    
    // redirect output to a file
    yicesl_set_output_file(s_tempOutput.getAbsolutePath());
    
    // input commands
    boolean error = false;
    String[] lines = input.split("\n");
    for (int i = 0; i < lines.length; i++) {
      if (yicesl_read(ctx, lines[i]) == 0) {
        error = true;
        break;
      }
    }
    
    // save input
    s_lastInput = input;
    
    boolean result;
    if (!error) {
      result      = yicesl_inconsistent(ctx) == 0;
      s_lastError = "";
      
      // read output from temporary output file
      int byteRead = 0;
      char[] buff = new char[51200];
      try {
        BufferedReader reader = new BufferedReader(new FileReader(s_tempOutput));
        byteRead = reader.read(buff, 0, buff.length);
        reader.close();
      } catch (IOException e) { /* should not throw exception */ }
      s_lastOutput = String.valueOf(buff, 0, byteRead < 0 ? 0 : byteRead);
      
      // type error message is passed through the normal output
      if (s_lastOutput.contains("type error: ")) {
        result = false;
        s_lastError  = s_lastOutput; // move error message to error output
        s_lastOutput = "";
      }
    }
    else {
      result       = false;
      s_lastOutput = "";
      s_lastError  = yicesl_get_last_error_message();
    }
    
    return result;
  }
  
  public static int createContext() {
    return yicesl_mk_context();
  }
  
  public static void deleteContext(int ctx) {
    yicesl_del_context(ctx);
  }
  
  public static void pushContext(int ctx) {
    checkInContext(ctx, "(push)\n");
  }
  
  public static void popContext(int ctx) {
    checkInContext(ctx, "(pop)\n");
  }
  
  public static String getLastInput() {
    return s_lastInput;
  }
  
  public static String getLastOutput() {
    return s_lastOutput;
  }
  
  public static String getLastErrorMsg() {
    return s_lastError;
  }
  
  public static void main(String[] args) {
    StringBuilder input = new StringBuilder();
    input.append("(reset)\n");
    input.append("(set-evidence! true)\n");
    input.append("(define a::int)\n");
    input.append("(define b::int)\n");
    input.append("(assert (= (/ a b) 1))\n");
    input.append("(check)\n");
    System.out.println("Result: " + YicesWrapper.check(input.toString()));
    System.out.println("Input: " + YicesWrapper.getLastInput());
    System.out.println("Output: " + YicesWrapper.getLastOutput());
    System.out.println("Error: " + YicesWrapper.getLastErrorMsg());
    System.out.println("--------------------------------");
    
    input = new StringBuilder();
    input.append("(reset)\n");
    input.append("(set-evidence! true)\n");
    input.append("(define a::int)\n");
    input.append("(define b::int)\n");
    input.append("(assert (= 2 1))\n");
    input.append("(assert (= 1 1))\n");
    input.append("(check)\n");
    System.out.println("Result: " + YicesWrapper.check(input.toString()));
    System.out.println("Input: " + YicesWrapper.getLastInput());
    System.out.println("Output: " + YicesWrapper.getLastOutput());
    System.out.println("Error: " + YicesWrapper.getLastErrorMsg());
    System.out.println("--------------------------------");
    
    input = new StringBuilder();
    input.append("(reset)\n");
    input.append("(set-evidence! true)\n");
    input.append("(define-type reference (scalar null notnull))\n");
    input.append("(define-type Lorg/apache/commons/math/MathRuntimeException reference)\n");
    input.append("(define-type [Ljava/lang/Object reference)\n"); 
    input.append("(define-type Ljava/lang/Throwable reference)\n");
    input.append("(define rootCause::Ljava/lang/Throwable)\n");
    input.append("(define arguments::[Ljava/lang/Object)\n");
    input.append("(define this::Lorg/apache/commons/math/MathRuntimeException)\n");
    input.append("(assert (/= this null))\n");
    input.append("(assert (= arguments null))\n");
    input.append("(check)\n");
    System.out.println("Result: " + YicesWrapper.check(input.toString()));
    System.out.println("Input: " + YicesWrapper.getLastInput());
    System.out.println("Output: " + YicesWrapper.getLastOutput());
    System.out.println("Error: " + YicesWrapper.getLastErrorMsg());
  }
  
  private static String s_lastInput;
  private static String s_lastOutput;
  private static String s_lastError;
  private static File   s_tempOutput;
}
