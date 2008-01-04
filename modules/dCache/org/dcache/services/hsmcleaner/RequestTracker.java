package org.dcache.services.hsmcleaner;

import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Iterator;

import java.net.URI;

import diskCacheV111.pools.PoolV2Mode;
import diskCacheV111.vehicles.PoolRemoveFilesFromHSMMessage;

import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.CellNucleus;
import dmg.cells.nucleus.NoRouteToCellException;
import java.io.NotSerializableException;

import org.dcache.services.AbstractCell;

/**
 * This class encapsulates the interaction with pools.
 *
 * At the abstract level it provides a method for submitting file
 * deletions. Notifcation of success or failure is provided
 * asynchronously via two sinks.
 *
 * To reduce the load on pools, files are deleted in batches. For each
 * HSM, at most one request is send at a time. The class defines an
 * upper limit on the size of a request.
 */
public class RequestTracker
{
    /**
     * Utility class to keep track of timeouts.
     */
    class Timeout extends TimerTask
    {
        String _hsm;
        String _pool;

        Timeout(String hsm, String pool)
        {
            _hsm = hsm;
            _pool = pool;
        }

        public void run()
        {
            timeout(_hsm, _pool);
        }

        public String getPool()
        {
            return _pool;
        }
    }

    private final static String POOLUP_MESSAGE =
        "diskCacheV111.vehicles.PoolManagerPoolUpMessage";

    /**
     * Task for periodically registering the request tracker to
     * receive pool up messages.
     */
    private final BroadcastRegistrationTask _broadcastRegistration;

    /**
     * Cell used for sending messages.
     */
    private AbstractCell _cell;

    /**
     * Timeout for delete request.
     *
     * For each HSM, we have at most one outstanding remove request.
     */
    private Map<String,Timeout> _poolRequests =
        new HashMap<String,Timeout>();

    /**
     * A simple queue of locations to delete, grouped by HSM.
     *
     * The main purpose is to allow bulk removal of files, thus not
     * spamming the pools with a large number of small delete
     * requests. For each HSM, there will be at most one outstanding
     * remove request; new entries during that period will be queued.
     */
    private Map<String,Set<URI>> _locationsToDelete =
        new HashMap<String,Set<URI>>();

    /**
     * Locations that could not be deleted are pushed to this sink.
     */
    private Sink<URI> _failureSink;

    /**
     * Locations that were deleted are pushed to this sink.
     */
    private Sink<URI> _successSink;

    /**
     * Maximum number of files to include in a single request.
     */
    private int _maxFilesPerRequest = 100;

    /**
     * Timeout in milliseconds for delete requests send to pools.
     */
    private long _timeout = 60000;

    /**
     * Timer used for implementing timeouts.
     */
    private Timer _timer = new Timer();

    /**
     * Pools currently available.
     */
    private PoolInformationBase _pools = new PoolInformationBase();

    public RequestTracker(AbstractCell cell)
    {
        CellNucleus nucleus = cell.getNucleus();
        CellPath me = new CellPath(nucleus.getCellName(),
                                   nucleus.getCellDomainName());
        _cell = cell;
        _broadcastRegistration =
            new BroadcastRegistrationTask(cell, POOLUP_MESSAGE, me);
        _timer.schedule(_broadcastRegistration, 0, 300000); // 5 minutes
        _cell.addMessageListener(this);
        _cell.addMessageListener(_pools);
    }

    /**
     * Set maximum number of files to include in a single request.
     */
    synchronized public void setMaxFilesPerRequest(int value)
    {
        _maxFilesPerRequest = value;
    }

    /**
     * Returns maximum number of files to include in a single request.
     */
    synchronized public int getMaxFilesPerRequest()
    {
        return _maxFilesPerRequest;
    }

    /**
     * Set timeout in milliseconds for delete requests send to pools.
     */
    synchronized public void setTimeout(long timeout)
    {
        _timeout = timeout;
    }

    /**
     * Returns timeout in milliseconds for delete requests send to
     * pools.
     */
    synchronized public long getTimeout()
    {
        return _timeout;
    }

    /**
     * Sets the sink to which success to delete a file is reported.
     */
    synchronized public void setSuccessSink(Sink<URI> sink)
    {
        _successSink = sink;
    }

    /**
     * Sets the sink to which failure to delete a file is reported.
     */
    synchronized public void setFailureSink(Sink<URI> sink)
    {
        _failureSink = sink;
    }

    /**
     * Submits a request to delete a file.
     *
     * The request may not be submitted right away. It may be queued
     * and submitted together with other requests.
     *
     * @param location the URI of the file to delete
     */
    synchronized public void submit(URI location)
    {
        String hsm = location.getAuthority();
        Set<URI> locations = _locationsToDelete.get(hsm);
        if (locations == null) {
            locations = new HashSet<URI>();
            _locationsToDelete.put(hsm, locations);
        }
        locations.add(location);

        flush(hsm);
    }

    /**
     * Submits requests queued for a given HSM.
     *
     * @param hsm the name of an HSM instance
     */
    synchronized private void flush(String hsm)
    {
        Collection<URI> locations = _locationsToDelete.get(hsm);
        if (locations == null || locations.isEmpty())
            return;

        if (_poolRequests.containsKey(hsm))
            return;

        /* To avoid excessively large requests, we limit the number
         * of files per request.
         */
        if (locations.size() > _maxFilesPerRequest) {
            Collection<URI> subset =
                new ArrayList<URI>(_maxFilesPerRequest);
            Iterator<URI> iterator = locations.iterator();
            for (int i = 0; i < _maxFilesPerRequest; i++) {
                subset.add(iterator.next());
            }
            locations = subset;
        }

        /* It may happen that our information about the pools is
         * outdated and that the pool is no longer
         * available. Therefore we may have to try several pools.
         */
        PoolInformation pool;
        while ((pool = _pools.getPoolWithHSM(hsm)) != null) {
            String name = pool.getName();
            try {
                PoolRemoveFilesFromHSMMessage message =
                    new PoolRemoveFilesFromHSMMessage(name, hsm, locations);

                _cell.sendMessage(new CellMessage(new CellPath(name), message));

                Timeout timeout = new Timeout(hsm, name);
                _timer.schedule(timeout, _timeout);
                _poolRequests.put(hsm, timeout);
                break;
            } catch (NotSerializableException e) {
                throw new RuntimeException("Internal error (cannot serialise message)", e);
            } catch (NoRouteToCellException e) {
                _cell.error("Failed to send message to " + name
                            + ": e.getMessage()");
                _pools.remove(pool.getName());
            }
        }

        /* If there is no available pool, then we report failure on
         * all files.
         */
        if (pool == null) {
            _cell.warn("No pools attached to " + hsm + " are available");

            Iterator<URI> i = _locationsToDelete.get(hsm).iterator();
            while (i.hasNext()) {
                URI location = i.next();
                assert location.getAuthority().equals(hsm);
                _failureSink.push(location);
                i.remove();
            }
        }
    }

    /**
     * Called when a request to a pool has timed out. We remove the
     * pool from out list of known pools and resubmit the request.
     *
     * One may worry that in case of problems we end up resubmit the
     * same requests over and over. A timeout will however only happen
     * if either the pool crashed or in case of a bug in the pool.  In
     * the first case we will end up trying another pool. In the
     * second case, we should simply fix the bug in the pool.
     */
    synchronized private void timeout(String hsm, String pool)
    {
        _cell.error("Timeout deleting files HSM " + hsm
                    + " attached to " + pool);
        _poolRequests.remove(hsm);
        _pools.remove(pool);
        flush(hsm);
    }

    /**
     * Message handler for responses from pools.
     */
    synchronized public void messageArrived(PoolRemoveFilesFromHSMMessage msg)
    {
        String hsm = msg.getHsm();
        Collection<URI> success = msg.getSucceeded();
        Collection<URI> failures = msg.getFailed();
        Collection<URI> locations = _locationsToDelete.get(hsm);

        if (locations == null) {
            /* Seems we got a reply for something this instance did
             * not request. We log this as a warning, but otherwise
             * ignore it.
             */
            _cell.warn("Received confirmation from a pool, for an action this cleaner did not request.");
            return;
        }

        if (!failures.isEmpty())
            _cell.warn("Failed to delete " + failures.size()
                       + " files from HSM " + hsm + ". Will try again later.");

        for (URI location : success) {
            assert location.getAuthority().equals(hsm);
            if (locations.remove(location))
                _successSink.push(location);
        }

        for (URI location : failures) {
            assert location.getAuthority().equals(hsm);
            if (locations.remove(location))
                _failureSink.push(location);
        }

        Timeout timeout = _poolRequests.remove(hsm);
        if (timeout != null) {
            timeout.cancel();
        }

        flush(hsm);
    }
}