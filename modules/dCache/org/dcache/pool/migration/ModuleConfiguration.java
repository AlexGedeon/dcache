package org.dcache.pool.migration;

import java.util.concurrent.ScheduledExecutorService;

import dmg.cells.nucleus.CellEndpoint;

import org.dcache.cells.CellStub;
import org.dcache.pool.repository.Repository;

/**
 * Configuration shared by all jobs.
 */
public class ModuleConfiguration
{
    private String _poolName;
    private ScheduledExecutorService _executor;
    private CellStub _pool;
    private CellStub _pnfs;
    private CellStub _poolManager;
    private Repository _repository;

    public String getPoolName()
    {
        return _poolName;
    }

    public void setPoolName(String poolName)
    {
        _poolName = poolName;
    }

    public ScheduledExecutorService getExecutor()
    {
        return _executor;
    }

    public void setExecutor(ScheduledExecutorService executor)
    {
        _executor = executor;
    }

    public CellStub getPoolStub()
    {
        return _pool;
    }

    public void setPoolStub(CellStub pool)
    {
        _pool = pool;
    }

    public CellStub getPnfsStub()
    {
        return _pnfs;
    }

    public void setPnfsStub(CellStub pnfs)
    {
        _pnfs = pnfs;
    }

    public CellStub getPoolManagerStub()
    {
        return _poolManager;
    }

    public void setPoolManagerStub(CellStub poolManager)
    {
        _poolManager = poolManager;
    }

    public Repository getRepository()
    {
        return _repository;
    }

    public void setRepository(Repository repository)
    {
        _repository = repository;
    }
}