/*
  COPYRIGHT STATUS:
  Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
  software are sponsored by the U.S. Department of Energy under Contract No.
  DE-AC02-76CH03000. Therefore, the U.S. Government retains a  world-wide
  non-exclusive, royalty-free license to publish or reproduce these documents
  and software for U.S. Government purposes.  All documents and software
  available from this server are protected under the U.S. and Foreign
  Copyright Laws, and FNAL reserves all rights.


  Distribution of the software available from this server is free of
  charge subject to the user following the terms of the Fermitools
  Software Legal Information.

  Redistribution and/or modification of the software shall be accompanied
  by the Fermitools Software Legal Information  (including the copyright
  notice).

  The user is asked to feed back problems, benefits, and/or suggestions
  about the software to the Fermilab Software Providers.


  Neither the name of Fermilab, the  URA, nor the names of the contributors
  may be used to endorse or promote products derived from this software
  without specific prior written permission.



  DISCLAIMER OF LIABILITY (BSD):

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED  WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL FERMILAB,
  OR THE URA, OR THE U.S. DEPARTMENT of ENERGY, OR CONTRIBUTORS BE LIABLE
  FOR  ANY  DIRECT, INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
  OF SUBSTITUTE  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY  OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE  POSSIBILITY OF SUCH DAMAGE.


  Liabilities of the Government:

  This software is provided by URA, independent from its Prime Contract
  with the U.S. Department of Energy. URA is acting independently from
  the Government and in its own private capacity and is not acting on
  behalf of the U.S. Government, nor as its contractor nor its agent.
  Correspondingly, it is understood and agreed that the U.S. Government
  has no connection to this software and in no manner whatsoever shall
  be liable for nor assume any responsibility or obligation for any claim,
  cost, or damages arising out of or resulting from the use of the software
  available from this server.


  Export Control:

  All documents and software available from this server are subject to U.S.
  export control laws.  Anyone downloading information from this server is
  obligated to secure any necessary Government licenses before exporting
  documents or software obtained from this server.
*/

package diskCacheV111.util;

import java.util.List;
import java.util.ArrayList;
import java.util.Random;
import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;

import diskCacheV111.doors.EDataBlockNio;
import dmg.cells.nucleus.CellAdapter;

/**
 * Data channel proxy for FTP door. The proxy will run at the GridFTP
 * door and relay data between the client and the pool. Mode S and
 * mode E are supported. Mode E is only supported when data flows from
 * the client to the pool.
 *
 * The class is also used to establish data channels for transfering
 * directory listings. This use should be reconsidered, at it is
 * unrelated to the proxy functionality.
 */
public class SocketAdapter implements Runnable, ProxyAdapter 
{
    /** Channel listening for connections from the client. */
    private ServerSocketChannel _clientListenerChannel;

    /** Channel listening for connections from the pool. */
    private ServerSocketChannel _poolListenerChannel;

    /** Current number of data channel connections. */
    private int _dataChannelConnections;

    /** True if mode E is used for transfer, false when mode S is used. */
    private boolean _modeE = false;

    /**
     * Expected number of data channels. In mode S this is 1. In mode
     * E this information is provided by the client.
     */
    private int _eodc = 1;

    /**
     * The number of data channels we have closed. For informational
     * purposes only.
     */
    private int _dataChannelsClosed;

    /**
     * The number of EOD markers we have seen.
     */
    private int _eodSeen;

    /** 
     * TCP send and receive buffer size. Will use default when zero.
     */
    private int _bufferSize = 0;

    /**
     * The maximum number of concurrent streams allowed. The transfer
     * will fail if more channels are opened.
     */
    private int _maxStreams = Integer.MAX_VALUE;

    /**
     * True for uploads, false for downloads.
     */
    private boolean _clientToPool;

    /**
     * The cell used for error logging.
     */
    private CellAdapter _door;

    /**
     * Non null if an error has occurred and the transfer has failed.
     */
    private String _error;

    /**
     * Selector for doing asynchronous accept.
     */
    private Selector _selector;

    /**
     * Size of the largest block allocated in mode E. Blocks larger
     * than this are divided into smaller blocks.
     */
    private int _maxBlockSize = 131072;

    /**
     * Random number generator used when binding sockets.
     */
    private static Random _random = new Random();
    
    /**
     * A thread driving the adapter
     */
    private Thread _thread = null;

    /**
     * True when the adapter is closing or has been closed. Used to
     * suppress error messages when killing the adapter.
     */ 
    private boolean _closing = false;

    /**
     * A redirector moves data between an input channel and an ouput
     * channel. This particular redirector does so in mode S.
     */
    class StreamRedirector extends Thread 
    {
	private SocketChannel _input, _output;

	public StreamRedirector(SocketChannel input, SocketChannel output) 
	{
	    super("SocketRedirector");
	    _input = input;
	    _output = output;
	}

	public void run() 
	{
	    try {
		say("Starting a redirector for mode S");
		ByteBuffer buffer = ByteBuffer.allocateDirect(128 * 1024);
		while (_input.read(buffer) != -1) {
		    buffer.flip();
		    _output.write(buffer);
		    buffer.clear();
		}
		_input.close();
	    } catch (IOException e) {
		setError(e);
	    } finally {
		subtractDataChannel();
	    }
	}
    }

    /**
     * A redirector moves data between an input channel and an ouput
     * channel. This particular redirector does so in mode E.
     */
    class ModeERedirector extends Thread 
    {
	private SocketChannel _input, _output;

	public ModeERedirector(SocketChannel input, SocketChannel output)
	{
	    super("SocketRedirector");
	    _input  = input;
	    _output = output;
	}

	public void run() 
	{
	    boolean       eod    = false;
            boolean       used   = false;
            ByteBuffer    header = ByteBuffer.allocate(17);
            EDataBlockNio block  = new EDataBlockNio(getName());

            long count, position;

	    try {
		say("Starting a redirector for mode E");

		loop: while (!eod && block.readHeader(_input) > -1) {
                    used = true;

                    /* EOF blocks are never forwarded as they do not
                     * contain any data and the SocketAdapter sends an
                     * EOF at the beginning of the stream. Other
                     * blocks are forwarded if they are not empty.
                     */
                    if (block.isDescriptorSet(EDataBlockNio.EOF_DESCRIPTOR)) {
                        setEODExpected(block.getDataChannelCount());
                        count = position = 0;
                    } else {
                        count = block.getSize();
                        position = block.getOffset();
                    }

                    /* Read and send a single block. To limit memory
                     * usage, we will read at most _maxBlockSize bytes
                     * at a time. Larger blocks divided into multiple
                     * blocks.
                     */
                    while (count > 0) {
                        long len = Math.min(count, _maxBlockSize);
                        if (block.readData(_input, len) != len) {
                            break loop;
                        }
                        
                        /* Generate output header.
                         */
                        header.clear();
                        header.put((byte)0);
                        header.putLong(len);
                        header.putLong(position);

                        /* Write output.
                         */
                        ByteBuffer[] buffers = {
                            header, 
                            block.getData()
                        };
                        buffers[0].flip();
                        buffers[1].flip();
                        _output.write(buffers);

                        /* Update counters.
                         */
                        count = count - len;
                        position = position + len;
                    }

                    /* Check for EOD mark.
                     */
                    if (block.isDescriptorSet(EDataBlockNio.EOD_DESCRIPTOR)) {
                        say("EOD received");
                        eod = true;
                    }
                }

		if (eod) {
		    addEODSeen();
		} else if (used) {
		    setError("Data channel was closed before EOD marker");
		}
		
		/* In case of an error, SocketAdapter will close the
		 * channel instead. We only call close here to free up
		 * sockets as early as possible when everything went
		 * as expected.
		 */
		_input.close(); 
	    } catch (Exception e) {		
		setError(e);
	    } finally {
		say("Redirector done, EOD = " + eod + ", used = " + used);
		subtractDataChannel();
	    }
	}
    }

    public SocketAdapter(int bufferSize, CellAdapter door) throws IOException 
    {
        this(door);
        _bufferSize = bufferSize;
    }

    public SocketAdapter(CellAdapter door) throws IOException 
    {
        _clientListenerChannel = ServerSocketChannel.open();
        _poolListenerChannel   = ServerSocketChannel.open();

        if (_bufferSize > 0) {
            _clientListenerChannel.socket().setReceiveBufferSize(_bufferSize);
            _poolListenerChannel.socket().setReceiveBufferSize(_bufferSize);
        }

	_clientListenerChannel.socket().bind(null);
	_poolListenerChannel.socket().bind(null);

        _clientToPool = true;
        _modeE        = false;
        _eodSeen      = 0;
        _door         = door;
        _thread	      = new Thread(this);
    }

    public SocketAdapter(CellAdapter door, int lowPort, int highPort) 
	throws IOException 
    {
        _door = door;
        if (lowPort > highPort) {
            throw new IllegalArgumentException("lowPort > highPort");
        }

        say("Port range=" + lowPort + "-" + highPort);
        if (lowPort > 0) {
	    /* We randomise the first socket to try to reduce the risk
	     * of conflicts and to make the port less predictable.
	     */
	    int start = _random.nextInt(highPort - lowPort + 1) + lowPort;
	    int i = start;
            do {
                try {
                    say("Trying Port " + i);
		    _clientListenerChannel = ServerSocketChannel.open();
		    _clientListenerChannel.socket().bind(new InetSocketAddress(i));
                    break;
                } catch (BindException ee) {
                    say("Problems trying port " + i + " " + ee);
                    if (i == highPort) {
                        throw ee;
                    }
                }
		i = (i < highPort ? i + 1 : lowPort);
            } while (i != start);
        } else {
	    _clientListenerChannel = ServerSocketChannel.open();
            _clientListenerChannel.socket().bind(null);
        }
        _poolListenerChannel = ServerSocketChannel.open();
	_poolListenerChannel.socket().bind(null);

        _clientToPool = true;
        _modeE        = false;
        _eodSeen      = 0;
        _thread	      = new Thread(this);
    }

    protected void say(String s) 
    {
        _door.say("SocketAdapter: " + s);
    }

    protected void esay(String s) 
    {
        _door.esay("SocketAdapter: " + s);
    }

    protected void esay(Throwable t) 
    {
        _door.esay("SocketAdapter exception:");
        _door.esay(t);
    }

    /** Increments the EOD seen counter. Thread safe. */
    protected synchronized void addEODSeen() 
    {
        _eodSeen++;
    }

    /** Returns the EOD seen counter. Thread safe. */
    protected synchronized int getEODSeen() 
    {
        return _eodSeen;
    }

    /** Returns the number of data channels to expect. Thread safe. */
    protected synchronized int getEODExpected() 
    {
        return _eodc;
    }

    /**
     * Sets the number of data channels to expect. Thread safe.  The
     * selector will be woken up, since run() checks the data channel
     * count in the loop.
     */
    protected synchronized void setEODExpected(long count) 
    {
	say("Setting data channel count to " + count);
	_selector.wakeup();
        _eodc = (int)count;
    }

    /** Called whenever a redirector finishes. Thread safe. */
    protected synchronized void subtractDataChannel() 
    {
        _dataChannelConnections--;
        _dataChannelsClosed++;
        say("Closing redirector: " + _dataChannelsClosed +
            ", remaining: " + _dataChannelConnections +
            ", eodc says there will be: " + getEODExpected());
    }

    /** Called whenever a new redirector is created. Thread safe. */
    protected synchronized void addDataChannel() 
    {
	_dataChannelConnections++;
    }

    /** 
     * Returns the current number of concurrent data channel
     * connections. Thread safe.
     */
    protected synchronized int getDataChannelConnections() 
    {
	return _dataChannelConnections;
    }

    /** 
     * Sets the error field. This indicates that the transfer has
     * failed.
     */
    protected synchronized void setError(String msg) 
    {
        if (!isClosing()) {
            esay(msg);
            if (_error == null) {
                _thread.interrupt();
                _error = msg;
            }
        }
    }

    /** 
     * Sets the error field. This indicates that the transfer has
     * failed. The SocketAdapter thread is interrupted, causing it to
     * break out of the run() method.
     */
    protected synchronized void setError(Exception e) 
    {
        if (!isClosing()) {
            esay(e);
            if (_error == null) {
                _thread.interrupt();
                _error = e.getMessage();
            }
        }
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#getError()
     */
    public synchronized String getError() 
    {
	return _error;
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#hasError()
     */
    public synchronized boolean hasError()
    {
        return _error != null;
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#setMaxBlockSize(int)
     */
    public void setMaxBlockSize(int size)
    {
        _maxBlockSize = size;
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#setMaxStreams(int)
     */
    public void setMaxStreams(int n) 
    {
        _maxStreams = (n > 0 ? n : Integer.MAX_VALUE);
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#setModeE(boolean)
     */
    public synchronized void setModeE(boolean modeE) 
    {
        _modeE = modeE;
	// MAX_VALUE = unknown until EODC
        _eodc = (modeE ? Integer.MAX_VALUE : 1);
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#getClientListenerPort()
     */
    public int getClientListenerPort() 
    {
        return _clientListenerChannel.socket().getLocalPort();
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#getPoolListenerPort()
     */
    public int getPoolListenerPort() 
    {
        return _poolListenerChannel.socket().getLocalPort();
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#acceptOnClientListener()
     */
    public Socket acceptOnClientListener() throws IOException 
    {
        return _clientListenerChannel.accept().socket();
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#setDirClientToPool()
     */
    public void setDirClientToPool() 
    {
        _clientToPool = true;
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#setDirPoolToClient()
     */
    public void setDirPoolToClient() 
    { 
        _clientToPool = false;
    }

    /**
     * Sets the closing flag. @see _closing
     */
    private synchronized void setClosing(boolean closing)
    {
        _closing = closing;
    }

    /**
     * Returns the value of the closing flag. @see _closing
     */
    private synchronized boolean isClosing()
    {
        return _closing;
    }

    public void run() 
    {
	assert _clientToPool || !_modeE;

        ServerSocketChannel inputSock;
        ServerSocketChannel outputSock;

        /** All redirectores created by the SocketAdapter. */
        List<Thread> redirectors = new ArrayList<Thread>();

        /** All sockets created by the SocketAdapter. */
	List<SocketChannel> sockets     = new ArrayList<SocketChannel>();

        try {
	    _selector = Selector.open();

	    if (_clientToPool) {
		inputSock = _clientListenerChannel;
		outputSock = _poolListenerChannel;
	    } else {
		inputSock = _poolListenerChannel;
		outputSock = _clientListenerChannel;
	    }

	    /* Accept connection on output channel. Since the socket
	     * adapter is only used when the client is active, and
	     * since in mode E the active part has to be the sender,
	     * and since we only create one connection between the
	     * adapter and the pool, there will in any case be exactly
	     * one connection on the output channel.
	     */
	    say("Accepting output connection on " 
		+ outputSock.socket().getLocalSocketAddress());
	    SocketChannel output = outputSock.accept();
	    sockets.add(output);
            if (_bufferSize > 0) {
                output.socket().setSendBufferSize(_bufferSize);
            }
            output.socket().setKeepAlive(true);
            say("Accepted output connection from " 
                + output.socket().getRemoteSocketAddress());

	    /* Send the EOF. The GridFTP protocol allows us to send
             * this information at any time. Doing it up front will
             * make sure, that the other end doesn't need to wait for
             * it.
	     */
	    if (_modeE) {
		ByteBuffer block = ByteBuffer.allocate(17);
		block.put((byte)(EDataBlockNio.EOF_DESCRIPTOR));
		block.putLong(0);
		block.putLong(1);
		block.flip();
		output.write(block);
	    }

	    /* Keep accepting connections on the input socket as long
	     * as the maximum number of concurrent connections allowed
	     * (_maxStreams, typically 10 or 20) has not been reached
	     * and as long as we have not reached the number of
	     * streams the client told us we should expect.
	     *
	     * This loop is one of the few places in which we check
	     * the interrupted flag of the current thread: At most
	     * other places, blocking operations will throw an
	     * exception when the thread was interrupted, however
	     * select() will return normally.
	     */
	    say("Accepting input connection on " 
		+ inputSock.socket().getLocalSocketAddress());
            int totalStreams = 0;
	    inputSock.configureBlocking(false);
	    inputSock.register(_selector, SelectionKey.OP_ACCEPT, null);
	    while (!Thread.currentThread().isInterrupted()
		   && totalStreams < getEODExpected()
	           && getDataChannelConnections() < _maxStreams) {
		_selector.select();
                for (SelectionKey key : _selector.selectedKeys()) {
		    if (key.isAcceptable()) {
			SocketChannel input = inputSock.accept();
			sockets.add(input);
			say("Accepted input connection from "
			    + input.socket().getRemoteSocketAddress());
			
			if (_bufferSize > 0) {
			    input.socket().setSendBufferSize(_bufferSize);
			}
                        input.socket().setKeepAlive(true);
			
			addDataChannel();
			
			say("creating socket redirector");
			Thread redir;
			if (_modeE) {
			    redir = new ModeERedirector(input, output);
			} else {
			    redir = new StreamRedirector(input, output);
			}
			redir.start();
			redirectors.add(redir);
			
			totalStreams++;
		    }
		}
		_selector.selectedKeys().clear();
	    }

	    /* We do not accept more than _maxStreams connections. If
	     * the client does not try to create any more connections,
	     * then nothing bad will happen.
	     *
	     * If the client tries to establish more connections, then
	     * we have to make sure that it notices that the transfer
	     * has failed. The most reliable way to do this is to
	     * close inputSock, thus causing all connections to that
	     * port to fail. We will not notice that the transfer
	     * failed until all existing connections have been
	     * closed. At that point we will realise that the EOD
	     * count is wrong.
	     *
	     * The alternative is to keep accepting connections. The
	     * first one passing the connection limit is immediately
	     * closed. The problem with that approach is that the
	     * sender may have already send an EOD on that channel and
	     * will not consider this behaviour to be an
	     * error. Although we will make sure to tell the client
	     * using the control channel that the transfer failed, the
	     * client may attempt to establish further connections
	     * before checking the control channel.
	     */ 
	    if (getDataChannelConnections() >= _maxStreams) {
		say("maximum number of data channels reached (not a problem)");
		inputSock.close();

		/* Since the channel is non-blocking, the close will
		 * be asynchronous. Closing the channel will move the
		 * channel's key to the cancelled-key set in the
		 * selector. We need another select operation to
		 * actually release the resources (read NIO
		 * documentation for the details).
		 */
		_selector.selectNow();
	    }

	    /* Block until all redirector threads have terminated.
             */
	    say("waiting for all redirectors to finish");
            for (Thread redirector : redirectors) {
		redirector.join();
	    }
	    redirectors.clear();
	    say("all redirectors have finished");
	    
	    /* Send the EOD (remember that we already sent the EOF
             * earlier).
	     */
	    if (_modeE) {
                if (getEODExpected() == Integer.MAX_VALUE) {
                    setError("Didn't receive EOF marker. Transfer failed.");
                } else if (getEODSeen() != getEODExpected()) {
		    setError("Didn't see enough EOD markers. Transfer failed.");
		} else {
                    ByteBuffer block = ByteBuffer.allocate(17);
                    block.put((byte)EDataBlockNio.EOD_DESCRIPTOR);
                    block.putLong(0);
                    block.putLong(0);
                    block.flip();
                    output.write(block);
                }
	    }
        } catch (InterruptedException e) {
            /* This will always be a symptom of another error, so
             * there is no reason to log this exception.
             */
        } catch (Exception e) {
	    setError(e);
        } finally {
	    /* Close the selector. Any keys are automatically cancelled.
	     */
	    if (_selector != null) {
		try {
		    _selector.close();
		    _selector = null;
		} catch (IOException e) {
		    setError(e);
		}
	    }

	    /* Tell any thread still alive to stop. In principal this
	     * should not be necessary since closing the channels
	     * below should cause all redirectors to break out. On the
	     * other hand it doesn't hurt and better safe than
	     * sorry...
	     */
            for (Thread redirector : redirectors) {
		redirector.interrupt();
	    }

	    /* Close all channels. The redirectors may already have
	     * closed the channels, however close() is a noop if the
	     * channel is already closed.
	     */
            for (SocketChannel channel : sockets) {
		try {
		    channel.close();
		} catch (IOException e) {
		    setError(e);
		}
	    }
        }
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#close()
     */
    public void close() {
	say("Closing listener sockets");

        setClosing(true);
	
	/* Interrupting this thread is enough to cause
	 * SocketAdapter.run() to break. SocketAdapter.run() will in
	 * turn interrupt all redirectors.
	 */
	_thread.interrupt();

        try {
	    if (_clientListenerChannel != null) {
		_clientListenerChannel.close();
		_clientListenerChannel = null;
	    }
        } catch (IOException e) {
            esay("_clientListenerSock.close() failed with IOException, ignoring");
            esay(e);
        }

        try {
	    if (_poolListenerChannel != null) {
		_poolListenerChannel.close();
		_poolListenerChannel = null;
	    }
	} catch (IOException e) {
            esay("_poolListenerSock.close() failed with IOException, ignoring");
            esay(e);
        }
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#isAlive()
     */
    public boolean isAlive() {
	return _thread.isAlive();
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#join()
     */
    public void join() throws InterruptedException {
	_thread.join();
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#join(long)
     */
    public void join(long millis) throws InterruptedException {
	_thread.join(millis);
    }

    /* (non-Javadoc)
     * @see diskCacheV111.util.ProxyAdapter#start()
     */
    public void start() {
	_thread.start();
    }
}
