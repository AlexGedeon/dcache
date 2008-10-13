package org.dcache.pool.migration;

import java.util.Collection;

import org.dcache.cell.CellStub;
import diskCacheV111.vehicles.PoolManagerGetPoolsByPoolGroupMessage;
import diskCacheV111.vehicles.PoolManagerGetPoolsMessage;

class PoolListByPoolGroup
    extends PoolListFromPoolManager
{
    private final CellStub _poolManager;
    private final String _poolGroup;

    public PoolListByPoolGroup(CellStub poolManager,
                               Collection<String> exclude,
                               double spaceFactor,
                               double cpuFactor,
                               String poolGroup)
    {
        super(exclude, spaceFactor, cpuFactor);
        _poolManager = poolManager;
        _poolGroup = poolGroup;
    }

    public void refresh()
    {
        _poolManager.send(new PoolManagerGetPoolsByPoolGroupMessage(_poolGroup),
                          PoolManagerGetPoolsMessage.class,
                          this);
    }

    public String toString()
    {
        return String.format("pool group %s, %d pools",
                             _poolGroup, _pools.size());
    }
}
