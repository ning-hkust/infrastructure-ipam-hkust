package hk.ust.cse.Prevision_PseudoImpl;

public class Integer2 {
  
  public static int parseInt(String2 paramString) throws NumberFormatException {
    if (paramString.value[0] == '-') {
      if (paramString.value.length >= 2) {
        if (paramString.value[1] >= '0' && paramString.value[1] <= '9') {
          if (paramString.value.length == 2) {
            return 48 - paramString.value[1];
          }
          else if (paramString.value[1] > '0' && paramString.value[2] >= '0' && paramString.value[2] <= '9') {
            if (paramString.value.length == 3) {
              return 0 - ((paramString.value[1] - 48) * 10 + (paramString.value[2] - 48));
            }
            else if (paramString.value[3] >= '0' && paramString.value[3] <= '9') {
              if (paramString.value.length == 4) {
                return 0 - ((paramString.value[1] - 48) * 100 + (paramString.value[2] - 48) * 10 + (paramString.value[3] - 48));
              }
            }
          }
        }
      }
    }
    else {
      if (paramString.value.length >= 1) {
        if (paramString.value[0] >= '0' && paramString.value[0] <= '9') {
          if (paramString.value.length == 1) {
            return paramString.value[0] - 48;
          }
          else if (paramString.value[0] > '0' && paramString.value[1] >= '0' && paramString.value[1] <= '9') {
            if (paramString.value.length == 2) {
              return (paramString.value[0] - 48) * 10 + (paramString.value[1] - 48);
            }
            else if (paramString.value[2] >= '0' && paramString.value[2] <= '9') {
              if (paramString.value.length == 3) {
                return (paramString.value[0] - 48) * 100 + (paramString.value[1] - 48) * 10 + (paramString.value[2] - 48);
              }
            }
          }
        }
      }
    }
    throw new NumberFormatException();
  }
  
  public static int parseInt(String2 paramString, int radix) throws NumberFormatException {
    if (radix == 10) {
      return parseInt(paramString);
    }
    else if (radix == 16) {
      if (paramString.value.length >= 1) {
        int sub1 = (paramString.value[0] >= '0' && paramString.value[0] <= '9') ? 48 : (paramString.value[0] >= 'A' && paramString.value[0] <= 'F') ? 55 : (paramString.value[0] >= 'a' && paramString.value[0] <= 'f') ? 87 : 48003519;
        if (sub1 != 48003519) {
          if (paramString.value.length == 1) {
            return paramString.value[0] - sub1;
          }
          else {
            int sub2 = (paramString.value[1] >= '0' && paramString.value[1] <= '9') ? 48 : (paramString.value[1] >= 'A' && paramString.value[1] <= 'F') ? 55 : (paramString.value[1] >= 'a' && paramString.value[1] <= 'f') ? 87 : 48003519;
            if (paramString.value[0] > '0' && sub2 != 48003519) {
              if (paramString.value.length == 2) {
                return (paramString.value[0] - sub1) * 16 + (paramString.value[1] - sub2);
              }
              else {
                int sub3 = (paramString.value[2] >= '0' && paramString.value[2] <= '9') ? 48 : (paramString.value[2] >= 'A' && paramString.value[2] <= 'F') ? 55 : (paramString.value[2] >= 'a' && paramString.value[2] <= 'f') ? 87 : 48003519;
                if (sub3 != 48003519) {
                  if (paramString.value.length == 3) {
                    return (paramString.value[0] - sub1) * 256 + (paramString.value[1] - sub2) * 16 + (paramString.value[2] - sub3);
                  }
                }
              }
            }
          }
        }
      }
    }
    throw new NumberFormatException(); 
  }
}
