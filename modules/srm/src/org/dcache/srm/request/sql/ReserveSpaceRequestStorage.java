/*
 * FileRequestStorage.java
 *
 * Created on June 17, 2004, 3:18 PM
 */

package org.dcache.srm.request.sql;

import org.dcache.srm.request.ReserveSpaceRequest;
import org.dcache.srm.request.RequestStorage;
import org.dcache.srm.request.Request;
import org.dcache.srm.scheduler.Job;
import org.dcache.srm.scheduler.State;
import org.dcache.srm.util.Configuration;
import java.sql.*;
import java.util.Set;

/**
 *
 * @author  timur
 */
public class ReserveSpaceRequestStorage extends DatabaseRequestStorage {
    public static final String TABLE_NAME ="reservespacerequests";
    private static final String UPDATE_PREFIX = "UPDATE " + TABLE_NAME + " SET "+
        "NEXTJOBID=?, " +
        "CREATIONTIME=?,  " +
        "LIFETIME=?, " +
        "STATE=?, " +
        "ERRORMESSAGE=?, " +//5
        "CREATORID=?,"+
        "SCHEDULERID=?, " +
        "SCHEDULERTIMESTAMP=?," +
        "NUMOFRETR=?," +
        "MAXNUMOFRETR=?," +
        "LASTSTATETRANSITIONTIME=? ";//10

        private static final String INSERT_SQL = "INSERT INTO "+ TABLE_NAME+ "(    " +
    "ID ,"+
    "NEXTJOBID ,"+
    "CREATIONTIME ,"+
    "LIFETIME ,"+
    "STATE ,"+ //5
    "ERRORMESSAGE ,"+
    "CREATORID ,"+
    "SCHEDULERID ,"+
    "SCHEDULERTIMESTAMP ,"+
    "NUMOFRETR ,"+
    "MAXNUMOFRETR ,"+ //10
    "LASTSTATETRANSITIONTIME,"+
     //Database Request Storage
    "CREDENTIALID , " +
    "RETRYDELTATIME , "+
    "SHOULDUPDATERETRYDELTATIME ,"+
    "DESCRIPTION ,"+ //15
    "CLIENTHOST ,"+
    "STATUSCODE ,"+
    // Reserve Space Request
    "SIZEINBYTES, "+
    "RESERVATIONLIFETIME, "+
    "SPACETOKEN, "+
    "RETENTIONPOLICY, "+
    "ACCESSLATENCY ) " +
    "VALUES (?,?,?,?,?,?,?,?,?,?,?," +//Job
                "?,?,?,?,?,?,?," +//Request
                "?,?,?,?,?)";

    
    @Override
    public PreparedStatement getCreateStatement(Connection connection, Job job) throws SQLException {
        ReserveSpaceRequest rsr = (ReserveSpaceRequest)job;
        String retentionPolicyValue=null;
        if(rsr.getRetentionPolicy() != null) {
            retentionPolicyValue = rsr.getRetentionPolicy().getValue();
        }
        String accessLatencyValue=null;
        if(rsr.getAccessLatency() != null) {
            accessLatencyValue = rsr.getAccessLatency().getValue();
        }
        PreparedStatement stmt = getPreparedStatement(connection,
                                  INSERT_SQL,
                                  rsr.getId(),
                                  rsr.getNextJobId(),
                                  rsr.getCreationTime(),
                                  rsr.getLifetime(),
                                  rsr.getState().getStateId(),//5
                                  rsr.getErrorMessage(),
                                  rsr.getCreatorId(),
                                  rsr.getSchedulerId(),
                                  rsr.getSchedulerTimeStamp(),
                                  rsr.getNumberOfRetries(),
                                  rsr.getMaxNumberOfRetries(),//10
                                  rsr.getLastStateTransitionTime(),
                                  //Database Request Storage
                                  rsr.getCredentialId(),
                                  rsr.getRetryDeltaTime(),
                                  rsr.isShould_updateretryDeltaTime()?0:1,
                                  rsr.getDescription(),
                                  rsr.getClient_host(),
                                  rsr.getStatusCodeString(),
                                  rsr.getSizeInBytes(),
                                  rsr.getSpaceReservationLifetime(),
                                  rsr.getSpaceToken(),
                                  retentionPolicyValue,
                                  accessLatencyValue);
       return stmt;
    }

    private static final String UPDATE_REQUEST_SQL =
            UPDATE_PREFIX + ", CREDENTIALID=?," +
                " RETRYDELTATIME=?," +
                " SHOULDUPDATERETRYDELTATIME=?," +
                " DESCRIPTION=?," +
                " CLIENTHOST=?," +
                " STATUSCODE=?," +
                // Reserve Space Request
                " SIZEINBYTES=?, "+
                " RESERVATIONLIFETIME=?, "+
                " SPACETOKEN=?, "+
                " RETENTIONPOLICY=?,"+
                " ACCESSLATENCY=?" +
                " WHERE ID=?";
    @Override
    public PreparedStatement getUpdateStatement(Connection connection,
            Job job) throws SQLException {
        ReserveSpaceRequest rsr = (ReserveSpaceRequest)job;
        String retentionPolicyValue=null;
        if(rsr.getRetentionPolicy() != null) {
            retentionPolicyValue = rsr.getRetentionPolicy().getValue();
        }
        String accessLatencyValue=null;
        if(rsr.getAccessLatency() != null) {
            accessLatencyValue = rsr.getAccessLatency().getValue();
        }
        PreparedStatement stmt = getPreparedStatement(connection,
                                  UPDATE_REQUEST_SQL,
                                  rsr.getNextJobId(),
                                  rsr.getCreationTime(),
                                  rsr.getLifetime(),
                                  rsr.getState().getStateId(),
                                  rsr.getErrorMessage(),//5
                                  rsr.getCreatorId(),
                                  rsr.getSchedulerId(),
                                  rsr.getSchedulerTimeStamp(),
                                  rsr.getNumberOfRetries(),
                                  rsr.getMaxNumberOfRetries(),
                                  rsr.getLastStateTransitionTime(),//10
                                  //Database Request Storage
                                  rsr.getCredentialId(),
                                  rsr.getRetryDeltaTime(),
                                  rsr.isShould_updateretryDeltaTime()?0:1,
                                  rsr.getDescription(),
                                  rsr.getClient_host(),
                                  rsr.getStatusCodeString(),
                                  rsr.getSizeInBytes(),
                                  rsr.getSpaceReservationLifetime(),
                                  rsr.getSpaceToken(),
                                  retentionPolicyValue,
                                  accessLatencyValue,
                                  rsr.getId());

        return stmt;
    }
    
    /** Creates a new instance of FileRequestStorage */
    public ReserveSpaceRequestStorage(Configuration configuration) throws SQLException {
        super(configuration);
    }
    
     
    public void say(String s){
        if(logger != null) {
           logger.log(" ReserveSpaceRequestStorage: "+s);
        }
    }
    
    public void esay(String s){
        if(logger != null) {
           logger.elog(" ReserveSpaceRequestStorage: "+s);
        }
    }
    
    public void esay(Throwable t){
        if(logger != null) {
           logger.elog(t);
        }
    }

    
    protected void _dbInit() throws SQLException {
        
    }
    
    
    public String getRequestCreateTableFields() {
        return
        ","+
        "SIZEINBYTES "+  longType+
         ","+
        "RESERVATIONLIFETIME "+  longType+
        ","+
         "SPACETOKEN "+  stringType+
         ","+
        "RETENTIONPOLICY "+  stringType+
        ","+
        "ACCESSLATENCY "+  stringType;
    }
    
    
    protected Request getRequest(
    Connection _con,
    Long ID,
    Long NEXTJOBID,
    long CREATIONTIME,
    long LIFETIME,
    int STATE,
    String ERRORMESSAGE,
    String CREATORID,
    String SCHEDULERID,
    long SCHEDULER_TIMESTAMP,
    int NUMOFRETR,
    int MAXNUMOFRETR,
    long LASTSTATETRANSITIONTIME,
    Long CREDENTIALID,
    int RETRYDELTATIME,
    boolean SHOULDUPDATERETRYDELTATIME,
    String DESCRIPTION,
    String CLIENTHOST,
    String STATUSCODE,
    java.sql.ResultSet set,
    int next_index) throws java.sql.SQLException {
        
            Job.JobHistory[] jobHistoryArray = 
            getJobHistory(ID,_con);

        long SIZEINBYTES = set.getLong(next_index++);
        long RESERVATIONLIFETIME = set.getLong(next_index++);
        String SPACETOKEN = set.getString(next_index++);
        String RETENTIONPOLICY = set.getString(next_index++);
        String ACCESSLATENCY = set.getString(next_index++);
        return new ReserveSpaceRequest(
            ID,
            NEXTJOBID ,
            this,
            CREATIONTIME,
            LIFETIME,
            STATE,
            ERRORMESSAGE,
            CREATORID,
            SCHEDULERID,
            SCHEDULER_TIMESTAMP,
            NUMOFRETR,
            MAXNUMOFRETR,
            LASTSTATETRANSITIONTIME,
            jobHistoryArray,
                CREDENTIALID,
                SIZEINBYTES,
                RESERVATIONLIFETIME,
                SPACETOKEN,
                RETENTIONPOLICY,
                ACCESSLATENCY,
                DESCRIPTION,
                CLIENTHOST,
                STATUSCODE,
                configuration
                );
    }
    
    public String getTableName() {
        return TABLE_NAME;
    }
    
    
    public void getUpdateAssignements(Request request,StringBuffer sb) {
        if(request == null || !(request instanceof ReserveSpaceRequest)) {
            throw new IllegalArgumentException("Request is not ReserveSpaceRequest" );
        }
        ReserveSpaceRequest r = (ReserveSpaceRequest)request;
        /*
         *additional fields:
                 ","+
        ","+
        "CREDENTIALID "+  longType+
        ","+
        "SIZEINBYTES "+  longType+
        ","+
        "RESERVATIONLIFETIME "+  longType+
        ","+
         "SPACETOKEN "+  stringType+
         ","+
        "RETENTIONPOLICY "+  stringType+
        ","+
        "ACCESSLATENCY "+  stringType+
        ","+
        "DESCRIPTION "+  stringType;
        */
        sb.append(", SIZEINBYTES = ").append( r.getSizeInBytes());
        sb.append(", RESERVATIONLIFETIME = ").append( r.getSpaceReservationLifetime());
        String spaceToken = r.getSpaceToken();
        if(spaceToken ==null){
         sb.append(", SPACETOKEN =NULL");
        } else {
         sb.append(", SPACETOKEN = \'").append( spaceToken).append('\'');
        }
        if(r.getRetentionPolicy() ==null) {
         sb.append(", RETENTIONPOLICY =NULL");
        } else {
         sb.append(", RETENTIONPOLICY = \'").append( r.getRetentionPolicy().getValue()).append('\'');
        }
        if(r.getAccessLatency() ==null) {
         sb.append(", ACCESSLATENCY =NULL");
        } else {
         sb.append(", ACCESSLATENCY = \'").append( r.getAccessLatency().getValue()).append('\'');
        }        
    }
    
    private static int ADDITIONAL_FIELDS_NUM=5;
    protected int getMoreCollumnsNum() {
        return ADDITIONAL_FIELDS_NUM;
    }  
    
    public void getCreateList(Request request, StringBuffer sb) {
        
        if(request == null || !(request instanceof ReserveSpaceRequest)) {
            throw new IllegalArgumentException("request is not ReserveSpaceRequest" );
        }
        ReserveSpaceRequest r = (ReserveSpaceRequest)request;
        
        /*
         *additional fields:
                 ","+
        ","+
        "CREDENTIALID "+  longType+
        ","+
        "SIZEINBYTES "+  longType+
        ","+
        "RESERVATIONLIFETIME "+  longType+
        ","+
         "SPACETOKEN "+  stringType+
         ","+
        "RETENTIONPOLICY "+  stringType+
        ","+
        "ACCESSLATENCY "+  stringType+
        ","+
        "DESCRIPTION "+  stringType;
        */
        sb.append(", ").append(r.getSizeInBytes());
        sb.append(", ").append(r.getSpaceReservationLifetime());
        if(r.getSpaceToken() == null){
            sb.append(", NULL");
        } else {
            sb.append(", '").append(r.getSpaceToken()).append('\'');
        }
        if(r.getRetentionPolicy() == null){
            sb.append(", NULL");
        } else {
            sb.append(", '").append(r.getRetentionPolicy().getValue()).append('\'');
        }
        if(r.getAccessLatency() == null){
            sb.append(", NULL");
        } else {
            sb.append(", '").append(r.getAccessLatency().getValue()).append('\'');
        }
    }
    
    public void updateJob(Job job, Connection _con) throws SQLException {
    }
    
    
       
    protected void __verify(int nextIndex, int columnIndex, String tableName, String columnName, int columnType) throws SQLException {
        /*
         *additional fields:
                 ","+
        ","+
        "CREDENTIALID "+  longType+
        ","+
        "SIZEINBYTES "+  longType+
        ","+
        "RESERVATIONLIFETIME "+  longType+
        ","+
         "SPACETOKEN "+  stringType+
         ","+
        "RETENTIONPOLICY "+  stringType+
        ","+
        "ACCESSLATENCY "+  stringType+
        ","+
        "DESCRIPTION "+  stringType;
        */
        if(columnIndex == nextIndex) {
            verifyLongType("SIZEINBYTES",columnIndex,tableName, columnName, columnType);
            
        }
        else if(columnIndex == nextIndex+1)
        {
            verifyLongType("RESERVATIONLIFETIME",columnIndex,tableName, columnName, columnType);
            
        }
        else if(columnIndex == nextIndex+2)
        {
            verifyStringType("SPACETOKEN",columnIndex,tableName, columnName, columnType);
        }
        else if(columnIndex == nextIndex+3)
        {
            verifyStringType("RETENTIONPOLICY",columnIndex,tableName, columnName, columnType);
        }
        else if(columnIndex == nextIndex+4)
        {
            verifyStringType("ACCESSLATENCY",columnIndex,tableName, columnName, columnType);
        }
   }    

}

