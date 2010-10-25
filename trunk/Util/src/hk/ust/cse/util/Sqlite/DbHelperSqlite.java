package hk.ust.cse.util.Sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DbHelperSqlite {
  
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
    ResultSet rs = executeQuery(conn, "Select Count(*) from sqlite_master Where type='table' and name='" + tableName + "'");
    boolean ret = rs.getInt(1) > 0;
    rs.close(); // close immediately
    return ret;
  }

  public static Connection openConnection(String dbName) throws Exception {
    Class.forName("org.sqlite.JDBC");
    Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbName + ".db");
    return conn;
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

  public static void main(String[] args) throws Exception {
    Class.forName("org.sqlite.JDBC");
    Connection conn = DriverManager.getConnection("jdbc:sqlite:test.db");
    Statement stat = conn.createStatement();
    stat.executeUpdate("drop table if exists people;");
    stat.executeUpdate("create table people (name, occupation);");
    PreparedStatement prep = conn
        .prepareStatement("insert into people values (?, ?);");

    prep.setString(1, "Gandhi");
    prep.setString(2, "politics");
    prep.addBatch();
    prep.setString(1, "Turing");
    prep.setString(2, "computers");
    prep.addBatch();
    prep.setString(1, "Wittgenstein");
    prep.setString(2, "smartypants");
    prep.addBatch();

    conn.setAutoCommit(false);
    prep.executeBatch();
    conn.setAutoCommit(true);

    ResultSet rs = stat.executeQuery("select * from people;");
    while (rs.next()) {
      System.out.println("name = " + rs.getString("name"));
      System.out.println("job = " + rs.getString("occupation"));
    }
    rs.close();
    prep.close();
    stat.close();
    conn.close();
  }
}
