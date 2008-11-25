// $Id$
// $Log: not supported by cvs2svn $
// Revision 1.7  2007/01/06 00:23:55  timur
// merging production branch changes to database layer to improve performance and reduce number of updates
//
// Revision 1.6  2006/12/13 22:09:04  timur
// commiting the changes from the branch related to database performance
//
// Revision 1.5.2.3  2007/01/04 02:58:55  timur
// changes to database layer to improve performance and reduce number of updates
//
// Revision 1.5.2.2  2006/12/10 01:23:01  timur
// fixed a bud
//
// Revision 1.5  2006/04/26 17:17:56  timur
// store the history of the state transitions in the database
//
// Revision 1.4  2005/03/30 22:42:11  timur
// more database schema changes
//
// Revision 1.3  2005/03/07 22:55:33  timur
// refined the space reservation call, restored logging of sql commands while debugging the
// sql performance
//
// Revision 1.2  2005/03/01 23:10:39  timur
// Modified the database scema to increase database operations performance and to account for
// reserved space"and to account for reserved space
//
// Revision 1.1  2005/01/14 23:07:15  timur
// moving general srm code in a separate repository
//
// Revision 1.5  2004/11/10 03:29:00  timur
// modified the sql code to be compatible with both Cloudescape and postges
//
// Revision 1.4  2004/10/30 04:19:07  timur
// Fixed a problem related to the restoration of the job from database
//
// Revision 1.3  2004/10/28 02:41:31  timur
// changed the database scema a little bit, fixed various synchronization bugs in the
// scheduler, added interactive shell to the File System srm
//
// Revision 1.2  2004/08/06 19:35:25  timur
// merging branch srm-branch-12_May_2004 into the trunk
//
// Revision 1.1.2.1  2004/06/22 01:38:07  timur
// working on the database part, created persistent storage for getFileRequests,
// for the next requestId
//
// Revision 1.1.2.2  2004/06/16 19:44:33  timur
// added cvs logging tags and fermi copyright headers at the top, removed
// Copier.java and CopyJob.java
//

/*
COPYRIGHT STATUS:
  Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
  software are sponsored by the U.S. Department of Energy under Contract No.
  DE-AC02-76CH03000. Therefore, the U.S. Government retains a  world-wide
  non-exclusive, royalty-free license to publish or reproduce these documents
  and software for U.S. Government purposes.  All documents and software
  available from this server are protected under the U.S. and Foreign
  Copyright Laws, and FNAL reserves all rights.
 
 
 Distribution of the software available from this server is free of
 charge subject to the user following the terms of the Fermitools
 Software Legal Information.
 
 Redistribution and/or modification of the software shall be accompanied
 by the Fermitools Software Legal Information  (including the copyright
 notice).
 
 The user is asked to feed back problems, benefits, and/or suggestions
 about the software to the Fermilab Software Providers.
 
 
 Neither the name of Fermilab, the  URA, nor the names of the contributors
 may be used to endorse or promote products derived from this software
 without specific prior written permission.
 
 
 
  DISCLAIMER OF LIABILITY (BSD):
 
  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED  WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL FERMILAB,
  OR THE URA, OR THE U.S. DEPARTMENT of ENERGY, OR CONTRIBUTORS BE LIABLE
  FOR  ANY  DIRECT, INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
  OF SUBSTITUTE  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY  OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE  POSSIBILITY OF SUCH DAMAGE.
 
 
  Liabilities of the Government:
 
  This software is provided by URA, independent from its Prime Contract
  with the U.S. Department of Energy. URA is acting independently from
  the Government and in its own private capacity and is not acting on
  behalf of the U.S. Government, nor as its contractor nor its agent.
  Correspondingly, it is understood and agreed that the U.S. Government
  has no connection to this software and in no manner whatsoever shall
  be liable for nor assume any responsibility or obligation for any claim,
  cost, or damages arising out of or resulting from the use of the software
  available from this server.
 
 
  Export Control:
 
  All documents and software available from this server are subject to U.S.
  export control laws.  Anyone downloading information from this server is
  obligated to secure any necessary Government licenses before exporting
  documents or software obtained from this server.
 */

/*
 * ResuestsPropertyStorage.java
 *
 * Created on April 27, 2004, 4:23 PM
 */

package org.dcache.srm.request.sql;
import java.sql.*;
import org.dcache.srm.Logger;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;

/**
 *
 * @author  timur
 */
public class RequestsPropertyStorage implements org.dcache.srm.scheduler.JobIdGenerator {
    private String jdbcUrl;
    private String jdbcClass;
    private String user;
    private String pass;
    private String nextRequestIdTableName;
    private Logger logger;
    private int nextIntBase;
    private static int NEXT_INT_STEP=1000;
    private int nextIntIncrement=NEXT_INT_STEP;
    private long nextLongBase;
    private static long NEXT_LONG_STEP=10000;
    private long nextLongIncrement=NEXT_LONG_STEP;
    
    /** Creates a new instance of ResuestsPropertyStorage */
    protected RequestsPropertyStorage(  String jdbcUrl,
            String jdbcClass,
            String user,
            String pass,
            String nextRequestIdTableName,Logger logger) {
        this.jdbcUrl = jdbcUrl;
        this.jdbcClass = jdbcClass;
        this.user = user;
        this.pass = pass;
        this.nextRequestIdTableName = nextRequestIdTableName;
        this.logger = logger;
        try{
            dbInit();
            
        } catch(SQLException sqle){
            sqle.printStackTrace();
        }
    }
    
    public void say(String s){
        if(logger != null) {
            logger.log(" RequestsPropertyStorage: "+s);
        }
    }
    
    public void esay(String s){
        if(logger != null) {
            logger.elog(" RequestsPropertyStorage: "+s);
        }
    }
    
    public void esay(Throwable t){
        if(logger != null) {
            logger.elog(t);
        }
    }
    
    JdbcConnectionPool pool;
    
    private void dbInit()
    throws SQLException {
        Connection _con = null;
        try {
            pool = JdbcConnectionPool.getPool(jdbcUrl, jdbcClass, user, pass);
            
            
            //connect
            _con = pool.getConnection();
            _con.setAutoCommit(true);
            
            //get database info
            DatabaseMetaData md = _con.getMetaData();
            
            
            ResultSet tableRs = md.getTables(null, null, nextRequestIdTableName , null );
            
            
            //fields to be saved from the  Job object in the database:
                /*
                    this.id = id;
                    this.nextJobId = nextJobId;
                    this.creationTime = creationTime;
                    this.lifetime = lifetime;
                    this.state = state;
                    this.errorMessage = errorMessage;
                    this.creator = creator;
                 
                 */
            if(!tableRs.next()) {
                try {
                    String createTable = "CREATE TABLE " + nextRequestIdTableName + "(" +
                            "NEXTINT INTEGER ,NEXTLONG BIGINT)";
                    say(nextRequestIdTableName+" does not exits");
                    Statement s = _con.createStatement();
                    say("dbInit trying "+createTable);
                    int result = s.executeUpdate(createTable);
                    s.close();
                    String select = "SELECT * FROM "+nextRequestIdTableName;
                    s = _con.createStatement();
                    ResultSet set = s.executeQuery(select);
                    if(!set.next()) {
                        s.close();
                        String insert = "INSERT INTO "+ nextRequestIdTableName+ " VALUES ("+Integer.MIN_VALUE+
                                ", "+Long.MIN_VALUE+")";
                        //say("dbInit trying "+insert);
                        s = _con.createStatement();
                        say("dbInit trying "+insert);
                        result = s.executeUpdate(insert);
                        s.close();
                    } else {
                        s.close();
                        say("dbInit set.next() returned nonnull");
                    }
                    
                    
                } catch(SQLException sqle) {
                    esay(sqle);
                    say("relation could already exist");
                }
            }
            // to be fast
            _con.setAutoCommit(false);
            pool.returnConnection(_con);
            _con = null;
        } catch (SQLException sqe) {
            if(_con != null) {
                pool.returnFailedConnection(_con);
                _con = null;
            }
            throw sqe;
        } catch (Exception ex) {
            if(_con != null) {
                pool.returnFailedConnection(_con);
                _con = null;
            }
            throw new SQLException(ex.toString());
        } finally {
            if(_con != null) {
                _con.setAutoCommit(false);
                pool.returnConnection(_con);
            }
        }
    }
    
    
    
    
    public  int getNextRequestId()  {
        return nextInt();
    }
    int _nextIntBase = 0;
    public synchronized  int nextInt()   {
        if(nextIntIncrement >= NEXT_INT_STEP) {
            nextIntIncrement =0;
            Connection _con = null;
            try {
                _con = pool.getConnection();
                String select_for_update = "SELECT * from "+nextRequestIdTableName+" FOR UPDATE ";
                Statement s = _con.createStatement();
                say("nextInt trying "+select_for_update);
                ResultSet set = s.executeQuery(select_for_update);
                if(!set.next()) {
                    s.close();
                    throw new SQLException("table "+nextRequestIdTableName+" is empty!!!");
                }
                nextIntBase = set.getInt(1);
                s.close();
                say("nextIntBase is ="+nextIntBase);
                String increase_nextint = "UPDATE "+nextRequestIdTableName+
                        " SET NEXTINT=NEXTINT+"+NEXT_INT_STEP;
                s = _con.createStatement();
                say("executing statement: "+increase_nextint);
                int i = s.executeUpdate(increase_nextint);
                s.close();
                _con.commit();
                pool.returnConnection(_con);
                _con = null;
            } catch(SQLException e) {
                e.printStackTrace();
                try{
                    _con.rollback();
                }catch(SQLException e1) {
                }
                nextIntBase = _nextIntBase;
            } finally {
                if(_con != null) {
                    pool.returnConnection(_con);
                }
                
            }
            _nextIntBase = nextIntBase+NEXT_INT_STEP;
        }
        int nextInt = nextIntBase +(nextIntIncrement++);
        say(" return nextInt="+nextInt);
        return nextInt;
        
        
    }
    
    private static java.text.SimpleDateFormat dateformat =
            new java.text.SimpleDateFormat("yyMMddHHmmssSSSSZ");
    
    public  String nextUniqueToken() throws SQLException{
        Connection _con = null;
        long nextLong = nextLong();
        return dateformat.format(new java.util.Date())+
                "-"+nextLong;
    }
    
    public Long getNextId() {
        return new Long((int)nextInt());
    }
    
    long _nextLongBase = 0;
    public synchronized long nextLong() {
        if(nextLongIncrement >= NEXT_LONG_STEP) {
            nextLongIncrement =0;
            Connection _con = null;
            String select_for_update = "SELECT * from "+nextRequestIdTableName+" FOR UPDATE ";
            try {
                _con = pool.getConnection();
                Statement s = _con.createStatement();
                say("nextLong trying "+select_for_update);
                ResultSet set = s.executeQuery(select_for_update);
                if(!set.next()) {
                    s.close();
                    throw new SQLException("table "+nextRequestIdTableName+" is empty!!!");
                }
                nextLongBase = set.getLong(2);
                s.close();
                say("nextLongBase is ="+nextLongBase);
                String increase_nextint = "UPDATE "+nextRequestIdTableName+
                        " SET NEXTLONG=NEXTLONG+"+NEXT_LONG_STEP;
                s = _con.createStatement();
                say("executing statement: "+increase_nextint);
                int i = s.executeUpdate(increase_nextint);
                s.close();
                _con.commit();
            } catch(SQLException e) {
                e.printStackTrace();
                try{
                    _con.rollback();
                }catch(Exception e1) {
                    
                }
                pool.returnFailedConnection(_con);
                _con = null;
                nextLongBase = _nextLongBase;
                
            } finally {
                if(_con != null) {
                    pool.returnConnection(_con);
                    
                }
            }
            _nextLongBase = nextLongBase+ NEXT_LONG_STEP;
        }
        
        long nextLong = nextLongBase +(nextLongIncrement++);;
        say(" return nextLong="+nextLong);
        return nextLong;
    }
    
    public boolean equals(Object o) {
        if( this == o) {
            return true;
        }
        
        if(o == null || !(o instanceof RequestsPropertyStorage)) {
            return false;
        }
        RequestsPropertyStorage rps = (RequestsPropertyStorage)o;
        return rps.jdbcClass.equals(jdbcClass) &&
        rps.jdbcUrl.equals(jdbcUrl) &&
        rps.pass.equals(pass) &&
        rps.user.equals(user) &&
        rps.nextRequestIdTableName.equals(nextRequestIdTableName);
    }
    
    public int hashCode() {
        return jdbcClass.hashCode() ^
        jdbcUrl.hashCode() ^
        pass.hashCode() ^
        user.hashCode() ^
        nextRequestIdTableName.hashCode();
    }
    private static Set propertyStorages = new HashSet();
    
    public synchronized static final RequestsPropertyStorage getPropertyStorage(String jdbcUrl,
    String jdbcClass,
    String user,
    String pass,
    String nextRequestIdTableName,Logger logger)  {
        for (Iterator i = propertyStorages.iterator();
        i.hasNext();) {
            RequestsPropertyStorage rps = (RequestsPropertyStorage) i.next();
            if(rps.jdbcClass.equals(jdbcClass) &&
            rps.jdbcUrl.equals(jdbcUrl) &&
            rps.pass.equals(pass) &&
            rps.user.equals(user) &&
            rps.nextRequestIdTableName.equals(nextRequestIdTableName) ){
                return rps;
            }
        }
        RequestsPropertyStorage rps = 
                new RequestsPropertyStorage(jdbcUrl,jdbcClass,user,pass,
                nextRequestIdTableName,logger);
        propertyStorages.add(rps);
        return rps;
        
    }

}
