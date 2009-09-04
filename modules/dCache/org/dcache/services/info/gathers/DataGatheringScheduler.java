package org.dcache.services.info.gathers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.dcache.services.info.base.StateExhibitor;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateUpdateManager;

/**
 * This thread is responsible for scheduling various data-gathering activity.
 * Multiple DataGatheringActivity instances can be registered, each will operate
 * independently.  The frequency at which they trigger, or even whether they are
 * periodic, is completely under the control of the DGA.
 * <p>
 * These DataGatheringActivities can (in principle) do anything when
 * triggered, but will typically send one or more messages to dCache.
 * 
 * @author Paul Millar <paul.millar@desy.de>
 */
public class DataGatheringScheduler implements Runnable {

	private static final long FIVE_MINUTES = 5*60*1000;
	
	private boolean _timeToQuit = false;
	private List<RegisteredActivity> _activity = new ArrayList<RegisteredActivity>();
	private static Logger _logSched = Logger.getLogger(DataGatheringScheduler.class);
	private static Logger _logRa = Logger.getLogger(RegisteredActivity.class);

		
	/**
	 * Class holding a periodically repeated DataGatheringActivity
	 * @author Paul Millar <paul.millar@desy.de>
	 */
	private static class RegisteredActivity {
		
		
		/** Min. delay (in ms). We prevent Schedulables from triggering more frequently than this */
		private static final long MINIMUM_DGA_DELAY = 400;
		
		private final Schedulable _dga;

		/** The delay until this DataGatheringActivity should be next triggered */
		private Date _nextTriggered;
		
		/** Whether we should include this activity when scheduling next activity */
		private boolean _enabled = true;
		
		/**
		 * Create a new PeriodicActvity, with specified DataGatheringActivity, that
		 * is triggered with a fixed period.  The initial delay is a randomly chosen
		 * fraction of the period.   
		 * @param dga the DataGatheringActivity to be triggered periodically
		 * @param period the period between successive triggering in milliseconds.
		 */
		RegisteredActivity( Schedulable dga) {
			_dga = dga;
			updateNextTrigger();
		}
		
		
		/**
		 * Try to make sure we don't hit the system with lots of queries at the same
		 * time
		 * @param period
		 */
		private final void updateNextTrigger() {
			Date nextTrigger = _dga.shouldNextBeTriggered();
			
			if( nextTrigger == null) {
				_logRa.error("registered dga returned null Date");
				nextTrigger = new Date( System.currentTimeMillis() + FIVE_MINUTES);
				
			} else {
				
				// Safety!  Check we wont trigger too quickly
				if( nextTrigger.getTime() - System.currentTimeMillis() <  MINIMUM_DGA_DELAY) {
					_logRa.warn( "DGA "+_dga.toString()+" triggering too quickly ("+(nextTrigger.getTime() - System.currentTimeMillis())+"ms): engaging safety.");
					nextTrigger = new Date (System.currentTimeMillis() + MINIMUM_DGA_DELAY);
				}
			}
			
			_nextTriggered = nextTrigger;
		}
		
		/**
		 * Update this PeriodicActivity so it's trigger time is <i>now</i>.
		 */
		public void shouldTriggerNow() {
			_nextTriggered = new Date();			
		}
		
		/**
		 * Check the status of this activity.  If the time has elapsed, 
		 * this will cause the DataGatheringActivity to be triggered
		 * and the timer to be reset.
		 * @return true if the DataGatheringActivity was triggered. 
		 */
		boolean checkAndTrigger( Date now) {
			
			if( !_enabled)
				return false;
			
			if( now.before(_nextTriggered))
				return false;
			
			_dga.trigger();
			updateNextTrigger();
			return true;
		}
		
		/**
		 * Calculate the duration until the event has triggered. 
		 * @return duration, in milliseconds, until event or zero if it
		 * should have been triggered already.
		 */
		long getDelay() {
			long delay = _nextTriggered.getTime() - System.currentTimeMillis();
			return delay > 0 ? delay : 0;
		}
		
		/**
		 * Return the time this will be next triggered.
		 * @return
		 */
		long getNextTriggered() {
			return _nextTriggered.getTime();
		}
		
		boolean isEnabled() {
			return _enabled;
		}
		
		void disable() {
			_enabled = false;
		}
		
		
		/**
		 * Enable a periodic activity.
		 */
		void enable()  {
			if( !_enabled) {
				_enabled = true;
				updateNextTrigger();
			}
		}
				
		/**
		 * A human-understandable name for this DGA
		 * @return the underlying DGA's name
		 */
		@Override
        public String toString() {
			return _dga.toString();
		}
		
		/**
		 * Render current status into a human-understandable form. 
		 * @return single-line String describing current status.
		 */
		public String getStatus() {
			StringBuilder sb = new StringBuilder();
			sb.append( this.toString());
			sb.append( " [");
			sb.append( _enabled ? "enabled" : "disabled");
			if( _enabled)
				sb.append( String.format(", next %1$.1fs", getDelay()/1000.0));
			sb.append("]");
			
			return sb.toString();
		}
	}
	
	private final StateUpdateManager _sum;

	public DataGatheringScheduler( StateUpdateManager sum) {
	    _sum = sum;
	}

	/**
	 * Main loop for this thread triggering DataGatheringActivity.
	 */
	public void run() {
		long delay;
		Date now = new Date();

		_logSched.debug("DGA Scheduler thread starting.");

		synchronized( _activity) {			
			do {
				now.setTime(System.currentTimeMillis());
				
				for( RegisteredActivity pa : _activity)
					pa.checkAndTrigger( now);	
				
				delay = getWaitTimeout();

				try {
					_activity.wait(delay);
				} catch( InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				
			} while( !_timeToQuit);
		}
		
		_logSched.debug("DGA Scheduler thread shutting down.");
	}

	/**
	 * Add a new data-gathering activity. 
	 * @param dga  The activity to add.
	 */
	public void addActivity( Schedulable dga) {
		RegisteredActivity pa = new RegisteredActivity( dga);

		synchronized( _activity) {
			_activity.add( pa);
			_activity.notify(); // Wake up thread to recalculate its sleep-time.
		}		
	}
	
	
	/**
	 * Search through out list of activity and find the one that matches this name.
	 * <p>
	 * This method assumes that the current thread already owns the _allActivity
	 * monitor
	 * @param name the name of the activity to fine
	 * @return the corresponding PeriodicActivity object, or null if not found.
	 */
	private RegisteredActivity findActivity( String name) {
		RegisteredActivity foundPA = null;
		
		for( RegisteredActivity pa : _activity)			
			if( pa.toString().equals(name)) {
				foundPA = pa;
				break;
			}
		
		return foundPA;		
	}
	
	
	/**
	 *  Enable a data-gathering activity, based on a human-readable name. 
	 * @param name - name of the DGA.
	 * @return null if successful or an error message if there was a problem.
	 */
	public String enableActivity( String name) {
		RegisteredActivity pa = null;
		boolean haveEnabled = false;
		
		synchronized( _activity) {		
			pa = findActivity( name);
			
			if( pa != null && !pa._enabled) {
				pa.enable();
				_activity.notify();			
				haveEnabled = true;
			}
		}
		
		return haveEnabled ? null : pa == null ? "Unknown DGA " + name : "DGA " + name + " already enabled"; 
	}
	
	/**
	 *  Disabled a data-gathering activity, based on a human-readable name. 
	 * @param name - name of the DGA.
	 * @return null if successful or an error message if there was a problem.
	 */
	public String disableActivity( String name) {
		RegisteredActivity pa = null;
		boolean haveDisabled = false;
		
		synchronized( _activity) {
			pa = findActivity( name);
			
			if( pa != null && pa._enabled) {
				pa.disable();
				_activity.notify();			
				haveDisabled = true;
			}			
		}
		
		return haveDisabled ? null : pa == null ? "Unknown DGA " + name : "DGA " + name + " already disabled"; 
	}
	
	
	/**
	 * Trigger a periodic activity right now.
	 * @param name the PeriodicActivity to trigger
	 * @return null if successful, an error message if there was a problem.
	 */
	public String triggerActivity( String name) {
		RegisteredActivity pa = null;
		
		synchronized( _activity) {
			pa = findActivity( name);
			
			if( pa != null) {
				pa.shouldTriggerNow();				
				_activity.notify();
			}
		}
		
		return pa != null ? null : "Unknown DGA " + name;
	}


	/**
	 * Request that this thread sends no more requests
	 * for data.
	 */
	public void shutdown() {
		_logSched.debug("Requesting DGA Scheduler to shutdown.");
		synchronized( _activity) {
			_timeToQuit = true;
			_activity.notify();
		}
	}
	
	
	/**
	 * Calculate the delay, in milliseconds, until the next
	 * PeriodicActivity is to be triggered, or 0 if there is
	 * no registered Schedulable objects.
	 * <p>
	 * <i>NB</i> we assume that the current thread has already obtained the monitor for
	 * _allActivity!
	 * @return delay, in milliseconds, until next trigger or zero if there
	 * is no recorded delay.
	 */
	private long getWaitTimeout() {
		long earliestTrig=0;
		
		synchronized( _activity) {
		
			for( RegisteredActivity thisPa : _activity) {

				if( !thisPa.isEnabled())
					continue;
			
				long thisTrig = thisPa.getNextTriggered();
				
				if( thisTrig < earliestTrig || earliestTrig == 0)
					earliestTrig = thisTrig;
			}
		}
		
		long delay = 0;
		
		if( earliestTrig > 0) {
			delay = earliestTrig - System.currentTimeMillis();
			delay = delay < 1 ? 1 : delay; // enforce >1 to distinguish between "should trigger now" and "no registered activity".
		}

		return delay; 
	}
	
	
	/**
	 * Return a human-readable list of known activity.
	 * @return
	 */
	public List<String> listActivity() {
		List<String> activityList = new ArrayList<String>();
		
		synchronized( _activity) {			
			for( RegisteredActivity thisRa : _activity)
				activityList.add(thisRa.getStatus());
		}
		
		return activityList;
	}
	
	
	/**
	 * Add hard-coded, default activity
	 */
	public void addDefaultActivity( StateExhibitor exhibitor) {

		addActivity( new SingleMessageDga( "PoolManager", "psux ls pool", new StringListMsgHandler( _sum, "pools"), 60));
		addActivity( new SingleMessageDga( "PoolManager", "psux ls pgroup", new StringListMsgHandler( _sum, "poolgroups"), 60));
		addActivity( new SingleMessageDga( "PoolManager", "psux ls unit", new StringListMsgHandler( _sum, "units"), 60));
		addActivity( new SingleMessageDga( "PoolManager", "psux ls ugroup", new StringListMsgHandler( _sum, "unitgroups"), 60));
		
		addActivity( new SingleMessageDga( "PoolManager", "xcm ls", new PoolCostMsgHandler( _sum), 60));

		addActivity( new SingleMessageDga( "PoolManager", "psux ls link -x -resolve", new LinkInfoMsgHandler( _sum), 60));

		LoginBrokerLsMsgHandler msgHandler = new LoginBrokerLsMsgHandler( _sum);
		addActivity( new SingleMessageDga( "LoginBroker",     "ls -binary", msgHandler, 60));
		addActivity( new SingleMessageDga( "srm-LoginBroker", "ls -binary", msgHandler, 60));
		
		// Add SRM DGAs..
		// We don't use LinkgroupListDga as it provides the wrong information, and isn't needed as
		// LinkgroupDetailsDga (mistakenly) provides all information about all linkgroups.
		//addActivity( new LinkgroupListDga( 60));
		addActivity( new LinkgroupDetailsDga( 300)); // every five minutes, as this may be a heavy-weight operation.
		addActivity( new SrmSpaceDetailsDga( 300)); // every five minutes, as this may be a heavy-weight operation.
		
		// Pick up domains
		addActivity( new SingleMessageDga( "topo", "gettopomap", new TopoMapHandler( _sum), 120));
		// Pick up cell information
		addActivity( new CellInfoDga( exhibitor, new CellInfoMsgHandler( _sum)));

		// Pick up a domain's routing information.
		addActivity( new RoutingMgrDga( exhibitor, new RoutingMgrMsgHandler( _sum)));

		addActivity( new ListBasedMessageDga( exhibitor, new StatePath("pools"),      "PoolManager", "psux ls pool",   new PoolInfoMsgHandler( _sum)));
		addActivity( new ListBasedMessageDga( exhibitor, new StatePath("poolgroups"), "PoolManager", "psux ls pgroup", new PoolGroupInfoMsgHandler( _sum)));
		addActivity( new ListBasedMessageDga( exhibitor, new StatePath("units"),      "PoolManager", "psux ls unit",   new UnitInfoMsgHandler( _sum)));
		addActivity( new ListBasedMessageDga( exhibitor, new StatePath("unitgroups"), "PoolManager", "psux ls ugroup", new UGroupInfoMsgHandler( _sum)));
	}


}
