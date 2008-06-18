// $Id$

package org.dcache.srm.util;

import java.net.URL;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.StringTokenizer;
import java.security.NoSuchAlgorithmException;
import java.security.MessageDigest;
import java.util.zip.Adler32;
import java.util.Date;

import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSManager;

import org.globus.gsi.GlobusCredential;
import org.globus.gsi.GlobusCredentialException;
import org.globus.gsi.TrustedCertificates;

import org.globus.gsi.gssapi.GlobusGSSManagerImpl;
import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;

import org.globus.gsi.gssapi.net.GssSocket;
import org.globus.gsi.gssapi.net.GssSocketFactory;
import org.globus.gsi.gssapi.net.impl.GSIGssSocket;
import org.globus.gsi.gssapi.auth.AuthorizationException;
import org.globus.gsi.gssapi.auth.Authorization;

import org.gridforum.jgss.ExtendedGSSContext;
import org.gridforum.jgss.ExtendedGSSManager;
import org.gridforum.jgss.ExtendedGSSCredential;

import org.globus.gsi.GSIConstants;
import org.globus.gsi.gssapi.GSSConstants;

import org.globus.ftp.GridFTPClient;
import org.globus.ftp.DataChannelAuthentication;
import org.globus.ftp.Session;
import org.globus.ftp.GridFTPSession;
import org.globus.ftp.RetrieveOptions;
import org.globus.ftp.DataSource;
import org.globus.ftp.DataSink;
import org.globus.ftp.Buffer;
import org.globus.ftp.FileRandomIO;
import org.globus.ftp.HostPort;
import org.globus.ftp.extended.GridFTPControlChannel;
import org.globus.ftp.exception.ServerException;
import org.globus.ftp.exception.FTPReplyParseException;
import org.globus.ftp.exception.ClientException;
import org.globus.ftp.exception.UnexpectedReplyCodeException;
import org.globus.ftp.vanilla.Reply;
import org.globus.ftp.vanilla.Command;
import org.globus.util.GlobusURL;

import org.globus.gsi.gssapi.GlobusGSSCredentialImpl;
import org.globus.gsi.gssapi.SSLUtil;
import org.globus.gsi.GlobusCredentialException;
import org.globus.gsi.TrustedCertificates;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.gssapi.GlobusGSSManagerImpl;
import org.globus.gsi.GSIConstants;
import org.globus.gsi.gssapi.GSSConstants;
import org.globus.gsi.GlobusCredential;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.GSSException;

import org.gridforum.jgss.ExtendedGSSContext;
import org.gridforum.jgss.ExtendedGSSManager;
import org.gridforum.jgss.ExtendedGSSCredential;
import org.globus.gsi.gssapi.net.impl.GSIGssSocket;
import org.globus.gsi.gssapi.net.GssSocket;

import java.io.RandomAccessFile;
import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.net.ServerSocket;

import org.dcache.srm.Logger;
/**
 * THE CLASS IS NOT THREAD SAVE
 * DO ONLY ONE OPERATION (READ / WRITE) AT A TIME
 */

public class GridftpClient
{
    private final static int FirstByteTimeout=60*60; //one hour
    private final static int NextByteTimeout=60*10; //10 minutes

    private final Logger _logger;
    private final FnalGridFTPClient _client;
    private final String _host;
    private String _cksmType;
    private String _cksmValue;

    private int _streamsNum = 10;
    private int _tcpBufferSize = 1024*1024;
    private int _bufferSize = 1024*1024;

    private volatile IDiskDataSourceSink _current_source_sink;
    private long _last_transfer_time = System.currentTimeMillis();

    private long _transferred = 0;
    private boolean _closed = false;

    private static String[] cksmTypeList = { "adler32","MD5","MD4" };

    public GridftpClient(String host, int port,
                         int tcpBufferSize,
                         GSSCredential cred,
                         Logger logger)
        throws IOException, ServerException, ClientException,
               GlobusCredentialException, GSSException
    {
        this(host, port, tcpBufferSize, 0, cred, logger);
    }

    public GridftpClient(String host, int port,
                         int tcpBufferSize,
                         int bufferSize,
                         GSSCredential cred,
                         Logger logger)
        throws IOException, ServerException, ClientException,
               GlobusCredentialException, GSSException
    {
        _logger = logger;
        if(bufferSize >0) {
            _bufferSize = bufferSize;
            say("memory buffer size is set to "+bufferSize);
        }
        if(tcpBufferSize > 0)
            {
                _tcpBufferSize = tcpBufferSize;
                say("tcp buffer size is set to "+tcpBufferSize);
            }
        if(cred == null) {
            GlobusCredential gcred = GlobusCredential.getDefaultCredential();
            cred = new GlobusGSSCredentialImpl(gcred, GSSCredential.INITIATE_ONLY);
        }
        _host = host;
        say("connecting to "+_host+" on port "+port);

        _client  = new FnalGridFTPClient(_host, port);
        _client.setLocalTCPBufferSize(_tcpBufferSize);
        say("gridFTPClient tcp buffer size is set to "+_tcpBufferSize);
        _client.authenticate(cred); /* use credentials */
        _client.setType(GridFTPSession.TYPE_IMAGE);
    }

    public  void say(String s) {
        if (_logger != null) {
            _logger.log("GridftpClient: "+s);
        }
    }
    public void esay(String s) {
        if (_logger != null) {
            _logger.elog("GridftpClient: "+s);
        }
    }
    public  void esay(Throwable t) {
        if (_logger != null) {
            _logger.elog(t);
        }
    }

    public long getLastTransferTime()
    {
        //do local copy to avoid null pointer exception
        IDiskDataSourceSink source_sink = _current_source_sink;
        if (source_sink != null) {
            _last_transfer_time = source_sink.getLast_transfer_time();
        }
        return _last_transfer_time;
    }

    public long getTransfered()
    {
        //do local copy to avoid null pointer exception
        IDiskDataSourceSink source_sink = _current_source_sink;
        if (source_sink != null) {
            _transferred = source_sink.getTransfered();
        }
        return _transferred;
    }

    public static long getAdler32(RandomAccessFile diskFile)
        throws IOException
    {
        Adler32 java_addler = new Adler32();
        diskFile.seek(0);
        byte [] buffer = new byte[4096] ;
        long sum=0L;
        while(true){
            int rc = diskFile.read( buffer , 0 , buffer.length ) ;

            if( rc <=0 )break ;
            sum += rc ;
            java_addler.update(buffer , 0 , rc ) ;
        }
        return java_addler.getValue();
    }

    public static String getCksmValue(RandomAccessFile diskFile,String type)
        throws IOException,NoSuchAlgorithmException
    {
        if (type.toLowerCase().equals("adler32"))
            return long32bitToHexString(getAdler32(diskFile));

        MessageDigest md = MessageDigest.getInstance(type);
        diskFile.seek(0);
        byte [] buffer = new byte[4096];
        long sum=0L;
        while(true){
            int rc = diskFile.read( buffer , 0 , buffer.length ) ;

            if( rc <=0 )break ;
            sum += rc ;
            md.update(buffer , 0 , rc ) ;
        }
        return printbytes(md.digest());
    }


    public String list(String directory,boolean serverPassive)
        throws IOException, ClientException, ServerException
    {
        setCommonOptions(false,serverPassive);
        _client.changeDir(directory);
        if (serverPassive) {
            _client.setPassive();
            _client.setLocalActive();
        } else {
            _client.setLocalPassive();
            _client.setActive();
        }
        final ByteArrayOutputStream received = new ByteArrayOutputStream(1000);

        // unnamed DataSink subclass will write data channel content
        // to "received" stream.

        DataSink sink = new DataSink() {
                public void write(Buffer buffer) throws IOException {
                    received.write(buffer.getBuffer(), 0, buffer.getLength());
                }
                public void close() throws IOException {
                };
            };

        _client.list(" "," ",sink);
        return received.toString();
    }

    public long getSize(String ftppath)
        throws IOException, ServerException
    {
        return _client.getSize(ftppath);
    }

    private void setCommonOptions(boolean emode,
                                  boolean passive_server_mode)
        throws IOException, ClientException, ServerException
    {
        if (_client.isFeatureSupported("DCAU")) {
            _client.setDataChannelAuthentication(DataChannelAuthentication.NONE);
        }
        say("set local data channel authentication mode to None");
        _client.setLocalNoDataChannelAuthentication();

        if(emode) {
            _client.setMode(GridFTPSession.MODE_EBLOCK);
            // adding parallelism
            say("parallelism: " + _streamsNum);
            _client.setOptions(new RetrieveOptions(_streamsNum));
        }
        else {
            _client.setMode(GridFTPSession.MODE_STREAM);
            say("stream mode transfer");

            if (!_client.isFeatureSupported("GETPUT")) {
                if(passive_server_mode){
                    say("server is passive");
                    HostPort serverHostPort = _client.setPassive();
                    say("serverHostPort="+serverHostPort.getHost()+":"+serverHostPort.getPort());
                    _client.setLocalActive();
                }else{
                    say("server is active");
                    _client.setLocalPassive();
                    _client.setActive();
                }
            }
        }

        //wait for ~ 24 days before timing out, poll every minute
        _client.setClientWaitParams(Integer.MAX_VALUE,1000);
    }

    /**
     * THIS IS A TEMPORARY CODE TO MAKE NCSA GSIFTP SERVER
     * TO STAGE FILES INSTEAD OF FAILING TRANSFERS
     * THIS REALLY IS @##@%$^
     */
    private void sendNCSAWaitCommand()
        throws IOException, ServerException, FTPReplyParseException,
               UnexpectedReplyCodeException
    {
        say(" sending wait command to ncsa host " + _host);
        GridFTPControlChannel channel = _client.getControlChannel();
        Reply reply = channel.execute(new Command("SITE","WAIT"));
        say("Reply is "+reply);
        if(Reply.isPositiveCompletion( reply)) {
            say("sending wait command successful");
        } else {
            esay("WARNING: sending wait command failed");
        }
    }

    // setChecksum will set cksmType and cksmValue for the FTP session
    // If both values are set the write and read are verified using supplied information
    // If only the type is set, value is calculated from the file's copy on the disk
    // If send_checksum is used in the write methods,  type and value will be set
    // to adler32 and dynamically calcualted upon completion of the transfer by the client side
    public void setChecksum(String cksmType,String cksmValue){
        _cksmType = cksmType;
        _cksmValue = cksmValue;
    }

    public String getChecksumValue() {
        return _cksmValue;
    }

    public String getChecksumType() {
        return _cksmType;
    }

    public void gridFTPRead(String sourcepath,
                            String destinationfilepath,
                            boolean emode,
                            boolean passive_server_mode)
        throws FileNotFoundException, IOException,
               ClientException, ServerException,
               FTPReplyParseException,UnexpectedReplyCodeException,
               InterruptedException, NoSuchAlgorithmException
    {
        RandomAccessFile diskFile = null;
        try {
            diskFile = new RandomAccessFile(destinationfilepath,"rw");
            gridFTPRead(sourcepath,diskFile, emode,passive_server_mode);
        } finally {
            try {
                if(diskFile != null) {
                    diskFile.close();
                }
            } catch(IOException e) {
                esay(" closing of file "+destinationfilepath+" failed");
                esay(e);
            }
        }
    }

    public void gridFTPRead(String sourcepath,
                            RandomAccessFile destinationDiskFile,
                            boolean emode,boolean passive_server_mode)
        throws IOException, ClientException, ServerException,
               FTPReplyParseException, UnexpectedReplyCodeException,
               InterruptedException, NoSuchAlgorithmException
    {

        DiskDataSourceSink sink =
            new DiskDataSourceSink(destinationDiskFile,_bufferSize,false);
        gridFTPRead(sourcepath,sink, emode,passive_server_mode);
    }

    public void gridFTPRead(String sourcepath,
                            IDiskDataSourceSink sink,
                            boolean emode)
        throws IOException, ClientException, ServerException,
               FTPReplyParseException, UnexpectedReplyCodeException,
               InterruptedException, NoSuchAlgorithmException
    {
        gridFTPRead(sourcepath,sink,emode,true);
    }

    public void gridFTPRead(String sourcepath,
                            IDiskDataSourceSink sink,
                            boolean emode,
                            boolean passive_server_mode)
        throws IOException, ClientException, ServerException,
               FTPReplyParseException, UnexpectedReplyCodeException,
               InterruptedException, NoSuchAlgorithmException
    {
        say("gridFTPRead started");
        // size of the file
        setCommonOptions(emode,passive_server_mode);
        if(_host.toLowerCase().indexOf("ncsa") != -1) {
            sendNCSAWaitCommand();
        }

        int read = 0;
        final long size = _client.getSize(sourcepath);
        _current_source_sink = sink;
        TransferThread getter = new TransferThread(_client,sourcepath,sink,emode,passive_server_mode,true,size);
        getter.start();
        getter.waitCompletion(FirstByteTimeout, NextByteTimeout);

        if(size - sink.getTransfered() >0) {
            esay("we wrote less then file size!!!");
            throw new IOException("we wrote less then file size!!!");
        }
        else if(size - sink.getTransfered() <0) {
            esay("we wrote more then file size!!!");
            throw new IOException("we wrote more then file size!!!");
        }

        say("gridFTPWrite() wrote "+sink.getTransfered()+"bytes");

        try {
          if ( _cksmType != null )
            verifyCksmValue(_current_source_sink,sourcepath);
        } catch ( ChecksumNotSupported ex){
          esay("Checksum is not supported:"+ex.toString());
        } catch ( ChecksumValueFormatException cvfe) {
          esay("Checksum format is not valid:"+cvfe.toString());
        }

        //make these remeber last values
        getTransfered();
        getLastTransferTime();
        _current_source_sink = null;
    }

    public void gridFTPWrite(String sourcefilepath,
                             String destinationpath,
                             boolean emode,
                             boolean use_chksum)
        throws InterruptedException, ClientException, ServerException,
               IOException, NoSuchAlgorithmException
    {
        gridFTPWrite(sourcefilepath,destinationpath,emode,use_chksum,true);
    }

    public void gridFTPWrite(String sourcefilepath,
                             String destinationpath,
                             boolean emode,
                             boolean use_chksum,
                             boolean passive_server_mode )
        throws InterruptedException, ClientException, ServerException,
               IOException, NoSuchAlgorithmException
    {
        RandomAccessFile diskFile = null;
        try {
            diskFile = new RandomAccessFile(sourcefilepath,"r");
            gridFTPWrite(diskFile, destinationpath, emode, use_chksum,passive_server_mode);
        } finally {
            try {
                if (diskFile != null) {
                    diskFile.close();
                }
            } catch(IOException ioe) {
                esay(" closing of file "+sourcefilepath+" failed");
                esay(ioe);
            }
        }
    }

    public void gridFTPWrite(RandomAccessFile sourceDiskFile,
                             String destinationpath,
                             boolean emode,
                             boolean use_chksum)
        throws InterruptedException, ClientException, ServerException,
               IOException, NoSuchAlgorithmException
    {
        gridFTPWrite(
                     sourceDiskFile,
                     destinationpath,
                     emode,
                     use_chksum,
                     true);
    }

    public void gridFTPWrite(RandomAccessFile sourceDiskFile,
                             String destinationpath,
                             boolean emode,
                             boolean use_chksum,
                             boolean passive_server_mode)
        throws InterruptedException, ClientException, ServerException,
               IOException, NoSuchAlgorithmException
    {
        say("gridFTPWrite started, source file is "+sourceDiskFile+
            " destination path is "+destinationpath);

        sourceDiskFile.seek(0);
        DiskDataSourceSink source = new DiskDataSourceSink(
                                                           sourceDiskFile,_bufferSize,true);
        gridFTPWrite( source, destinationpath, emode, use_chksum,passive_server_mode);

    }

    public void gridFTPWrite(IDiskDataSourceSink source,
                             String destinationpath,
                             boolean emode,
                             boolean use_chksum)
        throws InterruptedException, ClientException, ServerException,
               IOException, NoSuchAlgorithmException
    {
        gridFTPWrite(source,
                     destinationpath,
                     emode,
                     use_chksum,
                     true);

    }

    public void gridFTPWrite(IDiskDataSourceSink source,
                             String destinationpath,
                             boolean emode,
                             boolean use_chksum,
                             boolean passive_server_mode)
        throws InterruptedException, ClientException, ServerException,
               IOException, NoSuchAlgorithmException
    {
        say("gridFTPWrite started, destination path is "+destinationpath);

        setCommonOptions(emode,passive_server_mode);

        if(use_chksum || _cksmType != null) {

            sendCksmValue(source);
            /*
              try {
              sendAddler32Checksum(source.getAdler32());
              }
              catch(Exception e) {
              say("could not set addler 32 "+e.toString());
              }
            */
        }

        _current_source_sink = source;
        long diskFileLength = source.length();
        TransferThread putter = new TransferThread(_client,destinationpath,source,emode,passive_server_mode,false,diskFileLength);
        putter.start();
        putter.waitCompletion(FirstByteTimeout, NextByteTimeout);

        if(diskFileLength > source.getTransfered() ) {
            esay("we read less then file size!!!");
            throw new IOException("we read less then file size!!!");
        }
        else if(diskFileLength < source.getTransfered() ) {
            esay("we read more then file size!!!");
            throw new IOException("we read more then file size!!!");
        }

        say("gridFTPWrite() wrote "+source.getTransfered()+"bytes");
        getTransfered();
        getLastTransferTime();
        _current_source_sink = null;
    }

    private void sendCksmValue(IDiskDataSourceSink source)
        throws IOException,NoSuchAlgorithmException
    {
        String myType = _cksmType;
        if ( _cksmType == null  || _cksmType.equals("negotiate") )
            myType = cksmTypeList[0];

        if ( _cksmValue == null )
           _cksmValue = source.getCksmValue(myType);

        // send gridftp message
        try {
            _client.sendCksmValue(myType,_cksmValue);
        } catch ( Exception ex ){
            esay("Was not able to send checksum value:"+ex.toString());
        }
    }

   public Checksum negotiateCksm(String path) 
   throws IOException, 
       ServerException,
       ChecksumNotSupported,
       ChecksumValueFormatException
   {

       for ( int i = 0; i < cksmTypeList.length; ++i){
              try {
                 String serverCksmValue = _client.getCksmValue(cksmTypeList[i],path);
                 say("Negotiated type:"+cksmTypeList[i]+", value:"+serverCksmValue);
                 return new Checksum(cksmTypeList[i],serverCksmValue);
              } catch ( ChecksumNotSupported ex ){
                 int majorCode = ex.getCode()/10;
                 esay("code:"+Integer.toString(ex.getCode()));
                 if ( majorCode == 50 )
                    esay("Checksum is not supported:"+ex.toString()+" continuing search");
                 else
                    break;
              }
        }
        throw new ChecksumNotSupported("Checksum is not supported : couldn't negotiate type value",0);
   }

    private void verifyCksmValue(IDiskDataSourceSink source,String remotePath)
        throws IOException,
        ServerException,
        NoSuchAlgorithmException,
        ChecksumNotSupported,
        ChecksumValueFormatException
    {
        Checksum serverChecksum;
        if ( _cksmType == null )
            throw new IllegalArgumentException("verifyCksmValue: expected cksm type");

        if ( _cksmType.equals("negotiate") )
           serverChecksum = negotiateCksm(remotePath);
        else
           serverChecksum = new Checksum(_cksmType,_client.getCksmValue(_cksmType,remotePath));

        if ( _cksmValue == null )
            _cksmValue = source.getCksmValue(serverChecksum.type);

        if ( !_cksmValue.equals(serverChecksum.value) )
             throw new IOException("Server side checksum:"+serverChecksum.value+" does not match client side checksum:"+_cksmValue);
       // send gridftp message
    }

    public void close()
        throws IOException, ServerException
    {
        synchronized(this)
            {
                if(_closed) {
                    return;
                }
                else
                    {
                        _closed = true;
                    }
            }
        say("closing client : "+_client);
        _client.close(false);
        say("closed client");

    }

    protected void finalize() {
        try {
            close();
        }
        catch(Exception e) {
        }
    }
    /** Getter for property streamsNum.
     * @return Value of property streamsNum.
     *
     */
    public int getStreamsNum() {
        return _streamsNum;
    }

    /** Setter for property streamsNum.
     * @param streamsNum New value of property streamsNum.
     *
     */
    public void setStreamsNum(int streamsNum) {
        _streamsNum = streamsNum;
    }

    /** Getter for property tcpBufferSize.
     * @return Value of property tcpBufferSize.
     *
     */
    public int getTcpBufferSize() {
        return _tcpBufferSize;
    }

    /** Setter for property tcpBufferSize.
     * @param tcpBufferSize New value of property tcpBufferSize.
     *
     */
    public void setTcpBufferSize(int tcpBufferSize)
        throws ClientException
    {
        if(tcpBufferSize > 0)
            {
                _tcpBufferSize = tcpBufferSize;
                _client.setLocalTCPBufferSize(tcpBufferSize);
            }

    }

    /** Getter for property bufferSize.
     * @return Value of property bufferSize.
     *
     */
    public int getBufferSize() {
        return _bufferSize;
    }

    /** Setter for property bufferSize.
     * @param bufferSize New value of property bufferSize.
     *
     */
    public void setBufferSize(int bufferSize) {
        _bufferSize = bufferSize;
    }

    /** Setter to support checksum type negotiation
     * @param types List of checksum type names which will be tried by the checksum negotiation algo
     *
     */
    public static void setSupportedChecksumTypes(String[] types){
         cksmTypeList = types;
    }

    public static final void main( String[] args ) throws Exception {
        if(args.length <5 || args.length > 11) {
            System.err.println(
                               "usage:\n" +
                               "       gridftpcopy <source gridftp/file url> <dest gridftp/file url>  \n"+
                               "                     <memoryBufferSize> <tcpBufferSize> <parallel streams>\n"+
                               "                     <use emode(true or false)> [ <send checksum (true or false)>] [ <server-mode(active or passive)> ] \n"+
                               "                     [--checksumType=<value>] [--checksumValue=<value>] [--checksumPrint=true|false]\n" +
                               "  example:" +
                               "       gridftpcopy gsiftp://host1:2811//file1 file://localhost//tmp/file1 4194304 4194304 11 true false");
            System.exit(1);
            return;
        }
        String source = args[0];
        String dest   = args[1];
        int bs = Integer.parseInt(args[2]);
        int tcp_bs = Integer.parseInt(args[3]);
        int streams = Integer.parseInt(args[4]);
        boolean emode=true;
	String server_mode="active";

        if(args.length > 5) {
            if(args[5].equals("true")){
                emode=true;
            }else{
                emode=false;
            }
        }
	//if emode is false, then it means stream mode and server could be in active or passive mode
	if((emode == false ) && (args.length >= 8)){
            server_mode = args[7];
        }
        boolean send_checksum = true;
        if(args.length > 6) {
            send_checksum = args[6].equalsIgnoreCase("true");
        }

        OptionMap<String> sMap = new OptionMap<String>(new OptionMap.StringFactory(),args);

        String chsmType  = sMap.get("checksumType");
        String chsmValue = sMap.get("checksumValue");
        String cksmPrint = sMap.get("checksumPrint");

        GlobusURL src_url = new GlobusURL(source);
        GlobusURL dst_url = new GlobusURL(dest);
        GSSCredential credential = null;

        Logger logger = new Logger()
            {
                public synchronized void log(String s)
                {
                    System.out.println(new Date().toString()+": "+ s);
                }
                public synchronized void elog(String s)
                {
                    System.err.println(new Date().toString()+": "+ s);
                }
                public synchronized void elog(Throwable t)
                {
                    t.printStackTrace();
                }
            };

        if( ( src_url.getProtocol().equals("gsiftp") ||
              src_url.getProtocol().equals("gridftp") ) &&
            dst_url.getProtocol().equals("file")) {
            GridftpClient client;

            client = new GridftpClient(src_url.getHost(),
                                       src_url.getPort(), tcp_bs, bs,credential,logger);
            client.setStreamsNum(streams);
            client.setChecksum(chsmType,chsmValue);
            try {
                client.gridFTPRead(src_url.getPath(),dst_url.getPath(), emode,
                                   server_mode.equalsIgnoreCase("passive"));
            }
            finally {
                client.close();
            }
            return;
        }

        if(  src_url.getProtocol().equals("file") &&
             ( dst_url.getProtocol().equals("gsiftp") ||
               dst_url.getProtocol().equals("gridftp") )
             ) {
            GridftpClient client;
            client = new GridftpClient(dst_url.getHost(),
                                       dst_url.getPort(), tcp_bs, bs,credential,logger);
            client.setStreamsNum(streams);
            try {
                client.setChecksum(chsmType,chsmValue);
                client.gridFTPWrite(src_url.getPath(),dst_url.getPath(), emode, send_checksum,
                                    server_mode.equalsIgnoreCase("passive"));
            }
            finally {
                client.close();
            }
            return;
        }
        System.err.println("only \"file to gridftp\" and \"gridftp to file\" transfers are supported");
        System.exit(1);
    }

    private  class TransferThread implements Runnable {
        private boolean _done = false;
        private final boolean _emode;
        private final boolean _passive_server_mode;
        private final FnalGridFTPClient _client;
        private Exception _throwable;
        private final String _path;
        private final IDiskDataSourceSink _source_sink;
        private final boolean _read;
        private long _size;
        private Thread _runner;

        public TransferThread(FnalGridFTPClient client,
                              String path,
                              IDiskDataSourceSink source_sink,
                              boolean emode,
                              boolean passive_server_mode,
                              boolean read,
                              long size) {
            _client = client;
            _path = path;
            _source_sink = source_sink;
            _emode = emode;
            _passive_server_mode = passive_server_mode;
            _read = read;
            _size = size;
        }

        public void start()
        {
            _runner = new Thread(this);
            _runner.start();
        }
        /**
         * @param  FirstByteTimeout timeout before first byte
         *         arrives/leaves in seconds
         * @param NextByteTimeout timeout before next bytes arrive/leave
         */
        public void waitCompletion(int FirstByteTimeout,int  NextByteTimeout)
            throws InterruptedException, ClientException, ServerException,
                   IOException
        {
            long timeout = FirstByteTimeout*1000L;

            say("waiting for completion of transfer");
            boolean timedout = false;
            boolean interrupted = false;
            while(true ) {
                try {
                    waitCompleteion(timeout);
                    if(isDone()) {
                        break;
                    }
                    if( (System.currentTimeMillis() - _source_sink.getLast_transfer_time())
                        > timeout) {
                        timedout = true;
                        break;
                    }
                    timeout= NextByteTimeout*1000L;
                }
                catch(InterruptedException ie) {
                    _runner.interrupt();
                    interrupted = true;
                    break;
                }
            }

            if(timedout ||interrupted ) {
                _runner.interrupt();
                String error = "transfer timedout or interrupted";
                esay(error);
                throw new InterruptedException(error);
            }

            if(getThrowable() !=null) {
                Exception e = getThrowable();
                esay(" transfer exception");
                esay(e);
                if (e instanceof ClientException) {
                    throw (ClientException)e;
                } else if (e instanceof ServerException) {
                    throw (ServerException)e;
                } else if (e instanceof IOException) {
                    throw (IOException)e;
                } else {
                    throw new RuntimeException("Unexpected exception", e);
                }
            }
        }

        private void waitCompleteion() throws InterruptedException {
            while(true) {
                synchronized(this) {
                    wait(1000);
                    if(_done) {
                        return;
                    }
                }
            }
        }

        private void waitCompleteion(long timeout) throws InterruptedException {
            synchronized(this) {
                wait(timeout);
            }
        }

        public  synchronized void done() {
            _done = true;
            notifyAll();
        }

        public void run() {
            try {
                if(_read) {
                    say("starting a transfer from "+_path);
                    if(_client.isFeatureSupported("GETPUT")) {
                        _client.get2(_path, (_emode ? false: _passive_server_mode),
                                    _source_sink, null);
                    } else {
                        _client.get(_path,_source_sink,null);
                    }
                }
                else {
                    say("starting a transfer to "+_path);
                    if(_client.isFeatureSupported("GETPUT")) {
                        _client.put2(_path, (_emode ? true : _passive_server_mode),
                                    _source_sink, null);
                    } else {
                        _client.put(_path,_source_sink,null);
                    }
                }
            } catch (IOException e) {
                esay(e);
                _throwable = e;
            } catch (ServerException e) {
                esay(e);
                _throwable = e;
            } catch (ClientException e) {
                esay(e);
                _throwable = e;
            } finally {
                done();
            }
        }

        /** Getter for property done.
         * @return Value of property done.
         *
         */
        public synchronized boolean isDone() {
            return _done;
        }


        public Exception getThrowable() {
            return _throwable;
        }

    }

    public static class Checksum {
         public Checksum(String type,String value){ this.type = type; this.value = value; }
         public String type;
         public String value;
    }

    public static class ChecksumNotSupported extends Exception {
          public ChecksumNotSupported(String msg,int code){ super(msg); this.code = code; }
          public int getCode(){ return code; }
          private int code;
    }
    
   public static class ChecksumValueFormatException extends Exception {
          public ChecksumValueFormatException(String msg){ 
              super(msg); 
          }
    }

    public interface IDiskDataSourceSink extends  DataSink ,DataSource {
        /**
         * file postions should be reset to 0 if IDiskDataSourceSink is a wrapper
         * around random access disk file
         */
        public long getAdler32() throws IOException;
        public String getCksmValue(String type)
            throws IOException,NoSuchAlgorithmException;
        public long getLast_transfer_time();
        public long getTransfered();
        public long length() throws IOException;
    }

    private  class DiskDataSourceSink implements IDiskDataSourceSink {
        private final RandomAccessFile _diskFile;
        private final int _buf_size;
        private volatile long _last_transfer_time = System.currentTimeMillis();
        private long _transferred = 0;
        private final boolean _source;

        public DiskDataSourceSink(RandomAccessFile diskFile, int buf_size,boolean source) {
            _diskFile = diskFile;
            _buf_size = buf_size;
            _source = source;
        }

        public synchronized void write(Buffer buffer)
            throws IOException {
            if(_source) {
                String error = "DiskDataSourceSink is source and write is called";
                esay(error);
                throw new IllegalStateException(error);
            }
            //say("DiskDataSourceSink.write()");

            _last_transfer_time    = System.currentTimeMillis() ;
            int read = buffer.getLength();
            long offset = buffer.getOffset();
            if (offset >= 0) {
                _diskFile.seek(offset);
            }
            _diskFile.write(buffer.getBuffer(), 0, read);
            _transferred +=read;
        }

        public void close()
            throws IOException {
            say("DiskDataSink.close() called");
            _last_transfer_time    = System.currentTimeMillis() ;
        }

        /** Getter for property last_transfer_time.
         * @return Value of property last_transfer_time.
         *
         */
        public long getLast_transfer_time() {
            return _last_transfer_time;
        }

        /** Getter for property transfered.
         * @return Value of property transfered.
         *
         */
        public synchronized long getTransfered() {
            return _transferred;
        }

        public synchronized Buffer read() throws IOException {
            if(!_source) {
                String error = "DiskDataSourceSink is sink and read is called";
                esay(error);
                throw new IllegalStateException(error);
            }
            //say("DiskDataSourceSink.read()");

            _last_transfer_time    = System.currentTimeMillis() ;
            byte[] bytes = new byte[_buf_size];

            int read = _diskFile.read(bytes);
            //say("DiskDataSourceSink.read() read "+read+" bytes");
            if(read == -1) {
                return null;
            }
            Buffer buffer = new Buffer(bytes,read,_transferred);
            _transferred  += read;
            return buffer;
        }

        public long getAdler32() throws IOException{
            long adler32 = GridftpClient.getAdler32(_diskFile);
            say("adler 32 for file "+_diskFile+" is "+adler32);
            _diskFile.seek(0);
            return adler32;
        }

        public String getCksmValue(String type)
            throws IOException,NoSuchAlgorithmException
        {
            String v = GridftpClient.getCksmValue(_diskFile,type);
            say(type+" for file "+_diskFile+" is "+v);
            _diskFile.seek(0);
            return v;
        }

        public long length() throws IOException{
            return _diskFile.length();
        }

    }

    private static class FnalGridFTPClient extends GridFTPClient {
        public FnalGridFTPClient(String host, int port)
            throws IOException,ServerException
        {
            super(host,port);
        }

        public GridFTPControlChannel getControlChannel() {
            return (GridFTPControlChannel)controlChannel;
        }

        private void sendAddler32Checksum(String adler32String)
            throws IOException, ServerException
        {
            Reply reply = quote("SITE CHKSUM "+ adler32String);
            if(Reply.isPositiveCompletion( reply)) {
            } else {
                throw new IOException(reply.getMessage());
            }

        }

        public void sendCksmValue(String type,String value)
            throws IOException, ServerException
        {
            try {
                Reply reply = quote("SCKS "+type+" "+value);

                if ( !Reply.isPositiveCompletion(reply) ){
                    if ( type.toLowerCase().equals("adler32") ){
                        sendAddler32Checksum(value);
                        return;
                    }
                    throw new IOException(reply.getMessage());
                }
            } catch ( ServerException ex ){
                if ( type.toLowerCase().equals("adler32") ){
                    sendAddler32Checksum(value);
                    return;
                }
                throw ex;
            }
        }

        public String getCksmValue(String type,String path) 
        throws IOException, 
            ServerException,
            ChecksumNotSupported,
            ChecksumValueFormatException
        {
            try {
               org.globus.ftp.vanilla.Reply reply = quote("CKSM "+type+" 0 -1 "+path);
               if ( !org.globus.ftp.vanilla.Reply.isPositiveCompletion(reply) ){
                  throw new ChecksumNotSupported("Checksum type "+type+" can not be retrieved:"+reply.getMessage(),reply.getCode());
               }
               return validateChecksumTypeValue(type,reply.getMessage());
            } catch ( org.globus.ftp.exception.ServerException ex){
              throw new ChecksumNotSupported("Checksum type "+type+" can not be retrieved:"+ex.toString(),parseCode(ex.toString()));
            }
        }

        private String validateChecksumTypeValue(String type,String value) 
        throws ChecksumValueFormatException {
            if(type.equalsIgnoreCase("adler32")) {
                try {
                  long lvalue = Long.parseLong(value,16);
                } catch(Exception e) {
                    throw new 
                        ChecksumValueFormatException("value = "+value+
                        " caused by:"+ e.getMessage());
                }
            }
            
            return value;
            
        }
        private int parseCode(String msg){
         try {
            String delim = "Unexpected reply:";
            int pos = msg.indexOf(delim);
            if ( pos != -1 ){
               pos += delim.length() + 1;
               String codeS = msg.substring(pos,pos+3);
               return Integer.parseInt(codeS);
            }
         } catch ( Exception ex){ }
         return 1;
       }
    }

    public static String long32bitToHexString(long value){
        value |=0x100000000L;
        value &=0x1ffffffffL;
        String svalue = Long.toHexString(value);
        svalue = svalue.substring(1);
        if(svalue.length() != 8) {
            throw new IllegalStateException("32 bit integer hext string  length is not 8 bytes");
        }
        return svalue;
    }
    public static String printbytes(byte[] bs)
    {
        String out="";
        for ( int i = 0; i < bs.length; ++i)
            out += byteToHexString(bs[i]);
        return out;
    }

    private static final String [] __map =
    { "0","1","2","3","4","5","6","7","8","9","a","b","c","d","e","f" } ;

    static public String byteToHexString( byte b ) {

        int x = ( b < 0 ) ? ( 256 + (int)b ) : (int)b ;

        return __map[ ((int)b >> 4 ) & 0xf ] +
            __map[ ((int)b      ) & 0xf ] ;
    }


}
