// $Id: ReplicaDb1.java,v 1.12 2004-03-30 16:07:33 cvs Exp $

package diskCacheV111.replicaManager ;

import  diskCacheV111.util.* ;

import  java.util.* ;

public interface ReplicaDb1 extends ReplicaDb {

    static final String DOWN = "down";
    static final String ONLINE = "online";
    static final String OFFLINE = "offline";
    static final String OFFLINE_PREPARE = "offline-prepare";
    static final String DRAINOFF = "drainoff";

    public Iterator pnfsIds( String poolName ) ;
    public Iterator getPools( ) ;
    public Iterator getPoolsReadable( ) ;
    public Iterator getPoolsWritable( ) ;

    public Iterator getRedundant(int maxcnt);
    public Iterator getDeficient(int mincnt);
    public Iterator getMissing( );
    public Iterator getInDrainoffOnly( );
    public Iterator getInOfflineOnly( );

//     public void addPoolStatus(String poolName, String poolStatus); // removed
    public void removePoolStatus(String poolName);
    public void setPoolStatus(String poolName, String poolStatus);
    public String getPoolStatus(String poolName);

    public void addTransaction(PnfsId pnfsId, long timestamp, int count);
    public void removeTransaction(PnfsId pnfsId);
    public long getTimestamp(PnfsId pnfsId);
    public Iterator pnfsIds(long timestamp);

    public void removePool( String poolName ) ;

    public void setHeartBeat(String name, String desc);
    public void removeHeartBeat(String name);

    public void clearPool( String poolName ); // clear entries in pools and replicas tables
    public void clearTransactions();          // clear transactions in action table

}
