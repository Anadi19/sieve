package edu.uci.ics.tippers.db;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.tippers.common.PolicyConstants;
import edu.uci.ics.tippers.common.PolicyEngineException;
import edu.uci.ics.tippers.fileop.Reader;
import edu.uci.ics.tippers.model.data.Presence;
import org.apache.commons.dbutils.DbUtils;

import java.io.IOException;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * Created by cygnus on 10/29/17.
 */
public class MySQLQueryManager {

    private static final Connection connection = MySQLConnectionManager.getInstance().getConnection();

    private static long timeout = 300000;

    public MySQLResult runWithThread(String query, MySQLResult mySQLResult) {

        Statement statement = null;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<MySQLResult> future = null;
        try {
            statement = connection.createStatement();
            QueryExecutor queryExecutor = new QueryExecutor(statement, query, mySQLResult);
            future = executor.submit(queryExecutor);
            mySQLResult = future.get(timeout, TimeUnit.MILLISECONDS);
            executor.shutdown();
            return mySQLResult;
        } catch (SQLException | InterruptedException | ExecutionException ex) {
            cancelStatement(statement, ex);
            throw new PolicyEngineException("Failed to query the database. " + ex);
        } catch (TimeoutException ex) {
            cancelStatement(statement, ex);
            future.cancel(true);
            mySQLResult.setTimeTaken(PolicyConstants.MAX_DURATION);
            return mySQLResult;
        } finally {
            DbUtils.closeQuietly(statement);
            executor.shutdownNow();
        }
    }

    private void cancelStatement(Statement statement, Exception ex) {
        System.out.println("Cancelling the current query statement. Timeout occurred" + ex);
        try {
            statement.cancel();
        } catch (SQLException exception) {
            throw new PolicyEngineException("Calling cancel() on the Statement issued exception. Details are: " + exception);
        }
    }


    private class QueryExecutor implements  Callable<MySQLResult>{

        Statement statement;
        String query;
        MySQLResult mySQLResult;

        public QueryExecutor(Statement statement, String query, MySQLResult mySQLResult) {
            this.statement = statement;
            this.query = query;
            this.mySQLResult = mySQLResult;
        }

        @Override
        public MySQLResult call() throws Exception {
            try {
                Instant start = Instant.now();
                ResultSet rs = statement.executeQuery(query);
                Instant end = Instant.now();
                int rowcount = 0;
                if (hasColumn(rs, "total")){
                    rs.next();
                    rowcount = rs.getInt(1);
                }
                else if (rs.last()) {
                    rowcount = rs.getRow();
                }
                if(mySQLResult.getPathName() != null && mySQLResult.getFileName() != null){
                    mySQLResult.writeResultsToFile(rs);
                }
                mySQLResult.setResultCount(rowcount);
                mySQLResult.setTimeTaken(Duration.between(start, end));
                return mySQLResult;
            } catch (SQLException e) {
                System.out.println("Exception raised by : " + query);
                cancelStatement(statement, e);
                e.printStackTrace();
                throw new PolicyEngineException("Error Running Query");
            }
        }

        public boolean hasColumn(ResultSet rs, String columnName) throws SQLException {
            ResultSetMetaData rsmd = rs.getMetaData();
            int columns = rsmd.getColumnCount();
            for (int x = 1; x <= columns; x++) {
                if (columnName.equals(rsmd.getColumnName(x))) {
                    return true;
                }
            }
            return false;
        }
    }


    /**
     * Compute the cost by execution time of the query and writes the results to file
     * @param predicates
     * @param pathName
     * @param fileName
     * @return
     * @throws PolicyEngineException
     */

    public Duration runTimedQuery(String predicates, String pathName, String fileName) throws PolicyEngineException {
        try {
            MySQLResult mySQLResult = new MySQLResult(pathName, fileName);
            return runWithThread(PolicyConstants.SELECT_ALL_SEMANTIC_OBSERVATIONS + predicates, mySQLResult).getTimeTaken();
        } catch (Exception e) {
            e.printStackTrace();
            throw new PolicyEngineException("Error Running Query");
        }
    }

    /**
     * Compute the cost by execution time of the query
     * @param predicates
     * @return
     * @throws PolicyEngineException
     */

    public Duration runTimedQuery(String predicates) throws PolicyEngineException {
        try {
            MySQLResult mySQLResult = new MySQLResult();
            return runWithThread(PolicyConstants.SELECT_ALL_SEMANTIC_OBSERVATIONS + predicates, mySQLResult).getTimeTaken();
        } catch (Exception e) {
            e.printStackTrace();
            throw new PolicyEngineException("Error Running Query");
        }
    }

    public MySQLResult runTimedQueryWithResultCount(String predicates) throws PolicyEngineException {
        try {
            MySQLResult mySQLResult = new MySQLResult();
            return runWithThread(PolicyConstants.SELECT_COUNT_STAR_SEMANTIC_OBSERVATIONS + predicates, mySQLResult);
        } catch (Exception e) {
            e.printStackTrace();
            throw new PolicyEngineException("Error Running Query");
        }
    }

    public List<Presence> parseJSONList(String jsonData) {
        ObjectMapper objectMapper = new ObjectMapper();
        List<Presence> query_results = null;
        try {
            query_results = objectMapper.readValue(jsonData, new TypeReference<List<Presence>>(){});
        } catch (IOException e) {
            e.printStackTrace();
        }
        return query_results;
    }

}