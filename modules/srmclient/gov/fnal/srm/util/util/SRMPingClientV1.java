// $Id: SRMPingClientV1.java,v 1.1 2006-10-03 18:41:45 timur Exp $
// $Log: not supported by cvs2svn $
// Revision 1.4  2006/01/26 04:44:19  timur
// improved error messages
//
// Revision 1.3  2006/01/24 21:14:47  timur
// changes related to the return code
//
// Revision 1.2  2006/01/11 16:16:56  neha
// Changes made by Neha. So in case of file transfer success/failure, System exits with correct code 0/1
//
// Revision 1.1  2005/12/13 23:07:52  timur
// modifying the names of classes for consistency
//
// Revision 1.22  2005/12/07 02:05:22  timur
// working towards srm v2 get client
//
// Revision 1.21  2005/06/08 22:34:55  timur
// fixed a bug, which led to recognition of some valid file ids as invalid
//
// Revision 1.20  2005/04/27 19:20:55  timur
// make sure client works even if report option is not specified
//
// Revision 1.19  2005/04/27 16:40:00  timur
// more work on report added gridftpcopy and adler32 binaries
//
// Revision 1.18  2005/04/26 02:06:08  timur
// added the ability to create a report file
//
// Revision 1.17  2005/03/11 21:18:36  timur
// making srm compatible with cern tools again
//
// Revision 1.16  2005/01/25 23:20:20  timur
// srmclient now uses srm libraries
//
// Revision 1.15  2005/01/11 18:19:29  timur
// fixed issues related to cern srm, make sure not to change file status for failed files
//
// Revision 1.14  2004/06/30 21:57:04  timur
//  added retries on each step, added the ability to use srmclient used by srm copy in the server, added srm-get-request-status
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
 * SRMGetClient.java
 *
 * Created on January 28, 2003, 2:54 PM
 */

package gov.fnal.srm.util;

import org.globus.util.GlobusURL;
import diskCacheV111.srm.ISRM;
import java.util.HashSet;
import java.util.HashMap;
import java.io.IOException;
import java.util.Iterator;
/**
 *
 * @author  timur
 */
public class SRMPingClientV1 extends SRMClient {
	GlobusURL srmurl;
	/** Creates a new instance of SRMGetClient */
	public SRMPingClientV1(Configuration configuration, GlobusURL srmurl) {
		super(configuration);
		this.srmurl = srmurl;
	}
    
    
	public void connect() throws Exception {
		connect(srmurl);
	}
    
    
	public void start() throws Exception {
		try {
			boolean result = srm.ping();

			dsay(" srm ping returned = "+result);
                    }catch(Exception ioe) {
                            if(configuration.isDebug()) {
                                    ioe.printStackTrace();
                            }
                            else {
                                    esay(ioe.toString());
                            }
                            throw ioe;
                    }
	}
}
