/**
 * <p>Title: ReplicaManager </p>
 * <p>Description: </p>
 * @version $Id$
 */

package diskCacheV111.replicaManager ;

import  diskCacheV111.vehicles.* ;
import  diskCacheV111.util.* ;
import  diskCacheV111.pools.PoolV2Mode ;
import diskCacheV111.replicaManager.ReplicaDbV1.DbIterator;

import  dmg.cells.nucleus.* ;
import  dmg.util.* ;

import  java.io.* ;
import java.sql.SQLException;
import  java.util.*;

public class ReplicaManagerV2 extends DCacheCoreControllerV2 {
  private final static String _cvsId = "$Id$";

  private boolean _debug = false;
  public boolean getDebugRM ( )          { return _debug; }
  public void    setDebugRM( boolean d ) { _debug = d; }
  public void    setDebug2 ( boolean d ) { _debug = d; super.setDebug(d); }

  private String _jdbcUrl = "jdbc:postgresql://localhost/replicas";
  private String _driver = "org.postgresql.Driver";
  private String _user = "enstore";
  private String _pass = "NoPassword";
  private String _pwdfile = null;

  private ReplicaDbV1 _dbrmv2   = null;
  private boolean     _useDB = false;
  private Args        _args = null;
  private Adjuster    _adj  = null;
  private WatchPools  _watchPools= null;
  private Thread      _watchDog  = null;
  private Thread      _dbThread  = null;
  private Thread      _adjThread = null;
  private boolean     _stopThreads = false;
  private boolean     _runAdjuster = true;

  private boolean     _XXcheckPoolHost = false;
  public void   setCheckPoolHost  ( boolean d ) { _XXcheckPoolHost = d; }
  private boolean getCheckPoolHost() { return _XXcheckPoolHost; }

//  private ReplicaManagerCLI _cli = new ReplicaManagerCLI();
//  private ReplicaManagerCLIDebug _cliDebug = null;;

  private int         _repId = 1;
  private int         _redId = 1;
  private int         _cntOnlinePools = 0;
  private Set         _poolsToWait = new HashSet(); // Contains old online pools from db
  private Map         _poolMap = null;

  private int _repMin = 2;  // Min num. of replicas Adjuster will keep
  private int _repMax = 3;  // Max num. of replicas Adjuster will keep

  // Resilient pool Group

  private ResilientPools _resilientPools = null;

  public class ResilientPools {
    private List _resPoolsList = null;
    // defaults:
    private String _resilientPoolGroupName = "ResilientPools";
    final private boolean _usePoolGroup = true; // it MUST be true, we do not use '*' anymore

    private boolean usePoolGroup() { return _usePoolGroup ; }
    private List getResilientPools() {
      return ( _usePoolGroup ) ? _resPoolsList : null ; }

    public ResilientPools( Args args ) throws Exception {
      String group = args.getOpt("resilientGroupName");
      if( group != null && (! group.equals("")) ) {
//        if (group.equals("*") ) {
//          say("resilientGroupName="+group +"; Do not use resilientGroupName");
//          say("We consider all pools in the system as resilient pools");
//          _resilientPoolGroupName = "";
//          _usePoolGroup = false;
//        }else{
//          _usePoolGroup = true;

          _resilientPoolGroupName = group;
          esay("resilientGroupName=" + group + "\n");
//        }
      }else{
        esay("Argument 'resilientGroupName' is not defined, use default settings: "
             + " _usePoolGroup=" +_usePoolGroup
             + ((_usePoolGroup) ? (", _resilientPoolGroupName="+_resilientPoolGroupName):"")
             );
      }
    }

    public List init()
            throws Exception {

        boolean fatal = false;

        if (this.usePoolGroup()) {
            dsay("Asking for Resilient Pools Group List, resilientPoolGroupName="
                 + _resilientPoolGroupName);

            try {
                _resPoolsList = getPoolGroup(_resilientPoolGroupName);
            } catch (Exception ex) {
                esay("ERROR: ##### Can not get Resilient Pools Group " + _resilientPoolGroupName +" ####");
                throw ex;
            }
            if (_resPoolsList == null) {
                esay("ERROR: ##### Can not get Resilient Pools Group " + _resilientPoolGroupName +" ####");
                throw  new Exception("Can not get Group " + _resilientPoolGroupName) ;
            }

            say("Got " + _resPoolsList.size() + " pools listed in the group " +
                _resilientPoolGroupName);

            if (_resPoolsList.size() == 0) {
                esay("ERROR: ##### Group " + _resilientPoolGroupName + " is empty ####");
                throw  new Exception("Group " + _resilientPoolGroupName + " is empty") ;
            }

            say("ResilientPools pools: " + _resPoolsList);
            return _resPoolsList;
        }else{
          dsay("Resilient pool group is not used, skip initialization");
        }
        return null;
    }
  }

  private void initResilientPools() {
    while (true) { // try forever to connect the Pool Manager
      try {
        List l = null;
        if (_resilientPools != null) {
          l = _resilientPools.init();
          if (!_resilientPools.usePoolGroup() || l != null)
            break;
        }
      }
      catch (Exception ex) {
        esay("InitResilientPools() - got exception '" + ex + "'");
      }
//      finally { // error processing
//          dsay("Can not get resilient pool list, wait 60 sec. and retry");
//          try {
//              Thread.sleep(60 * 1000); // wait 60 sec. for Pool Manager
//          } catch (InterruptedException ex1) {
//              dsay("Getting resilient pool list, wait interrupted, retry");
//          }
//      }
    }
  }


  /**
   *  Returns a list of names (Strings) for active Resilient pools.
   *
   *  @return list of pool names (Strings)
   *  @throws Exception (see exceptions in getPoolList() ).
   */
  public List getPoolListResilient ()
          throws Exception
  {
    if( _resilientPools == null ){
      throw new Exception("ERROR: resilientPools are not defined");
    }

    List poolList    = getPoolList();

    if( _resilientPools.usePoolGroup() ){
      poolList.retainAll( _resilientPools.getResilientPools() );
    }
    return poolList;
  }

  //

  private Object _dbLock = new Object();
  private boolean _initDbActive = false;
  private boolean _runPoolWatchDog = false;
  private boolean _hotRestart = true;
  private InitDbRunnable _initDbRunnable = null;

  private final long SECOND = 1000L;
  private final long MINUTE =   60 * SECOND;
  private final long HOUR   =   60 * MINUTE;

  private long _delayDBStartTO  = 20*MINUTE; //  - wait for remote pools to get conncted
  private long _delayAdjStartTO = 21*MINUTE; //  - wait for new pools to start
  private long _delayPoolScan   =  2*MINUTE; //  - wait for remote pools get connected
                                             //           before polling pool status
  //
  private class DBUpdateMonitor  {
    boolean _bool;

    DBUpdateMonitor() { _bool = false; }

    synchronized public void reset() { _bool = false; }

    synchronized public boolean booleanValue() { return _bool; }

    synchronized public void wakeup() {
      _bool = true;
      try {
        this.notifyAll();
      }
      catch (IllegalMonitorStateException ex) { // Ignore
      }
    }
  }

//  private Boolean _dbUpdated = Boolean.FALSE;
  private DBUpdateMonitor _dbUpdated = new DBUpdateMonitor();

  protected void dsay(String s) {
        if (_debug)
            say("DEBUG: " + s);
    }

  private void parseDBArgs() {

    say("Parsing arguments..."+_args);

    _jdbcUrl = "jdbc:postgresql://localhost/replicas";
    _driver = "org.postgresql.Driver";
    _user = "enstore";
    _pass = "NoPassword";

    String cfURL = _args.getOpt("dbURL");
    if( cfURL != null) {
        _jdbcUrl = cfURL;
    }

    String cfDriver = _args.getOpt("jdbcDrv");
    if( cfDriver != null ) {
        _driver = cfDriver;
    }

    String cfUser = _args.getOpt("dbUser");
    if( cfUser != null ) {
        _user = cfUser;
    }

    String cfPass = _args.getOpt("dbPass");
    if( cfPass != null ) {
        _pass = cfPass;
    }

    _pwdfile = _args.getOpt("pgPass");

    // Now check if all required parameters are present
    if ((cfURL == null )  ||  (cfDriver == null) || (cfUser == null)  ||  (cfPass == null && _pwdfile == null) ) {
          throw new IllegalArgumentException("Not enough arguments to Init SQL database");
    }

    if (_pwdfile != null && _pwdfile.length() > 0) {
            Pgpass pgpass = new Pgpass(_pwdfile);
            _pass = pgpass.getPgpass(cfURL, cfUser);
    }
  }

  private void parseArgs() {
    // Parse arguments

    String min = _args.getOpt("min");
    if (min != null) {
      _repMin = Integer.parseInt(min);
      _adj.setMin(_repMin);
      say("Set _repMin=" + _repMin);
    }

    String max = _args.getOpt("max");
    if (max != null) {
      _repMax = Integer.parseInt(max);
      _adj.setMax(_repMax);
      say("Set _repMax=" + _repMax);
    }

    String delayDBStartTO = _args.getOpt("delayDBStartTO");
    if (delayDBStartTO != null) {
      _delayDBStartTO = Integer.parseInt(delayDBStartTO) * 1000;
      say("Set _delayDBStartTO=" + _delayDBStartTO + " ms");
    }

    String delayAdjStartTO = _args.getOpt("delayAdjStartTO");
    if (delayAdjStartTO != null) {
      _delayAdjStartTO = Integer.parseInt(delayAdjStartTO) * 1000;
      say("Set _delayAdjStartTO=" + _delayAdjStartTO + " ms");
    }

    String waitDBUpdateTO = _args.getOpt("waitDBUpdateTO");
    if (waitDBUpdateTO != null) {
      long timeout = Integer.parseInt(waitDBUpdateTO) * 1000L;
      _adj.setWaitDBUpdateTO(timeout);
      say("Set waitDBUpdateTO=" + timeout + " ms");
    }

    String waitReplicateTO = _args.getOpt("waitReplicateTO");
    if (waitReplicateTO != null) {
      long timeout = Integer.parseInt(waitReplicateTO) * 1000L;
      _adj.setWaitReplicateTO(timeout);
      say("Set waitReplicateTO=" + timeout + " ms");
    }

    String waitReduceTO = _args.getOpt("waitReduceTO");
    if (waitReduceTO != null) {
      long timeout = Integer.parseInt(waitReduceTO) * 1000L;
      _adj.setWaitReduceTO(timeout);
      say("Set waitReduceTO=" + timeout + " ms");
    }
    String poolWatchDogPeriod = _args.getOpt("poolWatchDogPeriod");
    if (poolWatchDogPeriod != null) {
      long timeout = Integer.parseInt(poolWatchDogPeriod) * 1000L;
      _watchPools.setPeriod(timeout);
      say("Set poolWatchDogPeriod=" + timeout + " ms");
    }

    String sExcludedFilesExpirationTO = _args.getOpt("excludedFilesExpirationTO");
    if (sExcludedFilesExpirationTO != null) {
      long timeout = Integer.parseInt(sExcludedFilesExpirationTO) * 1000L;
      _watchPools.setExcludedExpiration(timeout);
      say("Set excludedFilesExpirationTO=" + timeout + " ms");
    }

    String maxWorkers = _args.getOpt("maxWorkers");
    if (maxWorkers != null) {
      int mx = Integer.parseInt(maxWorkers);
      _adj.setMaxWorkers(mx);
      say("Set adjuster maxWorkers=" + mx);
    }

    if( _args.getOpt("coldStart") != null )
      _hotRestart = false;

    if( _args.getOpt("hotRestart") != null )
      _hotRestart = true;

    String argSameHost = _args.getOpt("enableSameHostReplica");
    if (argSameHost != null) {
        setEnableSameHostReplica(Boolean.valueOf(argSameHost).booleanValue());
    }
    String argCheckPoolHost = _args.getOpt("XXcheckPoolHost");
    if (argCheckPoolHost != null) {
        setCheckPoolHost(Boolean.valueOf(argCheckPoolHost).booleanValue());
    }
  }

  public ReplicaManagerV2(String cellName, String args) throws Exception {
    super(cellName, args);

// Instantiate classes
    _args = getArgs();

    {
      String argDebug = _args.getOpt("debug");
      if(argDebug != null) {
        _debug = Boolean.valueOf(argDebug).booleanValue();
      }
    }
    setDebug ( _debug ); // Set DCCC debug level the same
//    if ( _debug )
//      _cliDebug = new ReplicaManagerCLIDebug();

    parseDBArgs();

//  say("Setup database with: URL="+_jdbcUrl+" driver="+_driver+" user="+_user+" passwd=********");
    ReplicaDbV1.setup(_jdbcUrl, _driver, _user, _pass);
//  ReplicaDbV1.setup("/opt/d-cache/etc/primrose.conf");

    try {
      _dbrmv2 = installReplicaDb();
    }
    catch ( Exception ex ) {
      esay( "ERROR, can not instantiate replica DB - got exception, now exiting\n"
           +"=================================================================="
           +"Check if DB server running and restart Replica Manager");
      System.exit(1);
    }

    _adj        = new Adjuster( _repMin, _repMax) ;
    _watchPools = new WatchPools();

    esay("ReplicaManager version: " + _cvsId );

    say("Parse arguments");
    parseArgs();

    _resilientPools = new ResilientPools( _args );

    _initDbRunnable = new InitDbRunnable( _delayDBStartTO );

    say("Create threads");
    _dbThread  = getNucleus().newThread(_initDbRunnable,"RepMgr-initDB");
    _adjThread = getNucleus().newThread(_adj,           "RepMgr-Adjuster");
    _watchDog  = getNucleus().newThread(_watchPools,    "RepMgr-PoolWatchDog");

    say("Start Init DB  thread");
    _dbThread.start();

    say("Start Adjuster thread");
    _adjThread.start();

    say("Starting cell");
    start();

  }

  // methods from the cellEventListener Interface
  public void cleanUp() {
    dsay("=== cleanUp called ===");
    _stopThreads     = true;
    _runPoolWatchDog = false;
    super.cleanUp();
  }
  public void cellCreated( CellEvent ce ) {
    super.cellCreated(ce);
    dsay("=== cellCreated called ===, ce=" +ce);
  }
  public void cellDied( CellEvent ce ) {
    super.cellDied(ce);
    dsay("=== cellDied called ===, ce=" +ce);
  }
  public void cellExported( CellEvent ce ) {
    super.cellExported(ce);
    dsay("=== cellExported called ===, ce=" +ce);
  }
  public void routeAdded( CellEvent ce ) {
    super.routeAdded(ce);
    dsay("=== routeAdded called ===, ce=" +ce);
  }
  public void routeDeleted( CellEvent ce ) {
    super.routeDeleted(ce);
    dsay("=== routeDeleted called ===, ce=" +ce);
  }
  // end cellEventListener Interface

  public void getInfo(PrintWriter pw) {
    pw.println("       Version : " + _cvsId);
    super.getInfo( pw );

    synchronized (_dbLock) {
      pw.println(" initDb Active : " + _initDbActive);
    }
    pw.println(" debug : " + getDebugRM() );
    pw.println(" enableSameHostReplica : " + getEnableSameHostReplica() );
    pw.println(" XXcheckPoolHost : " + getCheckPoolHost() );
  }

  private ReplicaDbV1 installReplicaDb() throws SQLException {
      return new ReplicaDbV1(this) ;
  }

//  private ReplicaDbV1 installReplicaDb(boolean keep) throws SQLException {
//      return new ReplicaDbV1(this, keep) ;
//  }

  //
  //
  //
  private void dbUpdatePool(String poolName) throws Exception {
    List fileList = null;
    String hostName = null;

    say(" dbUpdatePool " + poolName);

    // Get pool list
    try {
      for (int loop = 1; true; loop++) {
        try {
          fileList = getPoolRepository(poolName);
          break;
        }
        catch (ConcurrentModificationException cmee) {
          esay(" dbUpdatePool - Pnfs List was invalidated. retry=" + loop + " pool=" + poolName);
          if (loop == 4)
            throw cmee;
        }
        catch(MissingResourceException mre) {
          esay(" dbUpdatePool - Can not get PnfsId List. retry=" + loop + " pool=" + poolName);
          if (loop == 4)
            throw mre;
        }
      }
    }
    catch (Exception ee) {
      esay(" dbUpdatePool - Problem fetching repository from " + poolName + " : " + ee);
      if ( _debug )
        ee.printStackTrace();
      throw ee;
    }

    // Got pool list OK
    say(" dbUpdatePool - Got " + fileList.size() + " pnfsIds from " + poolName);
    _dbrmv2.removePool( poolName );
//  for (Iterator n = fileList.iterator(); n.hasNext(); ) {
//      _db.addPool( (PnfsId) n.next(), poolName);
//  }
    _dbrmv2.addPnfsToPool(fileList, poolName);

    // get host name from pool
    if (_XXcheckPoolHost) {
        try {
            for (int loop = 1; true; loop++) {
                try {
                    hostName = getPoolHost(poolName);
                    if (hostName != null) {
                        synchronized (_hostMap) {
                            _hostMap.put(poolName, hostName);
                            dsay("dbUpdatePool: _hostMap updated, pool=" + poolName
                                   + " host=" + hostName );
                        }
                    }
                    break;
                } catch (NoRouteToCellException ex) {
                    esay(" dbUpdatePool - get hostname - No route to cell. retry=" + loop +
                         " pool=" + poolName);
                    if (loop == 4)
                        throw ex;
                }
            }
        } catch (Exception ee) {
            esay(" dbUpdatePool - Problem get/set host name for the pool " +
                 poolName +
                 " : " + ee);
            if (_debug)
                ee.printStackTrace();
            throw ee;
        }
    }

  }

  //
  // cleanupDb - cleanup db, preparation phase for initDb()
  //
  private void cleanupDb() {
    synchronized (_dbLock) {

      say("Starting cleanupDb()");

      // Save old pools state from DB into Map for hot restart
      // "pool" table will be cleared in DB and state lost

      _poolMap = new HashMap();

      if (_hotRestart) {
        say("Clear DB for online pools");

        dsay("Save old db pools state into map");

        _dbrmv2.clearTransactions();

        Iterator p = _dbrmv2.getPools();
        while (p.hasNext()) {
          String pool = p.next().toString();
          String poolSts = _dbrmv2.getPoolStatus(pool);
          _poolMap.put(pool, poolSts);
          dsay("Add to poolMap : [" + pool + "] " + poolSts);

          if (poolSts.equals(ReplicaDb1.ONLINE)) {
            _poolsToWait.add(pool); // List old online pools in DB
            _dbrmv2.clearPool(pool); // clear all entries for online pool
//          _db.setPoolStatus(pool,ReplicaDb1.DOWN);
          }
        }
        ((DbIterator)p).close();
      }
      else {
        say("Cleanup DB");
        _dbrmv2.clearAll(); // Clear "replica" and "pools" tables
        _dbrmv2.clearTransactions();
      }

      _cntOnlinePools = 0;
    }
  }

  //
  // initDb - update db with files locations in pools
  //
  private void initDb() throws Exception {

    say("Starting initDb()");

    synchronized (_dbLock) {

      initResilientPools();

      dsay("Asking for Pool List");

      List allPools = getPoolList();
      say("Got " + allPools.size() + " pools (any pools) connected");

      List pools = getPoolListResilient();
      say("Got " + pools.size() + " resilient pools connected");

      Iterator it = pools.iterator();
      while (it.hasNext()) {
        String poolName = (String) it.next();
        String oldStatus = null;

        dsay("Got pool [" + poolName + "]");

        oldStatus = (String) _poolMap.get(poolName);
        dsay("Got from poolMap : " + poolName + " " + oldStatus);

        _dbrmv2.setPoolStatus(poolName, ReplicaDb1.OFFLINE); // ... and add it - so record will be

        try {
          dbUpdatePool(poolName);
        }
        catch (Exception ee) {
          say(" initDb - Problem fetching repository from " + poolName + " : " + ee);
          say(" initDb - pool " + poolName +" stays '"+ ReplicaDb1.OFFLINE +"'");
          continue;
        }

        // Set status online for only 'new' (unknown) pools,
        // otherwise leave pool state as it was before
        String newStatus = (oldStatus == null || oldStatus.equals("UNKNOWN"))
            ? ReplicaDb1.ONLINE
            : oldStatus;

        _dbrmv2.setPoolStatus(poolName, newStatus);

        if (newStatus.equals(ReplicaDb1.ONLINE)) {
          _poolsToWait.remove(poolName);
          dsay("Pool " + poolName + " set online, _poolsToWait.size()=" +
               _poolsToWait.size());
          _cntOnlinePools++;
        }
      }
      _useDB = true; // set flag for call back routines

    } // synchronized
    say("Init DB done");
  }

  /////////////////////////////////////////////////////////////////////////////
  // Adjuster thread
  /////////////////////////////////////////////////////////////////////////////

  private class Adjuster implements Runnable {

    private long waitDBUpdateTO  = 10*MINUTE ; //  10 min - re-run Adjuster by TO if DB was NOT notified
    private long waitReplicateTO = 12*HOUR ; // wait for replicattion to finish
    private long waitReduceTO    = 12*HOUR ; // wait for reduction to finish

   private int _min = 2;
   private int _max = 2;
   private int _maxWorkers = 4;
   private int _replicated = 0;
   private int _removed = 0;
   private String _status = "not updated yet";
   private boolean _adjIncomplete = false;
   private boolean _adjFinished;

   private Semaphore workerCount = null;
   private Semaphore workerCountRM = null;

   private int _cntThrottleMsgs = 0;
   private boolean _throttleMsgs = false;

   private Set _poolsWritable = new HashSet(); // can be Destination pools
   private Set _poolsReadable = new HashSet(); // can be Source pools
   private ReplicaDbV1 _db;

   public Adjuster(int min, int max) throws SQLException {
     _min = min;
     _max = max;
     _db = installReplicaDb();
   }
   public void setMin( int min ){
     _min = min;
   }
   public void setMax( int max ){
     _max = max;
   }
   public void setWaitDBUpdateTO( long delay ){
     waitDBUpdateTO = delay;
   }
   public long getWaitDBUpdateTO(){ return waitDBUpdateTO; }

   public void setWaitReplicateTO( long delay ){
     waitReplicateTO = delay;
   }
   public void setWaitReduceTO( long delay ){
     waitReduceTO = delay;
   }
   public void setMaxWorkers( int n ){
     _maxWorkers = n;
   }

   public void run() {

     workerCount   = new Semaphore(_maxWorkers);
     workerCountRM = new Semaphore(_maxWorkers);

     say("Adjuster Thread started");

     // _db.setHeartBeat("Adjuster","startup");

     while (true) {

       if (_dbThread != null) {
         say("Adjuster - wait for Init DB to finish");
         try {
           _dbThread.join();
           break;
         }
         catch (InterruptedException ex) {
           say(
               "Adjuster - Waiting for connections to complete was interrupted, "
               + ( (_stopThreads) ? "stop thread" : "continue"));
         }
         catch (Exception ex) {
           say(
               "Adjuster - Got exception "+ex+"waiting for Init DB thread to complete, wait");
         }
       }
       else {
         esay(
             "Adjuster - did not get DB thread (it can be an error), so sleep for "
             + "60 sec and retry");
         try {
           Thread.sleep(60 * 1000); // n
         }
         catch (InterruptedException ex) {
           say(
               "Adjuster - Waiting for connections to complete was interrupted, "
               + ( (_stopThreads) ? "stop thread" : "continue"));
         }
       }
       if (_stopThreads)
         return;
     }

       // Adjuster can start.

     // Start pools watch dog thread
     if ( _watchDog == null ) {
       say("Starting pool watch dog for the first time - internal ERROR,"
           +" class _watchDog not instantiated, startup aborted");
       _db.setHeartBeat("Adjuster","aborted");
       return;
     } else {
       if( _runPoolWatchDog ) {
         say("Trying to start pool watch dog - Watch dog already running");
       }else{
         say("Adjuster - start pool watch dog");
         _runPoolWatchDog = true;
         _watchDog.start();
       }
     }

     // Check DB updates when noticed, or time-to-time
     //
     try {
       say("=== Adjuster Ready, Start the loop ===");
       _adjFinished = false; // preset not finished state if it falls to catch block
       _adjFinished = runAdjustment();

       _cntThrottleMsgs = 0;
       _throttleMsgs    = false;
       boolean dbUpdatedSnapshot;
       while ( _runAdjuster  && ! _stopThreads ) { // Loop forever to update DB

         synchronized (_dbUpdated) {
           // Check flag whether
           //  - DB changed during last adjustment
           //  - last adjustment was not completed (I do one replica at a time)
           // and wait for update for some time
           if (    ! _dbUpdated.booleanValue()
                && ! _adjIncomplete ) {
             _db.setHeartBeat("Adjuster","waitDbUpdate");
             _dbUpdated.wait(waitDBUpdateTO);
           }
           dbUpdatedSnapshot = _dbUpdated.booleanValue();
           _dbUpdated.reset();
         } // synchronized

         if ( ! _runAdjuster  || _stopThreads ) {
           dsay("Adjuster Thread - asked to stop");
           break;
         }

         if (dbUpdatedSnapshot || _adjIncomplete) {
           _cntThrottleMsgs = 0;
           _throttleMsgs = false;
         }
         // Check whether wait() broke due to update or timeout:
         if (dbUpdatedSnapshot)
           say("DB updated, rerun Adjust cycle");
         else if (_adjIncomplete)
           say("adjustment incomplete, rerun Adjust cycle");
         else {
           String msg = "DB update TimeOut (DB was not updated for "
               + waitDBUpdateTO / 1000L + " sec.),"
               + " run Adjust cycle";

           if (++_cntThrottleMsgs < 5)
             say(msg);
           else if (_cntThrottleMsgs == 5) {
             say(msg + "; throttle future 'DB update TimeOut' messages ");
             _throttleMsgs = true;
           }
         }

         _adjIncomplete = false;
         _adjFinished = false; // preset not finished state if it falls to catch block
         _adjFinished = runAdjustment();
         if( _adjFinished && ! dbUpdatedSnapshot && ! _throttleMsgs )
           say("adjustor finished check cycle, adjustment is " +
               ( (_adjIncomplete) ? "NOT completed":"completed" ) );
       } // - update DB loop
     }
     catch (InterruptedException ee) {
       if( _stopThreads ) {
         say("Adjuster Thread was interrupted, stop thread");
         return;
       } else
         say("Adjuster Thread was interrupted, continue");
     }
     _db.setHeartBeat("Adjuster","done");
     dsay("Adjuster Thread - return");
   }

   public boolean runAdjustment() throws InterruptedException {

     Iterator it;
     int corruptedCount;
     int count;
     StringBuffer oper = null;

     _adjIncomplete = false;

     dsay("runAdjustment - started");

     /*
      * Get list of all Writable and Readable pools for this pass of adjuster
      * As soon as some pool changes its state, _dbUpdated will change
      * and this pass of adjuster will finish
      * Such optimistic scheme is not 100% proof, rather reduces most of the
      * conflicts but not all of them - in any locking scheme pool can go down;
      * Distributed locking may be desirable but may be expensive as well.
      */

     synchronized (_poolsWritable) {
       _poolsWritable = new HashSet();
       for (it = _db.getPoolsWritable(); it.hasNext(); ) {
         _poolsWritable.add(it.next());
       }
       ((DbIterator)it).close();
       // dsay("runAdjustment - _poolsWritable.size()=" +_poolsWritable.size());
     }

     synchronized (_poolsReadable) {
       _poolsReadable = new HashSet();
       for (it = _db.getPoolsReadable(); it.hasNext(); ) {
         _poolsReadable.add(it.next());
       }
       ((DbIterator)it).close();
       // dsay("runAdjustment - _poolsReadable.size()=" +_poolsReadable.size());
     }

     /* ####
      * Scan for and replicate files which can get locked in drainoff pools
      * Copy out single replica, it shall be enough to have access to the file
      */
     dsay("runAdjustment - scan DrainOff");

     oper = new StringBuffer("in DrainOff pools only");
     _setStatus("Adjuster - scanning " +oper +" replicas");
     _db.setHeartBeat("Adjuster","scanDrainOff");

     synchronized (_dbLock) {
      // dsay("runAdjustment - scan DrainOff - got _dbLock, run db query getInDrainoffOnly");

       it = _db.getInDrainoffOnly();
     }
     // dsay("runAdjustment - scan DrainOff - got iterator");

     try {
         while ( it.hasNext() ) {
             PnfsId pnfsId =  new PnfsId((String) it.next());
             replicateAsync( pnfsId, true );

             if( _dbUpdated.booleanValue() ){
                 say("runAdjustment - DB updated during scan for "+oper+" replicas, restarting");
                 return ( false );
             }else if ( _stopThreads ) {
                 say("runAdjustment - stopThreads detected, stop adjustment");
                 return ( true );
             }

         }
     } finally {
         ((DbIterator)it).close();
     }

     /* ####
      * Scan for and replicate files which can get locked in set of OFFLINE_PREPARE pools
      * Copy out single replica, it shall be enough to have access to the file
      */

     oper = new StringBuffer("in offLine-prepare pools only");

     dsay("runAdjustment - scan offLine-prepare");

     _setStatus("Adjuster - scanning " +oper +" replicas");
     _db.setHeartBeat("Adjuster","scanOffLine-prepare");

     synchronized (_dbLock) {
       // dsay("runAdjustment - scan offLine-prepare - got _dbLock, run db query getInOfflineOnly");

       it = _db.getInOfflineOnly();
     }
     // dsay("runAdjustment - scan offLine-prepare - got iterator");

     try {
         while ( it.hasNext() ) {
             PnfsId pnfsId =  new PnfsId((String) it.next());
             replicateAsync( pnfsId, true );

             if( _dbUpdated.booleanValue() ){
                 say("runAdjustment - DB updated during scan for "+oper+" replicas, restarting");
                 return ( false );
             }else if ( _stopThreads ) {
                 say("runAdjustment - stopThreads detected, stop adjustment");
                 return ( true );
             }
         }
     } finally {
         ((DbIterator)it).close();
     }

     /* ####
      * Scan for and replicate Deficient files
      * -- all other files with fewer replicas
      */

     dsay("runAdjustment - scan Deficient");

     corruptedCount = 0; // start over corrupted record count in this DB scan
     _setStatus("Adjuster - scanning Deficient (Fewer) replicas");
     _db.setHeartBeat("Adjuster","replicate");

     it = null;
     synchronized (_dbLock) {
       // dsay("runAdjustment - scan Deficient - got _dbLock, run db query getDeficient");
       it = _db.getDeficient( _min );
     }
     // dsay("runAdjustment - scan Deficient - got iterator");

     try {
         while ( it.hasNext() ) {
             if( _dbUpdated.booleanValue() ){
                 say("runAdjustment - DB updated during scan for Deficient replicas, restarting");
                 return ( false );
             }

             Object[] rec = (Object[]) (it.next());
             if (rec.length < 2) {
                 if (corruptedCount++ < 10) // Throttle err.msgs
                     say("DB.getDeficient() corrupted, record length <2");
                 continue;
             }

             PnfsId pnfsId = new PnfsId ( ((String) rec[0]) );
             count = ( (Long)rec[1] ).intValue();

             int delta = _min - count; // Must be positive for Deficient

             if (delta <= 0) {
                 if (corruptedCount++ < 10) // Throttle err.msgs
                     say("DB.getDeficient() corrupted, nReplicas " + count + " >= "
                         + _min + " (_min)");
                 continue;

             } else {
                 if ( delta > 1 )
                     _adjIncomplete = true; // set flag to scan DB to replicate more replicas

                 replicateAsync( pnfsId, false );

                 if( _dbUpdated.booleanValue() ){
                     say("runAdjustment - DB updated during scan for Deficient replicas, restarting");
                     return ( false );
                 }else if ( _stopThreads ) {
                     say("runAdjustment - stopThreads detected, stop adjustment");
                     return ( true );
                 }
             }
         }
     } finally {
         ((DbIterator)it).close();
     }

     /* ####
      * Scan for and reduce Redundant files - with Extra replicas
      * recovers space in pools.
      */
     corruptedCount = 0; // start over corrupted record count in this DB scan

     dsay("runAdjustment - scan Redundant");
     _setStatus("Adjuster - scanning Redundant (Extra) replicas ");
     _db.setHeartBeat("Adjuster","reduce");

     it = null;
     synchronized (_dbLock) {
       // dsay("runAdjustment - REDUCE - got _dbLock, run db query getRedundant");

       it = _db.getRedundant( _max );
     }

     // dsay("runAdjustment - REDUCE - got iterator");
     try {
         while ( it.hasNext() ) {
             if( _dbUpdated.booleanValue() ){
                 say("runAdjustment - DB updated during scan for Redundant replicas, restarting");
                 return ( false );
             }
             Object[] rec = (Object[]) (it.next());
             if (rec.length < 2) {
                 if (corruptedCount++ < 10) // Throttle err.msgs
                     say("DB.getRedundant() corrupted, record length <2");
                 continue;
             }

             PnfsId pnfsId = new PnfsId ( ((String) rec[0]) );
             count = ( (Long)rec[1] ).intValue();

             int delta = count - _max; // Must be positive for Redundant

             if (delta <= 0) {
                 if (corruptedCount++ < 10) // Throttle err.msgs
                     say("DB.getRedundant() corrupted, nReplicas " + count + " <= "
                         + _max + " (_max)");
                 continue;
             } else {
                 // dsay("runAdjustment - REDUCE - pnfsId=" + pnfsId + ", delta=" +delta);

                 if ( delta > 1 ) {
                     _adjIncomplete = true; // set flag to scan DB to reduce more replicas
                     // dsay("runAdjustment - REDUCE - set _adjIncomplete");
                 }
//               reduce(pnfsId); // reduce ONE replica only
                 reduceAsync(pnfsId); // reduce ONE replica only

                 if( _dbUpdated.booleanValue() ){
                     say("runAdjustment - DB updated during scan for Redundant replicas, restarting");
                     return ( false );
                 }else if ( _stopThreads ) {
                     say("runAdjustment - stopThreads detected, stop adjustment");
                     return ( true );
                 }
             }
         }
     } finally {
         ((DbIterator)it).close();
     }

     // dsay("runAdjustment - got to the end of iteration");
     return ( true );
   }

   private void excludePnfsId(PnfsId pnfsId, String errcode, String errmsg) {
       synchronized (_dbLock) {
           long timeStamp = System.currentTimeMillis();
//         _db.addTransaction(pnfsId, timeStamp, 0);
           _db.addExcluded(pnfsId, timeStamp, errcode, errmsg);
       }
       say("pnfsId=" + pnfsId + " excluded from replication. ");
   }

   private class Replicator implements Runnable {
       private PnfsId _pnfsId = null;
       private int _Id = 0;
       int _wCnt;
       private boolean _extended; // include drainoff and offline prepare pools
                                  // into the source pools
       // HashSet brokenFiles = new HashSet();

       Replicator(PnfsId pnfsId, int Id, int cnt, boolean extended) {
           _pnfsId = pnfsId;
           _Id = Id;
           _wCnt = cnt;
           _extended = extended;
       }

       public void run() {
           try {
               if (_stopThreads)
                   say("Replicator ID=" + _Id + ", pnfsId=" + _pnfsId +
                       " can not start - "
                       + "shutdown detected");
               else {

                   say("Replicator ID=" + _Id + ", pnfsId=" + _pnfsId +
                       " starting"
                       + ", now " + (_maxWorkers - _wCnt) + "/" + _maxWorkers +
                       " workers are active");

                   replicate(_pnfsId);
               }
           } catch (InterruptedException ex) {
               say("Replicator for pnfsId=" + _pnfsId + " got exception " + ex);
           } finally {
               // release Adjuster thread waiting till locking record (exclude) is added to DB
               synchronized (this) { // synchronization is required only to invoke notify()
                 this.notifyAll();
               }
               _wCnt = workerCount.up();
               say("Replicator ID=" + _Id + ", pnfsId=" + _pnfsId + " finished"
                   + ", now " + (_maxWorkers - _wCnt) + "/" + _maxWorkers +
                   " workers are active");
           }
       }

       private void replicate(PnfsId pnfsId) throws InterruptedException {
           MoverTask observer = null;
           long start, stop, currentTime;
           long timerToWait, timeToStop;

           _setStatus("Adjuster - Replicating " + pnfsId);

           start = System.currentTimeMillis();
           timeToStop = start + waitReplicateTO;
           try {
               // extended == true  -- source pools include drainoff and offline-prepare pools
               // extended == false -- online pools only
               observer = (_extended)
                          ? replicatePnfsId(pnfsId,_poolsReadable, _poolsWritable)
                          : replicatePnfsId(pnfsId,_poolsWritable, _poolsWritable);
           } catch (MissingResourceException mrex) {
               String exMsg   = mrex.getMessage();
               String exClass = mrex.getClassName();
               String exKey   = mrex.getKey();

               say("replicate(" + pnfsId + ") reported : " + mrex);

               if (exMsg.startsWith("Pnfs File not found :")
                   || exMsg.equals("Pnfs lookup failed")
                   || exMsg.startsWith("Not a valid PnfsId")) {
                   // I must remove file from DB, if it has no pnfs entry;
                   // mark it 'exclude' for now
                   excludePnfsId(pnfsId, "", exMsg);
               } else {
                   dsay("msg='" + exMsg + "'  class='" + exClass + "' key='" +
                        exKey + "' ");
                   // not excluded - will retry
               }
               return;
           } catch (IllegalArgumentException iaex) {
               boolean sigFound = false;
               say("replicate(" + pnfsId + ") reported : " + iaex);
               String exMsg = iaex.getMessage();
               sigFound = exMsg.startsWith("No pools found,");

               // Exclude forever from replication;
               /** @todo
                * better to check again when pool arrives and situation changes
                */
               if (sigFound)
                   excludePnfsId(pnfsId, "", exMsg);

               // Report, But do not exclude

               if (exMsg.startsWith("replicatePnfsId, argument")) {
                   say("There are not enough pools to get replica from or to put it to; try operation later");
                   sigFound = true;
               } else if (exMsg.startsWith("Try again :")) {
                   say(exMsg);
                   sigFound = true;
               }

               dsay("msg='" + exMsg + "' "
                    + (sigFound
                       ? "signature OK"
                       : "signature not found"
                    )
                       );
               return;
           } catch (Exception ee) {
               esay("replicate(" + pnfsId + ") reported : " + ee + ", excluding");
               esay(ee);
               excludePnfsId(pnfsId, "", ee.getMessage());
               return;
           }

           String poolName = observer.getDstPool();
           say(pnfsId.toString() + " Replicating");

           synchronized (_dbLock) {
               _db.addTransaction(pnfsId, start, +1);
           }

           // release Adjuster thread waiting till locking record is added to DB
           // In this case parent will be notified twice
           synchronized (this) { // synchronization is required only to invoke notify()
             this.notifyAll();
           }

           synchronized (observer) {
               timerToWait = timeToStop - start;
               currentTime = System.currentTimeMillis();
               while (timerToWait > 0 && !observer.isDone()) {
                   observer.wait(timerToWait);
                   currentTime = System.currentTimeMillis();
                   timerToWait = timeToStop - currentTime;
               }
               if (!observer.isDone())
                   observer.setErrorCode( -1, "replicate pnfsID=" + pnfsId
                                         + ", Timed out after " +
                                         (currentTime - start) + " ms");
           }
           stop = System.currentTimeMillis();

           int oErr = observer.getErrorCode();
           long timeStamp = System.currentTimeMillis();
           if (oErr == 0) {
               _replicated++;
               say(pnfsId.toString() + " replication done after " + (stop - start) +
                   " ms, result " + observer);

               synchronized (_dbLock) {
                 _db.addPool(pnfsId, poolName);
                 _db.removeTransaction(pnfsId);
               }
               dsay("replicate("+pnfsId
                    +") : cleanup action record and add pnfsid to the pool="
                    +poolName +"- DB updated");
           } else {
               say(pnfsId.toString() + " replication ERROR=" + oErr
                   + ", timer=" + (stop - start) +
                   " ms, error " + observer.getErrorMessage());

               // was error '203' by "rc" code, but reply code is different
               if (oErr == 102 || oErr == -1) {
                 String eMsg;
                 if (oErr == 102)
                   eMsg = "entry already exists";
                 else if (oErr == -1)
                   eMsg = "operation timed out";
                 else
                   eMsg = "...";

                 synchronized (_dbLock) {
                   _db.removeTransaction(pnfsId);
                   _db.addExcluded(pnfsId, timeStamp, String.valueOf(oErr), eMsg);
                 }
                 say("pnfsId=" + pnfsId +
                     " is excluded from replication. (err="
                     + oErr + ", " + eMsg + ")");
               }
           }
       }
   }

   private class Reducer implements Runnable {
     private PnfsId _pnfsId = null;
     private int _Id = 0;
     int _wCnt;
     // HashSet brokenFiles = new HashSet();

     Reducer(PnfsId pnfsId, int Id, int cnt) {
       _pnfsId = pnfsId;
       _Id = Id;
       _wCnt = cnt;
     }

     public void run() {
       try {
         if (_stopThreads)
           say("Reducer ID=" + _Id + ", pnfsId=" + _pnfsId + " can not start - "
               + "shutdown detected");
         else {
           say("Reducer ID=" + _Id + ", pnfsId=" + _pnfsId + " starting"
               + ", now " + (_maxWorkers - _wCnt) + "/" + _maxWorkers +
               " workers are active");
           reduce(_pnfsId);
         }
       } catch (InterruptedException ex) {
         say("Reducer for pnfsId=" + _pnfsId + " got exception " + ex);
       } finally {
         // release Adjuster thread waiting till locking record (exclude) is added to DB
         synchronized (this) { // synchronization is required only to invoke notify()
           this.notifyAll();
         }
         _wCnt = workerCountRM.up();
         say("Reducer ID=" + _Id + ", pnfsId=" + _pnfsId + " finished"
             + ", now " + (_maxWorkers - _wCnt) + "/" + _maxWorkers +
             " workers are active");
       }
     }

     private void reduce(PnfsId pnfsId) throws InterruptedException {
       ReductionObserver observer = null;
       long start, stop, currentTime;
       long timerToWait, timeToStop;
       int err;

       _setStatus("Adjuster - reducing " + pnfsId);

       start = System.currentTimeMillis();
       timeToStop = start + waitReduceTO;
       try {
         observer = (ReductionObserver) removeCopy(pnfsId, _poolsWritable);
       } catch (Exception ee) {
         say("reduce(" + pnfsId + ") reported : " + ee);
         return;
       }

       String poolName = observer.getPool();
       say(pnfsId.toString() + " Reducing");

       synchronized (_dbLock) {
         _db.addTransaction(pnfsId, start, -1);
       }

       // release Adjuster thread waiting till locking record is added to DB
       // - notify could be sent twice in this case
       synchronized (this) { // synchronization is required only to invoke notify()
         this.notifyAll();
       }

       synchronized (observer) {
         timerToWait = timeToStop - start;
         currentTime = System.currentTimeMillis();
         while (timerToWait > 0 && !observer.isDone()) {
           observer.wait(timerToWait);
           currentTime = System.currentTimeMillis();
           timerToWait = timeToStop - currentTime;
         }
         if (!observer.isDone())
           observer.setErrorCode( -1, "reduce pnfsID=" + pnfsId
                                 + ", Timed out after " + (currentTime - start) +
                                 " ms");
       }
       stop = System.currentTimeMillis();

       int oErr    = observer.getErrorCode();
       String eMsg = observer.getErrorMessage();

       long timeStamp = System.currentTimeMillis();
       if (oErr == 0) {
         _removed++;
         say(pnfsId.toString() + " reduction done after " + (stop - start) +
             " ms, result " + observer);
         synchronized (_dbLock) {
           _db.removePool(pnfsId, poolName);
           _db.removeTransaction(pnfsId);
         }
         dsay("reduce("+pnfsId
              +") : cleanup action record and remove pnfsid from the pool="
              +poolName +"- DB updated");
       } else {
   //       say(pnfsId.toString() + " reduction ERROR, timer=" + (stop - start) + " ms, "
   //           + "error " + observer.getErrorMessage() );
         say(pnfsId.toString() + " reduction ERROR, result=[" + observer + "]");

// it is set already
//         if (oErr == -1) {
//           eMsg = "operation timed out";
//         }

         // ALWAYS exclude pnfsid if replication failed
         synchronized (_dbLock) {
           _db.removeTransaction(pnfsId);
           _db.addExcluded(pnfsId, timeStamp, String.valueOf(oErr), eMsg);
         }
         say("pnfsId=" + pnfsId + " excluded from replication. "
             + "(err=" + oErr + ", " + eMsg + ")");
       }
     }
   }

   private void replicateAsync(PnfsId pnfsId, boolean extended) throws InterruptedException {
     boolean noWorker = true;
     int cnt = 0;
     // Aquire "worker awailable" semaphore
     do {
       try {
         dsay("replicateAsync - get worker");
         cnt = workerCount.down();
         dsay("replicateAsync - got worker OK");

         noWorker = false;
       }
       catch (InterruptedException ex) {
         if( _stopThreads ) {
           say("replicateAsync: waiting for awailable worker thread interrupted, stop thread");
           return;
         } else
           say("replicateAsync: waiting for awailable worker thread interrupted, retry");
       }
     } while( noWorker );

     // Create Replicator object, thread, and start it
     Replicator r = new Replicator( pnfsId, _repId, cnt, extended );
     synchronized (r) {
       getNucleus().newThread(r, "RepMgr-Replicator-" + _repId).start();
       _repId++;

       // Wait until r.replicate() will add locking record to DB and will release current thread with notifyAll()
       r.wait();
     }
   }

   private void reduceAsync(PnfsId pnfsId) throws InterruptedException {
     boolean noWorker = true;
     int cnt = 0;
     // Aquire "worker awailable" semaphore
     do{
       try {
         dsay("reduceAsync - get worker");
         cnt = workerCountRM.down();
         dsay("reduceAsync - got worker - OK");
         noWorker = false;
       }
       catch (InterruptedException ex) {
         if( _stopThreads ) {
           say("reduceAsync: waiting for awailable worker thread interrupted, stop thread");
           return;
         } else
           say("reduceAsync: waiting for awailable worker thread interrupted, retry");
       }
     }while( noWorker );

     // Create Reducer object, thread, and start it
     Reducer r = new Reducer( pnfsId, _redId, cnt );
     synchronized (r) {
       getNucleus().newThread(r, "RepMgr-Reducer-" + _redId).start();
       _redId++;

       // Wait until r.reduce() will add locking record to DB and will release current thread with notifyAll()
       r.wait();
     }
   }

   /** @todo OBSOLETE
    * Verifies if info from DB corresponds to the real life
    *
    */
   protected List verifyPnfsId(PnfsId pnfsId, Iterator knownPoolList)
           throws Exception {
     List sourcePoolList = getCacheLocationList(pnfsId, true);
     HashSet newPoolSet = new HashSet(sourcePoolList);

     while (knownPoolList.hasNext()) {
       Object inext = knownPoolList.next();
       if (newPoolSet.contains(inext)) {
         newPoolSet.remove(inext);
       }
       else {
         newPoolSet.add(inext);
       }
     }
     List poolList = new ArrayList(newPoolSet);
     if (poolList.size() == 0) {
       return null;
     }
     else {
       return poolList;
     }
   }

    private void _setStatus( String status ){ _status = status ; }

  } //-- end class Adjuster

  private int poolDisable( String poolName ) {
    int modeBits = PoolV2Mode.DISABLED_STRICT;
    return setPoolMode( poolName, modeBits );
  }

  private int poolEnable( String poolName ) {
    int modeBits = PoolV2Mode.ENABLED;
    return setPoolMode( poolName, modeBits );
  }

  private int poolRdOnly( String poolName ) {
    int modeBits = PoolV2Mode.DISABLED_RDONLY;
    return setPoolMode( poolName, modeBits );
  }

  private int setPoolMode( String poolName, int modeBits ) {
    int    rc = 1 ;
    String rm = "Replica Manager Command";

    PoolV2Mode mode = new PoolV2Mode( modeBits ) ;
    PoolModifyModeMessage msg, reply;

    msg = new PoolModifyModeMessage(poolName,mode);
    msg.setStatusInfo( rc, rm );

    try{
       reply = (PoolModifyModeMessage) sendObject( poolName, msg ) ;
    }catch(Exception ee ){
       esay( "setPoolMode pool=" + poolName
            + ", mode=" + new PoolV2Mode(modeBits).toString()
            +" - got exception '"+ ee.getMessage() +"'" );
       return -1;
    }

    if( reply.getReturnCode() != 0 ){
       esay( "setPoolMode pool=" + poolName
            + ", mode=" + new PoolV2Mode(modeBits).toString()
            +" - error '" +reply.getErrorObject().toString() +"'" );
       return -1;
    }
    return 0;
  }

  /////////////////////////////////////////////////////////////////////////////
  // CLI
  //

//  public class ReplicaManagerCLI {
    //--------------------------------------------------------------------------
    //=== System ===
    //--------------------------------------------------------------------------

    // set / unset debug printout
    public String hh_debug = "true | false";
    public String ac_debug_$_1(Args args) {
      String cmd = args.argv(0);

      if ( cmd.equalsIgnoreCase("true") ) {
        setDebug2 ( true );
        say("debug true" );
        return "debug true";
      }else if ( cmd.equalsIgnoreCase("false") ) {
        setDebug2 ( false );
        say("debug false" );
        return "debug false";
      }
      say("Wrong argument '" +cmd +"'");
      return "wrong argument";
    }

    // enable / disable same host replication (test environment / production)
    public String hh_enable_same_host_replication = "true | false";
    public String ac_enable_same_host_replication_$_1(Args args) {
        String cmd = args.argv(0);
        String msg = "same host replication ";
        String m;

        if (cmd.equalsIgnoreCase("true")) {
            setEnableSameHostReplica(true);
            m = msg + "enabled";
        } else if (cmd.equalsIgnoreCase("false")) {
            setEnableSameHostReplica(false);
            m = msg + "disabled";
        } else {
            m = "Wrong argument '" + cmd + "'";
        }
        say(m);
        return m;
    }

    // enable / disable same host replication (test environment / production)
    public String hh_XX_check_pool_host = "true | false  # experimental, can be changed";
    public String ac_XX_check_pool_host_$_1(Args args) {
        String cmd = args.argv(0);
        String msg = "check pool host ";
        String m;

        if (cmd.equalsIgnoreCase("true")) {
            setCheckPoolHost(true);
            m = msg + "true";
        } else if (cmd.equalsIgnoreCase("false")) {
            setCheckPoolHost(false);
            m = msg + "false";
        } else {
            m = "Wrong argument '" + cmd + "'";
        }
        say(m);
        return m;
    }

    //--------------------------------------------------------------------------
    public String hh_db_wakeup = "                     # wakeup DB initialization on startup when it waits pools to connect";
    public String ac_db_wakeup(Args args) {
      if (_initDbRunnable != null) {
        _initDbRunnable.wakeupWaitInit();
        return "woke up db";
      }
      else
        return "_initDbRunnable is not instantiated";
    }

    //--------------------------------------------------------------------------
    //=== Pool ===
    //--------------------------------------------------------------------------
    public String hh_show_pool = "<pool>               # show pool status";
    public String ac_show_pool_$_1(Args args) {
      String poolName = new String(args.argv(0));
      String poolStatus;
      synchronized (_dbLock) {
        poolStatus = new String(_dbrmv2.getPoolStatus(poolName));
      }
      String s = new String("Pool '" + poolName + "' status " + poolStatus);
      say("INFO: "+ s);
      return s;
    }

    public String hh_set_pool = "<pool> <state>";
    public String ac_set_pool_$_2(Args args) {

      String poolName = new String(args.argv(0));
      String poolStatus = new String(args.argv(1));
      boolean updatedOK = false;
      boolean setOK = false;

      String sErrRet = "Resilient Pools List is not defined (yet), ignore";

      if( _resilientPools == null ) {
          // _resilientPools was not set yet
          dsay( sErrRet );
          return sErrRet;
      }

      if (_resilientPools.usePoolGroup()) {
          List l = _resilientPools.getResilientPools();
          if (l == null) {
              // usePoolGroup() == true, but we got 'null' list for resilient pools
              dsay( sErrRet );
              return sErrRet;
          } else if ( ! l.contains(poolName) ) { // pool is NOT resilient
              String sErrRet2 = "Pool " + poolName + " is not resilient pool, ignore command";
              dsay( sErrRet2 );
              return sErrRet2;
          }
      }

      synchronized (_dbLock) {
        String poolStatusOld = new String(_dbrmv2.getPoolStatus(poolName));

        say("Pool '" + poolName + "' status was " + poolStatusOld);

        // Check this is correct command - new pool state is valid
        if (poolStatus.equals("down")
            || poolStatus.equals("online")
            || poolStatus.equals("offline")
            || poolStatus.equals("offline-prepare")
            || poolStatus.equals("drainoff")
            ) { // new pool state is correct

          /* how it shall be:
          if ( poolStatus.equals("online") ) {
            setOK = ( poolEnable ( poolName )  == 0 );
          } else if ( poolStatus.equals("offline-prepare")
                      || poolStatus.equals("drainoff") ) {
            setOK = ( poolRdOnly ( poolName )  == 0 );
          }
           */

          // Really, poolRdOnly disables pool, so it is not considered readable anymore.
          // Do only up/down transitions.
          boolean countablePool = ( poolStatus.equals("online")
               || poolStatus.equals("offline-prepare")
               || poolStatus.equals("drainoff") );

          if ( countablePool ) {
//           setOK = ( poolEnable ( poolName )  == 0 );
                // Do not do pool Enable on Timur's and Patrick's reuquest. 9/28/07
                // They have problems with counting of space in pools and keep pool disabled.
                // User shall do "enable" pool manually by direct command to the pool
                //      set pool enabled
                setOK = true;
          }

          // Do not try to update countablePool when it already failed to change status
          // Still can try 'offline' pool
          if ((countablePool && setOK) || !countablePool) {

              if (((poolStatusOld.equals("down") || poolStatusOld.equals("UNKNOWN"))
                   && !(poolStatus.equals("down"))
                  )
                  ||
                  ((poolStatus.equals("online") && !poolStatus.equals(poolStatusOld)))
                      ) {
                  // Transition from state where we did not count files correctly
                  // to the state where we must count files:
                  // -- rerun pool inventory
                  try {
                      dbUpdatePool(poolName);
                      _dbrmv2.setPoolStatus(poolName, poolStatus);
                      say("setpool, pool " + poolName +
                          " state change to '" + poolStatus + "' updated in DB");
                  } catch (Exception ex) {
                      say(" setpool - Problem fetching repository from " + poolName +
                          " : " + ex);
                      say(" setpool - pool " + poolName + " stays '" + poolStatusOld +
                          "'");
                  } // try / catch
              } else {
                  // otherwise we simply change pool status in DB.
                  _dbrmv2.setPoolStatus(poolName, poolStatus);
                  say("Pool '" + poolName + "' status set to " + poolStatus);
              }
          }
          if (poolStatus.equals("down")
              || poolStatus.equals("offline") ) {
 //            setOK = ( poolDisable( poolName )  == 0 );
                // Do not do pool Enable on Timur's and Patrick's reuquest. 9/28/07
                // They have problems with counting of space in pools and want keep pool disabled.
                // User shall do "enable" pool manually by direct command to the pool
                //      set pool disable
                setOK = true;
         }

          updatedOK = true;
        }
      }

      if (updatedOK && setOK) {
          say("setpool, pool " + poolName + ", notify All");
          _dbUpdated.wakeup();
          return "ok";
      } else {
          say("Can not set pool '" + poolName + "' state to " + poolStatus +
              ", ignored");
          if (updatedOK && setOK)
              say("Tansaction error: pool state or DB modified, but not both");
          return "error";
      }
    }

    //--------------------------------------------------------------------------
    public String hh_ls_unique = "<pool>               # check if pool drained off (has unique pndfsIds)";
    public String ac_ls_unique_$_1(Args args) {
      String poolName    = new String(args.argv(0));

      say("pool '" +poolName +"'");
      List uniqueList = findUniqueFiles( poolName );

      int uniqueFiles = uniqueList.size();
      say("Found "+ uniqueFiles +" unique files in pool '" + poolName + "'");

      for (Iterator i = uniqueList.iterator(); i.hasNext(); ) {
        say("Unique in "+ poolName + ", pnfsId="+ i.next() );
      }

      return "Found " + uniqueFiles;
    }

    // helper function
    private List findUniqueFiles( String poolName ) {
      HashSet inPoolSet;
      List missingList = new ArrayList();
      List inPoolList  = new ArrayList();

      Iterator inPool  = _dbrmv2.pnfsIds( poolName );
      Iterator missing = _dbrmv2.getMissing();

      while (missing.hasNext()) {
        Object rec = (Object) (missing.next());

        missingList.add(  ( (String) rec) );  // pnfsId as string
      }
      ((DbIterator)missing).close();

      while (inPool.hasNext()) {
        Object rec = (Object) (inPool.next());

        inPoolList.add(  ( (String) rec) ); // pnfsId as String
      }
      ((DbIterator)inPool).close();

      inPoolSet = new HashSet(inPoolList);

      List uniqueList   = new ArrayList() ;

      for (Iterator i = missingList.iterator(); i.hasNext(); ) {
        Object inext = i.next();
        if (inPoolSet.contains(inext))
          uniqueList.add(inext);
      }

      return uniqueList;
    }

    //--------------------------------------------------------------------------
    // === pnfsId ===
    //--------------------------------------------------------------------------

    public String hh_ls_pnfsid = "[<pnfsId>]           # DEBUG: list pools for pnfsid[s], from DB";
    public String ac_ls_pnfsid_$_0_1(Args args) {

        StringBuffer sb = new StringBuffer();
        if (args.argc() == 0) {
            Iterator it = _dbrmv2.pnfsIds();
            while (it.hasNext()) {
                PnfsId pnfsId = new PnfsId((String) it.next());
                sb.append(printCacheLocation(pnfsId)).append("\n");
            }
            ((DbIterator) it).close();
        } else {
            PnfsId pnfsId = new PnfsId(args.argv(0));
            sb.append(printCacheLocation(pnfsId)).append("\n");
        }
        return sb.toString();
    }

    //--------------------------------------------------------------------------

    /**
      * COMMAND HELP for 'show hostmap'
      */
    public String hh_show_hostmap = " [<pool>] # show pool to host mapping";
    /**
      *  COMMAND : show hostmap [<pool>]
      *  displays list of pool to host mapping for all pools or specified pool
      */
     public String ac_show_hostmap_$_0_1(Args args) {
         StringBuffer sb = new StringBuffer();
         String poolName = null;
         String hostName = null;

         if (args.argc() == 0) {
             synchronized (_hostMap) {
                 for (Iterator i = _hostMap.keySet().iterator(); i.hasNext(); ) {
                     poolName = i.next().toString();
                     hostName = (String)_hostMap.get(poolName);
                     sb.append(poolName).append(" ").append(hostName).append("\n");
                 }
             }
         } else {
             poolName = new String(args.argv(0));
             if (poolName != null) {
                 synchronized (_hostMap) {
                     hostName = (String)_hostMap.get(poolName);
                     sb.append(poolName).append(" ").append(hostName).append("\n");
                 }
             }
         }
         return sb.toString();
     }

     /**
      * COMMAND HELP for 'set hostmap'
      */
     public String hh_set_hostmap = " <pool> <host> # set TEMPORARILY pool to host mapping";
     /**
      *  COMMAND : set hostmap pool host
      *  maps pool "pool" to specified "host"
      *  Pool to host mapping is updated automatically by "host" tag defined in .poollistfile
      *  This command may have sense if you want to define "host" which does not have this tag defined
      */
     public String ac_set_hostmap_$_2(Args args) {
         StringBuffer sb = new StringBuffer();

         String poolName = new String(args.argv(0));
         String hostName = new String(args.argv(1));

         if (poolName != null && hostName != null) {
             synchronized (_hostMap) {
                 _hostMap.put(poolName, hostName);
             }
             sb.append("set hostmap ").append(poolName).append(" ")
                     .append(hostName).append("\n");
         }
         return sb.toString();
     }

     /**
      * COMMAND HELP for 'remove hostmap'
      */
     public String hh_remove_hostmap =
             " <pool>  # remove pool to host mapping for the pool 'pool'";
     /**
      *  COMMAND : remove hostmap pool
      *  remove pool to host mapping for specified "pool"
      */
     public String ac_remove_hostmap_$_1(Args args) {
         StringBuffer sb = new StringBuffer();
         String poolName = new String(args.argv(0));

         if (poolName != null) {
             synchronized (_hostMap) {
                 _hostMap.remove(poolName);
             }
             sb.append("remove hostmap ").append(poolName).append("\n");
         }
         return sb.toString();
     }

    //--------------------------------------------------------------------------
    public String hh_update = "<pnfsid> [-c]           # DEBUG: get pools list from pnfs, '-c' confirm with pools";
    public String ac_update_$_1(Args args) throws Exception {

      StringBuffer sb = new StringBuffer();

      PnfsId pnfsId = new PnfsId(args.argv(0));

      sb.append("Old : ").append(printCacheLocation(pnfsId)).append("\n");

      List list = getCacheLocationList(pnfsId, args.getOpt("c") != null);

      _dbrmv2.clearPools(pnfsId);

      for (Iterator i = list.iterator(); i.hasNext(); ) {
        _dbrmv2.addPool(pnfsId, i.next().toString());
      }
      sb.append("New : ").append(printCacheLocation(pnfsId)).append("\n");
      return sb.toString();
    }

    //--------------------------------------------------------------------------
    public String hh_reduce = "<pnfsId>";
    public String ac_reduce_$_1(Args args) throws Exception {

      final PnfsId pnfsId = new PnfsId(args.argv(0));

      ReducePnfsIDRunnable r = new ReducePnfsIDRunnable(pnfsId);
      getNucleus().newThread(r).start();
      return "initiated (See pinboard for more information)";
    }

    //--------------------------------------------------------------------------
    public String hh_replicate = "<pnfsId>";
    public String ac_replicate_$_1(Args args) throws Exception {

      final PnfsId pnfsId = new PnfsId(args.argv(0));

      ReplicatePnfsIDRunnable r = new ReplicatePnfsIDRunnable(pnfsId);
      getNucleus().newThread(r).start();
      return "initiated (See pinboard for more information)";
    }

    //--------------------------------------------------------------------------
    public String hh_copy = "<pnfsId> <sourcePool>|* <destinationPool>  #  does not check for free space in dest";
    public String ac_copy_$_3(Args args) throws Exception {

      PnfsId pnfsId      = new PnfsId(args.argv(0));
      String source      = args.argv(1);
      String destination = args.argv(2);

      HashSet set = new HashSet();
      Iterator it = _dbrmv2.getPools(pnfsId);
      while (it.hasNext()) {
          set.add(it.next().toString());
      }
      ((DbIterator) it).close();


      if (set.isEmpty())
        throw new
            IllegalArgumentException("No source found for p2p");

      if (source.equals("*"))
        source = set.iterator().next().toString();

      if (!set.contains(source))
        throw new
            IllegalArgumentException("Source " + source +
                                     " not found in pools list");

      if (set.contains(destination))
        throw new
            IllegalArgumentException("Destination " + destination +
                                     " already found in pools list");

      TaskObserver observer = movePnfsId(pnfsId, source, destination);

      return observer.toString();
    }

    //--------------------------------------------------------------------------
    public String hh_exclude = "<pnfsId> [iErrCode [sErrorMessage] ]  # exclude <pnfsId> from replication";
    public String ac_exclude_$_1_3(Args args) throws Exception {

      long timeStamp = System.currentTimeMillis();
      PnfsId pnfsId = new PnfsId(args.argv(0));
      // It is supposed to be number
      String iErr    = ( args.argc() > 1 ) ? args.argv(1) : "-2";
      String eMsg = ( args.argc() > 2 ) ? args.argv(2) : "Operator intervention";

      synchronized (_dbLock) {
        _dbrmv2.addExcluded(pnfsId, timeStamp, iErr, eMsg);
      }
      String msg = "pnfsId=" + pnfsId + " excluded from replication";
      say( msg );
      return msg;
    }

    //--------------------------------------------------------------------------
    public String hh_release = "<pnfsId>               # removes transaction/'BAD' status for pnfsId";
    public String ac_release_$_1(Args args) throws Exception {

      PnfsId pnfsId = new PnfsId(args.argv(0));

      synchronized (_dbLock) {
        _dbrmv2.removeTransaction( pnfsId );
      }
      String msg = "pnfsId=" + pnfsId + " released";
      say( msg + ",  (active transaction or 'exclude' status cleared)" );
      return msg;
    }

//  } // end class ReplicaManagerCLI definiton

  //---------------------------------------------------------------------------
//  public class ReplicaManagerCLIDebug {

    //--------------------------------------------------------------------------
    // === System ===
    //--------------------------------------------------------------------------
    /*
    //----------------------------------------------------
    // Start / stop pool watch dog
    // Stop sequence is NOT fool proof - watch dog will notice
    //  command to stop when it will wake up, that is it can be after _period
    //  Anf it is NOT singleton to prevent second copy from running
    public String hh_stop = "threads | watchdog | adjuster     # DEBUG:";
    public String ac_stop_$_1(Args args) {
      String cmd = args.argv(0);

      if ( cmd.equals("threads") ) {
        _stopThreads = true;
        say("Threads were notified to stop");
        return "Threads were notified to stop";
      }else if ( cmd.equals("watchdog") ) {
        _runPoolWatchDog = false;
        say("Pool watch dog notified to stop, wait for "+ _watchPools.getPeriod()
            + " until it will wake up and notice the command");
        return "watch dog notified to stop";
      }
      else if (cmd.equals("adjuster")) {
        _runAdjuster = false;
        say("adjuster notified to stop, wait for " + _watchPools.getPeriod() +
            " until it will wake up and notice the command");
        return "adjuster notified to stop";
      }
      say("Wrong argument '" + cmd + "'");
      return "wrong argument";
    }
    */
    //--------------------------------------------------------------------------
    // === Pool ===
    //----------------------------------------------------------------------------
    public String hh_pool_inventory = "<pool>          # DEBUG - danger, DB not locked";
    public String ac_pool_inventory_$_1(Args args) {
      String poolName = new String(args.argv(0));

      synchronized (_dbLock) {
        if (_initDbActive)
          throw new
              IllegalArgumentException("InitDb still active");
        else
          _initDbActive = true;
      }

      dbUpdatePoolRunnable r = new dbUpdatePoolRunnable( poolName );
      getNucleus().newThread(r,"RepMgr-dbUpdatePool").start();
      return "Initiated";
    }

    //--------------------------------------------------------------------------
    // === PnfsId ===
    //--------------------------------------------------------------------------

    public String hh_clear = "<pnfsid>                 # DEBUG: removes pnfsid from replicas table in DB";
    public String ac_clear_$_1(Args args) {

      PnfsId pnfsId = new PnfsId(args.argv(0));

      synchronized (_dbLock) {
        _dbrmv2.clearPools(pnfsId);
      }
      return "";
    }


  //---------------------------------------------------------------------------
  private String printCacheLocation(PnfsId pnfsId) {

    StringBuffer sb = new StringBuffer();

    sb.append(pnfsId.toString()).append(" ");
    Iterator it = _dbrmv2.getPools(pnfsId);
    while (it.hasNext()) {
      sb.append(it.next().toString()).append(" ");
    }
    ((DbIterator) it).close();
    return sb.toString();
  }

  //---------------------------------------------------------------------------

  private class ReducePnfsIDRunnable implements Runnable {
      PnfsId _pnfsId;

      public ReducePnfsIDRunnable(PnfsId pnfsId) {
          _pnfsId = pnfsId;
      }

      public void run() {
          if (_adj == null) {
              say("adjuster class not instantiated yet");
              return;
          }
          say(_pnfsId.toString() + " Starting replication");

          try {
              _adj.reduceAsync(_pnfsId );
              say(_pnfsId.toString() + " async reduction started");
          } catch (Exception ex) {
              say(_pnfsId.toString() + " got exception " + ex);
          }
      }
  }

  //---------------------------------------------------------------------------

  private class ReplicatePnfsIDRunnable implements Runnable{
    PnfsId _pnfsId;

    public ReplicatePnfsIDRunnable(PnfsId pnfsId) {
      _pnfsId = pnfsId;
    }

    public void run() {
      if ( _adj == null ) {
        say( "adjuster class not instantiated yet" );
        return;
      }
      say(_pnfsId.toString() + " Starting replication");

      try {
          // use extended set of source pools for replication (include drainoff, offline-prepare)
        _adj.replicateAsync(_pnfsId, true);
        say(_pnfsId.toString() + " async replication started");
      }
      catch (Exception ex) {
        say(_pnfsId.toString() + " got exception " + ex );
      }
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // InitDb thread
  /////////////////////////////////////////////////////////////////////////////

  private class InitDbRunnable implements Runnable {
    private long _delayStart = 0L;
    Thread myThread = null;
    boolean _waiting = false;
//  private ReplicaDbV1 _db;

    public InitDbRunnable( long delay ) {
        _delayStart = delay;
//      _db = installReplicaDb();
    }

    public InitDbRunnable() {
        this(0L);
    }

    public void wakeupWaitInit() {
      if ( myThread != null && _waiting ) {
        myThread.interrupt();
      } else {
        say("DB thread does not sleep");
      }
    }

    public boolean isWaiting() { return _waiting; }

    public void run() {
      say("--- DB init started ---");
      myThread = Thread.currentThread();


      try {
        say( _hotRestart
             ? "=== Hot Restart ==="
             : "=== Cold Start  ===" );

        cleanupDb();

        dsay( "Sleep " + _delayPoolScan/1000 + " sec");
        Thread.sleep( _delayPoolScan ); // sleep x sec while communications are established
        dsay( "Sleep - waiting for communications to establish - is over");

        initDb();

        if (_delayStart != 0L) {
          synchronized (_poolsToWait) {

            if (_poolsToWait.size() > 0) {
              say("=== Adjuster wakeup is delayed for " + _delayStart/1000L +
                  " sec. for pools to connect - sleep ... ===\n");

              try {
                _waiting = true;
                _poolsToWait.wait(_delayStart);
              }
              catch (InterruptedException ex) {
                if (_stopThreads) {
                  say("DB init delay interrupted, stop thread");
                  _waiting = false;
                  return;
                }
                else
                  say("DB init delay interrupted, continue");
              }
              finally {
                _waiting = false;
              } // try / catch /finally
            }   // if
          }     // synchronized

        }
      }
      catch (Exception ex) {
        say("Exception in go : " + ex);
        ex.printStackTrace();
      }
      finally {
          synchronized (_dbLock) {
          _initDbActive = false;
          say("DB initialized, notify All");
        }
        // TODO IF I got exception, I shall not 'wakeup', but retry or shutdown
        _dbUpdated.wakeup();
      }
      myThread = null;
    }
  }

  /////////////////////////////////////////////////////////////////////////////
  // WatchDog thread
  /////////////////////////////////////////////////////////////////////////////

  private class WatchPools implements Runnable {
    Set      _knownPoolSet = new HashSet();
//    private long _timeout      = 10L * 1000L ; // 10 sec. - Pool Msg Reply timeout
    int     cntNoChangeMsgLastTime = 0;

    private long _period       = 10 *  60L * 1000L ; //  10 min. - cycle period
    private long _expire       = 12 *3600  * 1000L ; // 12 hours - expire excluded files after 12 hours

    private boolean _restarted = false;
    private ReplicaDbV1 _db;

    public WatchPools() throws Exception {
        _db = installReplicaDb();
    }

    public void setPeriod( long p ) {
      _period = p;
    }
    public long getPeriod() {
      return _period;
    }

    public void setExcludedExpiration( long e ) {
      _expire = e;
    }
    public long getExcludedExpiration() {
      return _expire;
    }

    public void run() {
      say("Starting pool watch dog thread");
      _restarted = true;

      do {
        try {
          if( ! _runPoolWatchDog )
            break;

          Thread.sleep( _period );

          runit();
        }
        catch (InterruptedException ex ){
          say("WatchPool Thread was interrupted");
          break;
        }
        catch (Exception ex) {
          say("WatchPool Thread got exception, continue");
          ex.printStackTrace();
        }
      } while( _runPoolWatchDog
               && ! _stopThreads );

      say("PoolWatch watch dog thread stopped");
      _db.setHeartBeat("PoolWatchDog", "stopped" );
    }

    public void runit() throws Exception {
      Set oldPoolSet;
      Set newPoolSet;
      List    poolList;
      boolean updated = false;
      String hbMsg;
      int releasedCount;

      // When WatchDog is restarted after some time,
      // get pool list from DB
      // and keep real pool status synchronized with with DB

      if( _restarted ) {
        _restarted    = false;
        _knownPoolSet = new HashSet();

        Iterator p = _db.getPools( );
        while (p.hasNext()) {
          String pool = p.next().toString();
          if( _db.getPoolStatus( pool ).equals( ReplicaDb1.ONLINE ) )
            _knownPoolSet.add( pool );
        }
        ((DbIterator) p).close();
      }

      poolList    = getPoolListResilient();

      newPoolSet  = new HashSet(poolList);
      oldPoolSet  = new HashSet(_knownPoolSet);

      for( Iterator i = poolList.iterator() ; i.hasNext() ; ) {
        Object inext = i.next();
        if (oldPoolSet.contains(inext)) { // remove common part from both sets
            oldPoolSet.remove(inext);
            newPoolSet.remove(inext);
        }
      }

      List arrived    = new ArrayList( newPoolSet ) ;
      List departed = new ArrayList( oldPoolSet ) ;

      if (   arrived.size()    == 0
          && departed.size() == 0 ) {
        hbMsg = "no changes";
        if ( ++cntNoChangeMsgLastTime < 5 )
          say("WatchPool - no pools arrived or departed");
        else if ( cntNoChangeMsgLastTime == 5 )
          say("WatchPool - no pools arrived or departed, throttle future 'no change' messages ");

      } else {
        hbMsg = "conf changed";
        cntNoChangeMsgLastTime = 0;

        if (arrived.size() == 0) {
          say("WatchPool - no new pools arrived");
        } else {
          for (Iterator i = arrived.iterator(); i.hasNext(); ) {
            Object inext = i.next();
            String poolName = (String) inext;
            say("WatchPool - pool arrived '" + poolName + "'");

            // Check if pool was "down" and bring it "online"
            // if and only if
            // Stay calm if it was "drainoff", "offline", "offline-prepare",
            //  or known to be "online" already
            synchronized (_dbLock) {
              String poolStatusOld = new String(_db.getPoolStatus(poolName));
              // can be "down" or "UNKNOWN" or who knows what else ("", null) ...
              // ... so explicitly check strings I want ignore
              if (!poolStatusOld.equals("drainoff")
                  && !poolStatusOld.equals("offline")
                  && !poolStatusOld.equals("offline-prepare")
                  && !poolStatusOld.equals("online")
                  ) {
                updatePool( (String) inext, ReplicaDb1.ONLINE, true);
                updated = true;
              }
            }
          }
        }

        if (departed.size() == 0)
          say("WatchPool - no pools departed");
        else {
          // For pools which left UPDATE pool status in DB
          // because
          //   if pool crashed, there will be no message
          //   if pool went down on node shut down,
          //      there will be no message until pool restarted manually
          //
          for (Iterator i = departed.iterator(); i.hasNext(); ) {
            Object inext = i.next();
            say("WatchPool - pool departed '" + inext + "'");
            synchronized (_dbLock) {
              updatePool( (String) inext, ReplicaDb1.DOWN, false);
              updated = true;
            }
          }
        }
      }

      // Release exculded files older then exclude expiration time
      releasedCount = 0;
      try {
        long now = System.currentTimeMillis();
        releasedCount = _db.releaseExcluded(now - getExcludedExpiration() );
      }
      catch (Exception ex) {
        ; // go on
      }

      if( updated || releasedCount > 0 ) {
        _dbUpdated.wakeup();
      }

      _knownPoolSet = new HashSet(poolList);

      _db.setHeartBeat("PoolWatchDog", hbMsg );
    }
  }

  private class dbUpdatePoolRunnable implements Runnable {
    String _poolName;

    public dbUpdatePoolRunnable(String poolName) {
      _poolName = poolName;
    }
    public void run() {
      synchronized (_dbLock) {
        try {
          dbUpdatePool(_poolName);
        }
        catch (Exception ee) {
          say(" poolStatusChanged - Problem fetching repository from " +
              _poolName + " : " + ee);
        }
        finally {
          _initDbActive = false;
        }
      }
      _dbUpdated.wakeup();
    }
  }
  //////////////////////////////////////////////////////////////////////////////
  // Callback functions
  //////////////////////////////////////////////////////////////////////////////

  //
  // Callback: File added to pool or removed from pool
  // -------------------------------------------------
  public void cacheLocationModified(
      PnfsModifyCacheLocationMessage msg,
      boolean wasAdded) {

    PnfsId pnfsId   = msg.getPnfsId();
    String poolName = msg.getPoolName();

    String strLocModified = "cacheLocationModified : pnfsID " + pnfsId
        + (wasAdded ? " added to" : " removed from")
        + " pool " + poolName ;

    if( _resilientPools == null ) {
        // _resilientPools was not set yet
        dsay(strLocModified);
        dsay("Resilient Pools List is not defined (yet), ignore file added/removed");
        return;
    } else if (_resilientPools.usePoolGroup()) {
        List l = _resilientPools.getResilientPools();
        if (l == null) {
            // usePoolGroup() == true, but we got 'null' list for resilient pools
            dsay(strLocModified);
            dsay("Resilient Pools List is not defined (yet), ignore file added/removed");
            return;
        } else if ( ! l.contains(poolName) ) { // pool is NOT resilient
            dsay(strLocModified);
            dsay("Pool " + poolName + " is not resilient pool, ignore file added/removed");
            return;
        }
    }

    say( strLocModified );

    synchronized (_dbLock) {

      //      if ( _initDbActive )
      //        return;

      if( ! _useDB ) {
        say("DB not ready yet, ignore ::" + strLocModified );
        return;
      }

      // DEBUG - check
      {
        StringBuffer sb = new StringBuffer();
        sb.append(printCacheLocation(pnfsId)).append("\n");
        dsay( "Pool list in DB before "
              +((wasAdded)?"Insertion":"Removal") +"\n"+
              "for pnfsId=" + pnfsId + " \n"
              +sb.toString() );
      }

      if (wasAdded) {
        _dbrmv2.addPool(pnfsId, poolName);
      } else {
        _dbrmv2.removePool(pnfsId, poolName);
      }

      dsay("cacheLocationModified, notify All");

    }
    _dbUpdated.wakeup();

    say("cacheLocationModified : pnfsID " + pnfsId
        + (wasAdded ? " added to" : " removed from") + " pool " + poolName
        + " - DB updated");
  }

  //////////////////////////////////////////////////////////////////////////////
  //
  // Callback: Pool went down or restarted
  // -------------------------------------
  protected void poolStatusChanged( PoolStatusChangedMessage msg ) {
    String msPool       = msg.getPoolName();
    String msPoolStatus = msg.getPoolStatus();
    String poolStatus;
    boolean doPoolInventory;
    String strStatusChanged = "Pool " +msPool +" status changed to "
        +msPoolStatus;

    if( _resilientPools == null ) {
        // _resilientPools was not set yet
        dsay(strStatusChanged);
        dsay("Resilient Pools List is not defined (yet), ignore pool status change");
        return;
    } else if (_resilientPools.usePoolGroup()) {
        List l = _resilientPools.getResilientPools();
        if (l == null) {
            // usePoolGroup() == true, but we got 'null' list for resilient pools
            dsay(strStatusChanged);
            dsay("Resilient Pools List is not defined (yet), ignore pool status change");
            return;
        } else if ( ! l.contains(msPool) ) { // pool is NOT resilient
            dsay(strStatusChanged);
            dsay("Pool " + msPool + " is not resilient pool, ignore pool status change");
            return;
        }
    }

    if ( msPoolStatus.equals("DOWN") )
      poolStatus = ReplicaDb1.DOWN;
    else if ( msPoolStatus.equals("UP") || msPoolStatus.equals("RESTART") )
      poolStatus = ReplicaDb1.ONLINE;
    else if ( msPoolStatus.equals("UNKNOWN") ) {
      poolStatus = ReplicaDb1.DOWN;
      say( "poolStatusChanged ERROR, pool " + msPool +
           " state changed to '" + msPoolStatus + "'"
           + " - set pool status to " + poolStatus ) ;
    }else{
      say( "poolStatusChanged ERROR, pool " + msPool +
           " state changed to unknown state '" + msPoolStatus + "'"
           + ", message ignored" ) ;
      return;
    }

    say( "poolStatusChanged, pool " + msPool +
         " state changed to '" + poolStatus + "'" ) ;

    String detailString = msg.getDetailMessage();

    if (_debug) {
      int pState = msg.getPoolState();
      PoolV2Mode pMode = msg.getPoolMode();
      int detailCode = msg.getDetailCode();

    dsay("PoolStatusChangedMessage msg=" + msg );
    //Again:
    dsay("pool_state=" + pState );
    dsay("pool_mode=" + ((pMode == null) ? "null" : pMode.toString()));
    dsay("detail_code=" + detailCode );
    dsay("detail_string=" + ((detailString == null) ? "null" : detailString) );
    // end DEBUG
    }

    boolean onReplicaMgrCommand = ( detailString != null )
        && detailString.equals("Replica Manager Command");

    if( onReplicaMgrCommand )
      dsay("pool status changed on RM command");

    String poolName = new String(msPool);
    /** @todo - do cleanup
     * string can be "offline" or "oflline-prepare" - it is set above
     */

    doPoolInventory = ( poolStatus.equals("online") ||
                        poolStatus.equals("offline") ||
                        poolStatus.equals("offline-prepare"));

    if ( ! onReplicaMgrCommand   // DB already set to drainoff | offline-prepare | offline
        || poolStatus.equals("online") ) {
      synchronized (_dbLock) {
        if( ! _useDB ) {
          say("DB not ready yet, skip DB update" );
          return;
        }
        updatePool(poolName, poolStatus, doPoolInventory);
      }

      _dbUpdated.wakeup();
    }

  }

  /**
   * updatePool()
   */
  private void updatePool(String poolName, String poolStatus,
                          boolean doPoolInventory) {

    synchronized (_dbLock) {

      String poolStatusOld = new String(_dbrmv2.getPoolStatus(poolName));

      if (poolStatusOld.equals("drainoff")) {
        say("poolStatusChanged, Pool '" + poolName + "' status is " +
            poolStatusOld
            + ", ignore pool status change messages");

      } else {
      say("poolStatusChanged, Pool '" + poolName + "' status was " +
          poolStatusOld);

      if (poolStatus.equals(ReplicaDb1.ONLINE))
        _poolsToWait.remove(poolName);

      if (!doPoolInventory) {
        // update DB only
        _dbrmv2.setPoolStatus(poolName, poolStatus);
        say("poolStatusChanged, pool " + poolName +
            " state change to '" + poolStatus + "' updated in DB, notify All");
      }
      else { // "RESTART" || "UP"
        // update pnfsId from this pool, and update DB
        _dbrmv2.setPoolStatus(poolName, ReplicaDbV1.OFFLINE);

        try {
          dbUpdatePool(poolName);
          _dbrmv2.setPoolStatus(poolName, poolStatus);
          say("poolStatusChanged, pool " + poolName +
              " state change to '" + poolStatus + "' updated in DB, notify All");
        }
        catch (Exception ee) {
          say(" poolStatusChanged - Problem fetching repository from " +
              poolName + " : " + ee);
          say(" poolStatusChanged - pool " + poolName + " stays '"
              +ReplicaDb1.OFFLINE+"'");
        }
      }

        synchronized (_poolsToWait) {
          if (_initDbRunnable != null
              && _initDbRunnable.isWaiting()
              && _poolsToWait.size() == 0) {
            _poolsToWait.notifyAll();
            dsay("Got all online pools back online, wakeup InitDB");
          }
        } // synchronized (_poolsToWait)
      } // synchronized (_dbLock)
    }
    if ( poolStatus.equals(ReplicaDb1.DOWN) ) {
      taskTearDownByPoolName( poolName );
    }
  }

  //////////////////////////////////////////////////////////////////////////////
  // Pool Remove Files message from Cleaner
  // - wipe out all pnfsID entries from replicas table

  protected void poolRemoveFiles( PoolRemoveFilesMessage msg )
  {
    // Ignore poolName: currently this is the same as cell name where message sent,
    //   that is "replicaManager"
    String poolName     = msg.getPoolName();
    String filesList[]  = msg.getFiles();
    String stringPnfsId = null;

    if( filesList == null ) {
      dsay("PoolRemoveFilesMessage - no file list defined");
      return;
    }

    // non strict check for number of pnfs cleared in DB:
    // - DB maybe waked up between locks, clearPools can get error
    // still wakeup db check
    int fileCount = 0;

    for( int j=0; j<filesList.length; j++ ){
      if(filesList[j] == null ) {
        dsay("ReplicaManager: pnfsid["+j+"]='null' in PoolRemoveFilesMessage");
      }else{
        stringPnfsId = filesList[j];

        PnfsId pnfsId;
        try {
          pnfsId = new PnfsId( stringPnfsId );
        }catch(IllegalArgumentException ex) {
          dsay("Can not construct pnfsId for '"+stringPnfsId+"'");
          continue;
        }

        synchronized (_dbLock) {
          if( ! _useDB ) {
            say("DB not ready yet, skip DB update" );
            return;
          }
          _dbrmv2.clearPools(pnfsId);
          fileCount++;
        }
        dsay("ReplicaManager: PoolRemoveFiles(): pnfsId["+j+"]=" + stringPnfsId +" cleared in DB");
      }
    }

    if( fileCount > 0 )
      _dbUpdated.wakeup();
  }


  //////////////////////////////////////////////////////////////////////////////
  // Task finished - Test

  public void taskFinished(TaskObserver task) {
    say("TaskFinished callback: task " + task);
    if (task.getType().equals("Reduction")) {
      ReductionObserver rt = (ReductionObserver) task;
      dsay("taskFinished() reduction " + rt.getPnfsId() + " at " +
          rt.getPool());
    }
    if (task.getType().equals("Replication")) {
      MoverTask mt = (MoverTask) task;
      dsay("taskFinished() replication " + mt.getPnfsId()
          + " from " + mt.getSrcPool()
          + " to   " + mt.getDstPool()
          );
    }
  }
}
