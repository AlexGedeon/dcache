// $Id: RemoteGsiftpTransferProtocol_1.java,v 1.12 2007-10-08 20:43:29 abaranov Exp $

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

package org.dcache.pool.movers;

import org.dcache.pool.repository.Allocator;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.Checksum;
import diskCacheV111.util.ChecksumFactory;
import diskCacheV111.util.PnfsId;
import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.StorageInfo;
import diskCacheV111.vehicles.transferManager.RemoteGsiftpDelegateUserCredentialsMessage;
import diskCacheV111.vehicles.transferManager.RemoteGsiftpTransferProtocolInfo;
import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellPath;
import dmg.cells.nucleus.NoRouteToCellException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.dcache.srm.util.GridftpClient.IDiskDataSourceSink;
import org.dcache.srm.util.GridftpClient;
import org.dcache.srm.security.SslGsiSocketFactory;
import org.globus.ftp.Buffer;
import org.globus.ftp.exception.ClientException;
import org.globus.ftp.exception.ServerException;
import org.globus.gsi.gssapi.auth.Authorization;
import org.globus.gsi.gssapi.net.GssSocket;
import org.globus.gsi.gssapi.net.impl.GSIGssSocket;
import org.globus.gsi.GlobusCredentialException;
import org.globus.util.GlobusURL;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;

public class RemoteGsiftpTransferProtocol_1
    implements MoverProtocol,ChecksumMover,DataBlocksRecipient
{
    private final static org.apache.log4j.Logger _log =
        org.apache.log4j.Logger.getLogger(RemoteGsiftpTransferProtocol_1.class);
    //timeout after 5 minutes if credentials not delegated
    private final static int SERVER_SOCKET_TIMEOUT = 60 * 5 *1000;
    private final CellEndpoint _cell;
    private long _starttime;
    private long _timeout_time;
    private PnfsId _pnfsId;

    private MessageDigest _transferMessageDigest;
    private Checksum _transferChecksum;

    private long _previousUpdateEndOffset = 0;

    private RandomAccessFile _raDiskFile;
    private GridftpClient _client;
    private org.dcache.srm.util.GridftpClient.Checksum _ftpCksm;
    private diskCacheV111.util.ChecksumFactory _cksmFactory;

    {
        // set checksum type set for further cksm negotiations
        org.dcache.srm.util.GridftpClient.setSupportedChecksumTypes(ChecksumFactory.getTypes());
    }

    public RemoteGsiftpTransferProtocol_1(CellEndpoint cell)
    {
        _cell = cell;
    }

    private void createFtpClient(RemoteGsiftpTransferProtocolInfo remoteGsiftpProtocolInfo ) throws CacheException,ServerException, ClientException,
                                                                                                    GlobusCredentialException, GSSException, IOException,NoRouteToCellException {

        if ( _client != null )
            return ;

        CellPath cellpath =
            new CellPath(remoteGsiftpProtocolInfo.getGsiftpTranferManagerName(),
                         remoteGsiftpProtocolInfo.getGsiftpTranferManagerDomain());
        _log.debug(" runIO() RemoteGsiftpTranferManager cellpath=" + cellpath);

        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(0, 1);
            serverSocket.setSoTimeout(SERVER_SOCKET_TIMEOUT);
        } catch (IOException e) {
            _log.error("exception while trying to create a server socket : " + e);
            throw e;
        }

        InetAddress localAddress = remoteGsiftpProtocolInfo.getLocalAddressForClient();

        RemoteGsiftpDelegateUserCredentialsMessage cred_request =
            new RemoteGsiftpDelegateUserCredentialsMessage(remoteGsiftpProtocolInfo.getId(),
                                                           remoteGsiftpProtocolInfo.getSourceId(),
                                                           localAddress.getHostName(),
                                                           serverSocket.getLocalPort(),
                                                           remoteGsiftpProtocolInfo.getRequestCredentialId());

        _log.debug(" runIO() created message");
        _cell.sendMessage(new CellMessage(cellpath, cred_request));
        _log.debug("waiting for delegation connection");
        //timeout after 5 minutes if credentials not delegated
        Socket deleg_socket = serverSocket.accept();
        _log.debug("connected");
        try {
            serverSocket.close();
        } catch (IOException e) {
            _log.error("failed to close server socket");
            _log.error(e);
            // we still can continue, this is non-fatal
        }
        GSSCredential deleg_cred;
        GSSContext context = getServerContext();
        GSIGssSocket gsiSocket = new GSIGssSocket(deleg_socket, context);
        gsiSocket.setUseClientMode(false);
        gsiSocket.setAuthorization(new Authorization() {
                public void authorize(GSSContext context, String host) {
                    //we might add some authorization here later
                    //but in general we trust that the connection
                    //came from a head node and user was authorized
                    //already
                }
            });
        gsiSocket.setWrapMode(GssSocket.SSL_MODE);
        gsiSocket.startHandshake();

        deleg_cred = context.getDelegCred();
        gsiSocket.close();
        /*
         *  the following code saves delegated credentials in a file
         *  this can be used for debugging the gsi problems
         *
         try
         {
         byte [] data = ((ExtendedGSSCredential)(deleg_cred)).export(
         ExtendedGSSCredential.IMPEXP_OPAQUE);
         String proxy_file = "/tmp/fnisd1.pool.proxy.pem";
         FileOutputStream out = new FileOutputStream(proxy_file);
         out.write(data);
         out.close();
         }catch (Exception e)
         {
         _log.error(e);
         }
        */

        if (deleg_cred != null) {
            _log.debug("successfully received user credentials: "
                + deleg_cred.getName().toString());
        } else {
            throw new CacheException("delegation request failed");
        }

        GlobusURL url = new GlobusURL(remoteGsiftpProtocolInfo.getGsiftpUrl());
        _client = new GridftpClient(url.getHost(), url.getPort(),
                                    remoteGsiftpProtocolInfo.getTcpBufferSize(),
                                    deleg_cred);
        _client.setStreamsNum(remoteGsiftpProtocolInfo.getStreams_num());
        _client.setTcpBufferSize(remoteGsiftpProtocolInfo.getTcpBufferSize());
    }

    public void runIO(RandomAccessFile diskFile,
                      ProtocolInfo protocol,
                      StorageInfo storage,
                      PnfsId pnfsId,
                      Allocator allocator,
                      int access)
        throws CacheException, IOException,
               NoRouteToCellException,
               ServerException, ClientException,
               GlobusCredentialException, GSSException
    {
        _pnfsId = pnfsId;
        _log.debug("runIO()\n\tprotocol="
            + protocol + ",\n\tStorageInfo=" + storage + ",\n\tPnfsId="
            + pnfsId + ",\n\taccess ="
            + (((access & MoverProtocol.WRITE) != 0) ? "WRITE" : "READ"));
        if (!(protocol instanceof RemoteGsiftpTransferProtocolInfo)) {
            throw new CacheException("protocol info is not RemoteGsiftpransferProtocolInfo");
        }
        _raDiskFile = diskFile;
        _starttime = System.currentTimeMillis();

        RemoteGsiftpTransferProtocolInfo remoteGsiftpProtocolInfo
            = (RemoteGsiftpTransferProtocolInfo) protocol;

        createFtpClient(remoteGsiftpProtocolInfo);

        if ((access & MoverProtocol.WRITE) != 0) {
            gridFTPRead(remoteGsiftpProtocolInfo,
                        storage,
                        allocator);
        } else {
            gridFTPWrite(remoteGsiftpProtocolInfo,
                         storage);
        }
        _log.debug(" runIO() done");
    }

    public long getLastTransferred()
    {
        return (_client == null ? 0 : _client.getLastTransferTime());
    }

    private synchronized void setTimeoutTime(long t)
    {
        _timeout_time = t;
    }

    private synchronized long getTimeoutTime()
    {
        return _timeout_time;
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
        return (_client == null ? 0 : _client.getTransfered());
    }

    public long getTransferTime()
    {
        return System.currentTimeMillis() - _starttime;
    }

    public boolean wasChanged()
    {
        return _client == null;
    }

    private GSSContext getServerContext() throws GSSException
    {
        return SslGsiSocketFactory.getServiceContext("/etc/grid-security/hostcert.pem",
                                                     "/etc/grid-security/hostkey.pem",
                                                     "/etc/grid-security/certificates");
    }

    public void gridFTPRead(RemoteGsiftpTransferProtocolInfo protocolInfo,
                            StorageInfo storage,
                            Allocator allocator)
        throws CacheException
    {
        try {
            GlobusURL src_url = new GlobusURL(protocolInfo.getGsiftpUrl());
            boolean emode = protocolInfo.isEmode();
            long size = _client.getSize(src_url.getPath());
            _log.debug(" received a file size info: " + size +
                " allocating space on the pool");
            _log.debug("ALLOC: " + _pnfsId + " : " + size );
            allocator.allocate(size);
            _log.debug(" allocated space " + size);
            DiskDataSourceSink sink =
                new DiskDataSourceSink(protocolInfo.getBufferSize(),
                                       false);
            try {
                _client.gridFTPRead(src_url.getPath(),sink, emode);
            } finally {
                try {
                    _client.close();
                } catch (IOException e) {
                    /* JGlobus is not happy when pre-1.8.0-14 dCaches
                     * send an empty line at the end of the
                     * session. Therefore we ignore this exception.
                     */
                }
            }
        } catch (Exception e) {
            _log.error(e);
            throw new CacheException(e.toString());
        }
    }

    public void gridFTPWrite(RemoteGsiftpTransferProtocolInfo protocolInfo,
                             StorageInfo storage)
        throws CacheException
    {
        _log.debug("gridFTPWrite started");

        try {
            String checksumTypes [] = ChecksumFactory.getTypes(_cell,_pnfsId);
            if ( checksumTypes != null ){
                _log.debug("Will use "+checksumTypes[0]+" for transfer verification of "+_pnfsId);
                _client.setChecksum(checksumTypes[0],null);
            } else {
                _log.debug("PnfsId "+_pnfsId+" does not have checksums");
            }

            GlobusURL dst_url =  new GlobusURL(protocolInfo.getGsiftpUrl());
            boolean emode = protocolInfo.isEmode();

            try {
                DiskDataSourceSink source =
                    new DiskDataSourceSink(protocolInfo.getBufferSize(),
                                           true);
                _client.gridFTPWrite(source, dst_url.getPath(), emode,  true);
            } finally {
                _client.close();
            }
        } catch (Exception e) {
            _log.error(e);
            throw new CacheException(e.toString());
        }
    }

    // the following methods were adapted from DCapProtocol_3_nio mover
    public Checksum getClientChecksum()
    {
        if ( _cksmFactory != null  && _ftpCksm != null ){
            return _cksmFactory.create(_ftpCksm.value);
        }
        return null;
    }

    public Checksum getTransferChecksum()
    {
        try {
            if (_transferChecksum == null) {
                return null;
            }

            if (_transferMessageDigest != null) {
                int read;
                byte[] bytes = new byte[128 * 1024];
                _raDiskFile.seek(_previousUpdateEndOffset);
                while ((read = _raDiskFile.read(bytes)) >= 0) {
                    _transferMessageDigest.update(bytes, 0, read);
                }
            }

            return _transferChecksum;
        } catch (IOException e) {
            _log.error(e);
            return null;
        }
    }

    public ChecksumFactory getChecksumFactory(ProtocolInfo protocol)
    {
        if ( _cksmFactory != null )
            return _cksmFactory;

        if (protocol instanceof RemoteGsiftpTransferProtocolInfo) {

            RemoteGsiftpTransferProtocolInfo remoteGsiftpProtocolInfo =  (RemoteGsiftpTransferProtocolInfo) protocol;
            try {
                createFtpClient(remoteGsiftpProtocolInfo);
                GlobusURL src_url =  new GlobusURL(remoteGsiftpProtocolInfo.getGsiftpUrl());
                _ftpCksm = _client.negotiateCksm(src_url.getPath());
                _cksmFactory = ChecksumFactory.getFactory(_ftpCksm.type);
                return _cksmFactory;
            } catch (NoSuchAlgorithmException e) {
                _log.error("Checksum algorithm is not supported: " + e.getMessage());
            } catch (NoRouteToCellException e) {
                _log.error("Failed to communicate with transfer manager: " + e.getMessage());
            } catch (GlobusCredentialException e) {
                _log.error("Failed to authenticate with FTP server: " + e.getMessage());
            } catch (GSSException e) {
                _log.error("Failed to authenticate with FTP server: " + e.getMessage());
            } catch (IOException e) {
                _log.error("I/O failure talking to FTP server: " + e.getMessage());
            } catch (Exception e) {
                _log.error("Failed to negotiate checksum with FTP server: " + e.getMessage());
            }
        }
        return null;
    }

    public void setDigest(Checksum checksum)
    {
        _transferChecksum      =  checksum;
        _transferMessageDigest =
            checksum != null? checksum.getMessageDigest() : null;
    }

    public synchronized void receiveEBlock(byte[] array,
                                           int offset,
                                           int length,
                                           long offsetOfArrayInFile)
        throws IOException
    {
        _raDiskFile.seek(offsetOfArrayInFile);
        _raDiskFile.write(array, offset, length);

        if (array == null) {
            /* REVISIT: Why do we need this?
             */
            return;
        }

        if (_transferMessageDigest != null
            && _previousUpdateEndOffset == offsetOfArrayInFile) {
            _previousUpdateEndOffset += length;
            _transferMessageDigest.update(array, offset, length);
        }
    }

    private class DiskDataSourceSink implements IDiskDataSourceSink
    {
        private final int _buf_size;
        private final boolean _source;
        private long _last_transfer_time = System.currentTimeMillis();
        private long _transferred = 0;

        public DiskDataSourceSink(int buf_size, boolean source)
        {
            _buf_size = buf_size;
            _source = source;
        }

        public synchronized void write(Buffer buffer) throws IOException
        {
            if (_source) {
                String error = "DiskDataSourceSink is source and write is called";
                _log.error(error);
                throw new IllegalStateException(error);
            }

            _last_transfer_time = System.currentTimeMillis();
            int read = buffer.getLength();
            long offset = buffer.getOffset();
            if (offset >= 0) {
                receiveEBlock(buffer.getBuffer(),
                              0, read,
                              buffer.getOffset());
            } else {
                //this is the case when offset is not supported
                // for example reading from a stream
                receiveEBlock(buffer.getBuffer(),
                              0, read,
                              _transferred);

            }
            _transferred += read;
        }

        public synchronized void close()
        {
            _log.debug("DiskDataSink.close() called");
            _last_transfer_time    = System.currentTimeMillis();
        }

        /** Specified in org.globus.ftp.DataSource. */
        public long totalSize() throws IOException
        {
            return _source ? _raDiskFile.length() : -1;
        }

        /** Getter for property last_transfer_time.
         * @return Value of property last_transfer_time.
         *
         */
        public synchronized long getLast_transfer_time()
        {
            return _last_transfer_time;
        }

        /** Getter for property transferred.
         * @return Value of property transferred.
         *
         */
        public synchronized long getTransfered()
        {
            return _transferred;
        }

        public synchronized Buffer read() throws IOException
        {
            if (!_source) {
                String error = "DiskDataSourceSink is sink and read is called";
                _log.error(error);
                throw new IllegalStateException(error);
            }

            _last_transfer_time = System.currentTimeMillis();
            byte[] bytes = new byte[_buf_size];

            int read = _raDiskFile.read(bytes);
            if (read == -1) {
                return null;
            }
            Buffer buffer = new Buffer(bytes, read, _transferred);
            _transferred  += read;
            return buffer;
        }

        public String getCksmValue(String type)
            throws IOException,NoSuchAlgorithmException
        {
            try {
                diskCacheV111.util.Checksum pnfsChecksum = diskCacheV111.util.ChecksumFactory.getFactory(type).createFromPersistentState(_cell, _pnfsId);
                if ( pnfsChecksum != null ){
                    String hexValue = pnfsChecksum.toHexString();
                    _log.debug(type+" read from pnfs for file "+_pnfsId+" is "+hexValue);
                    return hexValue;
                }
            }
            catch(Exception e){
                _log.error("could not get "+type+" from pnfs:");
                _log.error(e);
                _log.error("ignoring this error");

            }

            String hexValue = org.dcache.srm.util.GridftpClient.getCksmValue(_raDiskFile,type);
            _log.debug(type + " for file "+_raDiskFile+" is "+hexValue);
            _raDiskFile.seek(0);
            return hexValue;
        }

        public long getAdler32() throws IOException
        {
            try {
                String hexValue = getCksmValue("adler32");
                return Long.parseLong(hexValue,16);
            } catch ( java.security.NoSuchAlgorithmException ex){
                throw new IOException("adler 32 is not supported:"+ ex.toString());
            }
        }

        public long length() throws IOException
        {
            return _raDiskFile.length();
        }
    }
}
