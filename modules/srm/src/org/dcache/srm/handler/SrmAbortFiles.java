/*
 * SrmLs.java
 *
 * Created on October 4, 2005, 3:40 PM
 */

package org.dcache.srm.handler;

import org.dcache.srm.v2_2.TReturnStatus;
import org.dcache.srm.v2_2.TStatusCode;
import org.dcache.srm.v2_2.SrmAbortFilesRequest;
import org.dcache.srm.v2_2.SrmAbortFilesResponse;
import org.dcache.srm.v2_2.ArrayOfTSURLReturnStatus;
import org.dcache.srm.v2_2.TSURLReturnStatus;
import org.dcache.srm.SRMUser;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMException;
import org.dcache.srm.scheduler.Scheduler;
import org.dcache.srm.request.ContainerRequest;
import org.dcache.srm.request.FileRequest;
import org.dcache.srm.util.Configuration;
import org.dcache.srm.scheduler.State;
import org.dcache.srm.scheduler.IllegalStateTransition;
import org.dcache.srm.v2_2.ArrayOfTSURLReturnStatus;
import org.apache.log4j.Logger;
import org.apache.axis.types.URI.MalformedURIException;
import java.sql.SQLException;


/**
 *
 * @author  timur
 */
public class SrmAbortFiles {
    
    private static Logger logger = 
            Logger.getLogger(SrmAbortFiles.class);
    
    private final static String SFN_STRING="?SFN=";
    AbstractStorageElement storage;
    SrmAbortFilesRequest srmAbortFilesRequest;
    SrmAbortFilesResponse response;
    Scheduler getScheduler;
    SRMUser user;
    RequestCredential credential;
    Configuration configuration;
    private int results_num;
    private int max_results_num;
    int numOfLevels =0;
    /** Creates a new instance of SrmLs */
    public SrmAbortFiles(
            SRMUser user,
            RequestCredential credential,
            SrmAbortFilesRequest srmAbortFilesRequest,
            AbstractStorageElement storage,
            org.dcache.srm.SRM srm,
            String client_host) {
        this.srmAbortFilesRequest = srmAbortFilesRequest;
        this.user = user;
        this.credential = credential;
        this.storage = storage;
        this.getScheduler = srm.getGetRequestScheduler();
        this.configuration = srm.getConfiguration();
    }
    
    boolean longFormat =false;
    String servicePathAndSFNPart = "";
    int port;
    String host;
    public SrmAbortFilesResponse getResponse() {
        if(response != null ) return response;
        try {
            response = srmAbortFiles();
        } catch(MalformedURIException mue) {
            logger.debug(" malformed uri : "+mue.getMessage());
            response = getFailedResponse(" malformed uri : "+mue.getMessage(),
                    TStatusCode.SRM_INVALID_REQUEST);
        } catch(SQLException sqle) {
            logger.error(sqle);
            response = getFailedResponse("sql error "+sqle.getMessage(),
                    TStatusCode.SRM_INTERNAL_ERROR);
        } catch(SRMException srme) {
            logger.error(srme);
            response = getFailedResponse(srme.toString());
        } catch(IllegalStateTransition ist) {
            logger.error(ist);
            response = getFailedResponse(ist.toString());
        }
        return response;
    }
    
    public static final SrmAbortFilesResponse getFailedResponse(String error) {
        return getFailedResponse(error,null);
    }
    
    public static final SrmAbortFilesResponse getFailedResponse(String error,TStatusCode statusCode) {
        if(statusCode == null) {
            statusCode =TStatusCode.SRM_FAILURE;
        }
        TReturnStatus status = new TReturnStatus();
        status.setStatusCode(statusCode);
        status.setExplanation(error);
        SrmAbortFilesResponse srmAbortFilesResponse = new SrmAbortFilesResponse();
        srmAbortFilesResponse.setReturnStatus(status);
        return srmAbortFilesResponse;
    }
    /**
     * implementation of srm ls
     */
    public SrmAbortFilesResponse srmAbortFiles()
    throws SRMException,MalformedURIException,
            SQLException, IllegalStateTransition {
        String requestToken = srmAbortFilesRequest.getRequestToken();
        if( requestToken == null ) {
            return getFailedResponse("request contains no request token");
        }
        Long requestId;
        try {
            requestId = new Long( requestToken);
        } catch (NumberFormatException nfe){
            return getFailedResponse(" requestToken \""+
                    requestToken+"\"is not valid",
                    TStatusCode.SRM_INVALID_REQUEST);
        }
        
        ContainerRequest request =(ContainerRequest) ContainerRequest.getRequest(requestId);
        if(request == null) {
            return getFailedResponse("request for requestToken \""+
                    requestToken+"\"is not found",
                    TStatusCode.SRM_INVALID_REQUEST);
            
        }
        org.apache.axis.types.URI [] surls ;
        if(  srmAbortFilesRequest.getArrayOfSURLs() == null ){
            return getFailedResponse("request does not contain any SURLs",
                    TStatusCode.SRM_INVALID_REQUEST);
            
        }  else {
            surls = srmAbortFilesRequest.getArrayOfSURLs().getUrlArray();
        }
        String surl_stings[] = null;
        if(surls.length == 0) {
            return getFailedResponse("0 lenght SiteURLs array",
                    TStatusCode.SRM_INVALID_REQUEST);
        }
        surl_stings = new String[surls.length];
        for(int i = 0; i< surls.length; ++i) {
            if(surls[i] == null ) {
                return getFailedResponse("surls["+i+"]=null");
            }
            surl_stings[i] = surls[i].toString();
            FileRequest fileRequest = request.getFileRequestBySurl(surl_stings[i]);
            synchronized(fileRequest) {
                State state = fileRequest.getState();
                if(!State.isFinalState(state)) {
                    fileRequest.setState(State.CANCELED,"SrmAbortFiles called");
                }
            }
        }
        
        TReturnStatus status = new TReturnStatus();
        status.setStatusCode(TStatusCode.SRM_SUCCESS);
        SrmAbortFilesResponse srmAbortFilesResponse = new SrmAbortFilesResponse();
        srmAbortFilesResponse.setReturnStatus(status);
        TSURLReturnStatus[] surlReturnStatusArray =  request.getArrayOfTSURLReturnStatus(surl_stings);
        for (TSURLReturnStatus surlReturnStatus:surlReturnStatusArray) {
            if(surlReturnStatus.getStatus().getStatusCode() == TStatusCode.SRM_ABORTED) {
                surlReturnStatus.getStatus().setStatusCode(TStatusCode.SRM_SUCCESS);
            }
        }

        srmAbortFilesResponse.setArrayOfFileStatuses(
                new ArrayOfTSURLReturnStatus(surlReturnStatusArray));
        // we do this to make the srm update the status of the request if it changed
        request.getTReturnStatus();

        return srmAbortFilesResponse;
        
    }
    
    
}
