// $Id: FlushControlCellInfo.java,v 1.2 2006-04-03 05:51:55 patrick Exp $

package diskCacheV111.hsmControl.flush ;

import java.util.List ;
import java.util.ArrayList ;
import java.util.Map ;
import java.util.HashMap ;
import dmg.cells.nucleus.CellInfo ;

public class FlushControlCellInfo extends CellInfo implements java.io.Serializable {

   static final long serialVersionUID = -3473581391102980404L;

   private String  _driverName = "NONE" ;
   private long    _update;
   private boolean _control;
   private List    _poolGroups;
   private String  _status     = "Idle" ;
   private Map     _driverProperties;
   private long    _driverPropertiesAge;
   public FlushControlCellInfo( CellInfo cellInfo ){
       super(cellInfo);
   }
   void setParameter( String  driverName,
                      long    updateInterval ,
                      boolean control ,
                      List    poolGroups ,
                      String  status ){

       _driverName = driverName ;
       _update     = updateInterval ;
       _control    = control ;
       _poolGroups = new ArrayList( poolGroups ) ;
       _status     = status ;
   }
   void setDriverProperties( long updated , Map properties ){
      _driverPropertiesAge = updated ;
      _driverProperties    = new HashMap( properties ) ;
   }
   public long getDriverPropertiesAge(){ return _driverPropertiesAge ; }
   public Map getDriverProperties(){ return _driverProperties == null ? new HashMap() : _driverProperties ; }

    public boolean   getIsControlled(){ return  _control ; }
   public String    getDriverName(){ return _driverName ; }
   public String    getStatus(){ return _status ;}
   public long      getUpdateInterval(){ return _update ; }
   public List      getPoolGroups(){ return _poolGroups ; }

   public String toString(){
      return getCellName()+"@"+getDomainName()+";s="+_status+";c="+_control+";d="+_driverName+
             ";{pa="+_driverPropertiesAge+";p="+getDriverProperties()+"};";
   }
}
