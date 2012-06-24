package hk.ust.cse.util.DbHelper;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbHelperSqlite extends DBHelper {
  
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

  public static Connection openConnection(String dbName, String userName, String password) throws Exception {
    return openConnection(dbName);
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
