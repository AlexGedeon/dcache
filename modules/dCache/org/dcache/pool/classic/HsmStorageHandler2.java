// $Id$

package org.dcache.pool.classic;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.NoRouteToCellException;
import dmg.cells.nucleus.CellInfo;

import diskCacheV111.util.Batchable;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.CacheFileAvailable;
import diskCacheV111.util.FileNotInCacheException;
import diskCacheV111.util.FileInCacheException;
import diskCacheV111.util.HsmSet;
import diskCacheV111.util.InconsistentCacheException;
import diskCacheV111.util.JobScheduler;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.RunSystem;
import diskCacheV111.util.SimpleJobScheduler;
import diskCacheV111.util.Checksum;
import diskCacheV111.vehicles.PoolFileFlushedMessage;
import diskCacheV111.vehicles.PoolRemoveFilesFromHSMMessage;
import diskCacheV111.vehicles.StorageInfo;
import diskCacheV111.vehicles.StorageInfoMessage;

import org.dcache.pool.repository.Repository;
import org.dcache.pool.repository.IllegalTransitionException;
import org.dcache.pool.repository.WriteHandle;
import org.dcache.pool.repository.ReadHandle;
import org.dcache.pool.repository.CacheEntry;
import org.dcache.pool.repository.StickyRecord;
import org.dcache.pool.repository.EntryState;
import org.dcache.cells.AbstractCellComponent;

public class HsmStorageHandler2
    extends AbstractCellComponent
{
    private static Logger _log =
        Logger.getLogger(HsmStorageHandler2.class);

    private final Repository _repository;
    private final HsmSet _hsmSet;
    private final PnfsHandler _pnfs;
    private final ChecksumModuleV1 _checksumModule;
    private final Map<PnfsId, StoreThread> _storePnfsidList   = new HashMap();
    private final Map<PnfsId, FetchThread> _restorePnfsidList = new HashMap();
    private final JobScheduler _fetchQueue;
    private final JobScheduler _storeQueue;
    private final boolean _checkPnfs = true;
    private final Executor _hsmRemoveExecutor =
        Executors.newSingleThreadExecutor();
    private final ThreadPoolExecutor _hsmRemoveTaskExecutor =
        new ThreadPoolExecutor(1, 1, Long.MAX_VALUE, TimeUnit.NANOSECONDS,
                               new LinkedBlockingQueue());

    private long _maxRuntime = 4 * 3600 * 1000; // 4 hours
    private long _maxStoreRun = _maxRuntime;
    private long _maxRestoreRun = _maxRuntime;
    private long _maxRemoveRun = _maxRuntime;
    private int _maxLines = 200;
    private String _cellName;
    private String _domainName;

    private String _flushMessageTarget;

    ////////////////////////////////////////////////////////////////////////////////
    //
    //    the generic part
    //
    public class Info
    {
        private final List<CacheFileAvailable> _callbacks = new ArrayList();
        private final PnfsId _pnfsId;
        private long _startTime = System.currentTimeMillis();
        private Thread _thread;
        private boolean _active = false;

        private Info(PnfsId pnfsId)
        {
            _pnfsId = pnfsId;
        }

        public PnfsId getPnfsId()
        {
            return _pnfsId;
        }

        public int getListenerCount()
        {
            return _callbacks.size();
        }

        public long getStartTime()
        {
            return _startTime;
        }

        public  Thread getThread()
        {
            return _thread;
        }

        public void startThread()
        {
            _thread.start();
        }

        public void done()
        {
            _active = false;
        }

        synchronized void addCallback(CacheFileAvailable callback)
        {
            _callbacks.add(callback);
        }

        synchronized void executeCallbacks(Throwable exc)
        {
            _log.debug("excecuting callbacks  "
                       + _pnfsId + " (callback=" + _callbacks + ") " + exc);
            for (CacheFileAvailable callback : _callbacks) {
                try {
                    callback.cacheFileAvailable(_pnfsId.toString(), exc);
                } catch (Exception e) {
                    _log.fatal("Exception in callback to " +
                               callback.getClass().getName(), e);
                }
            }
        }

        void setThread(Thread thread)
        {
            _thread = thread;
        }
    }

    public HsmStorageHandler2(Repository repository,
                              HsmSet hsmSet,
                              PnfsHandler pnfs,
                              ChecksumModuleV1 checksumModule)
    {
        _repository = repository;
        _hsmSet = hsmSet;
        _pnfs = pnfs;
        _checksumModule = checksumModule;

        _fetchQueue = new SimpleJobScheduler("fetch");
        _storeQueue = new SimpleJobScheduler("store");
    }

    public void shutdown()
    {
        _fetchQueue.shutdown();
        _storeQueue.shutdown();
    }

    public void setCellEndpoint(CellEndpoint endpoint)
    {
        super.setCellEndpoint(endpoint);
        _cellName = getCellName();
        _domainName = getCellDomainName();
    }


    public void setFlushMessageTarget(String flushMessageTarget)
    {
        _flushMessageTarget = flushMessageTarget;
    }

    private void assertInitialized()
    {
        if (getCellEndpoint() == null)
            throw new IllegalStateException("Cell endpoint must be set");
        if (_flushMessageTarget == null)
            throw new IllegalStateException("Flush message target must be set");
    }

    synchronized void setTimeout(long storeTimeout, long restoreTimeout, long removeTimeout)
    {
        if (storeTimeout > 0) _maxStoreRun = storeTimeout;
        if (restoreTimeout > 0) _maxRestoreRun = restoreTimeout;
        if (removeTimeout > 0) _maxRemoveRun = removeTimeout;
    }

    public synchronized void setMaxActiveRestores(int restores)
    {
        _fetchQueue.setMaxActiveJobs(restores);
    }

    public synchronized void printSetup(PrintWriter pw)
    {
        pw.println("#\n# HsmStorageHandler2("+getClass().getName()+")\n#");
        pw.println("rh set max active "+_fetchQueue.getMaxActiveJobs());
        pw.println("st set max active "+_storeQueue.getMaxActiveJobs());
        pw.println("rm set max active " + getMaxRemoveJobs());
        pw.println("rh set timeout "+(_maxRestoreRun/1000L));
        pw.println("st set timeout "+(_maxStoreRun/1000L));
        pw.println("rm set timeout "+(_maxRemoveRun/1000L));
    }

    public synchronized void getInfo(PrintWriter pw)
    {
        pw.println("         Version  : $Id$");
        pw.println(" Restore Timeout  : "+(_maxRestoreRun/1000L));
        pw.println("   Store Timeout  : "+(_maxStoreRun/1000L));
        pw.println("  Remove Timeout  : "+(_maxRemoveRun/1000L));
        pw.println("  Job Queues ");
        pw.println("    to store   "+_storeQueue.getActiveJobs()+
                    "("+_storeQueue.getMaxActiveJobs()+
                    ")/"+_storeQueue.getQueueSize());
        pw.println("    from store "+_fetchQueue.getActiveJobs()+
                    "("+_fetchQueue.getMaxActiveJobs()+
                    ")/"+_fetchQueue.getQueueSize());
        pw.println("    delete     "+ "" +
                    "(" + getMaxRemoveJobs() +
                    ")/"+"");
    }

    private synchronized String
        getSystemCommand(File file, PnfsId pnfsId, StorageInfo storageInfo,
                         HsmSet.HsmInfo hsm, String direction)
            throws CacheException
    {
        String hsmCommand = hsm.getAttribute("command");
        if (hsmCommand == null)
            throw new
                IllegalArgumentException("hsmCommand not specified in HsmSet");

        String localPath = file.getPath();

        StringBuilder sb = new StringBuilder();

        sb.append(hsmCommand).append(" ").
            append(direction).append(" ").
            append(pnfsId).append("  ").
            append(localPath);


        sb.append(" -si=").append(storageInfo.toString());
        for (Map.Entry<String,String> attr : hsm.attributes()) {
            String key = attr.getKey();
            String val = attr.getValue();
            sb.append(" -").append(key);
            if ((val != null) && (val.length() > 0))
                sb.append("=").append(val);
        }

        if (!storageInfo.locations().isEmpty()) {
            /*
             * new style
             */

            for (URI location: storageInfo.locations()) {
                if (location.getScheme().equals(hsm.getType()) && location.getAuthority().equals(hsm.getInstance())) {
                    sb.append(" -uri=").append(location.toString());
                    break;
                }
            }
        }

        String completeCommand = sb.toString();
        _log.debug("HSM_COMMAND: " + completeCommand);
        return completeCommand;
    }

    //////////////////////////////////////////////////////////////////////
    //
    //   the fetch part
    //
    public JobScheduler getFetchScheduler()
    {
        return _fetchQueue;
    }

    public synchronized void fetch(PnfsId pnfsId,
                                   StorageInfo storageInfo,
                                   CacheFileAvailable callback)
        throws FileInCacheException, CacheException
    {
        assertInitialized();

        FetchThread info = _restorePnfsidList.get(pnfsId);

        if (info != null) {
            if (callback != null)
                info.addCallback(callback);
            return;
        }

        info = new FetchThread(pnfsId, storageInfo);
        if (callback != null)
            info.addCallback(callback);

        try {
            _fetchQueue.add(info);
            _restorePnfsidList.put(pnfsId, info);
        } catch (InvocationTargetException e) {
            /* This happens when the queued method of the FetchThread
             * throws an exception. They have been designed not to
             * throw any exceptions, so if this happens it must be a
             * bug.
             */
            throw new RuntimeException("Failed to queue fetch request",
                                       e.getCause());
        }
    }

    protected synchronized void removeFetchEntry(PnfsId id)
    {
        _restorePnfsidList.remove(id);
    }

    public synchronized Collection<PnfsId> getPnfsIds()
    {
    	// to avoid extra buffer copy tell array size in advance
        List<PnfsId> v =
            new ArrayList<PnfsId>(_restorePnfsidList.size() + _storePnfsidList.size());

        v.addAll(_restorePnfsidList.keySet());
        v.addAll(_storePnfsidList.keySet());

        return v;
    }

    public synchronized Collection<PnfsId> getStorePnfsIds()
    {
        return new ArrayList<PnfsId>(_storePnfsidList.keySet());
    }

    public synchronized Collection <PnfsId> getRestorePnfsIds()
    {
        return new ArrayList<PnfsId>(_restorePnfsidList.keySet());
    }

    public synchronized Info getRestoreInfoByPnfsId(PnfsId pnfsId)
    {
        return _restorePnfsidList.get(pnfsId);
    }

    /**
     * Returns the name of an HSM accessible for this pool and which
     * contains the given file. Returns null if no such HSM exists.
     */
    private String findAccessibleLocation(StorageInfo file)
    {
        if (file.locations().isEmpty()
            && _hsmSet.getHsmInstances().contains(file.getHsm())) {
            // This is for backwards compatibility until all info
            // extractors support URIs.
            return file.getHsm();
        } else {
            for (URI location : file.locations()) {
                if (_hsmSet.getHsmInstances().contains(location.getAuthority())) {
                    return location.getAuthority();
                }
            }
        }
        return null;
    }

    private synchronized String
        getFetchCommand(File file, PnfsId pnfsId, StorageInfo storageInfo)
            throws CacheException
    {
        String instance = findAccessibleLocation(storageInfo);
        if (instance == null) {
            throw new
                IllegalArgumentException("HSM not defined on this pool: " +
                                         storageInfo.locations());
        }
        HsmSet.HsmInfo hsm = _hsmSet.getHsmInfoByName(instance);

        _log.debug("getFetchCommand for pnfsid=" + pnfsId +
                   ";hsm=" + instance + ";si=" + storageInfo);

        return getSystemCommand(file, pnfsId, storageInfo, hsm, "get");
    }

    private class FetchThread extends Info implements Batchable
    {
        private final WriteHandle _handle;
        private final StorageInfoMessage _infoMsg;
        private long _timestamp = 0;
        private int _id;
        private Thread _thread;

        public FetchThread(PnfsId pnfsId, StorageInfo storageInfo)
            throws CacheException, FileInCacheException
        {
            super(pnfsId);
            String address = _cellName + "@" + _domainName;
            _infoMsg = new StorageInfoMessage(address, pnfsId, true);
            _infoMsg.setStorageInfo(storageInfo);

            long fileSize = storageInfo.getFileSize();

            // Fixme!
            if (fileSize == 0)
                throw new
                    CacheException("Couldn't get file size of " + pnfsId);

            _infoMsg.setFileSize(fileSize);

//             StickyRecord sticky = null;
//             String value = storageInfo.getKey("flag-s");
//             if (value != null && value.length() > 0) {
//                 say("setting sticky bit of " + pnfsId);
//                 sticky = new StickyRecord("system", -1);
//             }

            List<StickyRecord> stickyRecords = Collections.emptyList();
            _handle = _repository.createEntry(pnfsId,
                                              storageInfo,
                                              EntryState.FROM_STORE,
                                              EntryState.CACHED,
                                              stickyRecords);
        }

        @Override
        public String toString()
        {
            return getPnfsId().toString();
        }

        public String getClient()
        {
            return "[Unknown]";
        }

        public long getClientId()
        {
            return 0;
        }

        public void queued(int id)
        {
            _timestamp = System.currentTimeMillis();
            _id = id;
        }

        public double getTransferRate()
        {
            return 10.0;
        }

        @Override
        protected synchronized void setThread(Thread thread)
        {
            _thread = thread;
        }

        public synchronized boolean kill()
        {
            if (_thread == null)
                return false;

            _thread.interrupt();
            return true;
        }

        private void sendBillingInfo()
        {
            try {
                sendMessage(new CellMessage(new CellPath("billing"), _infoMsg));
            } catch (NoRouteToCellException e) {
                _log.error("Failed to send message to billing: " + e.getMessage());
            }
        }

        public void unqueued()
        {
            PnfsId pnfsId = getPnfsId();

            try {
                _log.info("Dequeuing " + pnfsId);
                _handle.close();
            } finally {
                removeFetchEntry(pnfsId);

                CacheException e =
                    new CacheException(33, "Job dequeued (by operator)");

                executeCallbacks(e);

                _infoMsg.setTimeQueued(System.currentTimeMillis() - _timestamp);
                _infoMsg.setResult(e.getRc(), e.getMessage());
                sendBillingInfo();
            }
        }

        public void run()
        {
            int returnCode = 1;
            Exception excep = null;
            PnfsId pnfsId = getPnfsId();
            CacheEntry entry = _handle.getEntry();
            StorageInfo storageInfo = entry.getStorageInfo();

            try {
                setThread(Thread.currentThread());

                _log.debug("FetchThread started");

                long now = System.currentTimeMillis();
                _infoMsg.setTimeQueued(now - _timestamp);
                _timestamp = now;

                String fetchCommand =
                    getFetchCommand(_handle.getFile(), pnfsId, storageInfo);
                long fileSize = storageInfo.getFileSize();

                _log.debug("Waiting for space (" + fileSize + " bytes)");
                _handle.allocate(fileSize);
                _log.debug("Got Space (" + fileSize + " bytes)");

                RunSystem run =
                    new RunSystem(fetchCommand, _maxLines, _maxRestoreRun);
                run.go();
                returnCode = run.getExitValue();
                if (returnCode != 0) {
                    /*
                     * while shell do not return error code bigger than 255,
                     * do a trick here
                     */
                    if (returnCode == 71 ) {
                        returnCode = CacheException.HSM_DELAY_ERROR;
                    }
                    excep  =
                        new CacheException(returnCode, run.getErrorString());
                    _log.error("HSM script returned " + returnCode
                               + ": " + run.getErrorString());
                }

                doChecksum(_handle);
                _handle.commit(null);
            } catch (CacheException e) {
                _log.error(e);
                returnCode = 1;
                excep = e;
            } catch (InterruptedException e) {
                _log.error("Process interrupted (timed out)");
                returnCode = 1;
                excep = e;
            } catch (IOException e) {
                _log.error("Process got an IOException: " + e);
                returnCode = 2;
                excep = e;
            } catch (IllegalThreadStateException  e) {
                _log.error("Cannot stop process: " + e);
                returnCode = 3;
                excep = e;
            } catch (IllegalArgumentException e) {
                _log.error("Cannot determine 'hsmInfo': " + e.getMessage());
                returnCode = 4;
                excep = e;
            } catch (NoRouteToCellException e) {
                _log.error("Cell communication error: " + e.getMessage());
                returnCode = 6;
                excep = e;
            } catch (Exception e) {
                _log.fatal(e, e);
                returnCode = 5;
                excep = e;
            } finally {
                /* Surpress thread interruptions after this point.
                 */
                setThread(null);
                Thread.interrupted();

                _handle.close();
                removeFetchEntry(pnfsId);
                executeCallbacks(excep);

                if (excep != null) {
                    if (excep instanceof CacheException) {
                        _infoMsg.setResult(((CacheException)excep).getRc(),
                                           ((CacheException)excep).getMessage());
                    } else {
                        _infoMsg.setResult(44, excep.toString());
                    }
                }
                _infoMsg.setTransferTime(System.currentTimeMillis() - _timestamp);
                sendBillingInfo();

                _log.info("File successfully restored from tape");
            }
        }

        private void doChecksum(WriteHandle handle)
            throws CacheException, InterruptedException, NoRouteToCellException
        {
            /* Return early without opening the entry if we don't need
             * to.
             */
            if (!_checksumModule.getCrcFromHsm() &&
                !_checksumModule.checkOnRestore())
                return;

            /* Check the checksum.
             */
            CacheEntry entry = handle.getEntry();
            PnfsId pnfsId = entry.getPnfsId();

            Checksum infoChecksum, fileChecksum;
            try {
                if (_checksumModule.getCrcFromHsm()) {
                    infoChecksum = getChecksumFromHsm(handle.getFile());
                    if (infoChecksum != null) {
                        _log.info("Imported checksum from HSM: " + infoChecksum);
                        _checksumModule.storeChecksumInPnfs(pnfsId, infoChecksum);
                    }
                } else {
                    infoChecksum = null;
                }

                if (!_checksumModule.checkOnRestore())
                    return;

                if (infoChecksum == null) {
                    infoChecksum = _checksumModule.getChecksumFromPnfs(pnfsId);
                    if (infoChecksum == null) {
                        _log.error("No checksum found; checksum not verified after restore.");
                        return;
                    }
                }

                long start = System.currentTimeMillis();

                fileChecksum =
                    _checksumModule.calculateFileChecksum(handle.getFile(),
                                                          _checksumModule.getDefaultChecksum());

                _log.debug("Checksum for " + pnfsId + " info=" + infoChecksum
                           + ";file=" + fileChecksum + " in "
                           + (System.currentTimeMillis() - start));
            } catch (IOException e) {
                throw new CacheException(1010, "Checksum calculation failed due to I/O error: " + e.getMessage());
            } catch (CacheException e) {
                throw new CacheException(1010, "Checksum calculation failed: " + e.getMessage());
            }

            /* Report failure in case of mismatch.
             */
            if (!infoChecksum.equals(fileChecksum)) {
                _log.debug("Checksum error; expected="
                           + infoChecksum + ";file=" + fileChecksum);
                throw new CacheException(1009,
                                         "Checksum error: expected=" + infoChecksum
                                         + ";actual=" + fileChecksum);
            }
        }

        private Checksum getChecksumFromHsm(File file)
            throws IOException
        {
            file = new File(file.getCanonicalPath() + ".crcval");
            try {
                String line;
                if (file.exists()) {
                    BufferedReader br =
                        new BufferedReader(new FileReader(file));
                    try {
                        line = "1:" + br.readLine();
                    } finally {
                        try {
                            br.close();
                        } catch (IOException e) {
                        }
                        file.delete();
                    }
                    return new Checksum(line);
                }
            } catch (FileNotFoundException e) {
                /* Should not happen unless somebody else is removing
                 * the file before we got a chance to read it.
                 */
                throw new RuntimeException("File not found: " + file, e);
            }
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    //   the remove part
    //

    public synchronized void setMaxRemoveJobs(int max)
    {
        _hsmRemoveTaskExecutor.setCorePoolSize(max);
        _hsmRemoveTaskExecutor.setMaximumPoolSize(max);
    }

    public synchronized int getMaxRemoveJobs()
    {
        return _hsmRemoveTaskExecutor.getMaximumPoolSize();
    }

    public synchronized void remove(CellMessage message)
    {
        assertInitialized();

        assert message.getMessageObject() instanceof PoolRemoveFilesFromHSMMessage;

        HsmRemoveTask task =
            new HsmRemoveTask(getCellEndpoint(),
                              _hsmRemoveTaskExecutor,
                              _hsmSet, _maxRemoveRun, message);
        _hsmRemoveExecutor.execute(task);
    }

    //////////////////////////////////////////////////////////////////////
    //
    //   the store part
    //
    private synchronized String
        getStoreCommand(File file, PnfsId pnfsId, StorageInfo storageInfo)
            throws CacheException
    {
        String hsmType = storageInfo.getHsm();
        _log.debug("getStoreCommand for pnfsid=" + pnfsId +
                   ";hsm=" + hsmType + ";si=" + storageInfo);
        List<HsmSet.HsmInfo> hsms = _hsmSet.getHsmInfoByType(hsmType);
        if (hsms.isEmpty()) {
            throw new
                IllegalArgumentException("Info not found for : " + hsmType);
        }

        // If multiple HSMs are defined for the given type, then we
        // currently pick the first. We may consider randomizing this
        // choice.
        HsmSet.HsmInfo hsm = hsms.get(0);

        return getSystemCommand(file, pnfsId, storageInfo, hsm, "put");
    }

    public synchronized Info getStoreInfoByPnfsId(PnfsId pnfsId)
    {
        return _storePnfsidList.get(pnfsId);
    }

    public JobScheduler getStoreScheduler()
    {
        return _storeQueue;
    }

    public synchronized boolean store(PnfsId pnfsId, CacheFileAvailable callback)
        throws CacheException
    {
        assertInitialized();

        _log.debug("store requested for " + pnfsId +
                   (callback == null ? " w/o " : " with ") + "callback");

        if (_repository.getState(pnfsId) == EntryState.CACHED) {
            _log.debug("is already cached " + pnfsId);
            return true;
        }

        StoreThread  info = _storePnfsidList.get(pnfsId);
        if (info != null) {
            if (callback != null)
                info.addCallback(callback);
            _log.debug("flush already in progress "
                       + pnfsId + " (callback=" + callback + ")");
            return false;
        }

        info = new StoreThread(pnfsId);
        if (callback != null)
            info.addCallback(callback);

        try {
            _storeQueue.add(info);
        } catch (InvocationTargetException  e) {
            throw new RuntimeException("Failed to queue store request",
                                       e.getCause());
        }

        _storePnfsidList.put(pnfsId, info);
        _log.debug("added to flush queue " + pnfsId + " (callback="+callback+")");
        return false;
    }

    protected synchronized void removeStoreEntry(PnfsId id)
    {
        _storePnfsidList.remove(id);
    }

    private class StoreThread extends Info implements Batchable
    {
        private final StorageInfoMessage _infoMsg;
        private long _timestamp = 0;
        private int _id;
        private Thread _thread;

	public StoreThread(PnfsId pnfsId)
            throws FileNotInCacheException
        {
	    super(pnfsId);
            String address = _cellName + "@" + _domainName;
            _infoMsg = new StorageInfoMessage(address, pnfsId, false);
	}

        @Override
        public String toString()
        {
            return getPnfsId().toString();
        }

        public double getTransferRate()
        {
            return 10.0;
        }

        public String getClient()
        {
            return "[Unknown]";
        }

        public long getClientId()
        {
            return 0;
        }

        @Override
        protected synchronized void setThread(Thread thread)
        {
            _thread = thread;
        }

        public synchronized boolean kill()
        {
            if (_thread == null)
                return false;

            _thread.interrupt();
            return true;
        }

        public void queued(int id)
        {
            _timestamp = System.currentTimeMillis();
            _id = id;
        }

        private void sendBillingInfo()
        {
            try {
                sendMessage(new CellMessage(new CellPath("billing"), _infoMsg));
            } catch (NoRouteToCellException e) {
                _log.error("Failed to send message to billing: " + e.getMessage());
            }
        }

        public void unqueued()
        {
            removeStoreEntry(getPnfsId());

            CacheException e =
                new CacheException(44, "Job dequeued (by operator)");

            executeCallbacks(e);

            _infoMsg.setTimeQueued(System.currentTimeMillis() - _timestamp);
            _infoMsg.setResult(e.getRc(), e.getMessage());
            sendBillingInfo();
        }

	public void run()
        {
            int returnCode;
            PnfsId pnfsId = getPnfsId();
            Throwable excep = null;

            try {
                setThread(Thread.currentThread());

                _log.debug("Store thread started " + _thread);

                if (_checkPnfs) {
                    _log.debug("Getting storageinfo");
                    try {
                        _pnfs.getStorageInfo(pnfsId.toString());
                    } catch (CacheException e) {
                        throw new CacheException("Cannot verify that file still exists:" + e.getMessage());
                    }
                }


                StorageInfo storageInfo;
                ReadHandle handle = _repository.openEntry(pnfsId);
                try {
                    storageInfo = handle.getEntry().getStorageInfo();
                    _infoMsg.setStorageInfo(storageInfo);
                    _infoMsg.setFileSize(storageInfo.getFileSize());
                    long now = System.currentTimeMillis();
                    _infoMsg.setTimeQueued(now - _timestamp);
                    _timestamp = now;

                    String storeCommand =
                        getStoreCommand(handle.getFile(), pnfsId, storageInfo);

                    RunSystem run =
                        new RunSystem(storeCommand, _maxLines, _maxStoreRun);
                    run.go();
                    returnCode = run.getExitValue();
                    if (returnCode != 0) {
                        _log.error("HSM script returned " + returnCode
                                   + ": " + run.getErrorString());
                        throw new CacheException(returnCode, run.getErrorString());
                    }

                    String outputData = run.getOutputString();
                    if (outputData != null && outputData.length() != 0) {
                        BufferedReader in =
                            new BufferedReader(new StringReader(outputData));
                        String line = null;
                        try {
                            while ((line = in.readLine()) != null) {
                                URI location = new URI(line);
                                storageInfo.addLocation(location);
                                storageInfo.isSetAddLocation(true);
                                _log.debug(pnfsId.toString()
                                           + ": added HSM location "
                                           + location);
                            }
                        } catch (URISyntaxException use) {
                            _log.error("HSM script produced BAD URI: " + line);
                            throw new CacheException(2, use.getMessage());
                        } catch (IOException ie) {
                            // never happens on strings
                            throw new RuntimeException("Bug detected");
                        }
                    }
                } finally {
                    /* Surpress thread interruptions after this point.
                     */
                    setThread(null);
                    Thread.interrupted();

                    handle.close();
                }

                for (;;) {
                    try {
                        _pnfs.fileFlushed(pnfsId, storageInfo);
                        break;
                    } catch (CacheException e) {
                        if (e.getRc() == CacheException.FILE_NOT_FOUND ||
                            e.getRc() == CacheException.NOT_IN_TRASH) {
                            /* In case the file was deleted, we are
                             * presented with the problem that the
                             * file is now on tape, however the
                             * location has not been registered
                             * centrally. Hence the copy on tape will
                             * not be removed by the HSM cleaner. The
                             * sensible thing seems to be to remove
                             * the file from tape here. For now we
                             * ignore this issue (REVISIT).
                             */
                            break;
                        }

                        /* The message to the PnfsManager
                         * failed. There are several possible
                         * reasons for this; we may have lost the
                         * connection to the PnfsManager; the
                         * PnfsManager may have lost its
                         * connection to PNFS or otherwise be in
                         * trouble; bugs; etc.
                         *
                         * We keep retrying until we succeed. This
                         * will effectively block this thread from
                         * flushing any other files, which seems
                         * sensible when we have trouble talking
                         * to the PnfsManager. If the pool crashes
                         * or gets restarted while waiting here,
                         * we will end up flushing the file
                         * again. We assume that the HSM script is
                         * able to eliminate the duplicate; or at
                         * least tolerate the duplicate (given
                         * that this situation should be rare, we
                         * can live with a little bit of wasted
                         * tape).
                         */
                        _log.error("Error notifying PNFS about a flushed file: "
                             + e.getMessage() + "(" + e.getRc() + ")");
                    }
                    Thread.sleep(120000); // 2 minutes
                }

                notifyFlushMessageTarget(storageInfo);

                _repository.setState(pnfsId, EntryState.CACHED);
            } catch (IllegalTransitionException e) {
                /* Apparently the file is no longer precious. Most
                 * likely it got deleted, which is fine, since the
                 * flush already succeeded.
                 */
            } catch (CacheException e) {
                excep = e;
                _log.error("CacheException : " + e);
                _infoMsg.setResult(e.getRc(), e.getMessage());
            } catch (InterruptedException e) {
                excep = e;
                _log.error("Process interrupted (timed out)");
                _infoMsg.setResult(1, "Flush timed out");
            } catch (IOException e) {
                excep = e;
                _log.error("Process got an IOException : " + e);
                _infoMsg.setResult(2, "IO Error: " + e.getMessage());
            } catch (IllegalThreadStateException e) {
                excep = e;
                _log.error("Cannot stop process : " + e);
                _infoMsg.setResult(3, e.getMessage());
            } catch (IllegalArgumentException e) {
                excep = e;
                _log.error("Cannot determine 'hsmInfo': " + e.getMessage());
                _infoMsg.setResult(4, e.getMessage());
            } catch (Throwable t) {
                excep = t;
                _log.fatal("Unexpected exception", t);
                _infoMsg.setResult(666, t.getMessage());
            } finally {
                removeStoreEntry(pnfsId);
                _infoMsg.setTransferTime(System.currentTimeMillis() - _timestamp);

                sendBillingInfo();

                executeCallbacks(excep);

                _log.info("File successfully stored to tape");
            }
        }

        private void notifyFlushMessageTarget(StorageInfo info)
        {
            try {
                PoolFileFlushedMessage poolFileFlushedMessage =
                    new PoolFileFlushedMessage(_cellName, getPnfsId(), info);
                /*
                 * no replays from secondary message targets
                 */
                poolFileFlushedMessage.setReplyRequired(false);
                CellMessage msg =
                    new CellMessage(new CellPath(_flushMessageTarget),
                                    poolFileFlushedMessage);
                sendMessage(msg);
            } catch (NoRouteToCellException e) {
                _log.info("Failed to send message to " + _flushMessageTarget + ": "
                          + e.getMessage());
            }
        }
    }
}
