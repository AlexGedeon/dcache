package org.dcache.pool.movers;

/**
 * @author Patrick F.
 * @author Timur Perelmutov. timur@fnal.gov
 * @version 0.0, 28 Jun 2002
 */

import diskCacheV111.vehicles.*;
import diskCacheV111.util.PnfsId;
import diskCacheV111.util.PnfsFile;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.HttpConnectionHandler;
import org.dcache.pool.repository.Allocator;

import dmg.cells.nucleus.*;
import java.io.*;
import java.net.URL;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;

public class HttpProtocol_1 implements MoverProtocol
{
    public static final Logger _log = Logger.getLogger(HttpProtocol_1.class);

    public static final int READ   =  1;
    public static final int WRITE  =  2;
    public static final long SERVER_LIFE_SPAN= 60 * 5 * 1000; /* 5 minutes */

    private final CellEndpoint      _cell;
    private HttpProtocolInfo httpProtocolInfo;
    private ServerSocket httpserver;
    private long starttime;
    private RandomAccessFile diskFile;
    private long timeout_time;
    private long start_transfer_time    = System.currentTimeMillis();

    public HttpProtocol_1(CellEndpoint cell) {
        _cell = cell;
        say("HttpProtocol_1 created");
    }

    private void say(String str) {
        _log.info(str);
    }

    private void esay(String str) {
        _log.error(str);
    }

    private HttpConnectionHandler httpconnection = null;
    public void runIO(RandomAccessFile diskFile,
                       ProtocolInfo protocol,
                       StorageInfo  storage,
                       PnfsId       pnfsId ,
                       Allocator    allocator,
                       int          access)
        throws Exception
    {
        say("runIO("+diskFile+",\n"+
            protocol+",\n"+storage+",\n"+pnfsId+",\n"+access+")");
        if(! (protocol instanceof HttpProtocolInfo))
            {
                throw new  CacheException(44, "protocol info not HttpProtocolInfo");
            }
        this.diskFile = diskFile;
        ServerSocket ss= null;
        try
            {
                ss = new ServerSocket(0);
            }
        catch(IOException ioe)
            {
                esay("exception while trying to create a server socket : "+ioe);
                throw ioe;
            }
        starttime = System.currentTimeMillis();
        this.httpserver = ss;

        httpProtocolInfo = (HttpProtocolInfo) protocol;
        StringBuffer url_sb = new StringBuffer("http://");
        url_sb.append(InetAddress.getLocalHost ().getHostName ());
        url_sb.append(':').append(ss.getLocalPort());
        if(!httpProtocolInfo.getPath().startsWith("/"))
            {
                url_sb.append('/');
            }
        url_sb.append(httpProtocolInfo.getPath());
        say(" redirecting to  "+
            url_sb.toString());

        CellPath cellpath = new CellPath(httpProtocolInfo.getHttpDoorCellName (),
                                         httpProtocolInfo.getHttpDoorDomainName ());
        say(" runIO() cellpath="+cellpath);
        HttpDoorUrlInfoMessage httpDoorMessage =
            new HttpDoorUrlInfoMessage(pnfsId.getId (),url_sb.toString());
        say(" runIO() created message");
        _cell.sendMessage (new CellMessage(cellpath,httpDoorMessage));

        try
            {
                httpserver.setSoTimeout((int) SERVER_LIFE_SPAN);
                Socket connection = httpserver.accept();
                say(" accepted connection!!!");
                httpconnection = new HttpConnectionHandler(connection);

                String method = httpconnection.getHttpMethod ();
                if(!method.equals("GET"))
                    {
                        String error_string = "method : "+method+" is not supported";
                        httpconnection.returnErrorHeader (error_string);
                        return;
                    }

                say("method = "+method+" url="+httpconnection.getUrlString());
                String[] headers = httpconnection.getHeaders();
                for(int i = 0;i<headers.length;++i)
                    {
                        String header =httpconnection.getHeaderValue(headers[i]);
                        say("header["+i+"]="+headers[i]+":"+header);
                    }

                URL url = httpconnection.getUrl();
                String path = url.getPath();
                say("url returned path : "+path);

                PnfsFile transferfile = new PnfsFile(url.getPath());
                PnfsFile requestedfile = new PnfsFile(httpProtocolInfo.getPath());

                if(!transferfile.equals (requestedfile))
                    {
                        say("incorrect file requested : "+url.getPath());
                        String error_string = "incorrect path : "+url.getPath();
                        httpconnection.returnErrorHeader (error_string);
                        return;
                    }
                say("received request for a correct file : "+url.getPath()+" start transmission");
                httpconnection.sendFile(diskFile);
                say("transmission complete");
            }
        catch(java.net.SocketTimeoutException ste)
            {
                say("(HttpProtocol_1) http servet timeout ");

            }
        catch(Exception e)
            {
                esay("(HttpProtocol_1) error in the http server thread : "+e);
            }
        finally
            {
                say("(HttpProtocol_1) closing server socket");
                try
                    {
                        httpserver.close();
                    }
                catch(IOException ee)
                    {
                    }
                say("(HttpProtocol_1) done");
            }
        say(" runIO() done");
    }
    public long getLastTransferred()
    {
        if(httpconnection == null)
            {
                return  start_transfer_time;
            }
        return httpconnection.getLast_transfer_time();
    }

    private synchronized void setTimeoutTime(long t)
    {
        timeout_time = t;
    }
    private synchronized long  getTimeoutTime()
    {
        return timeout_time;
    }
    public void setAttribute(String name, Object attribute)
    {
    }
    public Object getAttribute(String name)
    {
        return null;
    }
    public long getBytesTransferred()
    {
        if(httpconnection == null)
            {
                return  0;
            }
        return httpconnection.transfered();

    }

    public long getTransferTime()
    {
        return System.currentTimeMillis() - start_transfer_time;
    }
    public boolean wasChanged(){ return false; }
}



