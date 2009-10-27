package diskCacheV111.util;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;

import dmg.cells.nucleus.CellAdapter;
import dmg.cells.nucleus.CDC;

import org.dcache.util.CDCThreadFactory;

/**
 *
 * ThreadPoolNG ( Thread Pool New Generation is a
 * java concurrent based implementation of dCache
 * Thread pool. While it's nothing else than wrapper
 * around ThreadPoolExecutor, it's better to replace all
 * instances of ThreadPool with pure  ThreadPoolExecutor.
 *
 * @since 1.8
 */
public class ThreadPoolNG implements ThreadPool {

    private static final int CORE_SIZE = 0;
    private static final int MAX_SIZE = Integer.MAX_VALUE;
    private static final long KEEP_ALIVE = 60L;

    private final ThreadPoolExecutor _executor;

    public ThreadPoolNG(CellAdapter cell)
    {
        this(cell.getNucleus());
    }

    public ThreadPoolNG()
    {
        this(Executors.defaultThreadFactory());
    }

    private ThreadPoolNG(ThreadFactory factory)
    {
        _executor = new ThreadPoolExecutor(CORE_SIZE,
                                           MAX_SIZE,
                                           KEEP_ALIVE,
                                           TimeUnit.SECONDS,
                                           new SynchronousQueue<Runnable>(),
                                           new CDCThreadFactory(factory));
    }


	public int getCurrentThreadCount() {
		return _executor.getActiveCount();
	}

	public int getMaxThreadCount() {
		return _executor.getMaximumPoolSize();
	}

	public int getWaitingThreadCount() {
		return 0;
	}

    public void invokeLater(final Runnable runner, String name)
    {
        final CDC cdc = new CDC();
        Runnable wrapper = new Runnable() {
                public void run()
                {
                    cdc.apply();
                    try {
                        runner.run();
                    } finally {
                        CDC.clear();
                    }
                }
            };

        _executor.execute(wrapper);
    }

	public void setMaxThreadCount(int maxThreadCount)
			throws IllegalArgumentException {

        /*
         * Be backward compatible with
         */
         if(maxThreadCount == 0)
             maxThreadCount = MAX_SIZE;

		_executor.setMaximumPoolSize(maxThreadCount);
	}

	public String toString() {
		return "ThreadPoolNG $Revision: 1.4 $ max/active: " + getMaxThreadCount() + "/" + getCurrentThreadCount();
	}
}
