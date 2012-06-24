package hk.ust.cse.util.DbHelper;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DBHelper {
  
  public static ResultSet executeQuery(Connection conn, String sqlText) throws Exception {
    Statement statement = conn.createStatement();
    
    // execute
    ResultSet rs = statement.executeQuery(sqlText);
    return rs;
  }
  
  public static int executeNonQuery(Connection conn, String sqlText) throws Exception {
    Statement statement = conn.createStatement();
    
    // execute
    int ret = statement.executeUpdate(sqlText);
    return ret;
  }

  public static int[] executeBatch(Connection conn, String[] sqlTexts) throws Exception {
    // execute every statement in one transaction
    conn.setAutoCommit(false);
    
    // add batch
    int[] rets = new int[sqlTexts.length];
    for (int i = 0; i < sqlTexts.length; i++) {
      Statement statement = conn.createStatement();
      rets[i] = statement.executeUpdate(sqlTexts[i]);
    }
    conn.commit();
    return rets;
  }

  public static boolean exist(Connection conn, String tableName) throws Exception {
    DatabaseMetaData metaData = conn.getMetaData();
    ResultSet rs = metaData.getTables(null, null, tableName, null);
    boolean ret = rs.next();
    rs.close(); // close immediately
    return ret;
  }

  /* should be override */
  public static Connection openConnection(String url) throws Exception {
    return null;
  }
  
  /* should be override */
  public static Connection openConnection(String url, String userName, String password) throws Exception {
    return null;
  }
  
  public static void closeConnection(Connection conn) {
    if (conn != null) {
      try {
        conn.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
  }
}
