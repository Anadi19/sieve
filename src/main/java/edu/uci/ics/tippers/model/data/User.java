package edu.uci.ics.tippers.model.data;

import edu.uci.ics.tippers.db.MySQLConnectionManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by cygnus on 7/7/17.
 */
public class User {

    int userId;
    String userType;
    int totalTime;
    List<UserGroup> groups;

    private static Connection connection = MySQLConnectionManager.getInstance().getConnection();

    public User(int userId, List<UserGroup> groups) {
        this.userId = userId;
        this.groups = groups;
    }

    public User(int userId) {
        this.userId = userId;
    }

    public User() {

    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public List<UserGroup> getGroups() {
        return groups;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public int getTotalTime() {
        return totalTime;
    }

    public void setTotalTime(int totalTime) {
        this.totalTime = totalTime;
    }

    @Override
    public int hashCode() {
        return this.userId;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof User))
            return false;
        return (this.userId == ((User) obj).userId && this.userType.equalsIgnoreCase(((User) obj).userType));
    }

    public void retrieveUserGroups() {
        PreparedStatement queryStm = null;
        try {
            queryStm = connection.prepareStatement("SELECT USER_GROUP_ID as ug_id " +
                    "FROM USER_GROUP_MEMBERSHIP as ugm where ugm.USER_ID = ? ");
            queryStm.setInt(1, this.getUserId());
            ResultSet rs = queryStm.executeQuery();
            while (rs.next()) {
                UserGroup ug = new UserGroup();
                ug.setGroup_id(Integer.parseInt(rs.getString("ug_id")));
                this.getGroups().add(ug);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void retrieveUserDetails() {
        PreparedStatement queryStm = null;
        try {
            queryStm = connection.prepareStatement("SELECT u.user_type, u.totalTime " +
                    "FROM USER as u where u.ID = ?");
            queryStm.setString(1, String.valueOf(this.userId));
            ResultSet rs = queryStm.executeQuery();
            while (rs.next()) {
                this.setTotalTime(rs.getInt("u.totalTime"));
                this.setUserType(rs.getString("u.user_type"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
