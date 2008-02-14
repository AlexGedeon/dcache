// $Id$
// $Log: not supported by cvs2svn $
// Revision 1.15  2007/03/10 00:13:19  timur
// started work on adding support for optional overwrite
//
// Revision 1.14  2007/01/10 23:00:23  timur
// implemented srmGetRequestTokens, store request description in database, fixed several srmv2 issues
//
// Revision 1.13  2006/11/11 01:16:32  timur
// propagate the StorageType from copy to srmPrepareToPut
//
// Revision 1.12  2006/06/21 20:29:52  timur
// Upgraded code to the latest srmv2.2 wsdl (final)
//
// Revision 1.11  2006/06/20 15:42:16  timur
// initial v2.2 commit, code is based on a week old wsdl, will update the wsdl and code next
//
// Revision 1.10  2006/05/01 20:23:06  timur
// fixed a problems in srmturlGetter/Putter
//
// Revision 1.9  2006/03/24 00:22:16  timur
// regenerated stubs with array wrappers for v2_1
//
// Revision 1.8  2006/03/14 17:44:17  timur
// moving toward the axis 1_3
//
// Revision 1.7  2006/02/24 19:40:16  neha
// changes by Neha- to enable value of command line option 'webservice_path' to override its default value
//
// Revision 1.6  2006/02/03 01:43:38  timur
// make  srm v2 copy work with remote srm v1 and vise versa
//
// Revision 1.5  2006/01/31 21:27:06  timur
// fixed a few srm v2 copy problems
//
// Revision 1.4  2006/01/21 01:18:34  timur
// bug fixes in SrmCopy
//
// Revision 1.3  2006/01/19 01:48:21  timur
// more v2 copy work
//
// Revision 1.2  2006/01/11 17:29:50  timur
// first unetested implmentation of v2 turl getter and putter
//
// Revision 1.1  2006/01/10 19:03:37  timur
// adding srm v2 built in client
//
// Revision 1.5  2005/08/29 22:52:04  timur
// commiting changes made by Neha needed by OSG
//
// Revision 1.4  2005/03/24 19:16:18  timur
// made built in client always delegate credentials, which is required by LBL's DRM
//
// Revision 1.3  2005/03/13 21:56:28  timur
// more changes to restore compatibility
//
// Revision 1.2  2005/03/11 21:16:25  timur
// making srm compatible with cern tools again
//
// Revision 1.1  2005/01/14 23:07:14  timur
// moving general srm code in a separate repository
//
// Revision 1.3  2005/01/07 20:55:30  timur
// changed the implementation of the built in client to use apache axis soap toolkit
//
// Revision 1.2  2004/08/06 19:35:22  timur
// merging branch srm-branch-12_May_2004 into the trunk
//
// Revision 1.1.2.6  2004/08/03 16:51:47  timur
// removing unneeded dependancies on dcache
//
// Revision 1.1.2.5  2004/06/30 20:37:23  timur
// added more monitoring functions, added retries to the srm client part, adapted the srmclientv1 for usage in srmcp
//
// Revision 1.1.2.4  2004/06/16 19:44:32  timur
// added cvs logging tags and fermi copyright headers at the top, removed Copier.java and CopyJob.java
//
// Revision 1.1.2.3  2004/06/15 22:15:41  timur
// added cvs logging tags and fermi copyright headers at the top
//

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
 * RemoteTurlGetter.java
 *
 * Created on April 30, 2003, 2:38 PM
 */

package org.dcache.srm.client;

import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRM;
import org.globus.util.GlobusURL;
import org.globus.io.urlcopy.UrlCopy;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import org.dcache.srm.SRMUser;
import org.dcache.srm.client.SRMClientV1;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Iterator;
import org.dcache.srm.SRMException;
import org.dcache.srm.request.RequestCredential;
import java.beans.PropertyChangeListener;
import diskCacheV111.srm.server.SRMServerV1;
import org.dcache.srm.Logger;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.util.RequestStatusTool;

/**
 *
 * @author  timur
 */
public class RemoteTurlPutterV2 extends TurlGetterPutter
{
    private ISRM srmv2;
    private String requestToken;
    private String targetSpaceToken;
    private HashMap pendingSurlsToIndex = new HashMap();
    private Object sync = new Object();
    SrmPrepareToPutResponse srmPrepareToPutResponse;

    protected String SURLs[];
    protected int number_of_file_reqs;
    protected boolean createdMap;
    long[] sizes;
    long retry_timout;
    long requestLifetime;
    int retry_num;
    private TFileStorageType storageType;
    private TRetentionPolicy retentionPolicy;
    private TAccessLatency accessLatency;
    private TOverwriteMode overwriteMode;

    
    public void say(String s) {
        storage.log("RemoteTurlPutterV2"+
                (requestToken==null?"":" remote token="+requestToken)+" :"+s);
    }
    
    public void esay(String s) {
        storage.elog("RemoteTurlPutterV2"+
                (requestToken==null?"":" remote token="+requestToken)+" :"+s);
    }
    
    public void esay(Throwable t) {
        storage.elog("RemoteTurlPutterV2 exception"+
                (requestToken==null?"":" remote token="+requestToken));
        storage.elog(t);
    }
    
    public RemoteTurlPutterV2(AbstractStorageElement storage,
    RequestCredential credential, String[] SURLs,
    long sizes[],
    String[] protocols,
    PropertyChangeListener listener,
    long retry_timeout,
    int retry_num , 
    long requestLifetime,
    TFileStorageType storageType,
    TRetentionPolicy retentionPolicy,
    TAccessLatency accessLatency,
    TOverwriteMode overwriteMode,
    String targetSpaceToken) {
        super(storage,credential,protocols);
        this.SURLs = SURLs;
        this.number_of_file_reqs = SURLs.length;
        addListener(listener);
        this.sizes = sizes;
        this.retry_num = retry_num;
        this.retry_timout = retry_timeout;
        this.requestLifetime = requestLifetime;
        this.storageType = storageType;
        this.accessLatency = accessLatency;
        this.retentionPolicy = retentionPolicy;
        this.overwriteMode = overwriteMode;
        this.targetSpaceToken = targetSpaceToken;
   }
    
   
    protected  void putDone(String surl) throws java.rmi.RemoteException,org.apache.axis.types.URI.MalformedURIException{
        org.apache.axis.types.URI surlArray[] = new org.apache.axis.types.URI[1];
        SrmPutDoneRequest srmPutDoneRequest = new SrmPutDoneRequest();
        srmPutDoneRequest.setRequestToken(requestToken);
        srmPutDoneRequest.setArrayOfSURLs(new org.dcache.srm.v2_2.ArrayOfAnyURI(surlArray));
        SrmPutDoneResponse srmPutDoneResponse = 
        srmv2.srmPutDone(srmPutDoneRequest);
        TReturnStatus returnStatus = srmPutDoneResponse.getReturnStatus();
        if(returnStatus == null) {
            esay("srmPutDone return status is null");
            return;
        }
        say("srmPutDone status code="+returnStatus.getStatusCode());
    }
    
      public void getInitialRequest() throws SRMException {
         if(number_of_file_reqs == 0) {
            say("number_of_file_reqs is 0, nothing to do");
            return;
        }
        try {
           GlobusURL srmUrl = new GlobusURL(SURLs[0]);
            srmv2 = new SRMClientV2(srmUrl, 
            credential.getDelegatedCredential(),
            retry_timout,
            retry_num,
            storage,
            true, 
            true,
            "host",
	    "srm/managerv1");
            
            int len = SURLs.length;
            TPutFileRequest fileRequests[] = new TPutFileRequest[len];
            for(int i = 0; i < len; ++i) {
                long filesize = sizes[i];
                org.apache.axis.types.URI uri = 
                    new org.apache.axis.types.URI(SURLs[i]);
                fileRequests[i] = new TPutFileRequest();
                fileRequests[i].setTargetSURL(uri);
                pendingSurlsToIndex.put(SURLs[i],new Integer(i));
            }
            
            SrmPrepareToPutRequest srmPrepareToPutRequest = new SrmPrepareToPutRequest();
            
            
            if(retentionPolicy != null || accessLatency != null) {
                org.dcache.srm.v2_2.TRetentionPolicyInfo retentionPolicyInfo 
                        = new org.dcache.srm.v2_2.TRetentionPolicyInfo();
                retentionPolicyInfo.setRetentionPolicy(retentionPolicy);
                retentionPolicyInfo.setAccessLatency(accessLatency);
                srmPrepareToPutRequest.setTargetFileRetentionPolicyInfo(retentionPolicyInfo);
            }
            org.dcache.srm.v2_2.TTransferParameters transferParameters = 
                new org.dcache.srm.v2_2.TTransferParameters();
            
            transferParameters.setAccessPattern(org.dcache.srm.v2_2.TAccessPattern.TRANSFER_MODE);
            transferParameters.setConnectionType(org.dcache.srm.v2_2.TConnectionType.WAN);
            transferParameters.setArrayOfTransferProtocols(new org.dcache.srm.v2_2.ArrayOfString(protocols));
            srmPrepareToPutRequest.setTransferParameters(transferParameters);
            srmPrepareToPutRequest.setArrayOfFileRequests(
                new ArrayOfTPutFileRequest(fileRequests));            
            srmPrepareToPutRequest.setDesiredFileStorageType(storageType);
            srmPrepareToPutRequest.setDesiredTotalRequestTime(new Integer((int)requestLifetime));
            srmPrepareToPutRequest.setOverwriteOption(overwriteMode);
            srmPrepareToPutRequest.setTargetSpaceToken(targetSpaceToken);
            srmPrepareToPutResponse = srmv2.srmPrepareToPut(srmPrepareToPutRequest);
        }
        catch(Exception e) {
            throw new SRMException("failed to connect to "+SURLs[0],e);
        }

    }    
   
  
    public void run() {
       if(number_of_file_reqs == 0) {
            say("number_of_file_reqs is 0, nothing to do");
            return;
        }
        try {
            int len = SURLs.length;
            if(srmPrepareToPutResponse == null) {
                throw new IOException(" null srmPrepareToPutResponse");
            }
            TReturnStatus status = srmPrepareToPutResponse.getReturnStatus();
            if(status == null) {
                throw new IOException(" null return status");
            }
            TStatusCode statusCode = status.getStatusCode();
            if(statusCode == null) {
                throw new IOException(" null status code");
            }
            if(RequestStatusTool.isFailedRequestStatus(status)){
                throw new IOException("srmPrepareToPut submission failed, unexpected or failed status : "+
                    statusCode+" explanation="+status.getExplanation());
            }
            requestToken = srmPrepareToPutResponse.getRequestToken();
            say(" srm returned requestToken = "+requestToken+" one of remote surls = "+SURLs[0]);
            
            ArrayOfTPutRequestFileStatus arrayOfTPutRequestFileStatus =
                srmPrepareToPutResponse.getArrayOfFileStatuses();
            if(arrayOfTPutRequestFileStatus == null  ) {
                    throw new IOException("returned PutRequestFileStatuses is an empty array");
            }
             TPutRequestFileStatus[] putRequestFileStatuses = 
                arrayOfTPutRequestFileStatus.getStatusArray();
            if(putRequestFileStatuses == null  ) {
                    throw new IOException("returned PutRequestFileStatuses is an empty array");
            }
            if(putRequestFileStatuses.length != len) {
                    throw new IOException("incorrect number of GetRequestFileStatuses"+
                    "in RequestStatus expected "+len+" received "+ 
                    putRequestFileStatuses.length);
            }
            
            boolean haveCompletedFileRequests = false;



            while(!pendingSurlsToIndex.isEmpty()) {
                long estimatedWaitInSeconds = Integer.MAX_VALUE;
                for(int i = 0 ; i<len;++i) {
                    TPutRequestFileStatus putRequestFileStatus = putRequestFileStatuses[i];
                    org.apache.axis.types.URI surl = putRequestFileStatus.getSURL();
                    if(surl == null) {
                        esay("invalid putRequestFileStatus, surl is null");
                        continue;
                    }
                    String surl_string = surl.toString();
                    if(!pendingSurlsToIndex.containsKey(surl_string)) {
                        esay("invalid putRequestFileStatus, surl = "+surl_string+" not found");
                        continue;
                    }
                    TReturnStatus fileStatus = putRequestFileStatus.getStatus();
                    if(fileStatus == null) {
                        throw new IOException(" null file return status");
                    }
                    TStatusCode fileStatusCode = fileStatus.getStatusCode();
                    if(fileStatusCode == null) {
                        throw new IOException(" null file status code");
                    }
                    if(RequestStatusTool.isFailedFileRequestStatus(fileStatus)){
                        String error ="retreval of surl "+surl_string+" failed, status = "+fileStatusCode+
                        " explanation="+fileStatus.getExplanation();
                        esay(error);
                       int indx = ((Integer) pendingSurlsToIndex.remove(surl_string)).intValue();
                        notifyOfFailure(SURLs[indx], error, requestToken, null);
                        haveCompletedFileRequests = true;
                        continue;
                    }
                    if(putRequestFileStatus.getTransferURL() != null ) {
                            String turl = putRequestFileStatus.getTransferURL().toString();
                            int indx = ((Integer) pendingSurlsToIndex.remove(surl_string)).intValue();
                            // in case of put we do not need the size from the destination
                           notifyOfTURL(SURLs[i], turl,requestToken,null,null);
                        continue;
                    }
                    if(putRequestFileStatus.getEstimatedWaitTime() != null &&
                      putRequestFileStatus.getEstimatedWaitTime().intValue()< estimatedWaitInSeconds &&
                       putRequestFileStatus.getEstimatedWaitTime().intValue() >=1) {
                           estimatedWaitInSeconds = putRequestFileStatus.getEstimatedWaitTime().intValue();
                    }
                }

                if(pendingSurlsToIndex.isEmpty()) {
                    say("no more pending transfers, breaking the loop");
                    break;
                }
                // do not wait longer then 60 seconds
                if(estimatedWaitInSeconds > 60) {
                    estimatedWaitInSeconds = 60;
                }
                try {

                    say("sleeping "+estimatedWaitInSeconds+" seconds ...");
                    Thread.sleep(estimatedWaitInSeconds * 1000);
                }
                catch(InterruptedException ie) {
                }
                SrmStatusOfPutRequestRequest srmStatusOfPutRequestRequest = 
                new SrmStatusOfPutRequestRequest();
                srmStatusOfPutRequestRequest.setRequestToken(requestToken);
                // if we do not have completed file requests
                // we want to get status for all files
                // we do not need to specify any surls
                int expectedResponseLength;
                if(haveCompletedFileRequests){
                    String [] pendingSurlStrings = 
                        (String[])pendingSurlsToIndex.keySet().toArray(new String[0]);
                    expectedResponseLength= pendingSurlStrings.length;
                    org.apache.axis.types.URI surlArray[] = 
                            new org.apache.axis.types.URI[expectedResponseLength];

                    for(int i=0;i<expectedResponseLength;++i){
                        org.apache.axis.types.URI surl = 
                                new org.apache.axis.types.URI();
                        org.apache.axis.types.URI uri = 
                            new org.apache.axis.types.URI(pendingSurlStrings[i]);
                        surlArray[i]=uri;
                    }
                    srmStatusOfPutRequestRequest.setArrayOfTargetSURLs( 
                            new org.dcache.srm.v2_2.ArrayOfAnyURI(surlArray));
                }
                else {
                    expectedResponseLength = SURLs.length;
                    org.apache.axis.types.URI  surlArray[] = new  org.apache.axis.types.URI[expectedResponseLength];

                    for(int i=0;i<expectedResponseLength;++i){
                         org.apache.axis.types.URI surl = new  org.apache.axis.types.URI(SURLs[i]);
                        surlArray[i]=surl;
                    }
                    srmStatusOfPutRequestRequest.setArrayOfTargetSURLs(
                        new org.dcache.srm.v2_2.ArrayOfAnyURI(surlArray));
                }
                SrmStatusOfPutRequestResponse srmStatusOfPutRequestResponse =
                    srmv2.srmStatusOfPutRequest(srmStatusOfPutRequestRequest);
                if(srmStatusOfPutRequestResponse == null) {
                    throw new IOException(" null srmStatusOfPutRequestResponse");
                }
                arrayOfTPutRequestFileStatus =
                    srmStatusOfPutRequestResponse.getArrayOfFileStatuses();
                if(arrayOfTPutRequestFileStatus == null  ) {
                        throw new IOException("incorrect number of RequestFileStatuses");
                }
                putRequestFileStatuses = 
                    arrayOfTPutRequestFileStatus.getStatusArray();

                if(putRequestFileStatuses == null ||
                    putRequestFileStatuses.length !=  expectedResponseLength) {
                    esay( "incorrect number of RequestFileStatuses");
                    throw new IOException("incorrect number of RequestFileStatuses");
                }

                 status = srmStatusOfPutRequestResponse.getReturnStatus();
                if(status == null) {
                    throw new IOException(" null return status");
                }
                statusCode = status.getStatusCode();
                if(statusCode == null) {
                    throw new IOException(" null status code");
                }
                if(RequestStatusTool.isFailedRequestStatus(status)){
                    throw new IOException("srmPrepareToPut update failed, unexpected or failed status : "+
                        statusCode+" explanation="+status.getExplanation());
                }
            }
            
        }
        catch(Exception e) {
            this.esay(e);
            notifyOfFailure(e);
            return;
        }
            
    }
    
    
    public  static diskCacheV111.srm.RequestFileStatus getFileRequest(diskCacheV111.srm.RequestStatus rs,Integer nextID) {
        diskCacheV111.srm.RequestFileStatus[] frs = rs.fileStatuses;
        if(frs == null ) {
            return null;
        }
        
        for(int i= 0; i<frs.length;++i) {
            if(frs[i].fileId == nextID.intValue()) {
                return frs[i];
            }
        }
        return null;
    }
    
    

        public static void staticPutDone(RequestCredential credential, 
        String surl,
        String  requestTokenString,
        long retry_timeout,
        int retry_num,
        Logger logger) throws Exception
    {
        
        GlobusURL srmUrl = new GlobusURL(surl);
        SRMClientV2 srmv2 = new SRMClientV2(srmUrl, 
        credential.getDelegatedCredential(),
        retry_timeout,
        retry_num,
        logger,
        true, 
        true,
        "host",
	"srm/managerv1");
        String requestToken = requestTokenString;
         String[] surl_strings = new String[1];
        surl_strings[0] = surl;
        org.apache.axis.types.URI surlArray[] = new org.apache.axis.types.URI[1];
        surlArray[0]= new org.apache.axis.types.URI(surl);
        SrmPutDoneRequest srmPutDoneRequest = new SrmPutDoneRequest();
        srmPutDoneRequest.setRequestToken(requestToken);
        srmPutDoneRequest.setArrayOfSURLs(new org.dcache.srm.v2_2.ArrayOfAnyURI(surlArray));
        SrmPutDoneResponse srmPutDoneResponse = 
        srmv2.srmPutDone(srmPutDoneRequest);
        TReturnStatus returnStatus = srmPutDoneResponse.getReturnStatus();
        if(returnStatus == null) {
            logger.elog("srmPutDone return status is null");
            return;
        }
        logger.log("srmPutDone status code="+returnStatus.getStatusCode());

    }
    
    
}
