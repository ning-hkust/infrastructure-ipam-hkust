package hk.ust.cse.Prevision.Solver.Yices;

import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YicesUtils {
  
  private static final Pattern s_filterPattern;
  private static final Pattern s_unfilterPattern;
  private static final Hashtable<String, String> s_filterMap;
  private static final Hashtable<String, String> s_unfilterMap;
  
  static {
    s_filterPattern = Pattern.compile("([() :\";])");
    s_filterMap = new Hashtable<String, String>();
    s_filterMap.put("(", "\\$<");
    s_filterMap.put(")", "\\$>");
    s_filterMap.put(" ", "\\$_");
    s_filterMap.put(":", "\\$#");
    s_filterMap.put("\"", "\\$@");
    s_filterMap.put(";", "\\$|");
    
    s_unfilterPattern = Pattern.compile("(\\$[<>_#@|])");
    s_unfilterMap = new Hashtable<String, String>();
    s_unfilterMap.put("$<", "(");
    s_unfilterMap.put("$>", ")");
    s_unfilterMap.put("$_", " ");
    s_unfilterMap.put("$#", ":");
    s_unfilterMap.put("$@", "\"");
    s_unfilterMap.put("$|", ";");
  }
  
  // replace some unwanted chars
  public static String filterChars(String str) {
    return replacePattern(str, s_filterPattern, s_filterMap);
  }
  
  // replace some unwanted chars
  public static String unfilterChars(String str) { 
    return replacePattern(str, s_unfilterPattern, s_unfilterMap);
  }
  
  private static String replacePattern(String str, Pattern pattern, Hashtable<String, String> mapping) {
    Matcher matcher = pattern.matcher(str); 
    
    // find each
    StringBuffer sb = new StringBuffer(); 
    while(matcher.find()) {
      matcher.appendReplacement(sb, mapping.get(matcher.group(1)));
    } 
    matcher.appendTail(sb); 
    return sb.toString();
  }
}
