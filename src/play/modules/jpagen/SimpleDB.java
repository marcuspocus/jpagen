package play.modules.jpagen;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import play.exceptions.DatabaseException;

import com.mchange.v2.c3p0.DriverManagerDataSourceFactory;

/**
 * Database connection utilities.
 */
@SuppressWarnings("deprecation")
public class SimpleDB {

	public SimpleDB(String driver, String url, String user, String password){
		try {
			Class.forName(driver);
			datasource = DriverManagerDataSourceFactory.create(driver, url, user, password);
		} catch (Exception e) {
			throw new RuntimeException("ERROR: Can't get connection: ", e);
		}
	}
	/**
	 * The loaded datasource.
	 */
	public DataSource datasource = null;

	/**
	 * Close the connection opened for the current thread.
	 */
	public void close() {
		if (localConnection.get() != null) {
			try {
				Connection connection = localConnection.get();
				localConnection.set(null);
				connection.close();
			} catch (Exception e) {
				throw new DatabaseException("It's possible than the connection was not properly closed !", e);
			}
		}
	}

	ThreadLocal<Connection> localConnection = new ThreadLocal<Connection>();

	/**
	 * Open a connection for the current thread.
	 * 
	 * @return A valid SQL connection
	 */
	public Connection getConnection() {
		try {
			if (localConnection.get() != null) {
				return localConnection.get();
			}
			Connection connection = datasource.getConnection();
			localConnection.set(connection);
			return connection;
		} catch (SQLException ex) {
			throw new DatabaseException("Cannot obtain a new connection (" + ex.getMessage() + ")", ex);
		} catch (NullPointerException e) {
			if (datasource == null) {
				throw new DatabaseException("No database found. Check the configuration of your application.", e);
			}
			throw e;
		}
	}

	/**
	 * Execute an SQL update
	 * 
	 * @param SQL
	 * @return false if update failed
	 */
	public boolean execute(String SQL) {
		try {
			return getConnection().createStatement().execute(SQL);
		} catch (SQLException ex) {
			throw new DatabaseException(ex.getMessage(), ex);
		}
	}

	/**
	 * Execute an SQL query
	 * 
	 * @param SQL
	 * @return The query resultSet
	 */
	public ResultSet executeQuery(String SQL) {
		try {
			return getConnection().createStatement().executeQuery(SQL);
		} catch (SQLException ex) {
			throw new DatabaseException(ex.getMessage(), ex);
		}
	}
}
