package org.dcache.services.info.gathers;

import java.util.Date;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dcache.services.info.base.IntegerStateValue;
import org.dcache.services.info.base.State;
import org.dcache.services.info.base.StateComposite;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateUpdate;
import org.dcache.services.info.base.StringStateValue;

import diskCacheV111.services.space.Space;
import diskCacheV111.services.space.message.GetSpaceTokensMessage;
import diskCacheV111.vehicles.Message;

public class SrmSpaceDetailsMsgHandler implements MessageHandler {
	
	private static Logger _log = Logger.getLogger( SrmSpaceDetailsMsgHandler.class);
	private static final StatePath SPACES_PATH = StatePath.parsePath("spaces");
	private static final StatePath LINKGROUPS = new StatePath("linkgroups");
	private static final String SRM_ROLE_WILDCARD = "*";
	
	private State _state = State.getInstance();

	public boolean handleMessage(Message messagePayload, long metricLifetime) {
		
		if( !(messagePayload instanceof GetSpaceTokensMessage))
			return false;

		if( _log.isInfoEnabled())
			_log.info( "received spacetokens details msg.");

		GetSpaceTokensMessage msg = (GetSpaceTokensMessage) messagePayload;

		Set<Space> spaces = msg.getSpaceTokenSet();

		if( spaces.size() == 0) {
			_log.info( "received GetSpaceTokensMessage with no spaces listed");
			return true;
		}
		
		StateUpdate update = new StateUpdate();

		for( Space space : spaces) {
			StatePath thisSpacePath = SPACES_PATH.newChild( String.valueOf( space.getId()));

			if( space.getDescription() != null)
				update.appendUpdate( thisSpacePath.newChild("description"), new StringStateValue( space.getDescription(), metricLifetime));

			update.appendUpdate( thisSpacePath.newChild("access-latency"), new StringStateValue( space.getAccessLatency().toString(), metricLifetime));
			update.appendUpdate( thisSpacePath.newChild("retention-policy"), new StringStateValue( space.getRetentionPolicy().toString(), metricLifetime));
			
			StatePath spacePath = thisSpacePath.newChild( "space");
			update.appendUpdate( spacePath.newChild( "free"), new IntegerStateValue( space.getAvailableSpaceInBytes(), metricLifetime));
			update.appendUpdate( spacePath.newChild( "used"), new IntegerStateValue( space.getUsedSizeInBytes(), metricLifetime));
			update.appendUpdate( spacePath.newChild( "allocated"), new IntegerStateValue( space.getAllocatedSpaceInBytes(), metricLifetime));
			update.appendUpdate( spacePath.newChild( "total"), new IntegerStateValue( space.getSizeInBytes(), metricLifetime));
			
			Date creationDate = new Date(space.getCreationTime());
			CellMessageHandlerSkel.addTimeMetrics( update, thisSpacePath.newChild("created"), creationDate, metricLifetime);
			
			update.appendUpdate( thisSpacePath.newChild( "id"), new StringStateValue( String.valueOf( space.getId()), metricLifetime));			
			update.appendUpdate( thisSpacePath.newChild( "state"), new StringStateValue( space.getState().toString(), metricLifetime));
			
			long spaceLifetime = space.getLifetime();
			if( spaceLifetime > 0)
				update.appendUpdate( thisSpacePath.newChild( "lifetime"), new IntegerStateValue( spaceLifetime, metricLifetime));
				
			addLinkgroup( update, thisSpacePath, String.valueOf( space.getLinkGroupId()), String.valueOf(space.getId()), metricLifetime);
			
			addVoInfo( update, thisSpacePath.newChild( "authorisation"), space.getVoGroup(), space.getVoRole(), metricLifetime);
		}
			
		_state.updateState(update);
		
		return true;
	}

	/**
	 * Add VO Group and Role information as up-to three metrics under a common parent.
	 * @param update  the StateUpdate to append the new metrics.
	 * @param parentPath the common parent branch for these metrics.
	 * @param group the String representation of the group.
	 * @param role the String representation of the role.
	 * @param metricLifetime how long, in seconds, these metrics should exist for.
	 */
	private void addVoInfo( StateUpdate update, StatePath parentPath, String group, String role, long metricLifetime) {		
		if( role != null)
			update.appendUpdate( parentPath.newChild( "role"), new StringStateValue( role, metricLifetime));

		if( group != null) {
			update.appendUpdate( parentPath.newChild( "group"), new StringStateValue( group, metricLifetime));

			StringBuilder fqan = new StringBuilder();
		
			fqan.append( group);
			
			if( role != null && !role.equals( SRM_ROLE_WILDCARD)) {
				fqan.append( "/Role=");
				fqan.append( role);
			}

			update.appendUpdate( parentPath.newChild( "FQAN"), new StringStateValue( fqan.toString(), metricLifetime));
		}
	}


	/**
	 * Add references between space reservations and corresponding linkgroup 
	 * @param update the StateUpdate to append metric-updates to
	 * @param parentPath the path to the space under consideration
	 * @param lgid the linkgroup ID
	 * @param spaceId the space ID
	 * @param metricLifetime how long, in seconds, a metric should last.
	 */
	private void addLinkgroup( StateUpdate update, StatePath parentPath, String lgid, String spaceId, long metricLifetime) {
		
		// Add the reference to the linkgroup within the space.
		update.appendUpdate( parentPath.newChild( "linkgroupref"), new StringStateValue( lgid, metricLifetime));
		
		// Add the reference to this space reservation within the corresponding linkgroup
		update.appendUpdate( LINKGROUPS.newChild( lgid).newChild("spaces").newChild( spaceId), new StateComposite( metricLifetime));		
	}

}
