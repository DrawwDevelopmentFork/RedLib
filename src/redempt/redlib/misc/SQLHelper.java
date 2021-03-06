package redempt.redlib.misc;

import java.io.Closeable;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Wraps a {@link Connection} and offers helpful methods that don't need to be surrounded in a try/catch
 * @author Redempt
 */
public class SQLHelper implements Closeable {
	
	/**
	 * Opens a SQLite database file
	 * @param file The path to the SQLite database file
	 * @return The Connection to this SQLite database
	 */
	public static Connection openSQLite(java.nio.file.Path file) {
		try {
			Class.forName("org.sqlite.JDBC");
			return DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath().toString());
		} catch (ClassNotFoundException | SQLException e) {
			sneakyThrow(e);
			return null;
		}
	}
	
	/**
	 * Opens a connection to a MySQL database
	 * @param ip The IP address to connect to
	 * @param port The port to connect to
	 * @param username The username to log in with
	 * @param password The password to log in with
	 * @param database The database to use, will be created if it doesn't exist
	 * @return The Connection to the MySQL database
	 */
	public static Connection openMySQL(String ip, int port, String username, String password, String database) {
		try {
			Class.forName("com.mysql.jdbc.Driver");
			Connection connection = DriverManager.getConnection("jdbc:mysql://" + ip + ":" + port + "/?user=" + username + "&password=" + password);
			connection.createStatement().execute("CREATE DATABASE IF NOT EXISTS " + database + ";");
			connection.createStatement().execute("USE " + database + ";");
			return connection;
		} catch (ClassNotFoundException | SQLException e) {
			sneakyThrow(e);
			return null;
		}
	}
	
	/**
	 * Opens a connection to a MySQL database at localhost:3306
	 * @param username The username to log in with
	 * @param password The password to log in with
	 * @param database The database to use, will be created if it doesn't exist
	 * @return The Connection to the MySQL database
	 */
	public static Connection openMySQL(String username, String password, String database) {
		return openMySQL("localhost", 3306, username, password, database);
	}
	
	private static <T extends Exception> void sneakyThrow(Exception e) throws T {
		throw (T) e;
	}
	
	private Connection connection;
	
	/**
	 * Constructs a SQLHelper from a Connection. Get the Connection using one of the static SQLHelper open methods.
	 * @param connection The SQL Connection to wrap
	 */
	public SQLHelper(Connection connection) {
		this.connection = connection;
	}
	
	/**
	 * Executes a SQL query as a prepared statement, setting its fields to the elements of the vararg passed
	 * @param command The SQL command to execute
	 * @param fields A vararg of the fields to set in the prepared statement
	 */
	public void execute(String command, Object... fields) {
		try {
			PreparedStatement statement = prepareStatement(command, fields);
			statement.execute();
		} catch (SQLException e) {
			sneakyThrow(e);
		}
	}
	
	/**
	 * Executes a SQL query as a prepared statement, setting its fields to the elements of the vararg passed,
	 * returning the value in the first column of the first row in the results
	 * @param query The SQL query to execute
	 * @param fields A vararg of the fields to set in the prepared statement
	 * @param <T> The type to cast the return value to
	 * @return The value in the first column of the first row of the returned results, or null if none is present
	 */
	public <T> T querySingleResult(String query, Object... fields) {
		try {
			PreparedStatement statement = prepareStatement(query, fields);
			ResultSet results = statement.executeQuery();
			if (!results.next()) {
				return null;
			}
			T obj = (T) results.getObject(1);
			results.close();
			return obj;
		} catch (SQLException e) {
			sneakyThrow(e);
			return null;
		}
	}
	
	/**
	 * Executes a SQL query as a prepared statement, setting its fields to the elements of the vararg passed,
	 * returning the value in the first column of the first row in the results as a String.
	 * @param query The SQL query to execute
	 * @param fields A vararg of the fields to set in the prepared statement
	 * @return The String in the first column of the first row of the returned results, or null if none is present
	 * @implNote This method exists because {@link ResultSet#getObject(int)} can return an Integer if the String in the
	 * column can be parsed into one.
	 */
	public String querySingleResultString(String query, Object... fields) {
		try {
			PreparedStatement statement = prepareStatement(query, fields);
			ResultSet results = statement.executeQuery();
			if (!results.next()) {
				return null;
			}
			return results.getString(1);
		} catch (SQLException e) {
			sneakyThrow(e);
			return null;
		}
	}
	
	/**
	 * Executes a SQL query as a prepared statement, setting its fields to the elements of the vararg passed,
	 * returning a list of values in the first column of each row in the results
	 * @param query The SQL query to execute
	 * @param fields A vararg of the fields to set in the prepared statement
	 * @param <T> The type to populate the list with and return
	 * @return A list of the value in the first column of each row returned by the query
	 */
	public <T> List<T> queryResultList(String query, Object... fields) {
		List<T> list = new ArrayList<>();
		try {
			PreparedStatement statement = prepareStatement(query, fields);
			ResultSet results = statement.executeQuery();
			while (results.next()) {
				list.add((T) results.getObject(1));
			}
			results.close();
		} catch (SQLException e) {
			sneakyThrow(e);
		}
		return list;
	}
	
	/**
	 * Executes a SQL query as a prepared statement, setting its fields to the elements of the vararg passed,
	 * returning a String list of values in the first column of each row in the results
	 * @param query The SQL query to execute
	 * @param fields A vararg of the fields to set in the prepared statement
	 * @return A String list of the value in the first column of each row returned by the query
	 * @implNote This method exists because {@link ResultSet#getObject(int)} can return an Integer if the String in the
	 * column can be parsed into one.
	 */
	public List<String> queryResultStringList(String query, Object... fields) {
		List<String> list = new ArrayList<>();
		try {
			PreparedStatement statement = prepareStatement(query, fields);
			ResultSet results = statement.executeQuery();
			while (results.next()) {
				list.add(results.getString(1));
			}
			results.close();
		} catch (SQLException e) {
			sneakyThrow(e);
		}
		return list;
	}
	
	/**
	 * Executes a SQL query as a prepared statement, setting its fields to the elements of the vararg passed.
	 * Returns a {@link Results}, which wraps a {@link ResultSet} for easier use
	 * @param query The SQL query to execute
	 * @param fields A vararg of the fields to set in the prepared statement
	 * @return The results of the query
	 */
	public Results queryResults(String query, Object... fields) {
		try {
			ResultSet results = prepareStatement(query, fields).executeQuery();
			return new Results(results);
		} catch (SQLException e) {
			sneakyThrow(e);
			return null;
		}
	}
	
	/**
	 * @return The Connection this SQLHelper wraps
	 */
	public Connection getConnection() {
		return connection;
	}
	
	/**
	 * Prepares a statement, setting its fields to the elements of the vararg passed
	 * @param query The SQL query to prepare
	 * @param fields A vararg of the fields to set in the prepared statement
	 * @return The PreparedStatement with its fields set
	 */
	public PreparedStatement prepareStatement(String query, Object... fields) {
		try {
			PreparedStatement statement = connection.prepareStatement(query);
			int i = 1;
			for (Object object : fields) {
				statement.setObject(i, object);
				i++;
			}
			return statement;
		} catch (SQLException e) {
			sneakyThrow(e);
			return null;
		}
	}
	
	/**
	 * Closes the underlying connection this SQLHelper wraps
	 */
	@Override
	public void close() {
		try {
			connection.close();
		} catch (SQLException e) {
			sneakyThrow(e);
		}
	}
	
	/**
	 * Wraps a {@link ResultSet} with easier use
	 * @author Redempt
	 */
	public static class Results implements Closeable {
		
		private ResultSet results;
		private boolean empty;
		
		private Results(ResultSet results) {
			this.results = results;
			try {
				empty = !results.next();
			} catch (SQLException e) {
				sneakyThrow(e);
			}
		}
		
		/**
		 * @return False if the first call of {@link ResultSet#next()} on the wrapped ResultSet returned false,
		 * true otherwise
		 */
		public boolean isEmpty() {
			return empty;
		}
		
		/**
		 * Moves to the next row in the wrapped ResultSet. Note that this method is called immediately when the
		 * Results object is constructed, and does not need to be called to retrieve the items in the first row.
		 * @return True if there is another row available in the wrapped ResultSet
		 */
		public boolean next() {
			try {
				return results.next();
			} catch (SQLException e) {
				sneakyThrow(e);
				return false;
			}
		}
		
		/**
		 * Performs an operation on every row in these Results, passing itself each time it iterates to a new row
		 * @param lambda The callback to be run on every row in these Results
		 */
		public void forEach(Consumer<Results> lambda) {
			if (isEmpty()) {
				return;
			}
			lambda.accept(this);
			while (next()) {
				lambda.accept(this);
			}
		}
		
		/**
		 * Gets an Object in the given column in the current row
		 * @param column The index of the column to get, starting at 1
		 * @param <T> The type to cast the return value to
		 * @return The value in the column
		 */
		public <T> T get(int column) {
			try {
				return (T) results.getObject(column);
			} catch (SQLException e) {
				sneakyThrow(e);
				return null;
			}
		}
		
		/**
		 * Gets a String in the given column in the current row
		 * @param column The index of the column to get, starting at 1
		 * @return The String in the column
		 * @implNote This method exists because {@link ResultSet#getObject(int)} can return an Integer if the String in the
		 * column can be parsed into one.
		 */
		public String getString(int column) {
			try {
				return results.getString(column);
			} catch (SQLException e) {
				sneakyThrow(e);
				return null;
			}
		}
		
		/**
		 * Closes the wrapped ResultSet. Call this when you are done using these Results.
		 */
		@Override
		public void close() {
			try {
				results.close();
			} catch (SQLException e) {
				sneakyThrow(e);
			}
		}
		
	}
	
}
