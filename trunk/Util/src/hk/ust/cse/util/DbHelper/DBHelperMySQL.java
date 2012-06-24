package hk.ust.cse.util.DbHelper;

import java.sql.Connection;
import java.sql.DriverManager;

public class DBHelperMySQL extends DBHelper {

  public static Connection openConnection(String url) throws Exception {
    return openConnection(url, "", "");
  }
  
  public static Connection openConnection(String url, String userName, String password) throws Exception {
    Class.forName("com.mysql.jdbc.Driver");
    url = url.startsWith("jdbc:mysql://") ? url : "jdbc:mysql://" + url;
    Connection conn = DriverManager.getConnection(url, userName, password);
    return conn;
  }
}
