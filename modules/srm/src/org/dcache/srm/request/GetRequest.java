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
 * GetRequestHandler.java
 *
 * Created on July 15, 2003, 1:59 PM
 */


package org.dcache.srm.request;

import org.dcache.srm.v2_2.TRequestType;
import org.dcache.srm.SRMUser;
import java.util.HashSet;
import org.dcache.srm.SRMException;
import org.dcache.srm.scheduler.IllegalStateTransition;
import org.dcache.srm.scheduler.State;

import org.dcache.srm.v2_2.SrmPrepareToGetResponse;
import org.dcache.srm.v2_2.TGetRequestFileStatus;
import org.dcache.srm.v2_2.SrmStatusOfGetRequestResponse;
import org.dcache.srm.v2_2.TSURLReturnStatus;
import org.dcache.srm.v2_2.ArrayOfTGetRequestFileStatus;

/*
 * @author  timur
 */
public final class GetRequest extends ContainerRequest {
    /** array of protocols supported by client or server (copy) */
    protected String[] protocols;
    
    
    public GetRequest(SRMUser user,
    Long requestCredentialId,
    String[] surls,
    String[] protocols,
    long lifetime,
    long max_update_period,
    int max_number_of_retries,
    String description,
    String client_host
    ) throws Exception {
        super(user,
                requestCredentialId,
                max_number_of_retries,
                max_update_period,
                lifetime,
                description,
                client_host);
        say("constructor");
        say("user = "+user);
        say("requestCredetialId="+requestCredentialId);
        int len = protocols.length;
        this.protocols = new String[len];
        System.arraycopy(protocols,0,this.protocols,0,len);
        len = surls.length;
        fileRequests = new FileRequest[len];
        for(int i = 0; i<len; ++i) {
            GetFileRequest fileRequest =
            new GetFileRequest(getId(),requestCredentialId, surls[i],  lifetime,  max_number_of_retries);
            
            fileRequests[i] = fileRequest;
        }
        storeInSharedMemory();
    }
    
    /**
     * restore constructor
     */
    public  GetRequest(
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
    )  throws java.sql.SQLException {
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
        rlock();
        try {
            for(int i =0; i<fileRequests.length;++i) {
                if(((GetFileRequest)fileRequests[i]).getSurlString().equals(surl)) {
                    return fileRequests[i];
                }
            }
        } finally {
            runlock();
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
        rlock();
        try {
            for(int i = 0; i < fileRequests.length ;++ i) {
                GetFileRequest fileRequest = (GetFileRequest) fileRequests[i];
                fileRequest.schedule();
            }
        } finally {
            runlock();
        }
    }

    // ContainerRequest request;
    /**
     * for each file request in the request
     * call storage.PrepareToGet() which should do all the work
     */
    public int getNumOfFileRequest() {
        rlock();
        try {
            if(fileRequests == null) {
                return 0;
            }
            return fileRequests.length;
        } finally {
            runlock();
        }
    }
    
    
    public HashSet callbacks_set =  new HashSet();
    
    private static final long serialVersionUID = -3739166738239918248L;
    
    /**
     * storage.PrepareToGet() is given this callbacks
     * implementation
     * it will call the method of GetCallbacks to indicate
     * progress
     */
    
    public String getMethod() {
        return "Get";
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
            say("get request state changed to "+state);
            for(int i = 0 ; i < fileRequests.length; ++i) {
                try {
                    FileRequest fr = fileRequests[i];
                    State fr_state = fr.getState();
                    if(!State.isFinalState(fr_state ))
                    {

                        say("changing fr#"+fileRequests[i].getId()+" to "+state);
                            fr.setState(state,"changing file state becase requests state changed");
                    }
                }
                catch(IllegalStateTransition ist) {
                    esay("Illegal State Transition : " +ist.getMessage());
                }
            }
           
        }
            
    }
    
    public String[] getProtocols() {
        String[] copy = new String[protocols.length];
        rlock();
        try {
            System.arraycopy(protocols, 0, copy, 0, protocols.length);
        } finally {
            runlock();
        }
        return copy;
    }
        
    public final SrmPrepareToGetResponse getSrmPrepareToGetResponse()  
    throws SRMException ,java.sql.SQLException {
        SrmPrepareToGetResponse response = new SrmPrepareToGetResponse();
        // getTReturnStatus should be called before we get the
       // statuses of the each file, as the call to the 
       // getTReturnStatus() can now trigger the update of the statuses
       // in particular move to the READY state, and TURL availability\
        rlock();
        ArrayOfTGetRequestFileStatus arrayOfTGetRequestFileStatus;
        try {
            response.setReturnStatus(getTReturnStatus());
            response.setRequestToken(getTRequestToken());

            arrayOfTGetRequestFileStatus =
                new ArrayOfTGetRequestFileStatus();
            arrayOfTGetRequestFileStatus.setStatusArray(getArrayOfTGetRequestFileStatus(null));
        } finally {
            runlock();
        }
        response.setArrayOfFileStatuses(arrayOfTGetRequestFileStatus);
        return response;
    }
    
    public final SrmStatusOfGetRequestResponse getSrmStatusOfGetRequestResponse()  
    throws SRMException, java.sql.SQLException {
        return getSrmStatusOfGetRequestResponse(null);
    }
    
    public final SrmStatusOfGetRequestResponse getSrmStatusOfGetRequestResponse(
            String[] surls)  
    throws SRMException, java.sql.SQLException {
        SrmStatusOfGetRequestResponse response = new SrmStatusOfGetRequestResponse();
        // getTReturnStatus should be called before we get the
       // statuses of the each file, as the call to the 
       // getTReturnStatus() can now trigger the update of the statuses
       // in particular move to the READY state, and TURL availability
        response.setReturnStatus(getTReturnStatus());

        ArrayOfTGetRequestFileStatus arrayOfTGetRequestFileStatus;
        rlock();
        try {
            arrayOfTGetRequestFileStatus =
                new ArrayOfTGetRequestFileStatus();
            arrayOfTGetRequestFileStatus.setStatusArray(
                getArrayOfTGetRequestFileStatus(surls));
            response.setArrayOfFileStatuses(arrayOfTGetRequestFileStatus);
        } finally {
            runlock();
        }
        String s ="getSrmStatusOfGetRequestResponse:";
        s+= " StatusCode = "+response.getReturnStatus().getStatusCode().toString();
        for(TGetRequestFileStatus fs :arrayOfTGetRequestFileStatus.getStatusArray()) {
            s += " FileStatusCode = "+fs.getStatus().getStatusCode();
        }
        say(s);
        return response;
    }
    
   
    private String getTRequestToken() {
        return getId().toString();
    }
    
   /* private ArrayOfTGetRequestFileStatus getArrayOfTGetRequestFileStatus()throws SRMException,java.sql.SQLException {
        return getArrayOfTGetRequestFileStatus(null);
    }
    */
    private TGetRequestFileStatus[] getArrayOfTGetRequestFileStatus(String[] surls)
            throws SRMException,java.sql.SQLException {
        rlock();
        try {
            int len = surls == null ? getNumOfFileRequest():surls.length;
            TGetRequestFileStatus[] getFileStatuses
                = new TGetRequestFileStatus[len];
            if(surls == null) {
                for(int i = 0; i< len; ++i) {
                    GetFileRequest fr =(GetFileRequest)fileRequests[i];
                    getFileStatuses[i] = fr.getTGetRequestFileStatus();
                }
            } else {
                for(int i = 0; i< len; ++i) {
                    GetFileRequest fr =(GetFileRequest)getFileRequestBySurl(surls[i]);
                    getFileStatuses[i] = fr.getTGetRequestFileStatus();
                }

            }
            return getFileStatuses;
        } finally {
            runlock();
        }
    }
    /*public TSURLReturnStatus[] getArrayOfTSURLReturnStatus()
    throws SRMException,java.sql.SQLException {
        return null;
    }
     */
    public TSURLReturnStatus[] getArrayOfTSURLReturnStatus(String[] surls) throws SRMException,java.sql.SQLException {
        int len ;
        TSURLReturnStatus[] surlLReturnStatuses;

        rlock();
        try {
            if(surls == null) {
                len = getNumOfFileRequest();
               surlLReturnStatuses = new TSURLReturnStatus[len];
            }
            else {
                len = surls.length;
               surlLReturnStatuses = new TSURLReturnStatus[surls.length];
            }
            if(surls == null) {
                for(int i = 0; i< len; ++i) {
                    GetFileRequest fr =(GetFileRequest)fileRequests[i];
                    surlLReturnStatuses[i] = fr.getTSURLReturnStatus();
                }
            } else {
                for(int i = 0; i< len; ++i) {
                    GetFileRequest fr =(GetFileRequest)getFileRequestBySurl(surls[i]);
                    surlLReturnStatuses[i] = fr.getTSURLReturnStatus();
                }

            }
            return surlLReturnStatuses;
        } finally {
            runlock();
        }
    }

    public TRequestType getRequestType() {
        return TRequestType.PREPARE_TO_GET;
    }

}
