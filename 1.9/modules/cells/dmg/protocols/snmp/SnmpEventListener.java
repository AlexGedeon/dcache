package dmg.protocols.snmp ;

/**
  *  
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public interface SnmpEventListener {

  public SnmpRequest snmpEventArrived( SnmpEvent event ) ;
}
