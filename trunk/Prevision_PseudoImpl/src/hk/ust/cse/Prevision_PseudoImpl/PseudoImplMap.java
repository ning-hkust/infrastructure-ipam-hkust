package hk.ust.cse.Prevision_PseudoImpl;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;

public class PseudoImplMap {

  static {
    initializeMaps();
  }
  
  public static String findPseudoImpl(String methodSig) {
    String pseudoImpl = null;
    
    if (!s_notMatchCache.contains(methodSig)) {
      Enumeration<String> keys = s_map2.keys();
      while (keys.hasMoreElements() && pseudoImpl == null) {
        String methodSigRegex = (String) keys.nextElement();
        if (methodSig.matches(methodSigRegex)) {
          pseudoImpl = s_map2.get(methodSigRegex);
        }
      }
      
      if (pseudoImpl == null) {
        s_notMatchCache.add(methodSig);
      }
    }

    return pseudoImpl;
  }
  
  public static String findPseudoImplFieldName(String fullFieldName) {
    String pseudoImplFieldName = null;
    Enumeration<String> keys = s_map3.keys();
    while (keys.hasMoreElements() && pseudoImplFieldName == null) {
      String fieldNameRegex = (String) keys.nextElement();
      if (fullFieldName.matches(fieldNameRegex)) {
        pseudoImplFieldName = s_map3.get(fieldNameRegex);
      }
    }
    return pseudoImplFieldName;
  }
  
  public static boolean isPseudoImpl(String methodSig) {
    return s_map1.contains(methodSig);
  }
  
  private static void initializeMaps() {
    if (s_map1 == null) {
      s_map1          = new HashSet<String>();
      s_map2          = new Hashtable<String, String>();
      s_map3          = new Hashtable<String, String>();
      s_notMatchCache = new HashSet<String>();
      String mapInit          = "hk.ust.cse.Prevision_PseudoImpl.Map.<init>(IF)V";
      String mapGet           = "hk.ust.cse.Prevision_PseudoImpl.Map.get(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;)Ljava/lang/Object;";
      String mapPut           = "hk.ust.cse.Prevision_PseudoImpl.Map.put(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;Ljava/lang/Object;)Ljava/lang/Object;";
      String mapRemove        = "hk.ust.cse.Prevision_PseudoImpl.Map.remove(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;)Ljava/lang/Object;";
      String mapContainsKey   = "hk.ust.cse.Prevision_PseudoImpl.Map.containsKey(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;)Z";
      String mapKeySet        = "hk.ust.cse.Prevision_PseudoImpl.Map.keySet()Lhk/ust/cse/Prevision_PseudoImpl/Map$KeySet;";
      String mapKeySetIter    = "hk.ust.cse.Prevision_PseudoImpl.Map$KeySet.iterator()Lhk/ust/cse/Prevision_PseudoImpl/Map$KeyIterator;";
      String mapKeySetIterHasNext = "hk.ust.cse.Prevision_PseudoImpl.Map$KeyIterator.hasNext()Z";
      String mapKeySetIterNext = "hk.ust.cse.Prevision_PseudoImpl.Map$KeyIterator.next()Lhk/ust/cse/Prevision_PseudoImpl/HashCode;";
      String mapEntrySet      = "hk.ust.cse.Prevision_PseudoImpl.Map.entrySet()Lhk/ust/cse/Prevision_PseudoImpl/Map$EntrySet;";
      String mapEntrySetIter  = "hk.ust.cse.Prevision_PseudoImpl.Map$EntrySet.iterator()Lhk/ust/cse/Prevision_PseudoImpl/Map$EntryIterator;";
      String mapEntrySetIterHasNext = "hk.ust.cse.Prevision_PseudoImpl.Map$EntryIterator.hasNext()Z";
      String mapEntrySetIterNext = "hk.ust.cse.Prevision_PseudoImpl.Map$EntryIterator.next()Ljava/util/Map$Entry;";
      String tableInit        = "hk.ust.cse.Prevision_PseudoImpl.Table.<init>(IF)V";
      String tableGet         = "hk.ust.cse.Prevision_PseudoImpl.Table.get(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;)Ljava/lang/Object;";
      String tablePut         = "hk.ust.cse.Prevision_PseudoImpl.Table.put(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;Ljava/lang/Object;)Ljava/lang/Object;";
      String tableRemove      = "hk.ust.cse.Prevision_PseudoImpl.Table.remove(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;)Ljava/lang/Object;";
      String tableContainsKey   = "hk.ust.cse.Prevision_PseudoImpl.Table.containsKey(Lhk/ust/cse/Prevision_PseudoImpl/HashCode;)Z";
      String tableKeySet      = "hk.ust.cse.Prevision_PseudoImpl.Table.keySet()Lhk/ust/cse/Prevision_PseudoImpl/Table$KeySet;";
      String tableKeySetIter  = "hk.ust.cse.Prevision_PseudoImpl.Table$KeySet.iterator()Lhk/ust/cse/Prevision_PseudoImpl/Table$KeyIterator;";
      String tableKeySetIterHasNext = "hk.ust.cse.Prevision_PseudoImpl.Table$KeyIterator.hasNext()Z";
      String tableKeySetIterNext = "hk.ust.cse.Prevision_PseudoImpl.Table$KeyIterator.next()Lhk/ust/cse/Prevision_PseudoImpl/HashCode;";
      String tableKeys        = "hk.ust.cse.Prevision_PseudoImpl.Table.keys()Lhk/ust/cse/Prevision_PseudoImpl/Table$KeyEnumerator;";
      String tableValues      = "hk.ust.cse.Prevision_PseudoImpl.Table.values()Ljava/util/Collection;";
      String tableKeyEnumHasMore = "hk.ust.cse.Prevision_PseudoImpl.Table$KeyEnumerator.hasMoreElements()Z";
      String tableKeyEnumNext = "hk.ust.cse.Prevision_PseudoImpl.Table$KeyEnumerator.nextElement()Lhk/ust/cse/Prevision_PseudoImpl/HashCode;";
      String tableEntrySet      = "hk.ust.cse.Prevision_PseudoImpl.Table.entrySet()Lhk/ust/cse/Prevision_PseudoImpl/Table$EntrySet;";
      String tableEntrySetIter  = "hk.ust.cse.Prevision_PseudoImpl.Table$EntrySet.iterator()Lhk/ust/cse/Prevision_PseudoImpl/Table$EntryIterator;";
      String tableEntrySetIterHasNext = "hk.ust.cse.Prevision_PseudoImpl.Table$EntryIterator.hasNext()Z";
      String tableEntrySetIterNext = "hk.ust.cse.Prevision_PseudoImpl.Table$EntryIterator.next()Ljava/util/Map$Entry;";
      String hashCode         = "hk.ust.cse.Prevision_PseudoImpl.HashCode.hashCode()I";
      String sbInit1          = "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.<init>()V";
      String sbInit2          = "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.<init>(I)V";
      String sbAppend         = "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.append(Ljava/lang/Object;)Ljava/lang/Object;";
      String sbToStr          = "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.toString()Ljava/lang/String;";
      String sbCapacity       = "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.capacity()I";
      String stringInit1      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([C)V";
      String stringInit2      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>(II[C)V";
      String stringInit3      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>(Ljava/lang/String;)V";
      String stringInit4      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>(Ljava/lang/StringBuffer;)V";
      String stringInit5      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>(Ljava/lang/StringBuilder;)V";
      String stringInit6      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([B)V";
      String stringInit7      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BI)V";
      String stringInit8      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BII)V";
      String stringInit9      = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BIII)V";
      String stringInit10     = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BIILjava/lang/String;)V";
      String stringInit11     = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BIILjava/nio/charset/Charset;)V";
      String stringInit12     = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BLjava/lang/String;)V";
      String stringInit13     = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([BLjava/nio/charset/Charset;)V";
      String stringInit14     = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([CII)V";
      String stringInit15     = "hk.ust.cse.Prevision_PseudoImpl.String2.<init>([III)V";
      String stringContains   = "hk.ust.cse.Prevision_PseudoImpl.String2.contains(Lhk/ust/cse/Prevision_PseudoImpl/String2;)Z";
      String stringIndexOf1   = "hk.ust.cse.Prevision_PseudoImpl.String2.indexOf(I)I";
      String stringIndexOf2   = "hk.ust.cse.Prevision_PseudoImpl.String2.indexOf(II)I";
      String stringIndexOf3   = "hk.ust.cse.Prevision_PseudoImpl.String2.indexOf(Lhk/ust/cse/Prevision_PseudoImpl/String2;)I";
      String stringIndexOf4   = "hk.ust.cse.Prevision_PseudoImpl.String2.indexOf(Lhk/ust/cse/Prevision_PseudoImpl/String2;I)I";
      String stringLIndexOf1  = "hk.ust.cse.Prevision_PseudoImpl.String2.lastIndexOf(I)I";
      String stringLIndexOf2  = "hk.ust.cse.Prevision_PseudoImpl.String2.lastIndexOf(II)I";
      String stringStartsWith = "hk.ust.cse.Prevision_PseudoImpl.String2.startsWith(Lhk/ust/cse/Prevision_PseudoImpl/String2;)Z";
      String stringSubString  = "hk.ust.cse.Prevision_PseudoImpl.String2.substring(I)Lhk/ust/cse/Prevision_PseudoImpl/String2;";
      String stringSubString2 = "hk.ust.cse.Prevision_PseudoImpl.String2.substring(II)Lhk/ust/cse/Prevision_PseudoImpl/String2;";
      String stringEndsWith   = "hk.ust.cse.Prevision_PseudoImpl.String2.endsWith(Lhk/ust/cse/Prevision_PseudoImpl/String2;)Z";
      String stringEquals     = "hk.ust.cse.Prevision_PseudoImpl.String2.equals(Lhk/ust/cse/Prevision_PseudoImpl/String2;)Z";
      String stringEqualsIgnC = "hk.ust.cse.Prevision_PseudoImpl.String2.equalsIgnoreCase(Lhk/ust/cse/Prevision_PseudoImpl/String2;)Z";
      String stringCharAt     = "hk.ust.cse.Prevision_PseudoImpl.String2.charAt(I)C";
      String stringLength     = "hk.ust.cse.Prevision_PseudoImpl.String2.length()I";
      String stringTrim       = "hk.ust.cse.Prevision_PseudoImpl.String2.trim()Lhk/ust/cse/Prevision_PseudoImpl/String2;";
      String stringToLower    = "hk.ust.cse.Prevision_PseudoImpl.String2.toLowerCase()Lhk/ust/cse/Prevision_PseudoImpl/String2;";
      String stringToUpper    = "hk.ust.cse.Prevision_PseudoImpl.String2.toUpperCase()Lhk/ust/cse/Prevision_PseudoImpl/String2;";
      String objectClone      = "hk.ust.cse.Prevision_PseudoImpl.Object2.clone()Ljava/lang/Object;";
      String integerParse1    = "hk.ust.cse.Prevision_PseudoImpl.Integer2.parseInt(Lhk/ust/cse/Prevision_PseudoImpl/String2;)I";
      String integerParse2    = "hk.ust.cse.Prevision_PseudoImpl.Integer2.parseInt(Lhk/ust/cse/Prevision_PseudoImpl/String2;I)I";
      String fileStreamInit1  = "hk.ust.cse.Prevision_PseudoImpl.FileOutputStream.<init>(Ljava/lang/String;Z)V";
      String fileStreamInit2  = "hk.ust.cse.Prevision_PseudoImpl.FileOutputStream.<init>(Ljava/io/File;Z)V";
      String fileInit1        = "hk.ust.cse.Prevision_PseudoImpl.File.<init>(Ljava/io/File;Ljava/lang/String;)V";
      String fileIsDirectory  = "hk.ust.cse.Prevision_PseudoImpl.File.isDirectory()Z";
      String fileListFiles    = "hk.ust.cse.Prevision_PseudoImpl.File.listFiles()[Ljava/io/File;";
      String fileGetName      = "hk.ust.cse.Prevision_PseudoImpl.File.getName()Ljava/lang/String;";
      String fileGetParentFile = "hk.ust.cse.Prevision_PseudoImpl.File.getParentFile()Ljava/io/File;";
      String noEffect         = "hk.ust.cse.Prevision_PseudoImpl.NoEffect.noEffect()V";
      String listAddAll       = "hk.ust.cse.Prevision_PseudoImpl.List.addAll(Ljava/util/Collection;)Z";
      String listToArray      = "hk.ust.cse.Prevision_PseudoImpl.List.toArray()[Ljava/lang/Object;";
//      String listIter         = "hk.ust.cse.Prevision_PseudoImpl.List.iterator()Ljava/util/Iterator;";
//      String listIterHasNext  = "hk.ust.cse.Prevision_PseudoImpl.List$Iterator.hasNext()Z";
//      String listIterNext     = "hk.ust.cse.Prevision_PseudoImpl.List$Iterator.next()Ljava/lang/Object;";
//      String listIterHasPrev  = "hk.ust.cse.Prevision_PseudoImpl.List$Iterator.hasPrevious()Z";
//      String listIterPrev     = "hk.ust.cse.Prevision_PseudoImpl.List$Iterator.previous()Ljava/lang/Object;";
      s_map1.add(mapInit);
      s_map1.add(mapGet);
      s_map1.add(mapPut);
      s_map1.add(mapRemove);
      s_map1.add(mapContainsKey);
      s_map1.add(mapKeySet);
      s_map1.add(mapKeySetIter);
      s_map1.add(mapKeySetIterHasNext);
      s_map1.add(mapKeySetIterNext);
      s_map1.add(mapEntrySet);
      s_map1.add(mapEntrySetIter);
      s_map1.add(mapEntrySetIterHasNext);
      s_map1.add(mapEntrySetIterNext);
      s_map1.add(tableInit);
      s_map1.add(tableGet);
      s_map1.add(tablePut);
      s_map1.add(tableRemove);
      s_map1.add(tableContainsKey);
      s_map1.add(tableKeySet);
      s_map1.add(tableKeySetIter);
      s_map1.add(tableKeySetIterHasNext);
      s_map1.add(tableKeySetIterNext);
      s_map1.add(tableKeys);
      s_map1.add(tableValues);
      s_map1.add(tableKeyEnumHasMore);
      s_map1.add(tableKeyEnumNext);
      s_map1.add(tableEntrySet);
      s_map1.add(tableEntrySetIter);
      s_map1.add(tableEntrySetIterHasNext);
      s_map1.add(tableEntrySetIterNext);
      s_map1.add(hashCode);
      s_map1.add(sbInit1);
      s_map1.add(sbInit2);
      s_map1.add(sbAppend);
      s_map1.add(sbToStr);
      s_map1.add(sbCapacity);
      s_map1.add(stringInit1);
      s_map1.add(stringInit2);
      s_map1.add(stringInit3);
      s_map1.add(stringInit4);
      s_map1.add(stringInit5);
      s_map1.add(stringInit6);
      s_map1.add(stringInit7);
      s_map1.add(stringInit8);
      s_map1.add(stringInit9);
      s_map1.add(stringInit10);
      s_map1.add(stringInit11);
      s_map1.add(stringInit12);
      s_map1.add(stringInit13);
      s_map1.add(stringInit14);
      s_map1.add(stringInit15);
      s_map1.add(stringContains);
      s_map1.add(stringIndexOf1);
      s_map1.add(stringIndexOf2);
      s_map1.add(stringIndexOf3);
      s_map1.add(stringIndexOf4);
      s_map1.add(stringLIndexOf1);
      s_map1.add(stringLIndexOf2);
      s_map1.add(stringStartsWith);
      s_map1.add(stringSubString);
      s_map1.add(stringSubString2);
      s_map1.add(stringEndsWith);
      s_map1.add(stringEquals);
      s_map1.add(stringEqualsIgnC);
      s_map1.add(stringCharAt);
      s_map1.add(stringTrim);
      s_map1.add(stringToLower);
      s_map1.add(stringToUpper);
      s_map1.add(stringLength);
      s_map1.add(objectClone);
      s_map1.add(fileStreamInit1);
      s_map1.add(fileStreamInit2);
      s_map1.add(fileInit1);
      s_map1.add(fileIsDirectory);
      s_map1.add(fileListFiles);
      s_map1.add(fileGetName);
      s_map1.add(fileGetParentFile);
      s_map1.add(integerParse1);
      s_map1.add(integerParse2);
      s_map1.add(noEffect);
      s_map1.add(listAddAll);
      s_map1.add(listToArray);
      
      s_map2.put("[\\S]+Hash[\\S]+Map\\.<init>\\(IF\\)V", mapInit);
      s_map2.put("java\\.util\\.Hashtable\\.<init>\\(IF\\)V", tableInit);
      
      s_map2.put("[\\S]+Hash[\\S]+Map\\.get\\(Ljava/lang/Object;\\)Ljava/lang/Object;", mapGet);
      s_map2.put("java\\.util\\.HashMap\\.get\\(Ljava/lang/Object;\\)Ljava/lang/Object;", mapGet);
      s_map2.put("java\\.util\\.Hashtable\\.get\\(Ljava/lang/Object;\\)Ljava/lang/Object;", tableGet);

      s_map2.put("java\\.util\\.Hashtable\\.keySet\\(\\)Ljava/util/Set;", tableKeySet);
      s_map2.put("java\\.util\\.[\\S]+Map\\.keySet\\(\\)Ljava/util/Set;", mapKeySet);
      s_map2.put("java\\.util\\.[\\S]+Map\\$KeySet\\.iterator\\(\\)Ljava/util/Iterator;", mapKeySetIter);
      s_map2.put("java\\.util\\.[\\S]+Map\\$KeyIterator\\.hasNext\\(\\)Z", mapKeySetIterHasNext);
      s_map2.put("java\\.util\\.[\\S]+Map\\$HashIterator\\.hasNext\\(\\)Z", mapKeySetIterHasNext);
      s_map2.put("java\\.util\\.[\\S]+Map\\$KeyIterator\\.next\\(\\)Ljava/lang/Object;", mapKeySetIterNext);

      s_map2.put("java\\.util\\.[\\S]+Map\\.entrySet\\(\\)Ljava/util/Set;", mapEntrySet);
      s_map2.put("java\\.util\\.[\\S]+Map\\$EntrySet\\.iterator\\(\\)Ljava/util/Iterator;", mapEntrySetIter);
      s_map2.put("java\\.util\\.[\\S]+Map\\$EntryIterator\\.hasNext\\(\\)Z", mapEntrySetIterHasNext);
      s_map2.put("java\\.util\\.[\\S]+Map\\$HashIterator\\.hasNext\\(\\)Z", mapEntrySetIterHasNext);
      s_map2.put("java\\.util\\.[\\S]+Map\\$EntryIterator\\.next\\(\\)Ljava/lang/Object;", mapEntrySetIterNext);
      
      s_map2.put("java\\.util\\.Hashtable\\.entrySet\\(\\)Ljava/util/Set;", tableEntrySet);
      s_map2.put("java\\.util\\.Hashtable\\$EntrySet\\.iterator\\(\\)Ljava/util/Iterator;", tableEntrySetIter);
      s_map2.put("java\\.util\\.Hashtable\\$EntryIterator\\.hasNext\\(\\)Z", tableEntrySetIterHasNext);
      s_map2.put("java\\.util\\.Hashtable\\$HashIterator\\.hasNext\\(\\)Z", tableEntrySetIterHasNext);
      s_map2.put("java\\.util\\.Hashtable\\$EntryIterator\\.next\\(\\)Ljava/lang/Object;", tableEntrySetIterNext);
      
      s_map2.put("java\\.util\\.[\\S]+List\\.addAll\\(Ljava/util/Collection;\\)Z", listAddAll);
      s_map2.put("java\\.util\\.Vector\\.addAll\\(Ljava/util/Collection;\\)Z", listAddAll);
      s_map2.put("java\\.util\\.[\\S]+List\\.toArray\\([\\S]*\\)\\[Ljava/lang/Object;", listToArray);
      s_map2.put("java\\.util\\.Vector\\.toArray\\([\\S]*\\)\\[Ljava/lang/Object;", listToArray);
      
//      s_map2.put("java\\.util\\.[\\S]+List\\.iterator\\(\\)Ljava/util/Iterator;", listIter);
//      s_map2.put("java\\.util\\.[\\S]+List\\$Itr\\.hasNext\\(\\)Z", listIterHasNext);
//      s_map2.put("java\\.util\\.[\\S]+List\\$Itr\\.next\\(\\)Ljava/lang/Object;", listIterNext);
//      s_map2.put("java\\.util\\.[\\S]+List\\$Itr\\.hasPrevious\\(\\)Z", listIterHasPrev);
//      s_map2.put("java\\.util\\.[\\S]+List\\$Itr\\.previous\\(\\)Ljava/lang/Object;", listIterPrev);
//      s_map2.put("java\\.util\\.[\\S]+List\\$ListIterator\\.hasNext\\(\\)Z", listIterHasNext);
//      s_map2.put("java\\.util\\.[\\S]+List\\$ListIterator\\.next\\(\\)Ljava/lang/Object;", listIterNext);
//      s_map2.put("java\\.util\\.[\\S]+List\\$ListIterator\\.hasPrevious\\(\\)Z", listIterHasPrev);
//      s_map2.put("java\\.util\\.[\\S]+List\\$ListIterator\\.previous\\(\\)Ljava/lang/Object;", listIterPrev);
      
      s_map2.put("java\\.util\\.[\\S]+\\.keys\\(\\)Ljava/util/Enumeration;", tableKeys);
      s_map2.put("java\\.util\\.[\\S]+\\.values\\(\\)Ljava/util/Collection;", tableValues);
      
      s_map2.put("java\\.util\\.[\\S]+Enumerator\\.hasMoreElements\\(\\)Z", tableKeyEnumHasMore);
      s_map2.put("java\\.util\\.[\\S]+Enumerator\\.nextElement\\(\\)Ljava/lang/Object;", tableKeyEnumNext);
    
      s_map2.put("[\\S]+Hash[\\S]+Map\\.put\\(Ljava/lang/Object;Ljava/lang/Object;\\)Ljava/lang/Object;", mapPut);
      s_map2.put("java\\.util\\.HashMap\\.put\\(Ljava/lang/Object;Ljava/lang/Object;\\)Ljava/lang/Object;", mapPut);
      s_map2.put("java\\.util\\.Hashtable\\.put\\(Ljava/lang/Object;Ljava/lang/Object;\\)Ljava/lang/Object;", tablePut);
    
      s_map2.put("[\\S]+Hash[\\S]+Map\\.remove\\(Ljava/lang/Object;\\)Ljava/lang/Object;", mapRemove);
      s_map2.put("java\\.util\\.HashMap\\.remove\\(Ljava/lang/Object;\\)Ljava/lang/Object;", mapRemove);
      s_map2.put("java\\.util\\.Hashtable\\.remove\\(Ljava/lang/Object;\\)Ljava/lang/Object;", tableRemove);
      
      s_map2.put("[\\S]+Hash[\\S]+Map\\.containsKey\\(Ljava/lang/Object;\\)Z", mapContainsKey);
      s_map2.put("java\\.util\\.HashMap\\.containsKey\\(Ljava/lang/Object;\\)Z", mapContainsKey);
      s_map2.put("java\\.util\\.Hashtable\\.containsKey\\(Ljava/lang/Object;\\)Z", tableContainsKey);
      
      s_map2.put("[\\S]+\\.hashCode\\(\\)I", hashCode);

      s_map2.put("java\\.lang\\.StringBuffer\\.<init>\\(\\)V", sbInit1);
      s_map2.put("java\\.lang\\.StringBuffer\\.<init>\\([\\S]+\\)V", sbInit2);
      s_map2.put("java\\.lang\\.StringBuilder\\.<init>\\(\\)V", sbInit1);
      s_map2.put("java\\.lang\\.StringBuilder\\.<init>\\([\\S]+\\)V", sbInit2);
      s_map2.put("java\\.lang\\.StringBuffer\\.append\\([\\S]+\\)[\\S]+", sbAppend);
      s_map2.put("java\\.lang\\.StringBuilder\\.append\\([\\S]+\\)[\\S]+", sbAppend);
      s_map2.put("java\\.lang\\.StringBuffer\\.toString\\(\\)Ljava/lang/String;", sbToStr);
      s_map2.put("java\\.lang\\.StringBuilder\\.toString\\(\\)Ljava/lang/String;", sbToStr);
      s_map2.put("java\\.lang\\.StringBuffer\\.capacity\\(\\)I", sbCapacity);
      s_map2.put("java\\.lang\\.StringBuilder\\.capacity\\(\\)I", sbCapacity);

      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[C\\)V", stringInit1);
      s_map2.put("java\\.lang\\.String\\.<init>\\(II\\[C\\)V", stringInit2);
      s_map2.put("java\\.lang\\.String\\.<init>\\(Ljava/lang/String;\\)V", stringInit3);
      s_map2.put("java\\.lang\\.String\\.<init>\\(Ljava/lang/StringBuffer;\\)V", stringInit4);
      s_map2.put("java\\.lang\\.String\\.<init>\\(Ljava/lang/StringBuilder;\\)V", stringInit5);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[B\\)V", stringInit6);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BI\\)V", stringInit7);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BII\\)V", stringInit8);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BIII\\)V", stringInit9);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BIILjava/lang/String;\\)V", stringInit10);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BIILjava/nio/charset/Charset;\\)V", stringInit11);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BLjava/lang/String;\\)V", stringInit12);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[BLjava/nio/charset/Charset;\\)V", stringInit13);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[CII\\)V", stringInit14);
      s_map2.put("java\\.lang\\.String\\.<init>\\(\\[III\\)V", stringInit15);
      s_map2.put("java\\.lang\\.String\\.contains\\(Ljava/lang/CharSequence;\\)Z", stringContains);
      s_map2.put("java\\.lang\\.String\\.indexOf\\(I\\)I", stringIndexOf1);
      s_map2.put("java\\.lang\\.String\\.indexOf\\(II\\)I", stringIndexOf2);
      s_map2.put("java\\.lang\\.String\\.indexOf\\(Ljava/lang/String;\\)I", stringIndexOf3);
      s_map2.put("java\\.lang\\.String\\.indexOf\\(Ljava/lang/String;I\\)I", stringIndexOf4);
      s_map2.put("java\\.lang\\.String\\.lastIndexOf\\(I\\)I", stringLIndexOf1);
      s_map2.put("java\\.lang\\.String\\.lastIndexOf\\(II\\)I", stringLIndexOf2);
      s_map2.put("java\\.lang\\.String\\.startsWith\\(Ljava/lang/String;\\)Z", stringStartsWith);
      s_map2.put("java\\.lang\\.String\\.substring\\(I\\)Ljava/lang/String;", stringSubString);
      s_map2.put("java\\.lang\\.String\\.substring\\(II\\)Ljava/lang/String;", stringSubString2);
      s_map2.put("java\\.lang\\.String\\.endsWith\\(Ljava/lang/String;\\)Z", stringEndsWith);
      s_map2.put("java\\.lang\\.String\\.equals\\(Ljava/lang/Object;\\)Z", stringEquals);
      s_map2.put("java\\.lang\\.String\\.equalsIgnoreCase\\(Ljava/lang/String;\\)Z", stringEqualsIgnC);
      s_map2.put("java\\.lang\\.String\\.charAt\\(I\\)C", stringCharAt);
      s_map2.put("java\\.lang\\.String\\.trim\\(\\)Ljava/lang/String;", stringTrim);
      s_map2.put("java\\.lang\\.String\\.toLowerCase\\(\\)Ljava/lang/String;", stringToLower);
      s_map2.put("java\\.lang\\.String\\.toUpperCase\\(\\)Ljava/lang/String;", stringToUpper);
      s_map2.put("java\\.lang\\.String\\.length\\(\\)I", stringLength);

      s_map2.put("[\\S]+\\.clone\\(\\)Ljava/lang/Object;", objectClone);
      
      s_map2.put("java\\.io\\.FileOutputStream\\.<init>\\(Ljava/lang/String;Z\\)V", fileStreamInit1);
      s_map2.put("java\\.io\\.FileOutputStream\\.<init>\\(Ljava/io/File;Z\\)V", fileStreamInit2);
      s_map2.put("java\\.io\\.File\\.<init>\\(Ljava/io/File;Ljava/lang/String;\\)V", fileInit1);
      s_map2.put("java\\.io\\.File\\.isDirectory\\(\\)Z", fileIsDirectory);
      s_map2.put("java\\.io\\.File\\.listFiles\\([\\S]*\\)\\[Ljava/io/File;", fileListFiles);
      s_map2.put("java\\.io\\.File\\.getName\\(\\)Ljava/lang/String;", fileGetName);
      s_map2.put("java\\.io\\.File\\.getParentFile\\(\\)Ljava/io/File;", fileGetParentFile);
      
      s_map2.put("java\\.lang\\.Integer\\.parseInt\\(Ljava/lang/String;\\)I", integerParse1);
      s_map2.put("java\\.lang\\.Integer\\.parseInt\\(Ljava/lang/String;I\\)I", integerParse2);
      
      s_map2.put("java\\.io\\.PrintStream\\.[\\S]*\\([\\S]*\\)V", noEffect);
      s_map2.put("java\\.io\\.PrintWriter\\.[\\S]*\\([\\S]*\\)V", noEffect);
      s_map2.put("java\\.io\\.BufferedReader\\.[\\S]*\\([\\S]*\\)V", noEffect);
      s_map2.put("java\\.io\\.LineNumberReader\\.[\\S]*\\([\\S]*\\)V", noEffect);
      s_map2.put("java\\.lang\\.Throwable\\.[\\S]*\\([\\S]*\\)V", noEffect);
      s_map2.put("java\\.util\\.[\\S]+\\.ensureCapacity\\(I\\)V", noEffect);
      
      // help candidate method selection according to field defs
      s_map3.put("[\\S]+Hash[\\S]+Map.size", "hk.ust.cse.Prevision_PseudoImpl.Map.size");
      s_map3.put("java\\.util\\.Hashtable\\.count", "hk.ust.cse.Prevision_PseudoImpl.Table.count");
      s_map3.put("java\\.lang\\.String\\.offset", "hk.ust.cse.Prevision_PseudoImpl.String2.offset");
      s_map3.put("java\\.lang\\.String\\.count", "hk.ust.cse.Prevision_PseudoImpl.String2.count");
      s_map3.put("java\\.lang\\.String\\.value", "hk.ust.cse.Prevision_PseudoImpl.String2.value");
      s_map3.put("java\\.lang\\.StringBuffer\\.count", "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.count");
      s_map3.put("java\\.lang\\.StringBuilder\\.count", "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.count");
      s_map3.put("java\\.lang\\.StringBuffer\\.value", "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.value");
      s_map3.put("java\\.lang\\.StringBuilder\\.value", "hk.ust.cse.Prevision_PseudoImpl.StringBuffer.value");
      s_map3.put("java\\.io\\.File.__prop__name__", "hk.ust.cse.Prevision_PseudoImpl.File.__prop__name__");
      s_map3.put("java\\.io\\.File.__prop__parentFile__", "hk.ust.cse.Prevision_PseudoImpl.File.__prop__parentFile__");
      s_map3.put("java\\.io\\.File.__state__dir__", "hk.ust.cse.Prevision_PseudoImpl.File.__state__dir__");
      s_map3.put("java\\.io\\.File.__state__exist__", "hk.ust.cse.Prevision_PseudoImpl.File.__state__exist__");
    }
  }

  private static HashSet<String>           s_map1;
  private static Hashtable<String, String> s_map2;
  private static Hashtable<String, String> s_map3;
  private static HashSet<String>           s_notMatchCache;
}
