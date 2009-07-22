package dmg.util;

import java.net.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.Channels;

import org.apache.log4j.Logger;

import dmg.security.CellUser;

public class DummyStreamEngine implements StreamEngine
{
    private final static Logger _logger =
        Logger.getLogger(DummyStreamEngine.class);

    private final Socket _socket;
    private CellUser _userName = new CellUser("Unknown", null, null);
    private ByteChannel _channel;

    private OutputStream _outputStream;
    private InputStream _inputStream;
    private Reader _reader;
    private Writer _writer;

    public DummyStreamEngine(Socket socket)
    {
        _socket = socket;

        SocketChannel channel = _socket.getChannel();
        if (channel != null) {
            _channel = wrapChannel(channel);
        }

        try {
            Method meth = _socket.getClass().getMethod("getUserPrincipal", new Class[0]);
            String user = (String)meth.invoke(_socket, new Object[0]);

            meth = _socket.getClass().getMethod("getRole", new Class[0]);
            String role = (String)meth.invoke(_socket, new Object[0]);

            meth = _socket.getClass().getMethod("getGroup", new Class[0]);
            String group = (String)meth.invoke(_socket, new Object[0]);

            _userName = new CellUser(user, group, role);
        } catch (NoSuchMethodException nsm) {

        } catch (Exception e) {
            _logger.warn("Failed to initialize user name in DummyStreamEngine", e);
        }
    }

    public CellUser getUserName()
    {
        return _userName;
    }

    public InetAddress getInetAddress()
    {
        return _socket.getInetAddress();
    }

    public synchronized InputStream getInputStream()
    {
        if (_inputStream == null) {
            if (_channel == null) {
                try {
                    _inputStream = _socket.getInputStream();
                } catch (IOException e) {
                    _logger.error("Could not create input stream: " + e.getMessage());
                }
            } else {
                _inputStream = Channels.newInputStream(_channel);
            }
        }
        return _inputStream;
    }

    public synchronized OutputStream getOutputStream()
    {
        if (_outputStream == null) {
            if (_channel == null) {
                try {
                    _outputStream = _socket.getOutputStream();
                } catch (IOException e) {
                    _logger.error("Could not create output stream: " + e.getMessage());
                }
            } else {
                _outputStream = Channels.newOutputStream(_channel);
            }
        }
        return _outputStream;
    }

    public synchronized Reader getReader()
    {
        if (_reader == null) {
            if (_channel == null) {
                _reader = new InputStreamReader(getInputStream());
            } else {
                _reader = Channels.
                    newReader(_channel, Charset.defaultCharset().newDecoder(), -1);
            }
        }
        return _reader;
    }

    public synchronized Writer getWriter()
    {
        if (_writer == null) {
            if (_channel == null) {
                _writer = new OutputStreamWriter(getOutputStream());
            } else {
                _writer = Channels.
                    newWriter(_channel, Charset.defaultCharset().newEncoder(), -1);
            }
        }
        return _writer;
    }

    public Socket getSocket()
    {
        return _socket;
    }

    public InetAddress getLocalAddress()
    {
        return _socket.getLocalAddress();
    }

    /**
     * Workaround for Java bug
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4509080. The
     * workaround was taken from
     * http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4774871.
     */
    private static ByteChannel wrapChannel(final ByteChannel channel)
    {
        return new ByteChannel()
        {
            public int write(ByteBuffer src)
                throws IOException
            {
                return channel.write(src);
            }

            public int read(ByteBuffer dst)
                throws IOException
            {
                return channel.read(dst);
            }

            public boolean isOpen()
            {
                return channel.isOpen();
            }

            public void close()
                throws IOException
            {
                channel.close();
            }
        };
    }
}

