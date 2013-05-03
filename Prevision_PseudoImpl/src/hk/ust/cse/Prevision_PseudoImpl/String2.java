package hk.ust.cse.Prevision_PseudoImpl;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class String2 {
  
  public String2(char[] paramArrayOfChar)
  {
    this.offset = 0;
    this.count = paramArrayOfChar.length;
    this.value = paramArrayOfChar;
  }
  
  public String2(String paramString)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(char[] paramArrayOfChar, int paramInt1, int paramInt2)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(int[] paramArrayOfInt, int paramInt1, int paramInt2)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  @Deprecated
  public String2(byte[] paramArrayOfByte, int paramInt1, int paramInt2, int paramInt3)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  @Deprecated
  public String2(byte[] paramArrayOfByte, int paramInt)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }
  

  public String2(byte[] paramArrayOfByte, int paramInt1, int paramInt2, String paramString)
    throws UnsupportedEncodingException
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(byte[] paramArrayOfByte, int paramInt1, int paramInt2, Charset paramCharset)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(byte[] paramArrayOfByte, String paramString)
    throws UnsupportedEncodingException
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(byte[] paramArrayOfByte, Charset paramCharset)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(byte[] paramArrayOfByte, int paramInt1, int paramInt2)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(byte[] paramArrayOfByte)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(java.lang.StringBuffer paramStringBuffer)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }

  public String2(java.lang.StringBuilder paramStringBuilder)
  {
    this.offset = -100;
    this.count = -100;
    this.value = null;
  }
  
  String2(int paramInt1, int paramInt2, char[] paramArrayOfChar)
  {
    this.value = null;
    this.offset = -100;
    this.count =  -100;
  }
  
  public int indexOf(int paramInt) {
    
    if (value.length == 0) {
      return -1;
    }
    
    if (value[0] == paramInt) {
      return 0;
    }
    else if (value.length == 1) {
      return -1;
    }
    
    if (value[1] == paramInt) {
      return 1;
    }
    else if (value.length == 2) {
      return -1;
    }
    
    if (value[2] == paramInt) {
      return 2;
    }
    else if (value.length == 3) {
      return -1;
    }
    
    if (value[3] == paramInt) {
      return 3;
    }
//    else if (value.length == 4) {
//      return -1;
//    }
//    
//    if (value[4] == paramInt) {
//      return 4;
//    }
//    else if (value.length == 5) {
//      return -1;
//    }
//    
//    if (value[5] == paramInt) {
//      return 5;
//    }
//    else if (value.length == 6) {
//      return -1;
//    }
//    
//    if (value[6] == paramInt) {
//      return 6;
//    }
//    else if (value.length == 7) {
//      return -1;
//    }
    
    return -1;
  }
  
  public int indexOf(String2 str) {
    if (str.value.length == 0) {
      return 0;
    }
    else if (value.length == 0) {
      return -1;
    }
    else if (str.value.length == 1) {
      if (value[0] == str.value[0]) {
        return 0;
      }
      else if (value.length == 1) {
        return -1;
      }
      if (value[1] == str.value[0]) {
        return 1;
      }
      else if (value.length == 2) {
        return -1;
      }
      if (value[2] == str.value[0]) {
        return 2;
      }
      else if (value.length == 3) {
        return -1;
      }
      if (value[3] == str.value[0]) {
        return 3;
      }
      else if (value.length == 4) {
        return -1;
      }
      if (value[4] == str.value[0]) {
        return 4;
      }
      else if (value.length == 5) {
        return -1;
      }
      if (value[5] == str.value[0]) {
        return 5;
      }
      else if (value.length == 6) {
        return -1;
      }
      if (value[6] == str.value[0]) {
        return 6;
      }
      else {
        return -1;
      }
    }
    else if (str.value.length == 2) {
      if (value[0] == str.value[0] && value[1] == str.value[1]) {
        return 0;
      }
      if (value[1] == str.value[0] && value[2] == str.value[1]) {
        return 1;
      }
      if (value[2] == str.value[0] && value[3] == str.value[1]) {
        return 2;
      }
      if (value[3] == str.value[0] && value[4] == str.value[1]) {
        return 3;
      }
//      if (value[4] == str.value[0] && value[5] == str.value[1]) {
//        return 4;
//      }
//      if (value[5] == str.value[0] && value[6] == str.value[1]) {
//        return 5;
//      }
//      if (value[6] == str.value[0] && value[7] == str.value[1]) {
//        return 6;
//      }
    }
//    else if (str.value.length == 3) {
//      if (value[0] == str.value[0] && value[1] == str.value[1] && value[2] == str.value[2]) {
//        return 0;
//      }
//      if (value[1] == str.value[0] && value[2] == str.value[1] && value[3] == str.value[2]) {
//        return 1;
//      }
//      if (value[2] == str.value[0] && value[3] == str.value[1] && value[4] == str.value[2]) {
//        return 2;
//      }
//      if (value[3] == str.value[0] && value[4] == str.value[1] && value[5] == str.value[2]) {
//        return 3;
//      }
//      if (value[4] == str.value[0] && value[5] == str.value[1] && value[6] == str.value[2]) {
//        return 4;
//      }
//      if (value[5] == str.value[0] && value[6] == str.value[1] && value[7] == str.value[2]) {
//        return 5;
//      }
//      if (value[6] == str.value[0] && value[7] == str.value[1] && value[8] == str.value[2]) {
//        return 6;
//      }
//    }
//    else if (str.value.length == 4) {
//      if (value[0] == str.value[0] && value[1] == str.value[1] && value[2] == str.value[2] && value[3] == str.value[3]) {
//        return 0;
//      }
//      if (value[1] == str.value[0] && value[2] == str.value[1] && value[3] == str.value[2] && value[4] == str.value[3]) {
//        return 1;
//      }
//      if (value[2] == str.value[0] && value[3] == str.value[1] && value[4] == str.value[2] && value[5] == str.value[3]) {
//        return 2;
//      }
//      if (value[3] == str.value[0] && value[4] == str.value[1] && value[5] == str.value[2] && value[6] == str.value[3]) {
//        return 3;
//      }
//      if (value[4] == str.value[0] && value[5] == str.value[1] && value[6] == str.value[2] && value[7] == str.value[3]) {
//        return 4;
//      }
//      if (value[5] == str.value[0] && value[6] == str.value[1] && value[7] == str.value[2] && value[8] == str.value[3]) {
//        return 5;
//      }
//      if (value[6] == str.value[0] && value[7] == str.value[1] && value[8] == str.value[2] && value[9] == str.value[3]) {
//        return 6;
//      }
//    }
    return -1;
  }
  
  public int indexOf(int paramInt, int start) {

    if (value.length <= start) {
      return -1;
    }
    
    if (value[start] == paramInt) {
      return start;
    }
    else if (value.length == start + 1){
      return -1;
    }
    
    if (value[start + 1] == paramInt) {
      return start + 1;
    }
    else if (value.length == start + 2){
      return -1;
    }

    if (value[start + 2] == paramInt) {
      return start + 2;
    }
    else if (value.length == start + 3){
      return -1;
    }
    
    if (value[start + 3] == paramInt) {
      return start + 3;
    }
//    else if (value.length == start + 4){
//      return -1;
//    }
    
//    if (value[start + 4] == paramInt) {
//      return start + 4;
//    }
//    else if (value.length == start + 5){
//      return -1;
//    }
    
//    if (value[start + 5] == paramInt) {
//      return start + 5;
//    }
//    else if (value.length == start + 6){
//      return -1;
//    }
//    
//    if (value[start + 6] == paramInt) {
//      return start + 6;
//    }
//    else if (value.length == start + 7){
//      return -1;
//    }
    
    return -1;
  }
  
  public int indexOf(String2 str, int start) {
    if (str.value.length == 0) {
      return start;
    }
    else if (value.length == start) {
      return -1;
    }
    else if (str.value.length == 1) {
      if (value[start] == str.value[0]) {
        return start;
      }
      if (value[start + 1] == str.value[0]) {
        return start + 1;
      }
      if (value[start + 2] == str.value[0]) {
        return start + 2;
      }
      if (value[start + 3] == str.value[0]) {
        return start + 3;
      }
      if (value[start + 4] == str.value[0]) {
        return start + 4;
      }
      if (value[start + 5] == str.value[0]) {
        return start + 5;
      }
      if (value[start + 6] == str.value[0]) {
        return start + 6;
      }
    }
    else if (str.value.length == 2) {
      if (value[start] == str.value[0] && value[start + 1] == str.value[1]) {
        return start;
      }
      if (value[start + 1] == str.value[0] && value[start + 2] == str.value[1]) {
        return start + 1;
      }
      if (value[start + 2] == str.value[0] && value[start + 3] == str.value[1]) {
        return start + 2;
      }
      if (value[start + 3] == str.value[0] && value[start + 4] == str.value[1]) {
        return start + 3;
      }
      if (value[start + 4] == str.value[0] && value[start + 5] == str.value[1]) {
        return start + 4;
      }
      if (value[start + 5] == str.value[0] && value[start + 6] == str.value[1]) {
        return start + 5;
      }
      if (value[start + 6] == str.value[0] && value[start + 7] == str.value[1]) {
        return start + 6;
      }
    }
//    else if (str.value.length == 3) {
//      if (value[start] == str.value[0] && value[start + 1] == str.value[1] && value[start + 2] == str.value[2]) {
//        return start;
//      }
//      if (value[start + 1] == str.value[0] && value[start + 2] == str.value[1] && value[start + 3] == str.value[2]) {
//        return start + 1;
//      }
//      if (value[start + 2] == str.value[0] && value[start + 3] == str.value[1] && value[start + 4] == str.value[2]) {
//        return start + 2;
//      }
//      if (value[start + 3] == str.value[0] && value[start + 4] == str.value[1] && value[start + 5] == str.value[2]) {
//        return start + 3;
//      }
//      if (value[start + 4] == str.value[0] && value[start + 5] == str.value[1] && value[start + 6] == str.value[2]) {
//        return start + 4;
//      }
//      if (value[start + 5] == str.value[0] && value[start + 6] == str.value[1] && value[start + 7] == str.value[2]) {
//        return start + 5;
//      }
//      if (value[start + 6] == str.value[0] && value[start + 7] == str.value[1] && value[start + 8] == str.value[2]) {
//        return start + 6;
//      }
//    }
//    else if (str.value.length == 4) {
//      if (value[start] == str.value[0] && value[start + 1] == str.value[1] && value[start + 2] == str.value[2] && value[start + 3] == str.value[3]) {
//        return start;
//      }
//      if (value[start + 1] == str.value[0] && value[start + 2] == str.value[1] && value[start + 3] == str.value[2] && value[start + 4] == str.value[3]) {
//        return start + 1;
//      }
//      if (value[start + 2] == str.value[0] && value[start + 3] == str.value[1] && value[start + 4] == str.value[2] && value[start + 5] == str.value[3]) {
//        return start + 2;
//      }
//      if (value[start + 3] == str.value[0] && value[start + 4] == str.value[1] && value[start + 5] == str.value[2] && value[start + 6] == str.value[3]) {
//        return start + 3;
//      }
//      if (value[start + 4] == str.value[0] && value[start + 5] == str.value[1] && value[start + 6] == str.value[2] && value[start + 7] == str.value[3]) {
//        return start + 4;
//      }
//      if (value[start + 5] == str.value[0] && value[start + 6] == str.value[1] && value[start + 7] == str.value[2] && value[start + 8] == str.value[3]) {
//        return start + 5;
//      }
//      if (value[start + 6] == str.value[0] && value[start + 7] == str.value[1] && value[start + 8] == str.value[2] && value[start + 9] == str.value[3]) {
//        return start + 6;
//      }
//    }
    return -1;
  }
  
  public boolean startsWith(String2 str) {
    if (str.value.length == 0) {
      return true;
    }
    else if (str.value.length == 1) {
      if (value[0] == str.value[0]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 2) {
      if (value[0] == str.value[0] && value[1] == str.value[1]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 3) {
      if (value[0] == str.value[0] && value[1] == str.value[1] && value[2] == str.value[2]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 4) {
      if (value[0] == str.value[0] && value[1] == str.value[1] && value[2] == str.value[2] && value[3] == str.value[3]) {
        return true;
      }
      else {
        return false;
      }
    }
//    else if (str.value.length == 5) {
//      if (value[0] == str.value[0] && value[1] == str.value[1] && value[2] == str.value[2] && value[3] == str.value[3] && value[4] == str.value[4]) {
//        return true;
//      }
//    }
    return false;
  }
  
  public boolean endsWith(String2 str) {
    if (str.value.length == 0) {
      return true;
    }
    else if (str.value.length == 1) {
      if (value[value.length - 1] == str.value[0]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 2) {
      if (value[value.length - 2] == str.value[0] && value[value.length - 1] == str.value[1]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 3) {
      if (value[value.length - 3] == str.value[0] && value[value.length - 2] == str.value[1] && value[value.length - 1] == str.value[2]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 4) {
      if (value[value.length - 4] == str.value[0] && value[value.length - 3] == str.value[1] && value[value.length - 2] == str.value[2] && value[value.length - 1] == str.value[3]) {
        return true;
      }
      else {
        return false;
      }
    }
    else if (str.value.length == 5) {
      if (value[value.length - 5] == str.value[0] && value[value.length - 4] == str.value[1] && value[value.length - 3] == str.value[2] && value[value.length - 2] == str.value[3] && value[value.length - 1] == str.value[4]) {
        return true;
      }
      else {
        return false;
      }
    }
    return false;
  }
  
  public char charAt(int paramInt) {
    if ((paramInt < 0) || (paramInt >= value.length))
      throw new StringIndexOutOfBoundsException(paramInt);

    return this.value[paramInt];
  }
  
  public String2 substring(int paramInt) {
    if (paramInt < 0 || paramInt >= value.length) {
      throw new IndexOutOfBoundsException();
    }
    else {
//      if (paramInt == 0) {
//        if (value.length == 1) {
//          char[] chars = new char[] {value[0]};
//          return new String2(chars);
//        }
//        else if (value.length == 2) {
//          char[] chars = new char[] {value[0], value[1]};
//          return new String2(chars);
//        }
//        else if (value.length == 3) {
//          char[] chars = new char[] {value[0], value[1], value[2]};
//          return new String2(chars);
//        }
//      }
      if (paramInt == 1) {
        if (value.length == 2) {
          char[] chars = new char[] {value[1]};
          return new String2(chars);
        }
//        else if (value.length == 3) {
//          char[] chars = new char[] {value[1], value[2]};
//          return new String2(chars);
//        }
//        else if (value.length == 4) {
//          char[] chars = new char[] {value[1], value[2], value[3]};
//          return new String2(chars);
//        }
      }
      else if (paramInt == 2) {
        if (value.length == 3) {
          char[] chars = new char[] {value[2]};
          return new String2(chars);
        }
//        else if (value.length == 4) {
//          char[] chars = new char[] {value[2], value[3]};
//          return new String2(chars);
//        }
//        else if (value.length == 5) {
//          char[] chars = new char[] {value[2], value[3], value[4]};
//          return new String2(chars);
//        }
      }
    }
    return null;
  }
  
  public String2 substring(int paramInt1, int paramInt2) {
    if (paramInt1 < 0 || paramInt1 >= value.length || paramInt2 < 0 || paramInt2 > value.length || paramInt1 > paramInt2) {
      throw new IndexOutOfBoundsException();
    }
    else {
      int distance = paramInt2 - paramInt1;
      if (distance == 1) {
        char[] chars = new char[] {value[paramInt1]};
        return new String2(chars);
      }
      else if (distance == 2) {
        char[] chars = new char[] {value[paramInt1], value[paramInt1 + 1]};
        return new String2(chars);
      }
      else if (distance == 3) {
        char[] chars = new char[] {value[paramInt1], value[paramInt1 + 1], value[paramInt1 + 2]};
        return new String2(chars);
      }
      else if (distance == 4) {
        char[] chars = new char[] {value[paramInt1], value[paramInt1 + 1], value[paramInt1 + 2], value[paramInt1 + 3]};
        return new String2(chars);
      }
      else if (distance == 5) {
        char[] chars = new char[] {value[paramInt1], value[paramInt1 + 1], value[paramInt1 + 2], value[paramInt1 + 3], value[paramInt1 + 4]};
        return new String2(chars);
      }
    }
    return null;
  }
  
  public boolean equals(String2 str) {
    if (this == str && value == str.value) {
      return true;
    }
    else if (this != str && value != str.value && value.length != str.value.length) {
      return false;
    }
    else if (this != str && value != str.value && value.length == str.value.length) {
      boolean good = value[0] == str.value[0];
      if (value.length == 1) {
        return good;
      }
      else if (good) {
        good = value[1] == str.value[1];
        if (value.length == 2) {
          return good;
        }
        else if (good) {
          good = value[2] == str.value[2];
          if (value.length == 3) {
            return good;
          }
          else if (good) {
            good = value[3] == str.value[3];
            if (value.length == 4) {
              return good;
            }
//            else if (good) {
//              good = value[4] == str.value[4];
//              if (value.length == 5) {
//                return good;
//              }
//              else if (good) {
//                good = value[5] == str.value[5];
//                if (value.length == 6) {
//                  return good;
//                }
//                else if (good) {
//                  good = value[6] == str.value[6];
//                  if (value.length == 7) {
//                    return good;
//                  }
//                }
//              }
//            }
          }
        }
      }
    }
    return false;
  }
  
  public boolean equalsIgnoreCase(String2 str) {
    return this == str && value == str.value && value.length == str.value.length;
  }
  
  public String2 trim() {
    int i = value.length;
    int j = 0;

    while ((j < i) && (this.value[j] <= ' '))
      ++j;

    while ((j < i) && (this.value[i - 1] <= ' '))
      --i;

    return (((j > 0) || (i < this.count)) ? substring(j, i) : this);
  }
  
  public int length() {
    return value.length;
  }
  
  public int offset;
  public int count;
  public char[] value;
}
