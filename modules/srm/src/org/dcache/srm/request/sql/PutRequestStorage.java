/*
 * GetRequestStorage.java
 *
 * Created on June 22, 2004, 2:48 PM
 */

package org.dcache.srm.request.sql;
import org.dcache.srm.request.ContainerRequest;
import org.dcache.srm.request.FileRequest;
import org.dcache.srm.request.PutRequest;
import org.dcache.srm.util.Configuration;
import java.sql.*;
import org.dcache.srm.scheduler.Job;
import org.dcache.srm.SRMUser;
import org.apache.log4j.Logger;

/**
 *
 * @author  timur
 */
public class PutRequestStorage extends DatabaseContainerRequestStorage{
   private final static Logger logger =
            Logger.getLogger(PutRequestStorage.class);
   
     public static final String TABLE_NAME ="putrequests";
    private static final String UPDATE_PREFIX = "UPDATE " + TABLE_NAME + " SET "+
        "NEXTJOBID=?, " +
        "CREATIONTIME=?,  " +
        "LIFETIME=?, " +
        "STATE=?, " +
        "ERRORMESSAGE=?, " +//5
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
        "USERID  ) " +
        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    @Override
    public PreparedStatement getCreateStatement(Connection connection, Job job) throws SQLException {
        PutRequest pr = (PutRequest)job;
        PreparedStatement stmt = getPreparedStatement(connection,
                                  INSERT_SQL,
                                  pr.getId(),
                                  pr.getNextJobId(),
                                  pr.getCreationTime(),
                                  pr.getLifetime(),
                                  pr.getState().getStateId(),//5
                                  pr.getErrorMessage(),
                                  pr.getSchedulerId(),
                                  pr.getSchedulerTimeStamp(),
                                  pr.getNumberOfRetries(),
                                  pr.getMaxNumberOfRetries(),//10
                                  pr.getLastStateTransitionTime(),
                                  //Database Request Storage
                                  pr.getCredentialId(),
                                  pr.getRetryDeltaTime(),
                                  pr.isShould_updateretryDeltaTime()?0:1,
                                  pr.getDescription(),
                                  pr.getClient_host(),
                                  pr.getStatusCodeString(),
                                  pr.getUser().getId());
       return stmt;
    }

    private static final String UPDATE_REQUEST_SQL =
            UPDATE_PREFIX + ", CREDENTIALID=?," +
                " RETRYDELTATIME=?," +
                " SHOULDUPDATERETRYDELTATIME=?," +
                " DESCRIPTION=?," +
                " CLIENTHOST=?," +
                " STATUSCODE=?," +
                " USERID=?" +
                " WHERE ID=?";
    @Override
    public PreparedStatement getUpdateStatement(Connection connection,
            Job job) throws SQLException {
        PutRequest pr = (PutRequest)job;
        PreparedStatement stmt = getPreparedStatement(
                                  connection,
                                  UPDATE_REQUEST_SQL,
                                  pr.getNextJobId(),
                                  pr.getCreationTime(),
                                  pr.getLifetime(),
                                  pr.getState().getStateId(),
                                  pr.getErrorMessage(),//5
                                  pr.getSchedulerId(),
                                  pr.getSchedulerTimeStamp(),
                                  pr.getNumberOfRetries(),
                                  pr.getMaxNumberOfRetries(),
                                  pr.getLastStateTransitionTime(),//10
                                  //Database Request Storage
                                  pr.getCredentialId(),
                                  pr.getRetryDeltaTime(),
                                  pr.isShould_updateretryDeltaTime()?0:1,
                                  pr.getDescription(),
                                  pr.getClient_host(),
                                  pr.getStatusCodeString(),
                                  pr.getUser().getId(),
                                  pr.getId());

        return stmt;
    }
   
    
    /** Creates a new instance of GetRequestStorage */
    public PutRequestStorage(Configuration configuration) throws SQLException {
        super(
                configuration);
    }
    
    private String getProtocolsTableName() {
        return getTableName()+"_protocols";
    }
    
    public void dbInit1() throws SQLException {
        boolean should_reanamed_old_table = reanamed_old_table;
        String protocolsTableName = getProtocolsTableName().toLowerCase();
        Connection _con =null;
        try {
            _con = pool.getConnection();
            _con.setAutoCommit(true);
            
            DatabaseMetaData md = _con.getMetaData();
            ResultSet columns = md.getColumns(null, null, protocolsTableName , null);
            if(columns.next()){
                String columnName = columns.getString("COLUMN_NAME");
                int columnDataType = columns.getInt("DATA_TYPE");
                verifyStringType("PROTOCOL",1,protocolsTableName ,
                        columnName,columnDataType);
                if(columns.next()){
                    columnName = columns.getString("COLUMN_NAME");
                    columnDataType = columns.getInt("DATA_TYPE");
                    verifyLongType("RequestID",2,protocolsTableName ,
                            columnName,columnDataType);
                } else {
                    should_reanamed_old_table = true;
                }
            } else {
                should_reanamed_old_table = true;
            }
            _con.setAutoCommit(false);
            pool.returnConnection(_con);
            _con =null;
         } catch (SQLException sqe) {
             logger.error(sqe);
            should_reanamed_old_table = true;
            if(_con != null) {
                pool.returnFailedConnection(_con);
                _con = null;
            }
        } catch (Exception ex) {
            logger.error(ex);
            should_reanamed_old_table = true;
            if(_con != null) {
                pool.returnFailedConnection(_con);
                _con = null;
            }
        } finally {
            if(_con != null) {
                _con.setAutoCommit(false);
                pool.returnConnection(_con);
            }
        }
        try {
            if(should_reanamed_old_table) {
                renameTable(protocolsTableName);
            }
        }
        catch (SQLException sqle) {
            logger.error("renameTable  "+protocolsTableName+" failed, might have been removed already, ignoring");
        }
        
        String createProtocolsTable = "CREATE TABLE "+ protocolsTableName+" ( "+
                " PROTOCOL "+stringType+","+
                " RequestID "+longType+", "+ //forein key
                " CONSTRAINT fk_"+getTableName()+"_PP FOREIGN KEY (RequestID) REFERENCES "+
                getTableName() +" (ID) "+
                " ON DELETE CASCADE"+
                " )";
        logger.debug("calling createTable for "+protocolsTableName);
        createTable(protocolsTableName, createProtocolsTable);
        String protocols_columns[] = {
            "RequestID"};
        createIndex(protocols_columns,protocolsTableName );
        
    }
    
    public void getCreateList(ContainerRequest r, StringBuffer sb) {
        
    }
    
    private static int ADDITIONAL_FIELDS = 0;
    
    protected ContainerRequest getContainerRequest(
            Connection _con,
            Long ID,
            Long NEXTJOBID,
            long CREATIONTIME,
            long LIFETIME,
            int STATE,
            String ERRORMESSAGE,
            SRMUser user,
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
            FileRequest[] fileRequests,
            java.sql.ResultSet set,
            int next_index)throws java.sql.SQLException {
        String sqlStatementString = "SELECT PROTOCOL FROM " + getProtocolsTableName() +
                " WHERE RequestID="+ID;
        Statement sqlStatement = _con.createStatement();
        logger.debug("executing statement: "+sqlStatementString);
        ResultSet fileIdsSet = sqlStatement.executeQuery(sqlStatementString);
        java.util.Set utilset = new java.util.HashSet();
        while(fileIdsSet.next()) {
            utilset.add(fileIdsSet.getString(1));
        }
        String [] protocols = (String[]) utilset.toArray(new String[0]);
        sqlStatement.close();
        Job.JobHistory[] jobHistoryArray =
                getJobHistory(ID,_con);
        return new  PutRequest(
                ID,
                NEXTJOBID,
                CREATIONTIME,
                LIFETIME,
                STATE,
                ERRORMESSAGE,
                user,
                SCHEDULERID,
                SCHEDULER_TIMESTAMP,
                NUMOFRETR,
                MAXNUMOFRETR,
                LASTSTATETRANSITIONTIME,
                jobHistoryArray,
                CREDENTIALID,
                fileRequests,
                RETRYDELTATIME,
                SHOULDUPDATERETRYDELTATIME,
                DESCRIPTION,
                CLIENTHOST,
                STATUSCODE,
                protocols);
        
    }
    
    public String getRequestCreateTableFields() {
        return "";
    }
    
    public String getTableName() {
        return TABLE_NAME;
    }
    
    public void getUpdateAssignements(ContainerRequest r, StringBuffer sb) {
    }
    
    private final String insertProtocols =
        "INSERT INTO "+getProtocolsTableName()+
        " (PROTOCOL, RequestID) "+
        " VALUES (?,?)";

    @Override
    public PreparedStatement[] getAdditionalCreateStatements(Connection connection,
                                                             Job job) throws SQLException {
        if(job == null || !(job instanceof PutRequest)) {
            throw new IllegalArgumentException("Request is not PutRequest" );
        }
        PutRequest pr = (PutRequest)job;
        String[] protocols = pr.getProtocols();
        if(protocols ==null)  return null;
        PreparedStatement[] statements  = new PreparedStatement[protocols.length];
        for(int i=0; i<protocols.length ; ++i){
            statements[i] = getPreparedStatement(connection,
                    insertProtocols,
                    protocols[i],
                    pr.getId());
        }
        return statements;
    }
    
    public String getFileRequestsTableName() {
        return PutFileRequestStorage.TABLE_NAME;
    }
    
    protected void __verify(int nextIndex, int columnIndex, String tableName, String columnName, int columnType) throws SQLException {
    }
    
    protected int getMoreCollumnsNum() {
        return ADDITIONAL_FIELDS;
    }
    
}
