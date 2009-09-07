package org.dcache.pool.repository.v5;

import org.apache.log4j.Logger;
import org.dcache.pool.FaultAction;
import org.dcache.pool.repository.SpaceRecord;
import org.dcache.pool.repository.FileStore;
import org.dcache.pool.repository.MetaDataStore;
import org.dcache.pool.repository.Account;

class CheckHealthTask implements Runnable
{
    private final static Logger _log = Logger.getLogger(CheckHealthTask.class);

    private final CacheRepositoryV5 _repository;

    /**
     * Shared repository account object for tracking space.
     */
    private Account _account;

    /**
     * Meta data about files in the pool.
     */
    private MetaDataStore _metaDataStore;

    CheckHealthTask(CacheRepositoryV5 repository)
    {
        _repository = repository;
    }

    public void setAccount(Account account)
    {
        _account = account;
    }

    public void setMetaDataStore(MetaDataStore store)
    {
        _metaDataStore = store;
    }

    public void run()
    {
        if (!_metaDataStore.isOk()) {
            _repository.fail(FaultAction.DISABLED, "I/O test failed");
        }

        if (!checkSpaceAccounting()) {
            _log.error("Marking pool read-only due to accounting errors. This is a bug. Please report it to support@dcache.org.");
            _repository.fail(FaultAction.READONLY,
                             "Accounting errors detected");
        }

        adjustFreeSpace();
    }

    private boolean checkSpaceAccounting()
    {
        SpaceRecord record = _account.getSpaceRecord();
        long removable = record.getRemovableSpace();
        long total = record.getTotalSpace();
        long free = record.getFreeSpace();
        long precious = record.getPreciousSpace();
        long used = total - free;

        if (removable < 0) {
            _log.error("Removable space is negative.");
            return false;
        }

        if (total < 0) {
            _log.error("Repository size is negative.");
            return false;
        }

        if (free < 0) {
            _log.error("Free space is negative.");
            return false;
        }

        if (precious < 0) {
            _log.error("Precious space is negative.");
            return false;
        }

        if (used < 0) {
            _log.error("Used space is negative.");
            return false;
        }

        /* The following check cannot be made consistently, since we
         * do not retrieve these values atomically. Therefore we log
         * the error, but do not return false.
         */
        if (precious + removable > used) {
            _log.warn("Used space is less than the sum of precious and removable space (this may be a temporary problem - if it persists then please report it to support@dcache.org).");
        }

        return true;
    }

    private void adjustFreeSpace()
    {
        /* At any time the file system must have at least as much free
         * space as shows in the account. Thus invariantly
         *
         *      _metaDataStore.getFreeSpace >= _account.getFree
         *
         * Taking the monitor lock on the account object prevents
         * anybody else from allocating space from the account. Hence
         * throughout the period we have the lock, the file system
         * must have at least as much free space as the account.
         */
        synchronized (_account) {
            long free = _metaDataStore.getFreeSpace();
            long total = _metaDataStore.getTotalSpace();

            if (total < _account.getTotal()) {
                _log.warn("The file system containing the data files appears to be smaller than the configured pool size.");
            }

            if (free < _account.getFree()) {
                _log.warn("The file system containing the data files appears to have less free space than expected; reducing the pool size to compensate. Notice that this does not leave any space for the meta data. If such data is stored on the same file system, then it is paramount that the pool size is reconfigured to leave enough space for the meta data.");

                _account.setTotal(_account.getTotal() -
                                  (_account.getFree() - free));
            }
        }
    }
}