package edu.uci.ics.tippers.manager;

import edu.uci.ics.tippers.common.PolicyConstants;
import edu.uci.ics.tippers.db.MySQLConnectionManager;
import edu.uci.ics.tippers.db.PGSQLConnectionManager;
import edu.uci.ics.tippers.generation.policy.PolicyGen;
import edu.uci.ics.tippers.model.policy.BEPolicy;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FlatPolicyPersistor {

    private static FlatPolicyPersistor _instance = new FlatPolicyPersistor();

    private static Connection connection = MySQLConnectionManager.getInstance().getConnection();

    public static FlatPolicyPersistor getInstance() {
        return _instance;
    }

    /**
     * @param bePolicyList
     */
    public void insertPolicies(List<BEPolicy> bePolicyList) {

        try {
            connection.setAutoCommit(true);
            String policyInsert = "INSERT INTO FLAT_POLICY " +
                    "(id, querier, purpose, enforcement_action, inserted_at, ownerEq, profEq, groupEq, " +
                    "locEq, dateGe, dateLe, timeGe, timeLe, selectivity) VALUES (?, ?, ?, ?, ?, ?, ?," +
                    "?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement policyStmt = connection.prepareStatement(policyInsert);
            for (BEPolicy bePolicy : bePolicyList) {
                policyStmt.setString(1, bePolicy.getId());
                policyStmt.setString(2, bePolicy.fetchQuerier());
                policyStmt.setString(3, bePolicy.getPurpose());
                policyStmt.setString(4, bePolicy.getAction());
                policyStmt.setTimestamp(5, bePolicy.getInserted_at());
                policyStmt.setInt(6, bePolicy.fetchOwner());
                policyStmt.setString(7, bePolicy.fetchProfile());
                policyStmt.setString(8, bePolicy.fetchGroup());
                policyStmt.setString(9, bePolicy.fetchLocation());
                List<Date> start_date = bePolicy.fetchDate();
                policyStmt.setDate(10, start_date.get(0));
                policyStmt.setDate(11, start_date.get(1));
                List<Time> start_time = bePolicy.fetchTime();
                policyStmt.setTime(12, start_time.get(0));
                policyStmt.setTime(13, start_time.get(1));
                policyStmt.setFloat(14, bePolicy.computeL());
                policyStmt.addBatch();
            }
            policyStmt.executeBatch();
            policyStmt.close();
        } catch(SQLException e){
            e.printStackTrace();
        }
    }


    public static void main(String [] args){
        PolicyGen pg = new PolicyGen();
        List<Integer> users = pg.getAllUsers();
        PolicyPersistor polper = new PolicyPersistor();
        FlatPolicyPersistor flapolper = new FlatPolicyPersistor();
        for(int user: users) {
            List<BEPolicy> policiesPerQuerier = polper.retrievePolicies(String.valueOf(user),
                    PolicyConstants.USER_INDIVIDUAL, PolicyConstants.ACTION_ALLOW);
            if(policiesPerQuerier != null)
                flapolper.insertPolicies(policiesPerQuerier);
        }
    }
}
