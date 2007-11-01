// $Id: CostCalculationV5.java,v 1.10 2005-10-04 09:10:45 patrick Exp $
//
package diskCacheV111.pools ;
import  java.util.* ;

public class      CostCalculationV5 
       implements CostCalculatable,
                  java.io.Serializable  {

    private PoolCostInfo      _info = null ;
    private double _performanceCost = 0.0 ;
    private double _spaceCost       = 0.0 ;
    private long   _spaceCut        = 60 ;
    private PoolCostInfo.PoolSpaceInfo _space = null ;
    
    private static final long serialVersionUID = 1466064905628901498L;
    
    public CostCalculationV5( PoolCostInfo info ){
       _info  = info ;
       _space = _info.getSpaceInfo() ;
    }
    public double getSpaceCost(){
       return _spaceCost ;
    }
    public double getPerformanceCost(){
       return _performanceCost ;
    }
    private double recalculateV4( long filesize ){
//       System.out.println("calling recalculate V4");    
       if( filesize < _space.getFreeSpace() ){
       
          return  ((double)filesize) / 
                  ((double)_space.getFreeSpace() ) / 
                           _space.getBreakEven()  ;
                           
       }else if( _space.getRemovableSpace() < _space.getGap() ){
       
          return (double)200000000.0 ;
          
       }else{
       
	  return  ((double)filesize) / 
                  ((double)(
                    _space.getRemovableSpace()+_space.getFreeSpace() )) ; 
                    
       }
    }
    private double recalculateV5( long filesize , long lru ){

       double SPACECOST_AFTER_ONE_WEEK = (double)_space.getBreakEven() ;     
       double spaceFactor = SPACECOST_AFTER_ONE_WEEK * (double)(24*7*3600) ;
//       System.out.println("calling recalculate V5 "+SPACECOST_AFTER_ONE_WEEK+" "+filesize+" "+lru);    

//       if( filesize < _space.getFreeSpace() ){
       if(  _space.getFreeSpace() > _space.getGap() ){

          return  ((double)filesize) / 
                  ((double)_space.getFreeSpace()) ; 


       }else if( _space.getRemovableSpace() < _space.getGap() ){
       
          return (double)200000000.0 ;
          
       }else{

          return 1.0 +  
                      spaceFactor /
                       (double) Math.max( lru , _spaceCut ) ;

       }
    
    }
    public void recalculate( long filesize ){
          
       filesize = 3 * Math.max( filesize , 50 * 1000 * 1000 ) ;

       long lru = _space.getLRUSeconds() ;
       
       _spaceCost = _space.getBreakEven() >= 1.0 ?
                    recalculateV4( filesize ) : 
                    recalculateV5( filesize , lru ) ;

//       System.out.println("Calculated space cost : "+_spaceCost);
       
       double cost = 0.0 ;
       double div  = 0.0 ;

       PoolCostInfo.PoolQueueInfo queue = null ;

       Map map = _info.getExtendedMoverHash() ;
       
       PoolCostInfo.PoolQueueInfo [] q = {
       
          map == null ? _info.getMoverQueue() : null ,
          _info.getP2pQueue() ,
          _info.getP2pClientQueue() ,
          _info.getStoreQueue() ,
          _info.getRestoreQueue() 
       
       };
       for( int i = 0 ; i < q.length ; i++ ){
       
          queue = q[i] ;
          
          if( ( queue != null ) && ( queue.getMaxActive() > 0 ) ){
            cost += ( (double)queue.getQueued() +
                      (double)queue.getActive()  ) /
                      (double)queue.getMaxActive() ;
            div += 1.0 ;
//            System.out.println("DEBUG : top "+cost+" "+div);
          }
          
       }
       if( map != null )
       for( Iterator it = map.values().iterator() ; it.hasNext() ; ){
          queue = (PoolCostInfo.PoolQueueInfo)it.next() ;
          if( ( queue != null ) && ( queue.getMaxActive() > 0 ) ){
            cost += ( (double)queue.getQueued() +
                      (double)queue.getActive()  ) /
                      (double)queue.getMaxActive() ;
            div += 1.0 ;
//            System.out.println("DEBUG : top "+cost+" "+div);
          }
       }
       _performanceCost = div > (double) 0.0 ? cost / div : (double)1000000.0 ;
//       System.out.println("Calculation : "+_info+" -> cpu="+_performanceCost);

       /*
       if( ( queue = _info.getMoverQueue() ).getMaxActive() > 0 ){
         cost += ( (double)queue.getQueued() +
                   (double)queue.getActive()  ) /
                   (double)queue.getMaxActive() ;
         div += 1.0 ;
       }
       if( ( queue = _info.getStoreQueue() ).getMaxActive() > 0 ){
         cost += ( (double)queue.getQueued() +
                   (double)queue.getActive()  ) /
                   (double)queue.getMaxActive() ;
         div += 1.0 ;
       }
       if( ( queue = _info.getRestoreQueue() ).getMaxActive() > 0 ){
         cost += ( (double)queue.getQueued() +
                   (double)queue.getActive()  ) /
                   (double)queue.getMaxActive() ;
         div += 1.0 ;
       }
       */

    }

}
