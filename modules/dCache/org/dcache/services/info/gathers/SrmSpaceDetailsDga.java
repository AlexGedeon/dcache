package org.dcache.services.info.gathers;

import org.apache.log4j.Logger;
import org.dcache.services.info.InfoProvider;

import diskCacheV111.services.space.message.GetSpaceTokensMessage;
import dmg.cells.nucleus.CellPath;

/**
 * A class to fire off requests for detailed information about SRM Spaces.
 * <p>
 * Ideally, this would be based on SkelListBasedActivity, but the underlying Message
 * currently doesn't support requesting only a subset of all SRM Spaces.  So
 * we must pull all Space information in one go.
 * 
 * @author Paul Millar <paul.millar@desy.de>
 */
public class SrmSpaceDetailsDga extends SkelPeriodicActivity {
	private static Logger _log = Logger.getLogger( SrmSpaceDetailsDga.class);

	private static final String SRM_CELL_NAME = "SrmSpaceManager";

	/** Assume that a message might be lost and allow for 50% jitter */
	private static final double SAFETY_FACTOR = 2.5;
	
	private CellPath _cp = new CellPath( SRM_CELL_NAME);
	private MessageHandlerChain _mhc = InfoProvider.getInstance().getMessageHandlerChain();
	
	/** The period between successive requests for data, in seconds */
	final long _metricLifetime;
	
	/**
	 * Create new DGA for maintaining a list of all SRM Spaces.
	 * @param interval how often the list of spaces should be updated, in seconds.
	 */
	public SrmSpaceDetailsDga( int interval) {
		super( interval);
		
		_metricLifetime = Math.round( interval * SAFETY_FACTOR);
	}

	/**
	 * When triggered, send a message.
	 */
	@Override
	public void trigger() {
		super.trigger();
		
		if( _log.isInfoEnabled())
			_log.info( "Sending space token details request message");
		
		_mhc.sendMessage( _cp, new GetSpaceTokensMessage(), _metricLifetime); 
	}

	
	public String toString()
	{
		return this.getClass().getSimpleName();
	}	
	
}
