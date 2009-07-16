package org.dcache.pool.migration;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.net.UnknownHostException;
import java.io.File;
import java.io.IOException;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.CacheFileAvailable;
import diskCacheV111.util.Checksum;
import diskCacheV111.util.ChecksumFactory;
import diskCacheV111.vehicles.Message;
import diskCacheV111.vehicles.StorageInfo;

import org.dcache.cells.AbstractCellComponent;
import org.dcache.cells.CellMessageReceiver;
import org.dcache.pool.classic.ChecksumModuleV1;
import org.dcache.pool.p2p.P2PClient;
import org.dcache.pool.repository.Repository;
import org.dcache.pool.repository.EntryState;
import org.dcache.pool.repository.CacheEntry;
import org.dcache.pool.repository.StickyRecord;
import org.dcache.pool.repository.ReadHandle;
import org.dcache.pool.repository.IllegalTransitionException;
import static org.dcache.pool.repository.EntryState.*;

import dmg.util.Args;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.NoRouteToCellException;

import org.apache.log4j.Logger;

/**
 * Server component of migration module.
 *
 * This class is essentially a migration module specific frontend to
 * the pool to pool transfer component.
 *
 * The following messages are handled:
 *
 *   - PoolMigrationUpdateReplicaMessage
 *   - PoolMigrationCopyReplicaMessage
 *   - PoolMigrationPingMessage
 *   - PoolMigrationCancelMessage
 */
public class MigrationModuleServer
    extends AbstractCellComponent
    implements CellMessageReceiver
{
    private final static Logger _log =
        Logger.getLogger(MigrationModuleServer.class);

    private Map<UUID, Request> _requests = new ConcurrentHashMap();
    private P2PClient _p2p;
    private Repository _repository;
    private ExecutorService _executor;
    private ChecksumModuleV1 _checksumModule;

    public void setExecutor(ExecutorService executor)
    {
        _executor = executor;
    }

    public void setPPClient(P2PClient p2p)
    {
        _p2p = p2p;
    }

    public void setRepository(Repository repository)
    {
        _repository = repository;
    }

    public synchronized void setChecksumModule(ChecksumModuleV1 csm)
    {
        _checksumModule = csm;
    }

    public Message messageArrived(PoolMigrationUpdateReplicaMessage message)
        throws CacheException
    {
        if (message.isReply()) {
            return null;
        }

        PnfsId pnfsId = message.getPnfsId();
        EntryState targetState = message.getState();
        CacheEntry entry = _repository.getEntry(pnfsId);
        EntryState state = entry.getState();
        long ttl = message.getTimeToLive();

        /* Drop message if TTL is exceeded. This is done to avoid
         * modifying the entry after the requestor has timed out.
         */
        if (ttl < System.currentTimeMillis()) {
            _log.warn("PoolMigrationUpdateReplica message discarded: TTL exceeded");
            return null;
        }

        if (targetState != PRECIOUS && targetState != CACHED) {
            throw new IllegalArgumentException("State must be either CACHED or PRECIOUS");
        }

        try {
            switch (state) {
            case CACHED:
                if (targetState == PRECIOUS) {
                    _repository.setState(pnfsId, PRECIOUS);
                }
                // fall through
            case PRECIOUS:
                for (StickyRecord record: message.getStickyRecords()) {
                    _repository.setSticky(pnfsId,
                                          record.owner(),
                                          record.expire(),
                                          false);
                }
                break;
            default:
                throw new CacheException(CacheException.DEFAULT_ERROR_CODE,
                                         "Cannot update file in state " + state);
            }
        } catch (IllegalTransitionException e) {
            throw new CacheException(CacheException.DEFAULT_ERROR_CODE,
                                     "Cannot update file in state " + e.getSourceState());
        }

        message.setSucceeded();
        return message;
    }

    public Message
        messageArrived(CellMessage envelope, PoolMigrationCopyReplicaMessage message)
        throws UnknownHostException
    {
        if (message.isReply()) {
            return null;
        }

        if (envelope.getLocalAge() >= envelope.getTtl()) {
            _log.warn("PoolMigrationCopyReplica message discarded: TTL exceeded");
            return null;
        }

        Request request =
            new Request((CellPath)envelope.getSourcePath().clone(), message);
        _requests.put(request.getUUID(), request);
        request.start();

        message.setSucceeded();
        return message;
    }

    public Message messageArrived(PoolMigrationPingMessage message)
    {
        if (message.isReply()) {
            return null;
        }

        UUID uuid = message.getUUID();
        if (uuid == null) {
            uuid = findRequest(message.getPool(), message.getTaskId());
        }

        if (_requests.containsKey(uuid)) {
            message.setSucceeded();
        } else {
            message.setFailed(CacheException.INVALID_ARGS, "No such request");
        }

        return message;
    }

    public Message messageArrived(PoolMigrationCancelMessage message)
        throws CacheException
    {
        if (message.isReply()) {
            return null;
        }

        UUID uuid = message.getUUID();
        if (uuid == null) {
            uuid = findRequest(message.getPool(), message.getTaskId());
        }

        Request request = _requests.get(uuid);
        if (request == null || !request.cancel()) {
            throw new CacheException(CacheException.INVALID_ARGS,
                                     "No such request");
        }

        message.setSucceeded();
        return message;
    }

    /**
     * For compatibility with old pools that do not provide a
     * UUID. Will eventually be removed.
     */
    private UUID findRequest(String pool, long taskId)
    {
        for (Request request: _requests.values()) {
            if (request.getPool().equals(pool) &&
                request.getTaskId() == taskId) {
                return request.getUUID();
            }
        }
        return null;
    }

    private class Request implements CacheFileAvailable, Runnable
    {
        private final CellPath _requestor;
        private final UUID _uuid;
        private final PnfsId _pnfsId;
        private final StorageInfo _storageInfo;
        private final List<StickyRecord> _stickyRecords;
        private final EntryState _targetState;
        private final String _pool;
        private final long _taskId;
        private final boolean _computeChecksumOnUpdate;
        private Integer _companion;
        private Future _updateTask;

        public Request(CellPath requestor, PoolMigrationCopyReplicaMessage message)
        {
            _requestor = requestor;
            _pnfsId = message.getPnfsId();
            _storageInfo = message.getStorageInfo();
            _stickyRecords = message.getStickyRecords();
            _targetState = message.getState();
            _pool = message.getPool();
            _taskId = message.getTaskId();
            _computeChecksumOnUpdate = message.getComputeChecksumOnUpdate();

            if (_targetState != PRECIOUS && _targetState != CACHED) {
                throw new IllegalArgumentException("State must be either CACHED or PRECIOUS");
            }

            /* Old pools don't provide a UUID so we create one
             * ourselves. Will eventually be removed.
             */
            _uuid =
                (message.getUUID() != null) ? message.getUUID() : UUID.randomUUID();
        }

        public synchronized UUID getUUID()
        {
            return _uuid;
        }

        public synchronized String getPool()
        {
            return _pool;
        }

        public synchronized long getTaskId()
        {
            return _taskId;
        }

        public synchronized void start()
            throws UnknownHostException
        {
            EntryState state = _repository.getState(_pnfsId);
            if (state == EntryState.NEW) {
                _companion = _p2p.newCompanion(_pnfsId, _pool, _storageInfo,
                                               _targetState, _stickyRecords,
                                               this);
            } else {
                _updateTask = _executor.submit(this);
            }
        }

        public synchronized boolean cancel()
        {
            if (_companion != null) {
                return _p2p.cancel(_companion);
            } else if (_updateTask != null && _updateTask.cancel(true)) {
                if (_requests.remove(_uuid) != null) {
                    finished(new CacheException("Task was cancelled"));
                }
                return true;
            }
            return false;
        }

        protected synchronized void finished(Throwable e)
        {
            PoolMigrationCopyFinishedMessage message =
                new PoolMigrationCopyFinishedMessage(_uuid, _pool,
                                                     _pnfsId, _taskId);
            if (e != null) {
                if (e instanceof CacheException) {
                    CacheException ce = (CacheException) e;
                    message.setFailed(ce.getRc(), ce.getMessage());
                } else {
                    message.setFailed(CacheException.UNEXPECTED_SYSTEM_EXCEPTION,
                                      e);
                }
            }

            try {
                _requestor.revert();
                sendMessage(new CellMessage(_requestor, message));
            } catch (NoRouteToCellException f) {
                // We cannot tell the requestor that the
                // transfer has finished. Not much we can do
                // about that. The requestor will eventually
                // notice that nothing happens.
                _log.error("Transfer completed, but failed to send acknowledgment: " +
                           f.toString());
            }
        }

        public synchronized void cacheFileAvailable(String file, Throwable e)
        {
            _requests.remove(_uuid);
            finished(e);
        }

        public void run()
        {
            try {
                if (_computeChecksumOnUpdate) {
                    ReadHandle handle = _repository.openEntry(_pnfsId);
                    try {
                        File file = handle.getFile();
                        ChecksumFactory factory =
                            _checksumModule.getDefaultChecksumFactory();
                        Checksum checksum =
                            _checksumModule.calculateFileChecksum(file,
                                                                  factory.create());
                        _checksumModule.setMoverChecksums(_pnfsId,
                                                          file,
                                                          factory,
                                                          null,
                                                          checksum);
                    } finally {
                        handle.close();
                    }
                }

                EntryState state = _repository.getState(_pnfsId);
                switch (state) {
                case CACHED:
                    if (_targetState == PRECIOUS) {
                        _repository.setState(_pnfsId, PRECIOUS);
                    }
                    // fall through
                case PRECIOUS:
                    for (StickyRecord record: _stickyRecords) {
                        _repository.setSticky(_pnfsId,
                                              record.owner(),
                                              record.expire(),
                                              false);
                    }
                    break;
                default:
                    finished(new CacheException("Cannot update file in state " + state));

                }
                finished(null);
            } catch (NoRouteToCellException e) {
                finished(new CacheException(CacheException.TIMEOUT,
                                            "Communication failure during checksum calculation: " + e.getMessage()));
            } catch (IOException e) {
                finished(new CacheException(CacheException.ERROR_IO_DISK,
                                            "I/O error during checksum calculation: " + e.getMessage()));
            } catch (InterruptedException e) {
                finished(new CacheException("Task was cancelled"));
            } catch (IllegalTransitionException e) {
                finished(new CacheException("Cannot update file in state " + e.getSourceState()));
            } catch (CacheException e) {
                finished(e);
            } catch (RuntimeException e) {
                finished(e);
            } finally {
                _requests.remove(_uuid);
            }
        }
    }
}