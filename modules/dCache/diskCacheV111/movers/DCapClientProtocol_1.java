package diskCacheV111.movers;

/**
 * @author Patrick F.
 * @author Timur Perelmutov. timur@fnal.gov
 * @version 0.0, 28 Jun 2002
 */

import diskCacheV111.vehicles.DCapClientProtocolInfo;
import diskCacheV111.vehicles.DCapClientPortAvailableMessage;
import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.StorageInfo;
import diskCacheV111.util.PnfsId ;
import diskCacheV111.util.PnfsFile ;
import diskCacheV111.repository.SpaceMonitor ;
import diskCacheV111.util.CacheException;
import diskCacheV111.movers.MoverProtocol;

import org.apache.log4j.Logger;

import dmg.cells.nucleus.* ;
import java.io.* ;
import java.net.URL;
import java.net.URLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.StringTokenizer;


public class DCapClientProtocol_1 implements MoverProtocol
{
   private static final Logger _log =
       Logger.getLogger(DCapClientProtocol_1.class);
   public static final int READ   =  1 ;
   public static final int WRITE  =  2 ;
   private static final int INC_SPACE  =  (50*1024*1024) ;
   private long    allocated_space  = 0 ;
   private long last_transfer_time    = System.currentTimeMillis() ;
   private final CellEndpoint   cell;
   private DCapClientProtocolInfo dcapClient;
   private CellPath pathToSource;
   private long starttime;
   private RandomAccessFile diskFile;
   private long timeout_time;
   private String remoteURL;
   private volatile long transfered  = 0;
   private boolean changed;


   //
   // <init>( CellAdapter cell ) ;
   //

   public DCapClientProtocol_1(CellEndpoint cell)
  {
    this.cell = cell ;
    say( "DCapClientProtocol_1 created" ) ;
  }

   private void say( String str ){
       _log.info(str);
   }

   private void esay( String str ){
       _log.error(str);
   }

   private void esay( Throwable t )
   {
       _log.error(t);
   }

   public void runIO( RandomAccessFile diskFile ,
                      ProtocolInfo protocol ,
                      StorageInfo  storage ,
                      PnfsId       pnfsId  ,
                      SpaceMonitor spaceMonitor ,
                      int          access )
       throws Exception
   {
     say("runIO()\n\tprotocol="+
     protocol+",\n\tStorageInfo="+storage+",\n\tPnfsId="+pnfsId+
     ",\n\taccess ="+((( access & MoverProtocol.WRITE ) != 0)?"WRITE":"READ"));
     if( ! ( protocol instanceof DCapClientProtocolInfo ) )
     {
       throw new  CacheException(
       "protocol info is not RemoteGsiftpransferProtocolInfo" ) ;
     }
     this.diskFile = diskFile;
     starttime = System.currentTimeMillis();

     dcapClient = (DCapClientProtocolInfo) protocol;


     CellPath cellpath = new CellPath(dcapClient.getInitiatorCellName(),
                                        dcapClient.getInitiatorCellDomain());
     say(" runIO() RemoteGsiftpTranferManager cellpath="+cellpath);

     ServerSocket ss= null;
     try
     {
       ss = new ServerSocket(0,1);
     }
     catch(IOException ioe)
     {
       esay("exception while trying to create a server socket : "+ioe);
       throw ioe;
     }
     int port = ss.getLocalPort();
     String host = InetAddress.getLocalHost().getHostName();
     DCapClientPortAvailableMessage cred_request =
      new DCapClientPortAvailableMessage(host,port,dcapClient.getId());


      say(" runIO() created message");
     cell.sendMessage (new CellMessage(cellpath,cred_request));
     say("waiting for dcap server connection");
     Socket dcap_socket = ss.accept();
     say("connected");
     try
     {
        ss.close();
     }
     catch(IOException ioe)
     {
         esay("failed to close server socket");
         esay(ioe);
         // we still can continue, this is non-fatal
     }



     if( ( access & MoverProtocol.WRITE ) != 0 )
     {
         dcapReadFile(dcap_socket,diskFile,spaceMonitor);
     }
     else
     {
         throw new IOException("read is not implemented");
     }
     say(" runIO() done");
   }

   public long getLastTransferred()
   {
      return last_transfer_time ;
   }

   private synchronized void setTimeoutTime(long t)
   {
     timeout_time = t;
   }
   private synchronized long  getTimeoutTime()
   {
     return timeout_time;
   }
   public void setAttribute( String name , Object attribute )
   {
   }
   public Object getAttribute( String name )
   {
     return null;
   }
   public long getBytesTransferred()
   {
     return  transfered;
   }

   public long getTransferTime()
   {
      return System.currentTimeMillis() -starttime;
   }

   public boolean wasChanged()
   {
       return changed;
   }

      private void dcapReadFile(Socket _socket,
        RandomAccessFile _dataFile,
        SpaceMonitor _repository) throws Exception
      {
         last_transfer_time    = System.currentTimeMillis() ;
         DataInputStream in   = new DataInputStream(_socket.getInputStream()) ;
         DataOutputStream out = new DataOutputStream(_socket.getOutputStream()) ;

         say("<init>") ;
         int _sessionId = in.readInt() ;


         int challengeSize = in.readInt() ;
         in.skipBytes(challengeSize) ;


         say("<gettingFilesize>") ;
         out.writeInt(4) ; // bytes following
         out.writeInt(9) ;  // locate command
         //
         // waiting for reply
         //
         int following = in.readInt() ;
         if( following < 28 )
           throw new
           IOException( "Protocol Violation : ack too small : "+following);

         int type = in.readInt() ;
         if( type != 6 )   // REQUEST_ACK
           throw new
           IOException( "Protocol Violation : NOT REQUEST_ACK : "+type ) ;

         int mode = in.readInt() ;
         if( mode != 9 ) // SEEK
           throw new
           IOException( "Protocol Violation : NOT SEEK : "+mode ) ;

         int returnCode = in.readInt() ;
         if( returnCode != 0 ){
           String error = in.readUTF() ;
           throw new
           IOException( "Seek Request Failed : ("+
                        returnCode+") "+error ) ;
         }
         long filesize = in.readLong() ;
         say("<WaitingForSpace-"+filesize+">");
         _repository.allocateSpace(filesize) ;
         //
         in.readLong() ;   // file position


         say("<StartingIO>") ;
         //
         // request the full file
         //
         out.writeInt(12) ; // bytes following
         out.writeInt(2) ;  // read command
         out.writeLong(filesize) ;
         //
         // waiting for reply
         //
         following = in.readInt() ;
         if( following < 12 )
           throw new
           IOException( "Protocol Violation : ack too small : "+following);

         type = in.readInt() ;
         if( type != 6 )   // REQUEST_ACK
           throw new
           IOException( "Protocol Violation : NOT REQUEST_ACK : "+type ) ;

         mode = in.readInt() ;
         if( mode != 2 ) // READ
           throw new
           IOException( "Protocol Violation : NOT SEEK : "+mode ) ;

         returnCode = in.readInt() ;
         if( returnCode != 0 ){
           String error = in.readUTF() ;
           throw new
           IOException( "Read Request Failed : ("+
                        returnCode+") "+error ) ;
         }
         say("<RunningIO>") ;
         //
         // expecting data chain
         //
         //
         // waiting for reply
         //
         following = in.readInt() ;
         if( following < 4 )
           throw new
           IOException( "Protocol Violation : ack too small : "+following);

         type = in.readInt() ;
         if( type != 8 )   // DATA
           throw new
           IOException( "Protocol Violation : NOT DATA : "+type ) ;

         byte [] data = new byte[256*1024] ;
         int nextPacket = 0 ;
         long total     = 0L ;
         while( true ){
            if( ( nextPacket = in.readInt() ) < 0 )break ;

            int restPacket = nextPacket ;

            while( restPacket > 0 ){
               int block = Math.min( restPacket , data.length ) ;
               //
               // we collect a full block before we write it out
               // (a block always fits into our buffer)
               //
               int position = 0 ;
               for( int rest = block ;  rest > 0 ; ){
                  int rc = in.read( data , position , rest ) ;
                  last_transfer_time    = System.currentTimeMillis() ;

                  if( rc < 0 )
                    throw new
                    IOException("Premature EOF" ) ;

                  rest     -= rc ;
                  position += rc ;
               }
                    changed = true;
                    transfered +=block;
               total += block ;
              // say("<RunningIo="+total+">");
               _dataFile.write( data , 0 , block ) ;
               restPacket -= block ;
            }
         }
         say("<WaitingForReadAck>") ;
         //
         // waiting for reply
         //
         following = in.readInt() ;
         if( following < 12 )
           throw new
           IOException( "Protocol Violation : ack too small : "+following);

         type = in.readInt() ;
         if( type != 7 )   // REQUEST_FIN
           throw new
           IOException( "Protocol Violation : NOT REQUEST_ACK : "+type ) ;

         mode = in.readInt() ;
         if( mode != 2 ) // READ
           throw new
           IOException( "Protocol Violation : NOT SEEK : "+mode ) ;

         returnCode = in.readInt() ;
         if( returnCode != 0 ){
           String error = in.readUTF() ;
           throw new
           IOException( "Read Fin Failed : ("+
                        returnCode+") "+error ) ;
         }
         say("<WaitingForCloseAck>") ;
         //
         out.writeInt(4) ;  // bytes following
         out.writeInt(4) ;  // close request
         //
         // waiting for reply
         //
         following = in.readInt() ;
         if( following < 12 )
           throw new
           IOException( "Protocol Violation : ack too small : "+following);

         type = in.readInt() ;
         if( type != 6 )   // REQUEST_FIN
           throw new
           IOException( "Protocol Violation : NOT REQUEST_ACK : "+type ) ;

         mode = in.readInt() ;
         if( mode != 4 ) // READ
           throw new
           IOException( "Protocol Violation : NOT SEEK : "+mode ) ;

         returnCode = in.readInt() ;
         if( returnCode != 0 ){
           String error = in.readUTF() ;
           throw new
           IOException( "Close ack Failed : ("+
                        returnCode+") "+error ) ;
         }
         say("<Done>");

      }

}



