//______________________________________________________________________________
//
// $id: TransferManagerHandler.java,v 1.4 2006/05/18 20:16:09 litvinse Exp $
// $Author: behrmann $
//
// created 05/06 by Dmitry Litvintsev (litvinse@fnal.gov)
//
//______________________________________________________________________________


package diskCacheV111.services;

import dmg.cells.nucleus.*;
import diskCacheV111.util.PnfsId;
import diskCacheV111.vehicles.Message;
import diskCacheV111.vehicles.PnfsGetStorageInfoMessage;
import diskCacheV111.vehicles.PnfsGetFileMetaDataMessage;
import diskCacheV111.vehicles.StorageInfo;
import diskCacheV111.util.CacheException;
import diskCacheV111.vehicles.DoorRequestInfoMessage;
import diskCacheV111.vehicles.PnfsCreateEntryMessage;
import diskCacheV111.vehicles.PnfsDeleteEntryMessage;
import diskCacheV111.vehicles.PoolMgrSelectPoolMsg;
import diskCacheV111.vehicles.PoolMoverKillMessage;
import diskCacheV111.vehicles.PoolMgrSelectWritePoolMsg;
import diskCacheV111.vehicles.PoolMgrSelectReadPoolMsg;
import diskCacheV111.vehicles.PoolIoFileMessage;
import diskCacheV111.vehicles.PoolAcceptFileMessage;
import diskCacheV111.vehicles.PoolDeliverFileMessage;
import diskCacheV111.vehicles.DoorTransferFinishedMessage;
import diskCacheV111.vehicles.transferManager.RemoteGsiftpTransferManagerMessage;
import diskCacheV111.vehicles.transferManager.TransferManagerMessage;
import diskCacheV111.vehicles.transferManager.TransferFailedMessage;
import diskCacheV111.vehicles.transferManager.TransferCompleteMessage;
import diskCacheV111.vehicles.transferManager.CancelTransferMessage;
import diskCacheV111.vehicles.IpProtocolInfo;
import java.io.IOException;
import java.util.Iterator;
import diskCacheV111.doors.FTPTransactionLog;
import org.apache.log4j.Logger;
import org.dcache.namespace.PermissionHandler;
import org.dcache.namespace.ChainedPermissionHandler;
import org.dcache.namespace.PosixPermissionHandler;
import org.dcache.namespace.ACLPermissionHandler;
import org.dcache.vehicles.FileAttributes;
import org.dcache.acl.enums.AccessType;


public class TransferManagerHandler implements CellMessageAnswerable {
	private TransferManager manager;
	private TransferManagerMessage transferRequest;
	private CellPath sourcePath;
	private String pnfsPath;
	private transient String parentDir;
	boolean store;
	boolean created = false;
	private PnfsId          pnfsId;
	private String          pnfsIdString;
	private String          remoteUrl;
	private StorageInfo     storageInfo;
	transient boolean locked = false;
	private String pool;
	private FTPTransactionLog tlog;
    private FileAttributes fileAttributes;
	public static final int INITIAL_STATE=0;
	public static final int WAITING_FOR_PNFS_INFO_STATE=1;
	public static final int RECEIVED_PNFS_INFO_STATE=2;
	public static final int WAITING_FOR_PNFS_PARENT_INFO_STATE=3;
	public static final int RECEIVED_PNFS_PARENT_INFO_STATE=4;
	public static final int WAITING_FOR_PNFS_ENTRY_CREATION_INFO_STATE=5;
	public static final int RECEIVED_PNFS_ENTRY_CREATION_INFO_STATE=6;
	public static final int WAITING_FOR_POOL_INFO_STATE=7;
	public static final int RECEIVED_POOL_INFO_STATE=8;
	public static final int WAITING_FIRST_POOL_REPLY_STATE=9;
	public static final int RECEIVED_FIRST_POOL_REPLY_STATE=10;
	public static final int WAITING_FOR_SPACE_INFO_STATE=11;
	public static final int RECEIVED_SPACE_INFO_STATE=12;
	public static final int WAITING_FOR_PNFS_ENTRY_DELETE=13;
	public static final int RECEIVED_PNFS_ENTRY_DELETE=14;
	public static final int WAITING_FOR_PNFS_CHECK_BEFORE_DELETE_STATE=15;
	public static final int RECEIVED_PNFS_CHECK_BEFORE_DELETE_STATE=16;
	public static final int SENT_ERROR_REPLY_STATE=-1;
	public static final int SENT_SUCCESS_REPLY_STATE=-2;
	public int state = INITIAL_STATE;
	private long id;
	private Integer moverId;
	private IpProtocolInfo protocol_info;
	private String spaceReservationId;
	private transient Long size;
	private transient boolean space_reservation_strict;
	private long creationTime;
	private long lifeTime;
        private Long credentialId;
	private transient int numberOfRetries;

	private transient int _replyCode;
	private transient Object _errorObject;
	private transient boolean _cancelTimer;
	private DoorRequestInfoMessage info;
    private PermissionHandler permissionHandler;

/**      */
	public TransferManagerHandler(TransferManager tManager,
				      TransferManagerMessage message,
				      CellPath sourcePath)  {

	    info =
			new DoorRequestInfoMessage(tManager.getNucleus().getCellName()+"@"+
						   tManager.getNucleus().getCellDomainName());
		numberOfRetries=0;
		creationTime = System.currentTimeMillis();
	    info.setTransactionTime(creationTime);
		manager      = tManager;
		id           = manager.getNextMessageID();
		message.setId(id);
		this.transferRequest = message;
		Long longId          = new Long(id);

		pnfsPath = transferRequest.getPnfsPath();
		store    = transferRequest.isStore();
		remoteUrl= transferRequest.getRemoteURL();
                credentialId = transferRequest.getCredentialId();
        info.setGid(transferRequest.getUser().getGid());
        info.setUid(transferRequest.getUser().getUid());
        info.setPath(pnfsPath);
        info.setOwner(transferRequest.getUser().getName());
        info.setTimeQueued(-System.currentTimeMillis());
        info.setMessageType("request");
        this.sourcePath = sourcePath;
        if (transferRequest instanceof RemoteGsiftpTransferManagerMessage)
            info.setOwner(((RemoteGsiftpTransferManagerMessage)transferRequest).getCredentialName());
        try {
        	info.setClient(new org.globus.util.GlobusURL(transferRequest.getRemoteURL()).getHost());
        } catch (Exception e){

        }
		try {
			if(manager.getLogRootName() != null) {
				tlog = new FTPTransactionLog(manager.getLogRootName(),manager);
				String user_info = transferRequest.getUser().getName()+
					"("+transferRequest.getUser().getUid() +"."+
                    transferRequest.getUser().getGid()+")";
				String rw = store?"write":"read";
				java.net.InetAddress remoteaddr =
					java.net.InetAddress.getByName(
						new org.globus.util.GlobusURL(transferRequest.getRemoteURL()).getHost());
				tlog.begin(user_info, "remotegsiftp", rw, transferRequest.getPnfsPath(), remoteaddr);
			}
		}
		catch(Exception e) {
			esay("starting tlog failed :");
			esay(e);
		}
		this.spaceReservationId       = transferRequest.getSpaceReservationId();
		this.space_reservation_strict = transferRequest.isSpaceReservationStrict();
		this.size                     = transferRequest.getSize();
		synchronized(manager.activeTransfersIDs) {
			manager.addActiveTransfer(longId,this);
		}
		setState(INITIAL_STATE);
        permissionHandler =
            new ChainedPermissionHandler(
                new ACLPermissionHandler(),
                new PosixPermissionHandler());
	}

/**      */
	public void say(String s) {
		manager.say("["+toString()+"]:"+s);
	}
/**      */
	public void esay(String s) {
		manager.esay("["+toString()+"]:"+s);
	}
/**      */
	public void esay(Throwable t){
		manager.esay(t);
	}
/**      */
	public void handle() {
		say("handling:  "+toString(true));
		int last_slash_pos = pnfsPath.lastIndexOf('/');
		if(last_slash_pos == -1) {
			transferRequest.setFailed(2,
						  new java.io.IOException("pnfsFilePath is not absolute:"+pnfsPath));
			return;
		}
		parentDir = pnfsPath.substring(0,last_slash_pos);
		PnfsGetFileMetaDataMessage sInfo;
		if(store) {
			sInfo = new PnfsGetFileMetaDataMessage(
                    permissionHandler.getRequiredAttributes()) ;
			sInfo.setPnfsPath( parentDir ) ;
			setState(WAITING_FOR_PNFS_PARENT_INFO_STATE);
		}
		else {
			sInfo = new PnfsGetStorageInfoMessage(
                    permissionHandler.getRequiredAttributes()) ;
			sInfo.setPnfsPath( pnfsPath ) ;
			setState(WAITING_FOR_PNFS_INFO_STATE);
		}
		manager.persist(this);
		try {
			manager.sendMessage(
				new CellMessage(new CellPath(manager.getPnfsManagerName()),
						sInfo ),
				true ,
				true,
				this,
				manager.getPnfsManagerTimeout()*1000
				);
		}
		catch(Exception ee ) {
			esay(ee);
			//we do not need to send the new message
			// since the original reply has not been sent yet
			transferRequest.setFailed(2, ee);
			return ;
		}
	}
/**      */
	public void answerArrived(CellMessage req, CellMessage answer) {
		say("answerArrived("+req+","+answer+"), state ="+state);
		Object o = answer.getMessageObject();
		if(o instanceof Message) {
			Message message = (Message)answer.getMessageObject() ;
			if ( message instanceof PnfsCreateEntryMessage) {
				PnfsCreateEntryMessage  create_msg =
					(PnfsCreateEntryMessage)message;
				if( state == WAITING_FOR_PNFS_ENTRY_CREATION_INFO_STATE) {
					setState(RECEIVED_PNFS_ENTRY_CREATION_INFO_STATE);
					createEntryResponseArrived(create_msg);
					return;
				}
				esay(this.toString()+" got unexpected PnfsCreateEntryMessage "+
				     " : "+create_msg+" ; Ignoring");
			}
			else     if( message instanceof PnfsGetStorageInfoMessage) {
				PnfsGetStorageInfoMessage storage_info_msg =
					(PnfsGetStorageInfoMessage)message;
				if( state == WAITING_FOR_PNFS_INFO_STATE ) {
					setState(RECEIVED_PNFS_INFO_STATE);
					storageInfoArrived(storage_info_msg);
					return;
				}
				esay(this.toString()+" got unexpected PnfsGetStorageInfoMessage "+
				     " : "+storage_info_msg+" ; Ignoring");
			}
			else     if( message instanceof PnfsGetFileMetaDataMessage) {
				PnfsGetFileMetaDataMessage storage_metadata =
					(PnfsGetFileMetaDataMessage)message;
				if(state == WAITING_FOR_PNFS_PARENT_INFO_STATE) {
					setState(RECEIVED_PNFS_PARENT_INFO_STATE);
					parentDirectorMetadataArrived(storage_metadata);
					return;
				}
				else if ( state == WAITING_FOR_PNFS_CHECK_BEFORE_DELETE_STATE ) {
					if (storage_metadata.getReturnCode() != 0) {
						esay("We were about to delete entry that does not exist : "+storage_metadata.toString()+
						     " PnfsGetFileMetaDataMessage return code="+storage_metadata.getReturnCode()+
						     " reason : "+storage_metadata.getErrorObject());
						sendErrorReply();
						return;
					}
					else {
						state=RECEIVED_PNFS_CHECK_BEFORE_DELETE_STATE;
						deletePnfsEntry();
						return;
					}

				}
				else {
					esay(this.toString()+" got unexpected PnfsGetFileMetaDataMessage "+
					     " : "+storage_metadata+" ; Ignoring");
				}
			}
			else if(message instanceof PoolMgrSelectPoolMsg) {
				PoolMgrSelectPoolMsg select_pool_msg =
					(PoolMgrSelectPoolMsg)message;
				if( state == WAITING_FOR_POOL_INFO_STATE) {
					setState(RECEIVED_POOL_INFO_STATE);
					poolInfoArrived(select_pool_msg);
					return;
				}
				esay(this.toString()+" got unexpected PoolMgrSelectPoolMsg "+
				     " : "+select_pool_msg+" ; Ignoring");
			}
			else if(message instanceof PoolIoFileMessage) {
				PoolIoFileMessage first_pool_reply =
					(PoolIoFileMessage)message;
				if( state == WAITING_FIRST_POOL_REPLY_STATE) {
					setState(RECEIVED_FIRST_POOL_REPLY_STATE);
					poolFirstReplyArrived(first_pool_reply);
					return;
				}
				esay(this.toString()+" got unexpected PoolIoFileMessage "+
				     " : "+first_pool_reply+" ; Ignoring");
			}
			else if (message instanceof PnfsDeleteEntryMessage) {
				PnfsDeleteEntryMessage deleteReply = (PnfsDeleteEntryMessage) message;
				if ( state == WAITING_FOR_PNFS_ENTRY_DELETE ) {
					setState(RECEIVED_PNFS_ENTRY_DELETE);
					if (deleteReply.getReturnCode() != 0) {
						esay("Delete failed : "+deleteReply.getPath()+
						     " PnfsDeleteEntryMessage return code="+deleteReply.getReturnCode()+
						     " reason : "+deleteReply.getErrorObject());
						numberOfRetries++;
						int numberOfRemainingRetries = manager.getMaxNumberOfDeleteRetries()-numberOfRetries;
						esay("Will retry : "+numberOfRemainingRetries+" times");
						deletePnfsEntry();
					}
					else {
						say("Received PnfsDeleteEntryMessage, Deleted  : "+deleteReply.getPath());
						sendErrorReply();
					}
				}
			}
		}
		manager.persist(this);
	}
/**      */
	public void answerTimedOut(CellMessage request) {
	}
/**      */
	public void exceptionArrived(CellMessage request, Exception exception) {
	}
/**      */
	public void parentDirectorMetadataArrived(PnfsGetFileMetaDataMessage file_metadata) {
		say("parentInfoArrived(TransferManagerHandler)");
		if(file_metadata.getReturnCode() != 0) {
			sendErrorReply(3,  new java.io.IOException(
					       "can't get metadata for parent directory "+parentDir));
			return;
		}
        FileAttributes attributes = file_metadata.getFileAttributes();
        attributes.setPnfsId(file_metadata.getPnfsId());

        AccessType canCreateFile =
                  permissionHandler.canCreateFile(
                      transferRequest.getUser().getSubject(),
                      attributes);
        if(canCreateFile != AccessType.ACCESS_ALLOWED ) {
            say("user has no permission to write to directory "+parentDir);
			sendErrorReply(3,  new java.io.IOException(
					       "user has no permission to write to directory "+parentDir));
			return;
        }

		PnfsCreateEntryMessage create = new PnfsCreateEntryMessage(
                pnfsPath ,
                getUid(),
                getGid() ,
                0644,
                permissionHandler.getRequiredAttributes()) ;
		setState(WAITING_FOR_PNFS_ENTRY_CREATION_INFO_STATE);
		manager.persist(this);
		try {
			manager.sendMessage(new CellMessage(new CellPath( manager.getPnfsManagerName()),
						    create ) ,
				    true ,
				    true,
				    this,
				    manager.getPnfsManagerTimeout()*1000
				);
		}
		catch(Exception ee ) {
			esay(ee);
			sendErrorReply(4,ee);
			return ;
		}
	}

    /**      */
	public void createEntryResponseArrived(PnfsCreateEntryMessage create) {
        	if(create.getReturnCode() == 0) {
			created = true;
			manager.persist(this);
		}
		else {
			sendErrorReply(5, "failed to create pnfs entry: "+create.getErrorObject());
			return;
		}

        storageInfo  = create.getStorageInfo();
		fileAttributes = create.getFileAttributes();
        create.getMetaData();
        pnfsId        = create.getPnfsId();
        if(storageInfo == null || fileAttributes == null || pnfsId == null) {
            PnfsGetStorageInfoMessage sInfo = new PnfsGetStorageInfoMessage(
                    permissionHandler.getRequiredAttributes()) ;
            sInfo.setPnfsPath( pnfsPath ) ;
            setState(WAITING_FOR_PNFS_INFO_STATE);
            manager.persist(this);
            try {
                    manager.sendMessage(new CellMessage(
                            new CellPath(manager.getPnfsManagerName()),
                                sInfo),
                            true,
                            true,
                            this,
                            manager.getPnfsManagerTimeout()*1000
                        );
            }
            catch(Exception ee ) {
                    esay(ee);
                    transferRequest.setFailed(2, ee);
                    return ;
            }
            return;
        }
		pnfsIdString  = pnfsId.toString();
		info.setPnfsId(pnfsId);
                checkPermissionAndSelectPool();
	}
/**      */
	public void storageInfoArrived( PnfsGetStorageInfoMessage storage_info_msg){
		if( storage_info_msg.getReturnCode() != 0 ) {
			sendErrorReply(6, new
				       CacheException( "cant get storage info for file "+pnfsPath+" : "+
						       storage_info_msg.getErrorObject() ) );
			return;
		}
		if(!store && tlog != null) {
			tlog.middle(storage_info_msg.getStorageInfo().getFileSize());
		}
		//
		// Added by litvinse@fnal.gov
		//
		pnfsId        = storage_info_msg.getPnfsId();
		info.setPnfsId(pnfsId);
		pnfsIdString  = pnfsId.toString();
		manager.persist(this);
		if ( store ) {
			synchronized(manager.justRequestedIDs) {
				if (manager.justRequestedIDs.contains(storage_info_msg.getPnfsId())) {
					sendErrorReply(6, new
						       CacheException( "pnfs pnfsid: "+pnfsId.toString()+" file "+pnfsPath+"  is already there"));
					return;
				}
				Iterator iter = manager.justRequestedIDs.iterator();
				while(iter.hasNext()) {
					PnfsId pnfsid = (PnfsId)iter.next();
					say("found pnfsid: "+pnfsid.toString());
				}
				manager.justRequestedIDs.add(pnfsId);
			}
		}
                if(storageInfo == null) {
                    storageInfo  = storage_info_msg.getStorageInfo();
                }

                if(fileAttributes == null) {
                    fileAttributes =
                            storage_info_msg.getFileAttributes();
                }
		say("storageInfoArrived(uid="+
                transferRequest.getUser().getUid()+
                " gid="+transferRequest.getUser().getGid()+
                " pnfsid="+pnfsId+" storageInfo="+storageInfo+
                " fileAttributes="+fileAttributes);
                checkPermissionAndSelectPool();

        }

        public void checkPermissionAndSelectPool() {
		if(store) {
			boolean can_write =  
                AccessType.ACCESS_ALLOWED  ==permissionHandler.canWriteFile(
                     transferRequest.getUser().getSubject(),
                     fileAttributes);
			if(!can_write) {
				sendErrorReply(3,  new java.io.IOException(
						       "user has no permission to write to file"+pnfsPath));
				return;
			}
			if(fileAttributes.getSize() != 0 && !manager.isOverwrite()) {
				sendErrorReply(3,  new java.io.IOException(
						       "file size is not 0, user has no permission to write to file"+pnfsPath));
				return;

			}
		}
		else {
			boolean can_read =
                AccessType.ACCESS_ALLOWED  ==permissionHandler.canReadFile(
                     transferRequest.getUser().getSubject(),
                     fileAttributes);
			if(!can_read) {
				sendErrorReply(3,  new java.io.IOException(
						       "user has no permission to read file "+pnfsPath));
				return;
			}
		}
		try {
			protocol_info = manager.getProtocolInfo(getId(),transferRequest);
		}
		catch(IOException ioe) {
			esay(ioe);
			//we do not need to send the new message
			// since the original reply has not been sent yet
			sendErrorReply(4,ioe);
			return ;
		}
		Thread current = Thread.currentThread();
                long size =transferRequest.getSize() == null ? 0L: transferRequest.getSize().longValue();
		PoolMgrSelectPoolMsg request =
			store ?
			(PoolMgrSelectPoolMsg)
			new PoolMgrSelectWritePoolMsg(
				pnfsId,
				storageInfo,
				protocol_info ,
				size)
			:
			(PoolMgrSelectPoolMsg)
			new PoolMgrSelectReadPoolMsg(
				pnfsId  ,
				storageInfo,
				protocol_info ,
				size);
                request.setPnfsPath(pnfsPath);
		say("PoolMgrSelectPoolMsg: " + request );
		setState(WAITING_FOR_POOL_INFO_STATE);
		manager.persist(this);
		try {
			manager.sendMessage(new CellMessage(manager.getPoolManagerPath(),
						    request),
				    true,
				    true,
				    this,
				    manager.getPoolManagerTimeout()*1000
				);
		}
		catch(Exception e ) {
			esay(e);
			sendErrorReply(4,e);
			return ;
		}
	}
/**      */
	public void poolInfoArrived(PoolMgrSelectPoolMsg pool_info)  {
		say("poolManagerReply = "+pool_info);
		if( pool_info.getReturnCode() != 0 ) {
			sendErrorReply(5, new
				       CacheException( "Pool manager error: "+
						       pool_info.getErrorObject() ) );
			return;
		}
		setPool(pool_info.getPoolName());
		manager.persist(this);
		say("Positive reply from pool "+pool);
		startMoverOnThePool();
	}
/**      */
	public void startMoverOnThePool() {
		PoolIoFileMessage poolMessage = store ?
			(PoolIoFileMessage)
			new PoolAcceptFileMessage(
				pool,
				pnfsId,
				protocol_info ,
				storageInfo     ):
			(PoolIoFileMessage)
			new PoolDeliverFileMessage(
				pool,
				pnfsId,
				protocol_info ,
				storageInfo     );

		if( manager.getIoQueueName() != null ) {
			poolMessage.setIoQueueName(manager.getIoQueueName());
		}
		poolMessage.setInitiator(info.getTransaction());
		poolMessage.setId( id ) ;
		setState(WAITING_FIRST_POOL_REPLY_STATE);
		manager.persist(this);
                CellPath poolCellPath;
                String poolProxy = manager.getPoolProxy();
                if( poolProxy == null ){
                    poolCellPath = new CellPath(pool);
                }else{
                    poolCellPath = new CellPath(poolProxy);
                    poolCellPath.add(pool);
                }

		try {
			manager.sendMessage(
                                new CellMessage(poolCellPath,
				    poolMessage),
                                true,
                                true,
                                this,
                                manager.getPoolTimeout()*1000
                            );
		}
		catch(Exception ee ) {
			esay(ee);
			sendErrorReply(4,ee);
			return ;
		}
		return ;
	}
/**      */
	public void poolFirstReplyArrived(PoolIoFileMessage poolMessage)  {
		say("poolReply = "+poolMessage);
		info.setTimeQueued(info.getTimeQueued() + System.currentTimeMillis());
		if( poolMessage.getReturnCode() != 0 ) {
			sendErrorReply(5, new
				       CacheException( "Pool error: "+
						       poolMessage.getErrorObject() ) );
			return;
		}
		info.setTransactionTime(-System.currentTimeMillis());
		say("Pool "+pool+" will deliver file "+pnfsId +" mover id is "+poolMessage.getMoverId());
		say("Starting moverTimeout timer");
		manager.startTimer(id);
		setMoverId(new Integer(poolMessage.getMoverId()));
		manager.persist(this);

	}
/**      */

	public void deletePnfsEntry() {
		if ( state==RECEIVED_PNFS_CHECK_BEFORE_DELETE_STATE) {
			if ( numberOfRetries<manager.getMaxNumberOfDeleteRetries()) {
				PnfsDeleteEntryMessage pnfsMsg = new PnfsDeleteEntryMessage(pnfsPath);
				setState(WAITING_FOR_PNFS_ENTRY_DELETE);
				manager.persist(this);
				pnfsMsg.setReplyRequired(true);
				try {
					manager.sendMessage(new CellMessage(new CellPath(manager.getPnfsManagerName()),
									    pnfsMsg),
							    true ,
							    true,
							    this,
							    manager.getPnfsManagerTimeout()*1000
						);
					return;
				}
				catch (Exception e) {
					esay("sendErrorReply: can not send PnfsDeleteEntryMessage:");
					esay(e);
					sendErrorReply();
					return;
				}
			}
			else {
				esay("Failed to remove PNFS entry after "+numberOfRetries);
				sendErrorReply();
				return;
			}
		}
		else {
			PnfsGetFileMetaDataMessage sInfo;
			sInfo = new PnfsGetFileMetaDataMessage() ;
			sInfo.setPnfsPath(pnfsPath);
			setState(WAITING_FOR_PNFS_CHECK_BEFORE_DELETE_STATE);
			try {
				manager.sendMessage(
					new CellMessage(new CellPath(manager.getPnfsManagerName()),
							sInfo ),
					true ,
					true,
					this,
					manager.getPnfsManagerTimeout()*1000
					);
			}
			catch(Exception ee ) {
				esay(ee);
				sendErrorReply();
				return ;
				}
		}
	}

	public void poolDoorMessageArrived(DoorTransferFinishedMessage doorMessage) {
		say("poolDoorMessageArrived, doorMessage.getReturnCode()="+doorMessage.getReturnCode());
		if(doorMessage.getReturnCode() != 0 ) {
			sendErrorReply(8,"tranfer failed :"+doorMessage.getErrorObject());
			return;
		}

		DoorTransferFinishedMessage finished = (DoorTransferFinishedMessage) doorMessage;
		if(store && tlog != null) {
			tlog.middle(finished.getStorageInfo().getFileSize());
		}
		sendSuccessReply();
	}
/**      */
	public void sendErrorReply(int replyCode,
				    Object errorObject) {
		sendErrorReply(replyCode,errorObject,true);
	}
/**      */
	public void sendErrorReply(int replyCode,
				    Object errorObject,
				    boolean cancelTimer) {

		_replyCode=replyCode;
		_errorObject=errorObject;
		_cancelTimer=cancelTimer;

		esay("sending error reply, reply code="+replyCode+" errorObject="+errorObject+" for "+toString(true));

		if(store && created) {// Timur: I think this check  is not needed, we might not ever get storage info and pnfs id: && pnfsId != null && metadata != null && metadata.getFileSize() == 0) {
			if (state!=WAITING_FOR_PNFS_ENTRY_DELETE && state!=RECEIVED_PNFS_ENTRY_DELETE) {
				esay(" we created the pnfs entry and the store failed: deleting "+pnfsPath);
				deletePnfsEntry();
				return;
			}
		}


		if(tlog != null) {
			tlog.error("getFromRemoteGsiftpUrl failed: state = "+state+
				   " replyCode="+replyCode+" errorObject="+
				   errorObject);
		}
		if (info.getTimeQueued() < 0)
			info.setTimeQueued(info.getTimeQueued() + System.currentTimeMillis());
		if (info.getTransactionTime() < 0)
			info.setTransactionTime(info.getTransactionTime() + System.currentTimeMillis());
		sendDoorRequestInfo(replyCode, errorObject.toString());

		setState(SENT_ERROR_REPLY_STATE,errorObject);
		manager.persist(this);

		if(cancelTimer) {
			manager.stopTimer(id);
		}



		if ( store ) {
			synchronized(manager.justRequestedIDs) {
				manager.justRequestedIDs.remove(pnfsId);
			}
		}
		manager.finish_transfer();
		try {
			TransferFailedMessage errorReply = new TransferFailedMessage(transferRequest,replyCode, errorObject);
			manager.sendMessage(new CellMessage(sourcePath,errorReply));
		}
		catch(Exception e) {
			esay(e);
			//can not do much more here!!!
		}
		Long longId = new Long(id);
		//this will allow the handler to be garbage collected
		// once we sent a response
		synchronized(manager.activeTransfersIDs) {
			manager.removeActiveTransfer(longId);
		}
	}
	public void sendErrorReply() {



		int replyCode = _replyCode;
		Object errorObject=_errorObject;
		boolean cancelTimer=_cancelTimer;

		esay("sending error reply, reply code="+replyCode+" errorObject="+errorObject+" for "+toString(true));

		if(tlog != null) {
			tlog.error("getFromRemoteGsiftpUrl failed: state = "+state+
				   " replyCode="+replyCode+" errorObject="+
				   errorObject);
		}
		if (info.getTimeQueued() < 0)
			info.setTimeQueued(info.getTimeQueued() + System.currentTimeMillis());
		if (info.getTransactionTime() < 0)
			info.setTransactionTime(info.getTransactionTime() + System.currentTimeMillis());
		sendDoorRequestInfo(replyCode, errorObject.toString());

		setState(SENT_ERROR_REPLY_STATE,errorObject);
		manager.persist(this);

		if(cancelTimer) {
			manager.stopTimer(id);
		}



		if ( store ) {
			synchronized(manager.justRequestedIDs) {
				manager.justRequestedIDs.remove(pnfsId);
			}
		}
		manager.finish_transfer();
		try {
			TransferFailedMessage errorReply = new TransferFailedMessage(transferRequest,replyCode, errorObject);
			manager.sendMessage(new CellMessage(sourcePath,errorReply));
		}
		catch(Exception e) {
			esay(e);
			//can not do much more here!!!
		}
		Long longId = new Long(id);
		//this will allow the handler to be garbage collected
		// once we sent a response
		synchronized(manager.activeTransfersIDs) {
			manager.removeActiveTransfer(longId);
		}
	}
/**      */
	public void sendSuccessReply() {
		say("sendSuccessReply for: "+toString(true));
		if (info.getTimeQueued() < 0)
			info.setTimeQueued(info.getTimeQueued() + System.currentTimeMillis());
		if (info.getTransactionTime() < 0)
			info.setTransactionTime(info.getTransactionTime() + System.currentTimeMillis());
		sendDoorRequestInfo(0, "");
		setState(SENT_SUCCESS_REPLY_STATE);
		manager.persist(this);
		manager.stopTimer(id);
		if ( store ) {
			synchronized(manager.justRequestedIDs) {
				manager.justRequestedIDs.remove(pnfsId);
			}
		}
		manager.finish_transfer();
		if(tlog != null) {
			tlog.success();
		}
		try {
			TransferCompleteMessage errorReply = new TransferCompleteMessage(transferRequest);
			manager.sendMessage(new CellMessage(sourcePath,errorReply));
		}
		catch(Exception e)  {
			esay(e);
			//can not do much more here!!!
		}
		Long longId = new Long(id);
		//this will allow the handler to be garbage collected
		// once we sent a response
		synchronized(manager.activeTransfersIDs) {
			manager.removeActiveTransfer(longId);
		}
	}

	/** Sends status information to the biling cell. */
	void sendDoorRequestInfo(int code, String msg)
	{
	    try {
		info.setResult(code, msg);
                esay("Sending info: " + info);
		manager.sendMessage(new CellMessage(new CellPath("billing") , info));
	    } catch (NoRouteToCellException e) {
		esay("Couldn't send billing info : " + e);
            }
	}

	/**      */
	public CellPath getRequestSourcePath() {
		return sourcePath;
	}
/**      */
	public void cancel( ) {
		esay("cancel");
		if(moverId != null) {
			killMover(this.pool,moverId.intValue());
		}
		sendErrorReply(24, new java.io.IOException("canceled"));
	}
/**      */
	public void timeout( ) {
		esay("timeout");
		if(moverId != null) {
			killMover(this.pool,moverId.intValue());
		}
		sendErrorReply(24, new java.io.IOException("timed out while waiting for mover reply"),false);
	}
/**      */
	public void cancel(CancelTransferMessage cancel ) {
		esay("cancel");
		if(moverId != null) {
			killMover(this.pool,moverId.intValue());
		}
		sendErrorReply(24, new java.io.IOException("canceled"));
	}
/**      */
	public synchronized String toString(boolean long_format) {
		StringBuffer sb = new StringBuffer("id=");
		sb.append(id);
		if(!long_format) {
			if(store) {
				sb.append(" store src=");
				sb.append(transferRequest.getRemoteURL());
				sb.append(" dest=");
				sb.append(transferRequest.getPnfsPath());
			}
			else {
				sb.append(" restore src=");
				sb.append(transferRequest.getPnfsPath());
				sb.append(" dest=");
				sb.append(transferRequest.getRemoteURL());
			}
			return sb.toString();
		}
		sb.append("\n  state=").append(state);
		sb.append("\n  user=").append(transferRequest.getUser());
		if(pnfsId != null) {
			sb.append("\n   pnfsId=").append(pnfsId);
		}
		if(storageInfo != null) {
			sb.append("\n  storageInfo=").append(storageInfo);
		}
		if(pool != null) {
			sb.append("\n   pool=").append(pool);
			if(moverId != null) {
				sb.append("\n   moverId=").append(moverId);
			}
		}
		return sb.toString();
	}
/**      */
	public String toString() {
		return toString(false);
	}

/**      */
	public java.lang.String getPool() {
		return pool;
	}
/**      */
	public void setPool(java.lang.String pool) {
		this.pool = pool;
	}

	public void killMover(String pool,int moverId) {
		esay("sending mover kill to pool "+pool+" for moverId="+moverId );
		PoolMoverKillMessage killMessage = new PoolMoverKillMessage(pool,moverId);
		killMessage.setReplyRequired(false);
		try {
			manager.sendMessage( new CellMessage( new CellPath (  pool), killMessage )  );
		}
		catch(Exception e) {
			esay(e);
		}
	}

	public void setState(int istate) {
		this.state = istate;
		TransferManagerHandlerState ts = new TransferManagerHandlerState(this,null);
		manager.persist(ts);
	}


	public void setState(int istate, Object errorObject) {
		this.state = istate;
		TransferManagerHandlerState ts = new TransferManagerHandlerState(this,errorObject);
		manager.persist(ts);
	}

	public void setMoverId(Integer moverid) {
		moverId = moverid;
	}

        public static final void main(String[] args) {
            System.out.println("This is a main in handler");
        }

	public int getUid() { return transferRequest.getUser().getUid(); }
	public int getGid() { return transferRequest.getUser().getGid(); }
	public String getPnfsPath() { return pnfsPath; }
	public boolean getStore() { return store; }
	public boolean getCreated() { return created; }
	public boolean getLocked() { return locked; }
	public String getPnfsIdString() { return pnfsIdString; }
	public String getRemoteUrl() { return remoteUrl; }
	public int getState() { return state; }
	public long getId() { return id; }
	public Integer getMoverId() { return moverId; }
	public String getSpaceReservationId () { return spaceReservationId; }
	public long getCreationTime() { return creationTime; }
	public long getLifeTime() { return lifeTime; }
	public Long getCredentialId() { return credentialId; }
}
