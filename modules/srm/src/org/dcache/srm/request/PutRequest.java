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

/*
 * PutRequestHandler.java
 *
 * Created on July 15, 2003, 2:18 PM
 */

package org.dcache.srm.request;

import org.dcache.srm.v2_2.TRequestType;
import org.dcache.srm.SRMUser;
import java.util.HashSet;
import org.dcache.srm.scheduler.IllegalStateTransition;
import org.dcache.srm.scheduler.State;
import org.dcache.srm.SRMException;
import org.dcache.srm.v2_2.SrmPrepareToPutResponse;
import org.dcache.srm.v2_2.TPutRequestFileStatus;
import org.dcache.srm.v2_2.SrmStatusOfPutRequestResponse;
import org.dcache.srm.v2_2.TSURLReturnStatus;
import org.dcache.srm.v2_2.ArrayOfTPutRequestFileStatus;
import org.dcache.srm.v2_2.TAccessLatency;
import org.dcache.srm.v2_2.TRetentionPolicy;
import org.dcache.srm.v2_2.TOverwriteMode;
import org.dcache.srm.SRMInvalidRequestException;
import org.dcache.srm.SRMReleasedException;
import org.apache.log4j.Logger;
/**
 *
 * @author  timur
 */
public final class PutRequest extends ContainerRequest{
    private final static Logger logger =
            Logger.getLogger(PutRequest.class);

    // private PutFileRequest fileRequests[];
    protected String[] protocols;
    private TOverwriteMode overwriteMode;
    public PutRequest(SRMUser user,
    Long requestCredentialId,
    String[] srcFileNames,
    String[] destUrls,
    long[] sizes,
    boolean[] wantPermanent,
    String[] protocols,
    long lifetime,
    long max_update_period,
    int max_number_of_retries,
    String client_host,
    String spaceToken,
    TRetentionPolicy retentionPolicy,
    TAccessLatency accessLatency,
    String description
    ) throws Exception {

        super(user,
                requestCredentialId,
                max_number_of_retries,
                max_update_period,
                lifetime,
                description,
                client_host);
        int len = protocols.length;
        this.protocols = new String[len];
        System.arraycopy(protocols,0,this.protocols,0,len);

        len = srcFileNames.length;
        if(len != destUrls.length || len != sizes.length ||
        len != wantPermanent.length) {
            throw new IllegalArgumentException(
            "srcFileNames, destUrls, sizes,"+
            " wantPermanent arrays dimensions mismatch");
        }
        fileRequests = new FileRequest[len];
        for(int i = 0; i < len; ++i) {

            PutFileRequest fileRequest = new PutFileRequest(getId(),
            requestCredentialId,
            destUrls[i],sizes[i],
            lifetime,
            max_number_of_retries,
            spaceToken,
            retentionPolicy,
            accessLatency
            );

            fileRequests[i] = fileRequest;
        }
        storeInSharedMemory();

    }

    public  PutRequest(
    Long id,
    Long nextJobId,
    long creationTime,
    long lifetime,
    int stateId,
    String errorMessage,
    SRMUser user,
    String scheduelerId,
    long schedulerTimeStamp,
    int numberOfRetries,
    int maxNumberOfRetries,
    long lastStateTransitionTime,
    JobHistory[] jobHistoryArray,
    Long credentialId,
    FileRequest[] fileRequests,
    int retryDeltaTime,
    boolean should_updateretryDeltaTime,
    String description,
    String client_host,
    String statusCodeString,
    String[] protocols
    ) throws java.sql.SQLException {
        super( id,
        nextJobId,
        creationTime,
        lifetime,
        stateId,
        errorMessage,
        user,
        scheduelerId,
        schedulerTimeStamp,
        numberOfRetries,
        maxNumberOfRetries,
        lastStateTransitionTime,
        jobHistoryArray,
        credentialId,
        fileRequests,
        retryDeltaTime,
        should_updateretryDeltaTime,
        description,
        client_host,
        statusCodeString);
        this.protocols = protocols;

    }

    public FileRequest getFileRequestBySurl(String surl) throws java.sql.SQLException, SRMException{
        if(surl == null) {
           throw new SRMException("surl is null");
        }
        for(int i =0; i<fileRequests.length;++i) {
            if(((PutFileRequest)fileRequests[i]).getSurlString().equals(surl)) {
                return (PutFileRequest)fileRequests[i];
            }
        }
        throw new SRMException("file request for surl ="+surl +" is not found");
    }

    @Override
    public void schedule() throws InterruptedException,
    IllegalStateTransition {

        // save this request in request storage unconditionally
        // file requests will get stored as soon as they are
        // scheduled, and the saved state needs to be consistent

        saveJob(true);
        for(int i = 0; i < fileRequests.length ;++ i) {
            PutFileRequest fileRequest = (PutFileRequest) fileRequests[i];
            fileRequest.schedule();
        }
    }

    public int getNumOfFileRequest() {
        if(fileRequests == null) {
            return 0;
        }
        return fileRequests.length;
    }


    public void proccessRequest() {
        logger.debug("proccessing put request");
        String supported_protocols[];
        try {
            supported_protocols = getStorage().supportedGetProtocols();
        }
        catch(org.dcache.srm.SRMException srme) {
            logger.error(" protocols are not supported");
            logger.error(srme);
            //setFailedStatus ("protocols are not supported");
            return;
        }
        java.util.HashSet supported_protocols_set = new HashSet(java.util.Arrays.asList(supported_protocols));
        supported_protocols_set.retainAll(java.util.Arrays.asList(protocols));
        if(supported_protocols_set.isEmpty()) {
            logger.error("processPutRequest() : error selecting protocol");
            //setFailedStatus ("protocols are not supported");
            return;
        }
        //do not need it, let it be garbagecollected
        supported_protocols_set = null;

    }

    public HashSet callbacks_set =  new HashSet();

    private static final long serialVersionUID = -2911584313170829728L;

    /**
     * this callbacks are given to storage.prepareToPut
     * storage.prepareToPut calls methods of callbacks to indicate progress
     */

    public String getMethod() {
        return "Put";
    }

    //we do not want to stop handler if the
    //the request is ready (all file reqs are ready), since the actual transfer migth
    // happen any time after that
    // the handler, by staing in running state will prevent other queued
    // req from being executed
    public boolean shouldStopHandlerIfReady() {
        return false;
    }

    public void run() throws org.dcache.srm.scheduler.NonFatalJobFailure, org.dcache.srm.scheduler.FatalJobFailure {
    }

    protected void stateChanged(org.dcache.srm.scheduler.State oldState) {
        State state = getState();
        if(State.isFinalState(state)) {

            logger.debug("copy request state changed to "+state);
            for(int i = 0 ; i < fileRequests.length; ++i) {
                try {
                    FileRequest fr = fileRequests[i];
                    State fr_state = fr.getState();
                    if(!State.isFinalState(fr_state ))
                    {
                        logger.debug("changing fr#"+fileRequests[i].getId()+" to "+state);
                        fr.setState(state,"changing file state becase requests state changed");
                    }
                }
                catch(IllegalStateTransition ist) {
                    logger.error("Illegal State Transition : " +ist.getMessage());
                }
            }

        }

    }

    /**
     * Getter for property protocols.
     * @return Value of property protocols.
     */
    public java.lang.String[] getProtocols() {
        return this.protocols;
    }


    public final SrmPrepareToPutResponse getSrmPrepareToPutResponse()
    throws SRMException ,java.sql.SQLException {
        SrmPrepareToPutResponse response = new SrmPrepareToPutResponse();
       // getTReturnStatus should be called before we get the
       // statuses of the each file, as the call to the
       // getTReturnStatus() can now trigger the update of the statuses
       // in particular move to the READY state, and TURL availability
        response.setReturnStatus(getTReturnStatus());
        response.setRequestToken(getTRequestToken());
        ArrayOfTPutRequestFileStatus  arrayOfTPutRequestFileStatus =
            new ArrayOfTPutRequestFileStatus();
        arrayOfTPutRequestFileStatus.setStatusArray(
            getArrayOfTPutRequestFileStatus(null));
        response.setArrayOfFileStatuses(arrayOfTPutRequestFileStatus);
        return response;
    }

    public final SrmStatusOfPutRequestResponse getSrmStatusOfPutRequestResponse()
    throws SRMException, java.sql.SQLException {
            return getSrmStatusOfPutRequestResponse(null);
    }

    public  final SrmStatusOfPutRequestResponse getSrmStatusOfPutRequestResponse(
            String[] surls)
    throws SRMException, java.sql.SQLException {
        SrmStatusOfPutRequestResponse response = new SrmStatusOfPutRequestResponse();

        // getTReturnStatus should be called before we get the
        // statuses of the each file, as the call to the
        // getTReturnStatus() can now trigger the update of the statuses
        // in particular move to the READY state, and TURL availability
        response.setReturnStatus(getTReturnStatus());

        ArrayOfTPutRequestFileStatus  arrayOfTPutRequestFileStatus =
        new ArrayOfTPutRequestFileStatus();
        arrayOfTPutRequestFileStatus.setStatusArray(
            getArrayOfTPutRequestFileStatus(surls));
        response.setArrayOfFileStatuses(arrayOfTPutRequestFileStatus);
        String s ="getSrmStatusOfPutRequestResponse:";
        s+= " StatusCode = "+response.getReturnStatus().getStatusCode().toString();
        for(TPutRequestFileStatus fs :arrayOfTPutRequestFileStatus.getStatusArray()) {
            s += " FileStatusCode = "+fs.getStatus().getStatusCode();
        }
        logger.debug(s);
        return response;
    }

    private String getTRequestToken() {
        return getId().toString();
    }

    private TPutRequestFileStatus[] getArrayOfTPutRequestFileStatus(String[] surls) throws SRMException,java.sql.SQLException {
         int len = surls == null ? getNumOfFileRequest():surls.length;
        TPutRequestFileStatus[] putFileStatuses
            = new TPutRequestFileStatus[len];
        if(surls == null) {
            for(int i = 0; i< len; ++i) {
                PutFileRequest fr =(PutFileRequest)fileRequests[i];
                putFileStatuses[i] = fr.getTPutRequestFileStatus();
            }
        } else {
            for(int i = 0; i< len; ++i) {
                PutFileRequest fr =(PutFileRequest)getFileRequestBySurl(surls[i]);
                putFileStatuses[i] = fr.getTPutRequestFileStatus();
            }

        }
        return putFileStatuses;
    }


    public TSURLReturnStatus[] getArrayOfTSURLReturnStatus(String[] surls) throws SRMException,java.sql.SQLException {
        rlock();
        try {
            int len ;
            TSURLReturnStatus[] surlLReturnStatuses;
            if(surls == null) {
                len = getNumOfFileRequest();
               surlLReturnStatuses = new TSURLReturnStatus[len];
            }
            else {
                len = surls.length;
               surlLReturnStatuses = new TSURLReturnStatus[surls.length];
            }
            boolean failed_req = false;
            boolean pending_req = false;
            boolean running_req = false;
            boolean ready_req = false;
            boolean done_req = false;
            String fr_error="";
            if(surls == null) {
                for(int i = 0; i< len; ++i) {
                    PutFileRequest fr =(PutFileRequest)fileRequests[i];
                    surlLReturnStatuses[i] = fr.getTSURLReturnStatus();
                }
            } else {
                for(int i = 0; i< len; ++i) {
                    PutFileRequest fr =(PutFileRequest)getFileRequestBySurl(surls[i]);
                    surlLReturnStatuses[i] = fr.getTSURLReturnStatus();
                }

            }
            return surlLReturnStatuses;
        } finally {
            runlock();
        }
    }

    public TRequestType getRequestType() {
        return TRequestType.PREPARE_TO_PUT;
    }

    public TOverwriteMode getOverwriteMode() {
        rlock();
        try {
            return overwriteMode;
        } finally {
            runlock();
        }
    }

    public void setOverwriteMode(TOverwriteMode overwriteMode) {
        wlock();
        try {
            this.overwriteMode = overwriteMode;
        } finally {
            wunlock();
        }
    }

    public final boolean isOverwrite() {
        if(getConfiguration().isOverwrite()) {
            TOverwriteMode mode = getOverwriteMode();
            if(mode == null) {
                return getConfiguration().isOverwrite_by_default();
            }
            return mode.equals(TOverwriteMode.ALWAYS);
        }
        return false;
    }

    @Override
    public long extendLifetimeMillis(long newLifetimeInMillis) throws SRMException {
        try {
            return super.extendLifetimeMillis(newLifetimeInMillis);
        } catch(SRMReleasedException releasedException) {
            throw new SRMInvalidRequestException(releasedException.getMessage());
        }
    }
}
