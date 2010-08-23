package hk.ust.cse.Prevision.Yices;

public class Utils {
  // replace some unwanted chars
  public static String filterChars(String str) {
    StringBuilder strBuilder = new StringBuilder(str);
    replace(strBuilder, "(", "$<");
    replace(strBuilder, ")", "$>");
    replace(strBuilder, " ", "$_");
    replace(strBuilder, ":", "$#");
    replace(strBuilder, "\"", "$@");
    return strBuilder.toString();
  }
  
  // replace some unwanted chars
  public static String unfilterChars(String str) {
    StringBuilder strBuilder = new StringBuilder(str);
    replace(strBuilder, "$<", "(");
    replace(strBuilder, "$>", ")");
    replace(strBuilder, "$_", " ");
    replace(strBuilder, "$#", ":");
    replace(strBuilder, "$@", "\"");
    return strBuilder.toString();
  }
  
  public static StringBuilder replace(StringBuilder str, String fromStr, String toStr) {
    int nLength1 = fromStr.length();
    int nLength2 = toStr.length();  
    
    int nIndex = str.indexOf(fromStr);
    while (nIndex >= 0) {
      str.replace(nIndex, nIndex + nLength1, toStr);
      nIndex = str.indexOf(fromStr, nIndex + nLength2);
    }
    return str;
  }
}
