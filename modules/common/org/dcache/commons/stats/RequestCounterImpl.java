/*
 * Counter.java
 *
 * Created on April 4, 2009, 5:36 PM
 */

package org.dcache.commons.stats;
import java.util.Formatter;

/**
 * This class incapsulates two integer counters and  provides utility methods
 * for increments and discovery of the count of  request invocations and
 * failures 
 * This class is thread safe.
 * @author timur
 */
public class RequestCounterImpl implements RequestCounter {
    private final String name;
    private int   requests = 0;
    private int    failed  = 0;
    
    /** Creates a new instance of Counter
     * @param name
     */
    public RequestCounterImpl(String name) {
        this.name = name;
    }

    /**
     *
     * @return name of this counter
     */
    public String getName() {
        return name;
    }

    @Override
    public synchronized String toString() {
        StringBuilder sb = new StringBuilder();

        Formatter formatter = new Formatter(sb);

        formatter.format("%-32s %9d %9d", name, requests,  failed);
        formatter.flush();
        formatter.close();

        return sb.toString();
    }

    /**
     *
     * @return number of request invocations known to this counter
     */
    public synchronized int getTotalRequests() {
        return requests;
    }

    /**
     * increments the number of request invocations known to this counter
     * @param requests number by which to increment
     */
    public synchronized void incrementRequests(int requests) {
        this.requests += requests;
    }

    /**
     * increments the number of request invocations known to this counter by 1
     */
    public synchronized void incrementRequests() {
        requests++;
    }

    /**
     *
     * @return number of faild request invocations known to this counter
     */
    public synchronized int getFailed() {
        return failed;
    }

    /**
     * increments the number of failed request invocations known to this
     * counter
     * @param failed number by which to increment
     */
    public synchronized void incrementFailed(int failed) {
        this.failed += failed;
    }

    /**
     * increments the number of faild request invocations known to this counter
     * by 1
     */
    public synchronized void incrementFailed() {
        failed++;
    }

    /**
     *
     * @return number of requests that succesed
     *  This number is calculated as a difference between the
     *  total number of requests executed and the failed requests.
     *  The number of Successful requests  is accurate only if both
     *  number of requests executed and the failed requests are recorded
     *  accurately
     */
    public synchronized int getSuccessful() {
        return requests - failed;
    }
        
}
