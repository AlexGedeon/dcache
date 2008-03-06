package org.dcache.services.info.stateInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.dcache.services.info.base.BooleanStateValue;
import org.dcache.services.info.base.FloatingPointStateValue;
import org.dcache.services.info.base.IntegerStateValue;
import org.dcache.services.info.base.State;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateTransition;
import org.dcache.services.info.base.StateVisitor;
import org.dcache.services.info.base.StringStateValue;

/**
 * Scan through a dCache state tree, building a list of poolgroup-to-pools associations.
 * @author Paul Millar <paul.millar@desy.de>
 */
public class PoolgroupToPoolsVisitor implements StateVisitor {

	private static Logger _log = Logger.getLogger( PoolgroupToPoolsVisitor.class);

	private static final StatePath _poolgroupsPath = new StatePath( "poolgroups");
	
	/**
	 * Obtain a Map between a poolgroup and the pools that are currently members of this poolgroup.
	 * @return
	 */
	public static Map <String,Set<String>> getDetails() {
		if( _log.isInfoEnabled())
			_log.info( "Gathering current status");
		
		PoolgroupToPoolsVisitor visitor = new PoolgroupToPoolsVisitor();
		State.getInstance().visitState(visitor, _poolgroupsPath);		
		return visitor._poolgroups;		
	}

	/**
	 * Obtain a Map between a poolgroup and the pools that will be members of this poolgroup after
	 * the given StateTransition
	 * @param transition the StateTransition to consider
	 * @return
	 */
	public static Map <String,Set<String>> getDetails( StateTransition transition) {
		if( _log.isInfoEnabled())
			_log.info( "Gathering status after transition");
		
		PoolgroupToPoolsVisitor visitor = new PoolgroupToPoolsVisitor();
		State.getInstance().visitState(transition, visitor, _poolgroupsPath);		
		return visitor._poolgroups;		
	}

	
	Map <String,Set<String>> _poolgroups = new HashMap<String,Set<String>>();
	Set<String> _currentPoolgroupPools;
	StatePath _poolMembershipPath;

	public void visitCompositePreDescend( StatePath path, Map<String,String> metadata) {			
		if( _log.isDebugEnabled())
			_log.debug( "Examining "+path);

		// If something like poolgroups.<some poolgroup>
		if( _poolgroupsPath.isParentOf( path)) {
			if( _log.isDebugEnabled())
				_log.debug( "Found poolgroup "+path.getLastElement());

			_currentPoolgroupPools = new HashSet<String>();
			_poolgroups.put( path.getLastElement(), _currentPoolgroupPools);
			_poolMembershipPath = path.newChild("pools");
		}
		
		// If something like poolgroups.<some poolgroup>.pools.<some pool>
		if( _poolMembershipPath != null && _poolMembershipPath.isParentOf(path)) {
			if( _log.isDebugEnabled())
				_log.debug( "Found pool "+path.getLastElement());
			
			_currentPoolgroupPools.add( path.getLastElement());
		}
	}
	
	public void visitCompositePreLastDescend( StatePath path, Map<String,String> metadata) {}		
	public void visitCompositePostDescend( StatePath path, Map<String,String> metadata) {}
	public void visitCompositePreSkipDescend( StatePath path, Map<String,String> metadata) {
		if( _log.isDebugEnabled())
			_log.debug( "Preskip with "+path);		
	}
	public void visitCompositePostSkipDescend( StatePath path, Map<String,String> metadata) {}
	public void visitString( StatePath path, StringStateValue value) {}
	public void visitBoolean( StatePath path, BooleanStateValue value) {}
	public void visitInteger( StatePath path, IntegerStateValue value) {}
	public void visitFloatingPoint( StatePath path, FloatingPointStateValue value) {}		
}


