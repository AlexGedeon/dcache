package org.dcache.services.hsmcleaner;

import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import java.util.ArrayList;

import diskCacheV111.vehicles.PoolManagerPoolUpMessage;
import diskCacheV111.pools.PoolV2Mode;

/**
 * Maintains an index of available pools. 
 *
 * The information maintained is based on pool up messages send by the
 * pools. The class does not itself subscribe to these messages, see
 * the <code>messageArrived</code> method.
 */
class PoolInformationBase
{
    /** 
     * Time in milliseconds after which pool information is
     * invalidated. 
     */
    private long _timeout = 5 * 60 * 1000; // 5 minutes

    /**
     * Map of all pools currently up.
     */
    private Map<String, PoolInformation> _pools = 
        new HashMap<String, PoolInformation>();

    /**
     * Map from HSM instance name to the set of pools attached to that
     * HSM.
     */
    private Map<String, Collection<PoolInformation>> _hsmToPool = 
        new HashMap<String, Collection<PoolInformation>>();

    /**
     * 
     */
    public PoolInformation getPool(String pool)
    {
        return _pools.get(pool);
    }

    /**
     * 
     */
    public Collection<PoolInformation> getPools()
    {
        return _pools.values();
    }

    /** 
     * Returns a pool attached to a given HSM instance.
     *
     * @param hsm An HSM instance name.
     */
    public PoolInformation getPoolWithHSM(String hsm)
    {
        Collection<PoolInformation> pools = _hsmToPool.get(hsm);
        if (pools != null) {
            for (PoolInformation pool : pools) {
                if (pool.getAge() <= _timeout 
                    && !pool.isDisabled(PoolV2Mode.DISABLED_STAGE)) {
                    return pool;                
                }
            }
        }
        return null;
    }

    /**
     * Removes information about a pool. The pool will be readded next
     * time a pool up message is received.
     *
     * @param name A pool name.
     */
    public void remove(String name)
    {
        PoolInformation pool = _pools.get(name);
        if (pool != null) {
            for (String hsm : pool.getHsmInstances()) {
                _hsmToPool.remove(hsm);
            }
            _pools.remove(name);
        }
    }

    /**
     * Message handler for PoolUp messages. The class does not
     * subscribe to these messages, so the client must implement a
     * mechanism with which these messages arrive here.
     */ 
    public void messageArrived(PoolManagerPoolUpMessage message)
    {
        String name = message.getPoolName();

        remove(name);

        PoolInformation pool = new PoolInformation(message);
        _pools.put(name, pool);

        /* Update HSM to pool map.
         */
        for (String hsm : pool.getHsmInstances()) {
            Collection<PoolInformation> pools = _hsmToPool.get(hsm);
            if (pools == null) {
                pools = new ArrayList<PoolInformation>();
                _hsmToPool.put(hsm, pools);
            }
            pools.add(pool);
        }
    }
}
