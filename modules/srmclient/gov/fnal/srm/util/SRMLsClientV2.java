// $Id$

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
 * SRMGetClient.java
 *
 * Created on January 28, 2003, 2:54 PM
 */

package gov.fnal.srm.util;

import org.globus.util.GlobusURL;
import diskCacheV111.srm.FileMetaData;
import diskCacheV111.srm.RequestFileStatus;
import diskCacheV111.srm.RequestStatus;
import org.dcache.srm.v2_2.ISRM;
import org.dcache.srm.client.SRMClientV2;
import org.ietf.jgss.GSSCredential;


import org.apache.axis.types.URI;
import org.dcache.srm.v2_2.*;
import org.dcache.srm.handler.SrmLs;

public class SRMLsClientV2 extends SRMClient {
   
   private org.ietf.jgss.GSSCredential cred = null;
   
   private GlobusURL surls[];
   private String surl_strings[];
   private ISRM srm;
   /** Creates a new instance of SRMGetClient */
   public SRMLsClientV2(Configuration configuration, GlobusURL[] surls, String[] surl_strings) {
      super(configuration);
      this.surls = surls;
      this.surl_strings=surl_strings;
      
      try {cred = getGssCredential();}
      catch (Exception e) {
         cred = null;
         System.err.println("Couldn't getGssCredential.");
      }
   }
   
   public void connect() throws Exception {
       GlobusURL srmUrl = surls[0];
       srm = new SRMClientV2(srmUrl, getGssCredential(),configuration.getRetry_timeout(),configuration.getRetry_num(),configuration.getLogger(),doDelegation, fullDelegation,gss_expected_name,configuration.getWebservice_path());
      // Maybe we'll need this back again...
      // connect(surls[0]);
   }
   
   public void start() throws Exception {
      try {
         if(cred.getRemainingLifetime() < 60) 
            throw new Exception(
             "Remaining lifetime of credential is less than a minute.");
      }
      catch(org.ietf.jgss.GSSException gsse) {throw gsse;}
      
     SrmLsRequest req = new SrmLsRequest();
     req.setAllLevelRecursive(Boolean.FALSE);
     req.setFullDetailedList(Boolean.valueOf(configuration.isLongLsFormat()));
     req.setNumOfLevels(new Integer(configuration.getRecursionDepth()));
     req.setOffset(new Integer(configuration.getLsOffset()));
     req.setCount(new Integer(configuration.getLsCount()));
     org.apache.axis.types.URI[] turlia = new org.apache.axis.types.URI[surls.length];
     for(int i =0; i<surls.length; ++i) {
         turlia[i] = new org.apache.axis.types.URI(surl_strings[i]);
     }
     req.setArrayOfSURLs(new ArrayOfAnyURI(turlia));
     SrmLsResponse resp = srm.srmLs(req);
     if(resp == null){
         throw new Exception ("srm ls response is null!");
     }
     
     StringBuffer sb = new StringBuffer();


     if(resp.getReturnStatus().getStatusCode() != TStatusCode.SRM_SUCCESS) {
         sb.append(
            "Return status:\n" +
            " - Status code:  " +
	    resp.getReturnStatus().getStatusCode().getValue() + '\n' +
            " - Explanation:  " + resp.getReturnStatus().getExplanation() );
	 throw new Exception(sb.toString());
     }
     
     if(resp.getDetails() == null){
	 System.out.println(sb.toString());
         throw new Exception("srm ls response path details array is null!");
     }
     else { 
	 if (resp.getDetails().getPathDetailArray()!=null) {  
	     TMetaDataPathDetail[] details = resp.getDetails().getPathDetailArray();
	     SrmLs.printResults(sb,details,0," ",configuration.isLongLsFormat());
	 }
     }
     System.out.println(sb.toString());
     
   }
   
   public org.apache.axis.types.URI getTSURLInfo(String surl) throws Exception {
            
     org.apache.axis.types.URI uri = new org.apache.axis.types.URI(surl);
     //turli.setStorageSystemInfo(tssi);

     return uri;
   }
}

