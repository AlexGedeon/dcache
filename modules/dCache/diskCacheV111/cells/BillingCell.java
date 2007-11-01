// $Id: BillingCell.java,v 1.21 2007-05-24 13:51:12 tigran Exp $

package diskCacheV111.cells ;

import java.util.* ;
import java.io.* ;
import java.text.* ;
import java.sql.SQLException ;

import dmg.util.* ;
import dmg.cells.nucleus.* ;

import diskCacheV111.vehicles.* ;
import diskCacheV111.util.* ;

public class BillingCell extends CellAdapter {

    private final CellNucleus _nucleus ;
    private final Args        _args ;
    private File        _billingDb = null ;
    private File        _currentDb = null ;
    private int         _requests  = 0 ;
    private int         _failed    = 0 ;
    private final Map<String, int[]>     _map       = new HashMap<String, int[]>() ;
    private final Map<String, long[]>     _poolStatistics = new HashMap<String, long[]>() ;
    private final Map<String, Map<String, long[]>>     _poolStorageMap = new HashMap<String, Map<String, long[]>>() ;
    private BillingDB   _sqlLog    = null;
    private long        _communicationTimeout = 40000L ;

    private int         _printMode = 0 ;

    private final static SimpleDateFormat formatter
         = new SimpleDateFormat ("MM.dd HH:mm:ss");
    private final static SimpleDateFormat fileNameFormat
         = new SimpleDateFormat( "yyyy.MM.dd" ) ;
    private final static SimpleDateFormat directoryNameFormat
         = new SimpleDateFormat( "yyyy"+File.separator+"MM" ) ;

    public BillingCell( String name , String  args ) throws Exception {
       super( name , BillingCell.class.getName(), args , false ) ;
       _nucleus = getNucleus() ;
       _args    = getArgs() ;
       try{
          if( _args.argc() < 1 )
            throw new
            IllegalArgumentException("Usage : ... <billingDb> -printMode=<n> [-timeout=<timeoutInSecound>] [-useSQL ...]") ;

          _billingDb = new File(_args.argv(0)) ;
          if( ( ! _billingDb.isDirectory() ) || ( ! _billingDb.canWrite() ) )
            throw new
            IllegalArgumentException("<"+_args.argv(0)+"> doesn't exits or not writeable") ;

          if( _args.getOpt("useSQL") != null ) {
             try{
                _sqlLog = new BillingDB(_args);
             }catch( SQLException sqe) {
                 esay("Can't Connect to SQL database : "+sqe.getMessage() ) ;
             }
          }
          String printModeString = _args.getOpt("printMode") ;
          if( printModeString != null ){
             try{
                 _printMode = Integer.parseInt( printModeString ) ;
             }catch(Exception eee ){
                 esay("PrintMode : Illegal printMode values : "+printModeString);
             }
          }
          say("Property PrintMode="+_printMode);

          String timeoutString = _args.getOpt("timeout") ;
          if( timeoutString != null ){
             try{
                 _communicationTimeout = Long.parseLong( timeoutString ) * 1000L ;
             }catch(Exception eee ){
                 esay("PrintMode : Illegal communication timeout value : "+timeoutString);
             }
          }
          say("Property timeout ="+_communicationTimeout);

       }catch(Exception e){
          start() ;
          kill() ;
          throw e ;
       }
       useInterpreter( true );
       start();
       export();
    }

    public Object ac_get_billing_info( Args args )throws Exception {
       synchronized( _map ){
          Object [][] result = new Object[_map.size()][] ;
          Iterator it = _map.entrySet().iterator() ;
          for( int i = 0 ; it.hasNext() && ( i < result.length ) ; i++ ){
             Map.Entry entry = (Map.Entry)it.next() ;
             int [] values = (int [])entry.getValue() ;
             result[i] = new Object[2] ;
             result[i][0] = entry.getKey() ;
             result[i][1] = new int[2] ;
             ((int [])(result[i][1]))[0] = values[0] ;
             ((int [])(result[i][1]))[1] = values[1] ;
          }
          return result ;
       }
    }
    public String hh_get_pool_statistics = "[<poolName>]" ;
    public  Object ac_get_pool_statistics_$_0_1( Args args )throws Exception {
       synchronized( _poolStatistics ){
          if( args.argc() == 0 )return _poolStatistics ;
          HashMap map = (HashMap)_poolStorageMap.get(args.argv(0)) ;
          return map == null ? new HashMap() : map ;
       }
    }
    public String hh_clear_pool_statistics = "" ;
    public Object ac_clear_pool_statistics( Args args ){
       _poolStatistics.clear() ;
       _poolStorageMap.clear() ;
       return "";
    }
    public String hh_dump_pool_statistics = "" ;
    public String ac_dump_pool_statistics_$_0_1( Args args ) throws Exception {
       dumpPoolStatistics( args.argc() == 0 ? null : args.argv(0) ) ;
       return "" ;
    }
    private void dumpPoolStatistics( String name ) throws Exception {
       name    = name == null ?
                 ( "poolFlow-"+fileNameFormat.format(new Date()) ) :
                 name ;
       File report = new File( _billingDb , name ) ;
       PrintWriter pw = new PrintWriter(
                          new BufferedWriter(
                             new FileWriter(report) ) );
       try{
         Set<Map.Entry<String, Map<String, long[]>>>     pools = _poolStorageMap.entrySet() ;

         for(Map.Entry<String, Map<String, long[]>> poolEntry :pools ){

           String   poolName = poolEntry.getKey();
           Map<String, long[]>  map      = poolEntry.getValue();

           for( Map.Entry<String, long[]>  statiEntry: map.entrySet()){
              String className = statiEntry.getKey();
              long []  counter = statiEntry.getValue();
              pw.print(poolName) ;
              pw.print("  ");
              pw.print(className) ;
              for( int i = 0 ; i < counter.length ; i++ ){
                 pw.print("  "+counter[i]) ;
              }
              pw.println("");
           }
        }
       }catch(Exception ee ){
           esay( "Exception in dumpPoolStatistics : "+ee ) ;
           report.delete() ;
           throw ee ;
       }finally{
           pw.close() ;
       }
       return ;
    }
    public String toString(){
       return "Req="+_requests+";Err="+_failed+";" ;
    }
    public void getInfo( PrintWriter pw ){
       pw.print( Formats.field("Requests",20,Formats.RIGHT) );
       pw.print(" : ") ;
       pw.print( Formats.field(""+_requests,6,Formats.RIGHT) ) ;
       pw.print(" / ") ;
       pw.println( Formats.field(""+_failed,6,Formats.LEFT) ) ;
       synchronized( _map ){
          Iterator i = _map.entrySet().iterator() ;
          while( i.hasNext() ){
             Map.Entry entry = (Map.Entry)i.next() ;
             int [] values = (int [])entry.getValue() ;
             pw.print( Formats.field(entry.getKey().toString(),20,Formats.RIGHT) );
             pw.print(" : ") ;
             pw.print( Formats.field(""+values[0],6,Formats.RIGHT) ) ;
             pw.print(" / ") ;
             pw.println( Formats.field(""+values[1],6,Formats.LEFT) ) ;
          }
       }
    }

    private void doStatistics( InfoMessage info ){
       if( info instanceof WarningPnfsFileInfoMessage )return ;
       String cellName = info.getCellName() ;
       int pos = 0 ;
       cellName = ( ( pos = cellName.indexOf("@") ) < 1 ) ?
                  cellName : cellName.substring(0,pos);
       String transactionType = info.getMessageType() ;
       synchronized( _poolStatistics ){
          long [] counters = (long [])_poolStatistics.get(cellName) ;
          if( counters == null )
	     _poolStatistics.put( cellName , counters = new long[4] ) ;

          if( info.getResultCode() != 0 ){
             counters[3]++ ;
          }else if( transactionType.equals("transfer") ){
             counters[0]++ ;
          }else if( transactionType.equals("restore") ){
             counters[1]++ ;
          }else if( transactionType.equals("store") ){
             counters[2]++;
          }
          if( ( info instanceof PnfsFileInfoMessage )  ){
             PnfsFileInfoMessage pnfsInfo = (PnfsFileInfoMessage)info ;
             StorageInfo sinfo = (pnfsInfo).getStorageInfo() ;
             if( sinfo != null ){
                Map<String, long[]> map = _poolStorageMap.get(cellName) ;
                if( map == null )
                   _poolStorageMap.put( cellName , map = new HashMap<String, long[]>() ) ;

                String key = sinfo.getStorageClass()+"@"+sinfo.getHsm() ;

                counters = (long [])map.get(key) ;

                if( counters == null )map.put( key , counters = new long[8] ) ;

                if( info.getResultCode() != 0 ){
                   counters[3]++ ;
                }else if( transactionType.equals("transfer") ){
                   counters[0]++ ;
                   MoverInfoMessage mim = (MoverInfoMessage)info ;
                   counters[mim.isFileCreated()?4:5] += mim.getDataTransferred();
                }else if( transactionType.equals("restore") ){
                   counters[1]++ ;
                   counters[6] += pnfsInfo.getFileSize() ;
                }else if( transactionType.equals("store") ){
                   counters[2]++;
                   counters[7] += pnfsInfo.getFileSize() ;
                }

             }
          }
       }
    }
    public void messageArrived( CellMessage msg ){
       Object obj = msg.getMessageObject() ;
       String output = null ;
       Date thisDate = null ;
       InfoMessage info = null ;
       if( obj instanceof InfoMessage ){
          info   = (InfoMessage)obj ;
          //
          // currently we have to ignore 'check'
          //
          if( info.getMessageType().equals("check") )return ;

          String      key    = info.getMessageType()+":"+info.getCellType() ;
          synchronized( _map ){
             int    []   values = (int [])_map.get( key ) ;

             if( values == null )_map.put( key , values = new int[2] ) ;

             values[0] ++ ;
             _requests ++ ;
             if( info.getResultCode() != 0 ){
                _failed ++ ;
                values[1] ++ ;
             }
          }
	  if( info.getCellType().equals("pool") )doStatistics( info ) ;
          thisDate = new Date( info.getTimestamp() ) ;
          output   = info.toString() ;
       }else{
          thisDate = new Date() ;
          output   = formatter.format(new Date())+" "+obj.toString() ;
       }
       pin( output ) ;
       if( _sqlLog != null ) {
          try {
             _sqlLog.log(info);
          }catch (SQLException sqe) {
             esay("Can't log billing into SQL database : "+sqe.getMessage() ) ;
             sqe.printStackTrace();
             say("Trying to reconnect");
             try{
                 _sqlLog = new BillingDB(_args);
             }catch( SQLException sqe2) {
                 esay("Can't Connect to SQL database : "+sqe2.getMessage() ) ;
             }
          } catch (Exception ex) {
             esay("Billing into SQL database failed: "+ex.getMessage() ) ;
             ex.printStackTrace();
          }
       }
       String fileNameExtention = null ;
       if( _printMode == 0 ){
           _currentDb = _billingDb ;
           fileNameExtention = fileNameFormat.format(thisDate) ;
       }else{
           Date date = new Date() ;
           _currentDb  = new File( _billingDb , directoryNameFormat.format( date ) ) ;
           if( ! _currentDb.exists() )_currentDb.mkdirs() ;
           fileNameExtention = fileNameFormat.format( date ) ;
       }

       File outputFile = new File( _currentDb , "billing-"+ fileNameExtention );
       File errorFile  = new File( _currentDb , "billing-error-"+ fileNameExtention ) ;

       try{
          PrintWriter pw = new PrintWriter( new FileWriter( outputFile , true ));
          try{
             pw.println(output);
          }finally{
             pw.close() ;
          }
       }catch(Exception ee){
          esay("Can't write billing ["+outputFile+"] : "+ee.toString() ) ;
       }
       //
       // exclude check
       //
       if( ( info != null ) &&
           ( info.getResultCode() != 0 ) &&
           ! info.getMessageType().equals("check") ){

          try{
             PrintWriter pw = new PrintWriter( new FileWriter( errorFile , true ));
             try{
                pw.println(output);
             }finally{
                pw.close() ;
             }
          }catch(Exception ee){
             esay("Can't write billing-error : "+ee.toString() ) ;
          }
       }
    }
    public String hh_get_poolstatus = "[<fileName>]" ;
    public String ac_get_poolstatus_$_0_1( Args args ) throws Exception {
       CollectPoolStatus status =
            new CollectPoolStatus( args.argc() > 0 ? args.argv(0) : null ) ;
       return status.getReportFile().toString()  ;

    }
    private class CollectPoolStatus implements Runnable {
       private File _report = null ;

       private CollectPoolStatus( String name )  {
          name    = name == null ?
                    ( "poolStatus-"+fileNameFormat.format(new Date()) ) :
                    name ;
          _report = new File( _billingDb , name ) ;
          _nucleus.newThread( this , "poolStatus-"+name ).start() ;
       }
       public File getReportFile(){ return _report ; }
       public void run(){
           PrintWriter pw = null ;

           try{
              pw = new PrintWriter(
                              new BufferedWriter(
                                 new FileWriter(_report) ) );

           }catch(IOException ioe ){
              esay("Problem opening "+_report+" : "+ioe.getMessage() ) ;
              return ;
           }
           try{
              CellMessage m = new CellMessage( new CellPath("PoolManager") ,
                                               "xgetcellinfo" ) ;
              m = _nucleus.sendAndWait( m , _communicationTimeout ) ;
              if( m == null )
                 throw new
                 Exception("xgetcellinfo timed out" );

              Object o = m.getMessageObject() ;
              if( ! ( o instanceof diskCacheV111.poolManager.PoolManagerCellInfo ) )
                 throw new
                 Exception( "Illegal Reply from PoolManager : "+o.getClass().getName()) ;

              diskCacheV111.poolManager.PoolManagerCellInfo info
                   = (diskCacheV111.poolManager.PoolManagerCellInfo)o ;
              String [] poolList = info.getPoolList() ;
              String line = null ;
              for( int i = 0 ; i < poolList.length ; i++ ){
                 m = new CellMessage( new CellPath(poolList[i]) , "rep ls -s" ) ;
                 try{
                    m = _nucleus.sendAndWait(m,_communicationTimeout) ;
                    if( m == null ){
                        esay("CollectPoolStatus : pool status (rep ls -s) of "+poolList[i]+" didn't arrive in time (skipped)");
                        continue ;
                    }
                    BufferedReader br = new BufferedReader(
                                          new StringReader(
                                              m.getMessageObject().toString() ) ) ;
                    while( ( line = br.readLine() ) != null ){
                       pw.println( poolList[i]+"  "+line ) ;
                    }

                 }catch(Exception eee ){
                    esay("CollectPoolStatus : "+poolList[i]+" : "+eee ) ;
                    continue ;
                 }
              }
           }catch(Exception ee ){
              esay( "Exception in CollectPools status : "+ee ) ;
              _report.delete() ;
              return ;
           }finally{
              pw.close() ;
           }
       }
    }
}
