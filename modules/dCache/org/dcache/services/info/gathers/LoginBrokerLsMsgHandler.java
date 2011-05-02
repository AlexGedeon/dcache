package org.dcache.services.info.gathers;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dcache.services.info.base.FloatingPointStateValue;
import org.dcache.services.info.base.IntegerStateValue;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateUpdate;
import org.dcache.services.info.base.StateUpdateManager;
import org.dcache.services.info.base.StringStateValue;

import dmg.cells.nucleus.UOID;
import dmg.cells.services.login.LoginBrokerInfo;

/**
 * Parse the reply messages from sending the LoginBroker CellMessages with "ls -binary".
 * These replies are an array of LoginBrokerInfo objects.
 * 
 * @author Paul Millar <paul.millar@desy.de>
 */
public class LoginBrokerLsMsgHandler extends CellMessageHandlerSkel {

	private static Logger _log = LoggerFactory.getLogger( LoginBrokerLsMsgHandler.class);

	private static final StatePath PATH_TO_DOORS = new StatePath( "doors");


	public LoginBrokerLsMsgHandler(StateUpdateManager sum, MessageMetadataRepository<UOID> msgMetaRepo) {
		super(sum, msgMetaRepo);
	}

	@Override
    public void process(Object msgPayload, long metricLifetime) {

		if( !msgPayload.getClass().isArray()) {
			_log.error( "unexpected received non-array payload");
			return;			
		}

		Object[] array = (Object []) msgPayload;

		if( array.length == 0)
			return;

		StateUpdate update = new StateUpdate();

		for( int i = 0; i < array.length; i++) {
			
			if( !(array [i] instanceof LoginBrokerInfo)) {
				_log.warn( "Skipping array element that is not LoginBrokerInfo");
				continue;
			}
			
			LoginBrokerInfo info = (LoginBrokerInfo) array[i];
			addDoorInfo( update, PATH_TO_DOORS.newChild( info.getIdentifier()), info, metricLifetime);
		}
		
		applyUpdates( update);
	}
	
	
	/**
	 * Add additional state-update to record information about a door.
	 * @param update the StateUpdate we are to add metrics to.
	 * @param pathToDoor a StatePath under which we are to add data.
	 * @param info the information about the door.
	 * @param lifetime the duration, in seconds, for this information
	 */
	private void addDoorInfo( StateUpdate update, StatePath pathToDoor, LoginBrokerInfo info, long lifetime) {

		StatePath pathToProtocol = pathToDoor.newChild( "protocol");
		
		conditionalAddString( update, pathToProtocol, "engine",  info.getProtocolEngine(), lifetime);
		conditionalAddString( update, pathToProtocol, "family",  info.getProtocolFamily(), lifetime);
		conditionalAddString( update, pathToProtocol, "version", info.getProtocolVersion(), lifetime);
		
		update.appendUpdate( pathToDoor.newChild("load"),
					new FloatingPointStateValue( info.getLoad(), lifetime));
		update.appendUpdate( pathToDoor.newChild( "port"),
					new IntegerStateValue( info.getPort(), lifetime));
		
		update.appendUpdate( pathToDoor.newChild( "cell"),
					new StringStateValue( info.getCellName(), lifetime));

		update.appendUpdate( pathToDoor.newChild( "domain"),
				new StringStateValue( info.getDomainName(), lifetime));

		update.appendUpdate( pathToDoor.newChild( "update-time"),
					new IntegerStateValue( info.getUpdateTime(), lifetime));

		StatePath pathToInterfaces = pathToDoor.newChild("interfaces");
		
		String[] interfaceNames = info.getHosts();
		
		if( interfaceNames != null) {
			for( int i = 0; i < interfaceNames.length; i++) {
				if( interfaceNames[i] != null)
					addInterfaceInfo( update, pathToInterfaces, interfaceNames[i], i+1, lifetime);
			}
		}
	}
	
	
	/**
	 * Add a string metric at a specific point in the State tree if the value is not NULL.
	 * @param update the StateUpdate to append with the metric definition 
	 * @param parentPath the path to the parent branch for this metric
	 * @param name the name of the metric
	 * @param value the metric's value, or null if the metric should not be added.
	 * @param storeTime how long, in seconds the metric should be preserved.
	 */
	private void conditionalAddString( StateUpdate update, StatePath parentPath, String name, String value, long storeTime) {
		if( value != null) {
			update.appendUpdate( parentPath.newChild(name),
						new StringStateValue( value, storeTime));
		}
	}
	

	/**
	 * Add a standardised amount of information about an interface.  This is in the form:
	 * <pre>
	 *     [interfaces]
	 *       |
	 *       |
	 *       +--[ id ] (branch)
	 *       |   |
	 *       |   +-- "name" (string metric: the host's name, as presented by the door)
	 *       |   +-- "order"  (integer metric: 1 .. 2 ..)
	 *       |   +-- "FQDN" (string metric: the host's FQDN)
	 *       |   +-- "address" (string metric: the host's address; e.g., "127.0.0.1")
	 *       |   +-- "address-type"    (string metric: "IPv4", "IPv6" or "unknown")
	 *       |
	 * </pre> 
	 * @param update The StateUpdate to append the new metrics.
	 * @param parentPath the path that the id branch will be added.  
	 * @param name something that identifies the interface (e.g., IP address or simple name).
	 * @param order the order in which the interfaces should be considered: 1 is the lowest number.
	 * @param lifetime how long the created metrics should last.
	 */
	private void addInterfaceInfo( StateUpdate update, StatePath parentPath, String name, int order, long lifetime) {
		
		String id = name + "-" + order;
		
		StatePath pathToInterfaceBranch = parentPath.newChild(id);

		// Always add the name
		update.appendUpdate( pathToInterfaceBranch.newChild( "name"), new StringStateValue( name, lifetime));

		// Attempt to add information after resolving the interface
		try {
			InetAddress address = InetAddress.getByName(name);
			
			update.appendUpdate( pathToInterfaceBranch.newChild( "FQDN"), new StringStateValue( address.getCanonicalHostName(), lifetime));
					
			update.appendUpdate( pathToInterfaceBranch.newChild( "address"), new StringStateValue( address.getHostAddress(), lifetime));
			update.appendUpdate( pathToInterfaceBranch.newChild( "address-type"),
								new StringStateValue( (address instanceof Inet4Address) ? "IPv4" : (address instanceof Inet6Address) ? "IPv6" : "unknown", lifetime));
			
			update.appendUpdate( pathToInterfaceBranch.newChild( "order"), new IntegerStateValue( order, lifetime));
			
		} catch( UnknownHostException e) {
			/**
			 *  If interface is (for whatever reason) unknown, simply skip publishing the
			 *  related information.
			 */
		}
	}

}
