//______________________________________________________________________________
//
// $Id$
// $Author$
//
// created 10/05 by Dmitry Litvintsev (litvinse@fnal.gov)
//
//______________________________________________________________________________

/*
 * SrmCopy
 *
 * Created on 10/05
 */

package org.dcache.srm.handler;

import org.dcache.srm.v2_2.*;
import org.dcache.srm.request.RequestUser;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMException;
import org.dcache.srm.request.CopyRequest;
import org.dcache.srm.request.sql.CopyFileRequestStorage;
import org.dcache.srm.request.sql.CopyRequestStorage;
import org.dcache.srm.util.Configuration;
import org.dcache.srm.scheduler.Scheduler;
import org.apache.axis.types.URI;
import org.dcache.srm.request.ContainerRequest;
import org.dcache.srm.SRMProtocol;

/**
 *
 * @author  litvinse
 */

public class SrmReleaseSpace {
    private final static String SFN_STRING="?SFN=";
    AbstractStorageElement  storage;
    SrmReleaseSpaceRequest  request;
    SrmReleaseSpaceResponse response;
    RequestUser             user;
    Scheduler               scheduler;
    RequestCredential       credential;
    Configuration           configuration;
    
    public SrmReleaseSpace(RequestUser user,
            RequestCredential credential,
            SrmReleaseSpaceRequest request,
            AbstractStorageElement storage,
            org.dcache.srm.SRM srm,
            String client_host) {
        
        if (request == null) {
            throw new NullPointerException("request is null");
        }
        this.request    = request;
        this.user       = user;
        this.credential = credential;
        if (storage == null) {
            throw new NullPointerException("storage is null");
        }
        this.storage = storage;
        this.scheduler = srm.getCopyRequestScheduler();
        if (scheduler == null) {
            throw new NullPointerException("scheduler is null");
        }
        this.configuration = srm.getConfiguration();
        if (configuration == null) {
            throw new NullPointerException("configuration is null");
        }
    }
    
    private void say(String txt) {
        if(storage!=null) {
            storage.log(txt);
        }
    }
    
    private void esay(String txt) {
        if(storage!=null) {
            storage.elog(txt);
        }
    }
    
    private void esay(Throwable t) {
        if(storage!=null) {
            storage.elog(" SrmCopy exception : ");
            storage.elog(t);
        }
    }
    
    public SrmReleaseSpaceResponse getResponse() {
        if(response != null ) return response;
        try {
            response = releaseSpace();
        } catch(Exception e) {
            storage.elog(e);
            response = getFailedResponse("Exception : "+e.toString());
        }
        return response;
    }
    
    public static final SrmReleaseSpaceResponse getFailedResponse(String text) {
        return getFailedResponse(text,null);
    }
    
    public static final SrmReleaseSpaceResponse getFailedResponse(String text, TStatusCode statusCode) {
        if(statusCode == null) {
            statusCode = TStatusCode.SRM_FAILURE;
        }
        SrmReleaseSpaceResponse response = new SrmReleaseSpaceResponse();
        TReturnStatus returnStatus    = new TReturnStatus();
        TReturnStatus status = new TReturnStatus();
        status.setStatusCode(statusCode);
        status.setExplanation(text);
        response.setReturnStatus(status);
        return response;
    }
    /**
     * implementation of srm copy
     */
    
    TReturnStatus status = new TReturnStatus();
    public SrmReleaseSpaceResponse releaseSpace() 
        throws SRMException,org.apache.axis.types.URI.MalformedURIException {
        if(request==null) {
            return getFailedResponse("srmReleaseSpace: null request passed to SrmReleaseSpace",
                    TStatusCode.SRM_INVALID_REQUEST);
        }
        String token = request.getSpaceToken();
        SrmReleaseSpaceCallbacks callbacks = new SrmReleaseSpaceCallbacks();
        storage.srmReleaseSpace(user,token,null, callbacks );
         callbacks.waitResult(60*1000); //one minute max
        SrmReleaseSpaceResponse response = new SrmReleaseSpaceResponse();
        response.setReturnStatus(status);
        return response;
   }
    
   private class  SrmReleaseSpaceCallbacks implements org.dcache.srm.SrmReleaseSpaceCallbacks {
         private boolean completed;
           public SrmReleaseSpaceCallbacks() {

           }
         
        public synchronized void waitResult(long timeout)
        {
           //System.out.println("PutCallbacks waitResult() starting for CopyFileRequest "+fileId);
           long start = System.currentTimeMillis(); 
           long current = start;
           while(true)
           {
               if(completed)
               {
                    //System.out.println("PutCallbacks waitResult() completed with success="+success+
                    //" for CopyFileRequest "+fileId);
                   return;
               }
               long wait = timeout - (current -start);
               if(wait > 0)
               {
                   try
                   {
                       this.wait(wait);
                   }
                   catch(InterruptedException ie){

                   }
               }
               else
               {
                status.setStatusCode(TStatusCode.SRM_INTERNAL_ERROR);
                status.setExplanation("release takes longer then "+timeout +" millis");
                return ;
               }
               current = System.currentTimeMillis(); 

           }
        }
        
        public synchronized void complete()
        {
            this.completed = true;
            this.notifyAll();
        }
           public void ReleaseSpaceFailed(String reason){
                esay("ReleaseSpaceFailed: "+reason);
                status.setStatusCode(TStatusCode.SRM_FAILURE);
                status.setExplanation(reason);
                complete();
                
            }
    
            public void SpaceReleased(String spaceReservationToken,long remainingSpaceSize){
                say("space "+spaceReservationToken+
                        " released successfully, remainingSpaceSize="+
                        remainingSpaceSize);
                status.setStatusCode(TStatusCode.SRM_SUCCESS);
                status.setExplanation("Space released");
                complete();
                
            }
    
            public void ReleaseSpaceFailed(Exception e){
                esay("ReleaseSpaceFailed");
                esay(e);
                status.setStatusCode(TStatusCode.SRM_FAILURE);
                status.setExplanation(e.toString());
                complete();
            }
           
        }
   public String toString(){
       return "SrmReleaseSpace("+request.getSpaceToken()+")";
   }
}
