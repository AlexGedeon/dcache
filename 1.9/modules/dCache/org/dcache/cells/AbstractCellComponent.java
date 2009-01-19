package org.dcache.cells;

import java.io.PrintWriter;
import java.util.Dictionary;

import dmg.util.Args;
import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellInfo;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.NoRouteToCellException;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.SerializationException;

public class AbstractCellComponent
    implements CellInfoProvider,
               CellSetupProvider,
               CellMessageSender
{
    private CellEndpoint _endpoint;

    /**
     * Implements CellInfoProvider interface.
     */
    public void getInfo(PrintWriter pw) {}

    /**
     * Implements CellInfoProvider interface.
     */
    public CellInfo getCellInfo(CellInfo info)
    {
        return info;
    }

    /**
     * Implements CellSetupProvider interface.
     */
    public void printSetup(PrintWriter pw) {}

    /**
     * Implements CellSetupProvider interface.
     */
    public void afterSetupExecuted() {}

    /**
     * Implements CellMessageSender interface.
     */
    public void setCellEndpoint(CellEndpoint endpoint)
    {
        _endpoint = endpoint;
    }

    /**
     * Implements CellMessageSender interface.
     */
    protected CellEndpoint getCellEndpoint()
    {
        return _endpoint;
    }

    /**
     * Sends <code>envelope</code>.
     *
     * @param envelope the cell message to be sent.
     * @throws SerializationException if the payload object of this
     *         message is not serializable.
     * @throws NoRouteToCellException if the destination could not be
     *         reached.
     */
    protected void sendMessage(CellMessage envelope)
        throws SerializationException,
               NoRouteToCellException
    {
        _endpoint.sendMessage(envelope);
    }

    /**
     * Sends <code>envelope</code>. The <code>callback</code> argument
     * (which has to be non-null) allows to specify an object which is
     * informed as soon as an has answer arrived or if the timeout has
     * expired.
     *
     * @param envelope the cell message to be sent.
     * @param callback specifies an object class which will be informed
     *                 as soon as the message arrives.
     * @param timeout  is the timeout in msec.
     * @exception SerializationException if the payload object of this
     *            message is not serializable.
     */
    protected void sendMessage(CellMessage envelope,
                               CellMessageAnswerable callback,
                               long timeout)
        throws SerializationException
    {
        _endpoint.sendMessage(envelope, callback, timeout);
    }

    /**
     * Sends <code>envelope</code> and waits <code>timeout</code>
     * milliseconds for an answer to arrive.  The answer will bypass
     * the ordinary queuing mechanism and will be delivered before any
     * other asynchronous message.  The answer need to have the
     * getLastUOID set to the UOID of the message send with
     * sendAndWait. If the answer does not arrive withing the specified
     * time interval, the method returns <code>null</code> and the
     * answer will be handled as if it was an ordinary asynchronous
     * message.
     *
     * @param envelope the cell message to be sent.
     * @param timeout milliseconds to wait for an answer.
     * @return the answer or null if the timeout was reached.
     * @throws SerializationException if the payload object of this
     *         message is not serializable.
     * @throws NoRouteToCellException if the destination
     *         couldnot be reached.
     */
    protected CellMessage sendAndWait(CellMessage envelope, long timeout)
        throws SerializationException,
               NoRouteToCellException,
               InterruptedException
    {
        return _endpoint.sendAndWait(envelope, timeout);
    }

    /**
     * Provides information about the host cell.
     *
     * Depending on the cell, a subclass of CellInfo with additional
     * information may be returned instead.
     *
     * @return The cell information encapsulated in a CellInfo object.
     */
    protected CellInfo getCellInfo()
    {
        return _endpoint.getCellInfo();
    }

    /**
     * Returns the name of the cell hosting this component.
     */
    protected String getCellName()
    {
        return getCellInfo().getCellName();
    }

    /**
     * Returns the name of the domain hosting the cell hosting this
     * component.
     */
    protected String getCellDomainName()
    {
        return getCellInfo().getDomainName();
    }

    /**
     * Returns the domain context. The domain context is shared by all
     * cells in a domain.
     */
    protected Dictionary getDomainContext()
    {
        return _endpoint.getDomainContext();
    }

    /**
     * Returns the cell command line arguments provided when the cell
     * was created.
     */
    protected Args getArgs()
    {
        return _endpoint.getArgs();
    }
}