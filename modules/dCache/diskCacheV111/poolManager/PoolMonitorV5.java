// $Id: PoolMonitorV5.java,v 1.32 2007-08-01 20:00:45 tigran Exp $

package diskCacheV111.poolManager ;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.SpreadAndWait;
import diskCacheV111.vehicles.IpProtocolInfo;
import diskCacheV111.vehicles.PoolCheckCostMessage;
import diskCacheV111.vehicles.PoolCheckFileMessage;
import diskCacheV111.vehicles.PoolCheckable;
import diskCacheV111.vehicles.PoolCostCheckable;
import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.StorageInfo;

public class PoolMonitorV5 {

   private long              _poolTimeout   = 15 * 1000;
   private final CellAdapter       _cell          ;
   private final PoolSelectionUnit _selectionUnit ;
   private final PnfsHandler       _pnfsHandler   ;
   private final CostModule        _costModule    ;
   private double            _maxWriteCost          = 1000000.0;
   private boolean           _verbose               = true ;
   private final PartitionManager  _partitionManager ;

   public PoolMonitorV5( CellAdapter cell ,
                         PoolSelectionUnit selectionUnit ,
                         PnfsHandler  pnfsHandler ,
                         CostModule   costModule ,
                         PartitionManager partitionManager ){

      _cell             = cell ;
      _selectionUnit    = selectionUnit ;
      _pnfsHandler      = pnfsHandler ;
      _costModule       = costModule ;
      _partitionManager = partitionManager ;

   }
   public void messageToCostModule( CellMessage cellMessage ){
      _costModule.messageArrived(cellMessage);
   }
   public void setPoolTimeout( long poolTimeout ){
      _poolTimeout = poolTimeout ;
   }
   /*
   public void setSpaceCost( double spaceCost ){
      _spaceCostFactor = spaceCost ;
   }
   public void setPerformanceCost( double performanceCost ){
      _performanceCostFactor = performanceCost ;
   }*/
   public long getPoolTimeout(){ return _poolTimeout ;}
    // output[0] -> Allowed and Available
    // output[1] -> available but not allowed (sorted, cpu)
    // output[2] -> allowed but not available (sorted, cpu + space)
    // output[3] -> pools from pnfs
    // output[4] -> List of List (all allowed pools)
   public PnfsFileLocation getPnfsFileLocation(
                               PnfsId pnfsId ,
                               StorageInfo storageInfo ,
                               ProtocolInfo protocolInfo, String linkGroup){

      return new PnfsFileLocation( pnfsId, storageInfo ,protocolInfo , linkGroup) ;

   }
   public class PnfsFileLocation {

      private List<PoolManagerParameter> _listOfPartitions;
      private List<List<PoolCostCheckable>> _allowedAndAvailableMatrix;
      private List<PoolCostCheckable> _acknowledgedPnfsPools;
      private int  _allowedPoolCount          = 0 ;
      private int  _availablePoolCount        = 0 ;
      private boolean  _calculationDone       = false ;

      private final PnfsId       _pnfsId       ;
      private final StorageInfo  _storageInfo  ;
      private final ProtocolInfo _protocolInfo ;
      private final String _linkGroup          ;

      //private PoolManagerParameter _recentParameter = _partitionManager.getParameterCopyOf()  ;

      private PnfsFileLocation( PnfsId pnfsId ,
                                StorageInfo storageInfo ,
                                ProtocolInfo protocolInfo ,
                                String linkGroup){

         _pnfsId       = pnfsId ;
         _storageInfo  = storageInfo ;
         _protocolInfo = protocolInfo ;
         _linkGroup    = linkGroup;
      }

       public List<PoolManagerParameter> getListOfParameter()
       {
           return _listOfPartitions;
       }

      public void clear(){
          _allowedAndAvailableMatrix = null ;
          _calculationDone           = false ;
      }

       public PoolManagerParameter getCurrentParameterSet()
       {
           return _listOfPartitions.get(0);
       }

       public List getAllowedButNotAvailable()
       {
           return null;
       }

       public List<PoolCostCheckable> getAcknowledgedPnfsPools()
           throws CacheException, InterruptedException
       {
           if (_acknowledgedPnfsPools == null)
               calculateFileAvailableMatrix();
           return _acknowledgedPnfsPools;
       }

       public int getAllowedPoolCount()
       {
           return _allowedPoolCount;
       }

       public int getAvailablePoolCount()
       {
           return _availablePoolCount;
       }

       public List<List<PoolCostCheckable>> getFileAvailableMatrix()
           throws CacheException, InterruptedException
       {
           if (_allowedAndAvailableMatrix == null)
               calculateFileAvailableMatrix();
           return _allowedAndAvailableMatrix;
       }
      //
      //   getFileAvailableList
      //  -------------------------
      //
      //  expected  = getPoolsFromPnfs() ;
      //  allowed[] = getAllowedFromConfiguration() ;
      //
      //   +----------------------------------------------------+
      //   |  for i in  0,1,2,3,...                             |
      //   |     result = intersection( expected , allowed[i] ) |
      //   |     found  = CheckFileInPool( result)              |
      //   |     if( found > 0 )break                           |
      //   |     if( ! allowFallbackOnCost )break               |
      //   |     if( minCost( found ) < MAX_COST )break         |
      //   +----------------------------------------------------+
      //   |                  found == 0                        |
      //   |                      |                             |
      //   |        yes           |             NO              |
      //   |----------------------|-----------------------------|
      //   | output[0] = empty    | [0] = SortCpuCost(found)    |
      //   | output[1] = null     | [1] = null                  |
      //   | output[2] = null     | [2] = null                  |
      //   | output[3] = expected | [3] = expected              |
      //   | output[4] = allowed  | [4] = allowed               |
      //   +----------------------------------------------------+
      //
      //   preparePool2Pool
      //  -------------------------
      //
      //  output[1] = SortCpuCost( CheckFileInPool( expected ) )
      //
      //   +----------------------------------------------------+
      //   |                   output[0] > 0                    |
      //   |                                                    |
      //   |        yes              |             NO           |
      //   |-------------------------|--------------------------|
      //   | veto = Hash( output[0] )|                          |
      //   |-------------------------|                          |
      //   |for i in  0,1,2,3,.      | for i in  0,1,2,3,.      |
      //   |  tmp = allowed[i]-veto  |   if(allowed[i]==0)cont  |
      //   |  if( tmp == 0 )continue |                          |
      //   |  out[2] =               |   out[2] =               |
      //   |   SortCost(getCost(tmp))|     SortCost(getCost(    |
      //   |                         |        allowed[i]))      |
      //   |   break                 |   break                  |
      //   +----------------------------------------------------+
      //   |if(out[2] == 0)          |if(out[2] == 0)           |
      //   |    out[2] = out[0]      |    out[2] = empty        |
      //   +----------------------------------------------------+
      //
       /*
        *   Input : storage info , pnfsid
        *   Output :
        *             _acknowledgedPnfsPools
        *             _allowedAndAvailableMatrix
        *             _allowedAndAvailable
        */
       private void calculateFileAvailableMatrix()
           throws CacheException, InterruptedException
       {

         if( _storageInfo == null )
            throw new
            CacheException(189,"Storage Info not available");

         String hsm          = _storageInfo.getHsm() ;
         String storageClass = _storageInfo.getStorageClass() ;
         String cacheClass   = _storageInfo.getCacheClass() ;
         String hostName     = _protocolInfo instanceof IpProtocolInfo  ?((IpProtocolInfo)_protocolInfo).getHosts()[0] : null ;
         String protocolString = _protocolInfo.getProtocol() + "/" + _protocolInfo.getMajorVersion() ;
         //
         // will ask the PnfsManager for a hint
         // about the pool locations of this
         // pnfsId. Returns an enumeration of
         // the possible pools.
         //
         List<String> expectedFromPnfs = _pnfsHandler.getCacheLocations( _pnfsId ) ;
         say( "calculateFileAvailableMatrix _expectedFromPnfs : "+expectedFromPnfs ) ;
         //
         // check if pools are up and file is really there.
         // (returns unsorted list of costs)
         //
         _acknowledgedPnfsPools =
             queryPoolsForPnfsId(expectedFromPnfs.iterator(), _pnfsId, 0,
                                 _protocolInfo.isFileCheckRequired());
         say( "calculateFileAvailableMatrix _acknowledgedPnfsPools : "+_acknowledgedPnfsPools ) ;
         Map<String, PoolCostCheckable> availableHash =
             new HashMap<String, PoolCostCheckable>() ;
         for( PoolCostCheckable cost: _acknowledgedPnfsPools ){
            availableHash.put( cost.getPoolName() , cost ) ;
         }
         //
         //  get the prioritized list of allowed pools for this
         //  request. (We are only allowed to use the level-1
         //  pools.
         //
         PoolPreferenceLevel [] level =
             _selectionUnit.match( "read" ,
                                   storageClass+"@"+hsm ,
                                   cacheClass ,
                                   hostName ,
                                   protocolString ,
                                   _storageInfo,
                                   _linkGroup ) ;

         _listOfPartitions          = new ArrayList<PoolManagerParameter>();
         _allowedAndAvailableMatrix = new ArrayList<List<PoolCostCheckable>>();
         _allowedPoolCount          = 0 ;
         _availablePoolCount        = 0 ;

         for( int prio = 0 ; prio < level.length ; prio++ ){

            List<String> poolList = level[prio].getPoolList() ;
            //
            //
            PoolManagerParameter parameter = _partitionManager.getParameterCopyOf(level[prio].getTag()) ;
            _listOfPartitions.add(  parameter ) ;
            //
            // get the allowed pools for this level and
            // and add them to the result list only if
            // they are really available.
            //
            say( "calculateFileAvailableMatrix : db matrix[*,"+prio+"] "+poolList);

            List<PoolCostCheckable> result =
                new ArrayList<PoolCostCheckable>(poolList.size());
            for (String poolName : poolList) {
                PoolCostCheckable cost;
                if ((cost = availableHash.get(poolName)) != null) {
                    result.add(cost);
                    _availablePoolCount++;
                }
                _allowedPoolCount++;
            }

            sortByCost(result, false, parameter);

            say("calculateFileAvailableMatrix : av matrix[*," + prio + "] "
                + result);

            _allowedAndAvailableMatrix.add(result);
         }
         //
         // just in case, let us define a default parameter set
         //
         if( _listOfPartitions.size() == 0 )_listOfPartitions.add( _partitionManager.getParameterCopyOf() ) ;
         //
         _calculationDone = true ;
         return  ;
      }

       public List<PoolCostCheckable> getCostSortedAvailable()
           throws CacheException, InterruptedException
       {
           //
           // here we don't now exactly which parameter set to use.
           //
           if (!_calculationDone)
               calculateFileAvailableMatrix();
           List<PoolCostCheckable> list =
               new ArrayList<PoolCostCheckable>(getAcknowledgedPnfsPools());
           sortByCost(list, false);
           return list;
       }

       public List<List<PoolCostCheckable>>
           getStagePoolMatrix(StorageInfo  storageInfo,
                              ProtocolInfo protocolInfo,
                              long         filesize)
           throws CacheException, InterruptedException
       {
           return getFetchPoolMatrix("cache",
                                     storageInfo,
                                     protocolInfo,
                                     filesize);
       }

       public List<List<PoolCostCheckable>>
           getFetchPoolMatrix(String       mode ,        /* cache, p2p */
                              StorageInfo  storageInfo ,
                              ProtocolInfo protocolInfo ,
                              long         filesize  )
           throws CacheException, InterruptedException
       {

         String hsm          = storageInfo.getHsm() ;
         String storageClass = storageInfo.getStorageClass() ;
         String cacheClass   = storageInfo.getCacheClass() ;
         String hostName     =
                    protocolInfo instanceof IpProtocolInfo ?
                    ((IpProtocolInfo)protocolInfo).getHosts()[0] :
                    null ;


         PoolPreferenceLevel [] level =
             _selectionUnit.match( mode ,
                                   storageClass+"@"+hsm ,
                                   cacheClass ,
                                   hostName ,
                                   protocolInfo.getProtocol()+"/"+protocolInfo.getMajorVersion() ,
                                   storageInfo,
                                   _linkGroup) ;
         //
         //
         if( level.length == 0 )return new ArrayList<List<PoolCostCheckable>>() ;

         //
         // Copy the matrix into a linear HashMap(keys).
         // Exclude pools which contain the file.
         //
         List<PoolCostCheckable> acknowledged =
             getAcknowledgedPnfsPools();
         Map<String, PoolCostCheckable> poolMap =
             new HashMap<String,PoolCostCheckable>();
         Set<String> poolAvailableSet =
             new HashSet<String>();
         for (PoolCheckable pool : acknowledged)
             poolAvailableSet.add(pool.getPoolName());
         for (int prio = 0; prio < level.length; prio++) {
            for (String poolName : level[prio].getPoolList()) {
               //
               // skip if pool already contains the file.
               //
               if (poolAvailableSet.contains(poolName))
                   continue;

               poolMap.put(poolName, null);
            }
         }
         //
         // Add the costs to the pool list.
         //
         for (PoolCostCheckable cost :
                  queryPoolsForCost(poolMap.keySet().iterator(), filesize)) {
             poolMap.put(cost.getPoolName(), cost);
         }
         //
         // Build a new matrix containing the Costs.
         //
         _listOfPartitions = new ArrayList<PoolManagerParameter>();
         List<List<PoolCostCheckable>> costMatrix =
             new ArrayList<List<PoolCostCheckable>>();
         for (int prio = 0; prio < level.length; prio++) {
             //
             // skip empty level
             //
             PoolManagerParameter parameter =
                 _partitionManager.getParameterCopyOf(level[prio].getTag());
            _listOfPartitions.add(parameter);

             List<String> poolList = level[prio].getPoolList() ;
             if( poolList.size() == 0 )continue ;

             List<PoolCostCheckable> row = new ArrayList<PoolCostCheckable>();
             for (String pool : poolList) {
                PoolCostCheckable cost = poolMap.get(pool);
                if (cost != null)
                    row.add(cost);
             }
             //
             // skip if non of the pools is available
             //
             if( row.size() == 0 )continue ;
             //
             // sort according to (cpu & space) cost
             //
             sortByCost( row , true , parameter ) ;
             //
             // and add it to the matrix
             //
             costMatrix.add( row ) ;
         }

         return costMatrix ;
      }
      private void say(String message ){
         if( _verbose )_cell.say("PFL ["+_pnfsId+"] : "+message) ;
      }

       public List<PoolCostCheckable> getStorePoolList(long filesize)
           throws CacheException, InterruptedException
       {
           return getStorePoolList(_storageInfo, _protocolInfo, filesize);
       }

       private List<PoolCostCheckable>
           getStorePoolList(StorageInfo  storageInfo,
                            ProtocolInfo protocolInfo,
                            long         filesize)
           throws CacheException, InterruptedException
       {
         String hsm          = storageInfo.getHsm() ;
         String storageClass = storageInfo.getStorageClass() ;
         String cacheClass   = storageInfo.getCacheClass() ;
         String  hostName    =
                    protocolInfo instanceof IpProtocolInfo ?
                    ((IpProtocolInfo)protocolInfo).getHosts()[0] :
                    null ;
         int  maxDepth      = 9999 ;
         PoolPreferenceLevel [] level =
             _selectionUnit.match( "write" ,
                                   storageClass+"@"+hsm ,
                                   cacheClass ,
                                   hostName ,
                                   protocolInfo.getProtocol()+"/"+protocolInfo.getMajorVersion() ,
                                   storageInfo,
                                   _linkGroup ) ;
         //
         // this is the final knock out.
         //
         if( level.length == 0 )
            throw new
            CacheException( 19 ,
                             "No write pools configured for <"+
                             storageClass+"@"+hsm+">" ) ;

         List<PoolCostCheckable> costs = null ;

         PoolManagerParameter parameter = null ;

         for( int prio = 0 ; prio < Math.min( maxDepth , level.length ) ; prio++ ){

            costs     = queryPoolsForCost( level[prio].getPoolList().iterator() , filesize ) ;

            parameter = _partitionManager.getParameterCopyOf(level[prio].getTag()) ;

            if( costs.size() != 0 )break ;
         }

         if( costs == null || costs.size() == 0 )
            throw new
            CacheException( 20 ,
                            "No write pool available for <"+
                            storageClass+"@"+hsm+">" ) ;

         sortByCost( costs , true , parameter ) ;

         PoolCostCheckable check = costs.get(0) ;

         double lowestCost = calculateCost( check , true , parameter ) ;

         if( lowestCost  > _maxWriteCost )
             throw new
             CacheException( 21 , "Best pool <"+check.getPoolName()+
                                  "> too high : "+lowestCost ) ;

         return costs ;
      }

       public void sortByCost(List<PoolCostCheckable> list, boolean cpuAndSize)
       {
           sortByCost(list, cpuAndSize, getCurrentParameterSet());
       }

       private void sortByCost(List<PoolCostCheckable> list, boolean cpuAndSize,
                               PoolManagerParameter parameter)
       {
           ssortByCost(list, cpuAndSize, parameter);
       }
   }

    public void ssortByCost(List<PoolCostCheckable> list, boolean cpuAndSize,
                            PoolManagerParameter parameter)
    {
        if( ( parameter._performanceCostFactor == 0.0 ) &&
            ( parameter._spaceCostFactor == 0.0       )     ){

            Collections.shuffle( list ) ;
        }else{
            Collections.sort( list , new CostComparator( cpuAndSize , parameter ) ) ;
        }
    }

    public Comparator<PoolCostCheckable>
        getCostComparator(boolean both, PoolManagerParameter parameter)
    {
        return new CostComparator(both, parameter);
    }

   public class CostComparator implements Comparator<PoolCostCheckable> {

       private boolean              _useBoth = true ;
       private PoolManagerParameter _para    = null ;
       private CostComparator( boolean useBoth , PoolManagerParameter para ){
         _useBoth = useBoth ;
         _para    = para ;
       }
       public int compare(PoolCostCheckable check1, PoolCostCheckable check2)
       {
          Double d1 = new Double( calculateCost( check1 , _useBoth , _para ) ) ;
          Double d2 = new Double( calculateCost( check2 , _useBoth , _para ) ) ;
          int c = d1.compareTo( d2 ) ;
          if( c != 0 )return c ;
          return check1.getPoolName().compareTo( check2.getPoolName() ) ;
       }
    }
    public double calculateCost( PoolCostCheckable checkable , boolean useBoth , PoolManagerParameter para ){
       if( useBoth ){
          return Math.abs(checkable.getSpaceCost())       * para._spaceCostFactor +
                 Math.abs(checkable.getPerformanceCost()) * para._performanceCostFactor ;
       }else{
          return Math.abs(checkable.getPerformanceCost()) * para._performanceCostFactor ;
       }
    }
    /*
    public double getMinPerformanceCost( List list ){
       double cost = 1000000.0 ;
       for( int i = 0 ; i < list.size() ; i++ ){
          double x = ((PoolCostCheckable)(list.get(i))).getPerformanceCost() ;
          cost = Math.min( cost , x ) ;
       }
       return cost ;
    }
    */
    //------------------------------------------------------------------------------
    //
    //  'queryPoolsForPnfsId' sends PoolCheckFileMessages to all pools
    //  specified in the pool iterator. It waits until all replies
    //  have arrived, the global timeout has expired or the thread
    //  was interrupted.
    //

    private List<PoolCostCheckable> queryPoolsForPnfsId(Iterator<String> pools,
                                                        PnfsId pnfsId,
                                                        long filesize,
                                                        boolean checkFileExistence)
        throws InterruptedException
    {
        List<PoolCostCheckable> list = new ArrayList<PoolCostCheckable>();

        if (checkFileExistence) {

            SpreadAndWait control = new SpreadAndWait(_cell.getNucleus(),
                    _poolTimeout);

            while (pools.hasNext()) {

                String poolName = pools.next();
                //
                // deselection inactive and disabled pools
                //
                PoolSelectionUnit.SelectionPool pool = _selectionUnit
                        .getPool(poolName);
                if ((pool == null) || !pool.canRead() || !pool.isActive())
                    continue;

                _cell.say("queryPoolsForPnfsId : PoolCheckFileRequest to : "
                        + poolName);
                //
                // send query
                //
                CellMessage cellMessage = new CellMessage(
                        new CellPath(poolName), new PoolCheckFileMessage(
                                poolName, pnfsId));

                try {
                    control.send(cellMessage);
                } catch (Exception exc) {
                    //
                    // here we don't care about exceptions
                    //
                    _cell.esay("Exception sending PoolCheckFileRequest to "
                            + poolName + " : " + exc);
                }
            }

            //
            // scan the replies
            //
            CellMessage answer = null;

            while ((answer = control.next()) != null) {

                Object message = answer.getMessageObject();

                if (!(message instanceof PoolCheckFileMessage)) {
                    _cell
                            .esay("queryPoolsForPnfsId : Unexpected message from ("
                                    + answer.getSourcePath()
                                    + ") "
                                    + message.getClass());
                    continue;
                }

                PoolCheckFileMessage poolMessage =
                    (PoolCheckFileMessage) message;
                _cell.say("queryPoolsForPnfsId : reply : " + poolMessage);

                boolean have = poolMessage.getHave();
                boolean waiting = poolMessage.getWaiting();
                String poolName = poolMessage.getPoolName();
                if (have || waiting) {

                    PoolCheckAdapter check = new PoolCheckAdapter(_costModule
                            .getPoolCost(poolName, filesize));
                    check.setHave(have);
                    check.setPnfsId(pnfsId);

                    list.add(check);
                    _cell.say("queryPoolsForPnfsId : returning : " + check);
                } else if (poolMessage.getReturnCode() == 0) {
                    _cell
                            .esay("queryPoolsForPnfsId : clearingCacheLocation for pnfsId "
                                    + pnfsId + " at pool " + poolName);
                    _pnfsHandler.clearCacheLocation(pnfsId, poolName);
                }
            }

        } else {

            while ( pools.hasNext() ) {

                String poolName = pools.next();
                PoolCheckAdapter check = new PoolCheckAdapter(_costModule
                        .getPoolCost(poolName, filesize));
                check.setHave(true);
                check.setPnfsId(pnfsId);

                list.add(check);
            }

        }

        _cell.say("queryPoolsForPnfsId : number of valid replies : "
                + list.size());
        return list;

    }
    public List<PoolCostCheckable>
        queryPoolsByLinkName(String linkName, long filesize)
        throws InterruptedException
    {
       List<String> pools = new ArrayList<String>() ;

       PoolSelectionUnit.SelectionLink link = _selectionUnit.getLinkByName( linkName ) ;
       PoolManagerParameter       parameter = _partitionManager.getParameterCopyOf( link.getTag()  ) ;

       for( Iterator<PoolSelectionUnit.SelectionPool> i = link.pools() ; i.hasNext() ; ){
          pools.add( i.next().getName() ) ;
       }

       List<PoolCostCheckable> list =
           queryPoolsForCost( pools.iterator() , filesize ) ;

       ssortByCost( list , true , parameter ) ;

       return list ;
    }
    private boolean _dontAskForCost = true ;
    private List<PoolCostCheckable> queryPoolsForCost(Iterator<String> pools,
                                                      long filesize)
        throws InterruptedException
    {
        List<PoolCostCheckable> list = new ArrayList<PoolCostCheckable>();
        SpreadAndWait control =
              new SpreadAndWait( _cell.getNucleus() , _poolTimeout ) ;

	while( pools.hasNext() ){

	    String poolName = pools.next();
            PoolCostCheckable costCheck = _costModule.getPoolCost( poolName , filesize ) ;
            if( costCheck != null ){
               list.add( costCheck ) ;
               _cell.say( "queryPoolsForCost : costModule : "+poolName+" ("+filesize+") "+costCheck);
            }else{
               //
               // send query
               //
               if( _dontAskForCost )continue ;
	       CellMessage  cellMessage =
                      new CellMessage(  new CellPath(poolName),
                                        new PoolCheckCostMessage(poolName,filesize)
                                     );

               _cell.say( "queryPoolsForCost : "+poolName+" query sent");
	       try{
                  control.send( cellMessage ) ;
	       }catch(Exception exc){
                  //
                  // here we don't care about exceptions
                  //
	          _cell.esay ("queryPoolsForCost : Exception sending PoolCheckFileRequest to "+poolName+" : "+exc);
	       }
            }

        }

        if( _dontAskForCost )return list ;

        //
        // scan the replies
        //
        CellMessage answer = null ;

        while( ( answer = control.next() ) != null ){

           Object message = answer.getMessageObject();

	   if( ! ( message instanceof PoolCostCheckable )){
	      _cell.esay("queryPoolsForCost : Unexpected message from ("+
                   answer.getSourcePath()+") "+message.getClass());
              continue ;
	   }
	   PoolCostCheckable poolMessage = (PoolCostCheckable)message;
           _cell.say( "queryPoolsForCost : reply : "+poolMessage ) ;
           list.add( poolMessage ) ;
        }
        _cell.say( "queryPoolsForCost : number of valid replies : "+list.size() );
        return list ;
    }

}
