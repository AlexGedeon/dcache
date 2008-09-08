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
 * Configuration.java
 *
 * Created on April 23, 2003, 10:19 AM
 */

package org.dcache.srm.util;

import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMAuthorization;
import org.dcache.srm.SRM;
import org.dcache.srm.SRMUserPersistenceManager;

//import org.xml.sax.*;
//import org.xml.sax.helpers.DefaultHandler;
//import javax.xml.parsers.SAXParserFactory;
//import javax.xml.parsers.SAXParser;
//import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
//import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.Comment;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

// for writing xml
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
//import javax.xml.transform.TransformerException;
//import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

//import java.io.File;
import java.io.IOException;
import java.io.FileWriter;
import org.dcache.srm.qos.*;

//
// just testing cvs
//

/**
 *
 * @author  timur
 */
public class Configuration {
    public static final String ON_RESTART_FAIL_REQUEST="fail";
    public static final String ON_RESTART_RESTORE_REQUEST="restore";
    public static final String ON_RESTART_WAIT_FOR_UPDATE_REQUEST="wait-update";
    
    private boolean debug = false;
    
    private String urlcopy="../scripts/urlcopy.sh";
    
    private String gsiftpclinet = "globus-url-copy";
    
    private boolean gsissl = true;
    
    private String glue_mapfile=null;
    
    private String webservice_path = "srm/managerv1.wsdl";
    
    private String webservice_protocol="https";
    
    private int buffer_size=2048;
    private int tcp_buffer_size;
    private int parallel_streams=10;
    
    private String[] protocols = new String[]
    {"http","dcap","ftp","gsiftp"};
    
    private int port=8443;
    private String kpwdfile="../conf/dcache.kpwd";
    private boolean use_gplazmaAuthzCellFlag=false;
    private boolean delegateToGplazmaFlag=false;
    private boolean use_gplazmaAuthzModuleFlag=false;
    private String authzCacheLifetime="180";
    private String gplazmaPolicy="../conf/dcachesrm-gplazma.policy";
    private String srm_root="/";
    private String proxies_directory = "../proxies";
    private int timeout=60*60; //one hour
    private String timeout_script="../scripts/timeout.sh";
    private String srmhost=null;
    private AbstractStorageElement storage;
    private SRMAuthorization authorization;
    private SRM localSRM;
    
    // scheduler parameters
    
    private int getReqTQueueSize=1000;
    private int getThreadPoolSize=30;
    private int getMaxWaitingRequests=1000;
    private int getReadyQueueSize=1000;
    private int getMaxReadyJobs=60;
    private int getMaxNumOfRetries=10;
    private long getRetryTimeout=60000;
    private int getMaxRunningBySameOwner=10;
    private String getRequestRestorePolicy=ON_RESTART_WAIT_FOR_UPDATE_REQUEST;
    
    private int putReqTQueueSize=1000;
    private int putThreadPoolSize=30;
    private int putMaxWaitingRequests=1000;
    private int putReadyQueueSize=1000;
    private int putMaxReadyJobs=60;
    private int putMaxNumOfRetries=10;
    private long putRetryTimeout=60000;
    private int putMaxRunningBySameOwner=10;
    private String putRequestRestorePolicy=ON_RESTART_WAIT_FOR_UPDATE_REQUEST;
    
    private int copyReqTQueueSize=1000;
    private int copyThreadPoolSize=30;
    private int copyMaxWaitingRequests=1000;
    private int copyMaxNumOfRetries=10;
    private long copyRetryTimeout=60000;
    private int copyMaxRunningBySameOwner=10;
    private String copyRequestRestorePolicy=ON_RESTART_WAIT_FOR_UPDATE_REQUEST;
    
    
    private long getLifetime = 24*60*60*1000;
    private long putLifetime = 24*60*60*1000;
    private long copyLifetime = 24*60*60*1000;
    private long defaultSpaceLifetime = 24*60*60*1000;
    
    private String x509ServiceKey="/etc/grid-security/hostkey.pem";
    private String x509ServiceCert="/etc/grid-security/hostcert.pem";
    private String x509TrastedCACerts="/etc/grid-security/certificates";
    private boolean useUrlcopyScript=false;
    private boolean useDcapForSrmCopy=false;
    private boolean useGsiftpForSrmCopy=true;
    private boolean useHttpForSrmCopy=true;
    private boolean useFtpForSrmCopy=true;
    private boolean recursiveDirectoryCreation=false;
    private boolean advisoryDelete=false;
    private boolean moveEntry=false;
    private boolean createDirectory=false;
    private boolean removeDirectory=false;
    private boolean removeFile=false;
    private boolean saveMemory=false;
    private String jdbcUrl;
    private String jdbcClass;
    private String jdbcUser;
    private String jdbcPass;
    private String jdbcPwdfile;
    private String nextRequestIdStorageTable = "srmnextrequestid";
    private boolean connect_to_wsdl;
    private boolean reserve_space_implicitely;
    private boolean space_reservation_strict;
    private long storage_info_update_period=5*60*1000;
    private long vacuum_period_sec=60*60;
    private boolean vacuum = true;
    private boolean start_server=true;
    private String qosPluginClass = null; 
    private String qosConfigFile = null;
    private String getPriorityPolicyPlugin="DefaultJobAppraiser";
    private String putPriorityPolicyPlugin="DefaultJobAppraiser";
    private String copyPriorityPolicyPlugin="DefaultJobAppraiser";
    private int numDaysHistory = 30;
    private long oldRequestRemovePeriodSecs = 3600;
    private Integer maxQueuedJdbcTasksNum ; //null by default
    private Integer jdbcExecutionThreadNum;//null by default
    private String credentialsDirectory="/opt/d-cache/credentials";
    private boolean jdbcMonitoringEnabled=false;
    private boolean jdbcMonitoringDebugLevel = false;
    private boolean cleanPendingRequestsOnRestart = false;
    private boolean overwrite = false;
    private boolean overwrite_by_default = false;
	private int sizeOfSingleRemoveBatch = 100;
    private SRMUserPersistenceManager srmUserPersistenceManager;
    
    /** Creates a new instance of Configuration */
    public Configuration() {
        
    }
    
    public Configuration(String configuration_file) {
        try {
            read(configuration_file);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
    
    
    public void read(String file) throws Exception {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        Node root =document.getFirstChild();
        for(;root != null && !"srm-configuration".equals(root.getNodeName());
        root = document.getNextSibling()) {
        }
        if(root == null) {
            System.err.println(" error, root element \"srm-configuration\" is not found");
            throw new IOException();
        }
        
        
        if(root != null && root.getNodeName().equals("srm-configuration")) {
            
            Node node = root.getFirstChild();
            for(;node != null; node = node.getNextSibling()) {
                if(node.getNodeType()!= Node.ELEMENT_NODE) {
                    continue;
                }
                
                Node child = node.getFirstChild();
                for(;child != null; child = node.getNextSibling()) {
                    if(child.getNodeType() == Node.TEXT_NODE) {
                        break;
                    }
                }
                if(child == null) {
                    continue;
                }
                Text t  = (Text)child;
                String node_name = node.getNodeName();
                String text_value = t.getData().trim();
                if(text_value != null && text_value.equalsIgnoreCase("null")) {
                    text_value = null;
                }
                set(node_name,text_value);
            }
        }
        if(srmhost == null) {
            //System.out.println("srmhost = null, detecting");
            try {
                srmhost = java.net.InetAddress.getLocalHost().getCanonicalHostName();
            } catch(IOException ioe) {
                srmhost = "localhost";
            }
        }
        
    }
    
    private static void put(Document document,Node root,String elem_name,String value, String comment_str) {
        //System.out.println("put elem_name="+elem_name+" value="+value+" comment="+comment_str);
        Text t = document.createTextNode("\n\n\t");
        root.appendChild(t);
        Comment comment = document.createComment(comment_str);
        root.appendChild(comment);
        t = document.createTextNode("\n\t");
        root.appendChild(t);
        Element element = document.createElement(elem_name);
        t = document.createTextNode(" "+value+" ");
        element.appendChild(t);
        root.appendChild(element);
    }
    
    public void write(String file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();
        //System.out.println("document is instanceof "+document.getClass().getName());
        Element root = document.createElement("srm-configuration");
        
        put(document,root,"debug",new Boolean(debug).toString()," true or false");
        put(document,root,"urlcopy",urlcopy," path to the urlcopy script ");
        put(document,root,"gsiftpclient",gsiftpclinet," \"globus-url-copy\" or \"kftp\"");
        put(document,root,"gsissl",new Boolean(gsissl).toString(),"true if use http over gsi over ssl for SOAP invocations \n\t"+
                "or false to use plain http (no authentication or encryption)");
        put(document,root,"mapfile",glue_mapfile," path to the \"glue\" mapfile");
        put(document,root,"webservice_path",webservice_path,
                " path to the  in the srm webservices server,\n\t"+
                "srm/managerv1.wsdl\" in case of srm in dcache");
        put(document,root,"webservice_protocol",webservice_protocol," this could be http or https");
        put(document,root,"buffer_size",Integer.toString(buffer_size),
                "nonnegative integer, 2048 by default");
        put(document,root,"tcp_buffer_size",Integer.toString(tcp_buffer_size),
                "integer, 0 by default (which means do not set tcp_buffer_size at all)");
        put(document,root,"port",Integer.toString(port),
                "port on which to publish the srm service");
        put(document,root,"kpwdfile", kpwdfile,
                "kpwdfile, a dcache authorization database ");
        put(document,root,"use_gplazmaAuthzCellFlag",new Boolean(use_gplazmaAuthzCellFlag).toString()," true or false");
        put(document,root,"delegateToGplazmaFlag",new Boolean(delegateToGplazmaFlag).toString()," true or false");
        put(document,root,"use_gplazmaAuthzModuleFlag",new Boolean(use_gplazmaAuthzModuleFlag).toString()," true or false");
        put(document,root,"srmAuthzCacheLifetime", authzCacheLifetime,
                "time in seconds to cache authorizations ");
        put(document,root,"gplazmaPolicy", gplazmaPolicy,
                "gplazmaPolicy, a dcache-srm pluggable authorization management module ");
        put(document,root,"srm_root", srm_root,
                "root of the srm within the file system, nothing outside the root is accessible to the users");
        put(document,root,"proxies_directory", proxies_directory,
                "directory where deligated credentials will be temporarily stored, if external client is to be utilized");
        put(document,root,"timeout",Integer.toString(timeout),
                "timeout in seconds, how long to wait for the completeon of the transfer via external client, should the external client be used for the MSS to MSS transfers");
        put(document,root,"timeout_script",timeout_script ,
                "location of the timeout script");
        
        put(document,root,"srmhost",srmhost ,
                "hostname of the server, will be detected automatically if it is null");
        
        put(document,root,"getReqTQueueSize",Integer.toString(getReqTQueueSize),
                "getReqTQueueSize");
        put(document,root,"getThreadPoolSize",Integer.toString(getThreadPoolSize),
                "getThreadPoolSize");
        put(document,root,"getMaxWaitingRequests",Integer.toString(getMaxWaitingRequests),
                "getMaxWaitingRequests");
        put(document,root,"getReadyQueueSize",Integer.toString(getReadyQueueSize),
                "getReadyQueueSize");
        put(document,root,"getMaxReadyJobs",Integer.toString(getMaxReadyJobs),
                "getMaxReadyJobs");
        put(document,root,"getMaxNumOfRetries",Integer.toString(getMaxNumOfRetries),
                "Maximum Number Of Retries for get file request");
        put(document,root,"getRetryTimeout",Long.toString(getRetryTimeout),
                "get request Retry Timeout in milliseconds");
        
        put(document,root,"getMaxRunningBySameOwner",Integer.toString(getMaxRunningBySameOwner),
                "getMaxRunningBySameOwner");
        put(document,root,"getRequestRestorePolicy",getRequestRestorePolicy ,
                "getRequestRestorePolicy determines what happens with the pending/runnig get request\n"+
                "       request, when srm is restarted, the possble values are:\n"+
                "       \""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" and \""+
                ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\\n"+
                "       \""+ON_RESTART_FAIL_REQUEST+"\"  will lead to the failure of the restored get requests\n"+
                "       \""+ON_RESTART_RESTORE_REQUEST+"\" - will cause the execution of the restored get requests\n"+
                "       \""+ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" - will cause the system not to execute the restored requests\n"+
                "                         unless srmcp client attempts to update the status of the request");
        
        
        put(document,root,"putReqTQueueSize",Integer.toString(putReqTQueueSize),
                "putReqTQueueSize");
        put(document,root,"putThreadPoolSize",Integer.toString(putThreadPoolSize),
                "putThreadPoolSize");
        put(document,root,"putMaxWaitingRequests",Integer.toString(putMaxWaitingRequests),
                "putMaxWaitingRequests");
        put(document,root,"putReadyQueueSize",Integer.toString(putReadyQueueSize),
                "putReadyQueueSize");
        put(document,root,"putMaxReadyJobs",Integer.toString(putMaxReadyJobs),
                "putMaxReadyJobs");
        put(document,root,"putMaxNumOfRetries",Integer.toString(putMaxNumOfRetries),
                "Maximum Number Of Retries for put file request");
        put(document,root,"putRetryTimeout",Long.toString(putRetryTimeout),
                "put request Retry Timeout in milliseconds");
        
        put(document,root,"putMaxRunningBySameOwner",Integer.toString(putMaxRunningBySameOwner),
                "putMaxRunningBySameOwner");
        put(document,root,"putRequestRestorePolicy",putRequestRestorePolicy ,
                "putRequestRestorePolicy determines what happens with the pending/runnig put request\n"+
                "       request, when srm is restarted, the possble values are:\n"+
                "       \""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" and \""+
                ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\\n"+
                "       \""+ON_RESTART_FAIL_REQUEST+"\"  will lead to the failure of the restored put requests\n"+
                "       \""+ON_RESTART_RESTORE_REQUEST+"\" - will cause the execution of the restored put requests\n"+
                "       \""+ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" - will cause the system not to execute the restored requests\n"+
                "                         unless srmcp client attempts to update the status of the request");
        
        
        put(document,root,"copyReqTQueueSize",Integer.toString(copyReqTQueueSize),
                "copyReqTQueueSize");
        put(document,root,"copyThreadPoolSize",Integer.toString(copyThreadPoolSize),
                "copyThreadPoolSize");
        put(document,root,"copyMaxWaitingRequests",Integer.toString(copyMaxWaitingRequests),
                "copyMaxWaitingRequests");
        put(document,root,"copyMaxNumOfRetries",Integer.toString(copyMaxNumOfRetries),
                "Maximum Number Of Retries for copy file request");
        put(document,root,"copyRetryTimeout",Long.toString(copyRetryTimeout),
                "copy request Retry Timeout in milliseconds");
        
        put(document,root,"copyMaxRunningBySameOwner",Integer.toString(copyMaxRunningBySameOwner),
                "copyMaxRunningBySameOwner");
        put(document,root,"copyRequestRestorePolicy",copyRequestRestorePolicy ,
                "copyRequestRestorePolicy determines what happens with the pending/runnig copy request\n"+
                "       request, when srm is restarted, the possble values are:\n"+
                "       \""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" and \""+
                ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\\n"+
                "       \""+ON_RESTART_FAIL_REQUEST+"\"  will lead to the failure of the restored copy requests\n"+
                "       \""+ON_RESTART_RESTORE_REQUEST+"\" - will cause the execution of the restored copy requests\n"+
                "       \""+ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" - will cause the system not to execute the restored requests\n"+
                "                         unless srmcp client attempts to update the status of the request");
        
        
        put(document,root,"getLifetime",Long.toString(getLifetime),
                "getLifetime");
        put(document,root,"putLifetime",Long.toString(putLifetime),
                "putLifetime");
        put(document,root,"copyLifetime",Long.toString(copyLifetime),
                "copyLifetime");
        put(document,root,"defaultSpaceLifetime",Long.toString(defaultSpaceLifetime),
                "defaultSpaceLifetime");
        put(document,root,"x509ServiceKey",x509ServiceKey ,
                "x509ServiceKey");
        put(document,root,"x509ServiceCert",x509ServiceCert ,
                "x509ServiceCert");
        put(document,root,"x509TrastedCACerts",x509TrastedCACerts ,
                "x509TrastedCACerts");
        
        put(document,root,"useUrlcopyScript",new Boolean(useUrlcopyScript).toString(),
                "useUrlcopyScript");
        put(document,root,"useDcapForSrmCopy",new Boolean(useDcapForSrmCopy).toString(),
                "useDcapForSrmCopy");
        put(document,root,"useGsiftpForSrmCopy",new Boolean(useGsiftpForSrmCopy).toString(),
                "useGsiftpForSrmCopy");
        put(document,root,"useHttpForSrmCopy",new Boolean(useHttpForSrmCopy).toString(),
                "useHttpForSrmCopy");
        put(document,root,"useFtpForSrmCopy",new Boolean(useFtpForSrmCopy).toString(),
                "useFtpForSrmCopy");
        put(document,root,"recursiveDirectoryCreation",new Boolean(recursiveDirectoryCreation).toString(),
                "recursiveDirectoryCreation");
        put(document,root,"advisoryDelete",new Boolean(advisoryDelete).toString(),
                "advisoryDelete");
        put(document,root,"removeFile",new Boolean(removeFile).toString(),
                "removeFile");
        put(document,root,"removeDirectory",new Boolean(removeDirectory).toString(),
                "removeDirectory");
        put(document,root,"createDirectory",new Boolean(createDirectory).toString(),
                "createDirectory");
        put(document,root,"createDirectory",new Boolean(moveEntry).toString(),
                "moveEntry");
        put(document,root,"saveMemory",new Boolean(saveMemory).toString(),
                "saveMemory");
        put(document,root,"jdbcUrl",jdbcUrl ,
                "jdbcUrl");
        put(document,root,"jdbcClass",jdbcClass ,
                "jdbcClass");
        put(document,root,"jdbcUser", jdbcUser,
                "jdbcUser");
        put(document,root,"jdbcPass", jdbcPass,
                "jdbcPass");
        put(document,root,"jdbcPwdfile", jdbcPwdfile,
                "jdbcPwdfile");
        put(document,root,"nextRequestIdStorageTable", nextRequestIdStorageTable,
                "nextRequestIdStorageTable");
        put(document,root,"connect_to_wsdl",new Boolean(connect_to_wsdl).toString()," true or false");
        
        put(document,root,"reserve_space_implicitely",new Boolean(reserve_space_implicitely).toString()," true or false");
        put(document,root,
                "space_reservation_strict",
                new Boolean(space_reservation_strict).toString()," true or false");
        put(document,root,
                "storage_info_update_period",
                Long.toString(storage_info_update_period),
                "storage_info_update_period in milliseconds");
        put(document,root,
                "vacuum",
                new Boolean(space_reservation_strict).toString(),"enables or disables postgres vacuuming, true or false");
        put(document,root,
                "vacuum_period_sec",
                Long.toString(vacuum_period_sec),
                "period between invocation of vacuum operations in seconds");
        
        Text t = document.createTextNode("\n");
        root.appendChild(t);
        document.appendChild(root);
        
        
        
        //System.out.println("using Transformer!!!");
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(new FileWriter(file));
        transformer.transform(source, result);
    }
    
    
    private void set(String name, String value) {
        //if(value == null)
        //{
        //System.out.println("value is null");
        //}
        //System.out.println("setting "+name+" to "+value);
        name=name.trim();
        value=value==null?null:value.trim();
        if(name.equals("debug")) {
            debug = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("gsissl")) {
            gsissl = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("gsiftpclient")) {
            gsiftpclinet = value;
        } else if(name.equals("mapfile")) {
            glue_mapfile = value;
        } else if(name.equals("webservice_path")) {
            webservice_path = value;
        } else if(name.equals("webservice_protocol")) {
            webservice_protocol = value;
        } else if(name.equals("urlcopy")) {
            urlcopy = value;
        } else if(name.equals("buffer_size")) {
            buffer_size = Integer.parseInt(value);
        } else if(name.equals("tcp_buffer_size")) {
            tcp_buffer_size = Integer.parseInt(value);
        } else if(name.equals("port")) {
            port = Integer.parseInt(value);
        } else if(name.equals("kpwdfile")) {
            kpwdfile = value;
        } else if(name.equals("use_gplazmaAuthzCellFlag")) {
            use_gplazmaAuthzCellFlag = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("delegateToGplazmaFlag")) {
            delegateToGplazmaFlag = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("use_gplazmaAuthzModuleFlag")) {
            use_gplazmaAuthzModuleFlag = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("srmAuthzCacheLifetime")) {
            authzCacheLifetime = value;
        } else if(name.equals("gplazmaPolicy")) {
            gplazmaPolicy = value;
        } else if(name.equals("srm_root")) {
            srm_root = value;
        } else if(name.equals("proxies_directory")) {
            proxies_directory = value;
        } else if(name.equals("timeout")) {
            timeout= Integer.parseInt(value);
        } else if(name.equals("timeout_script")) {
            timeout_script= value;
        } else if(name.equals("srmhost")) {
            srmhost = value;
        } else if(name.equals("getReqTQueueSize")) {
            getReqTQueueSize= Integer.parseInt(value);
        } else if(name.equals("getThreadPoolSize")) {
            getThreadPoolSize = Integer.parseInt(value);
        } else if(name.equals("getMaxWaitingRequests")) {
            getMaxWaitingRequests= Integer.parseInt(value);
        } else if(name.equals("getReadyQueueSize")) {
            getReadyQueueSize= Integer.parseInt(value);
        } else if(name.equals("getMaxReadyJobs")) {
            getMaxReadyJobs= Integer.parseInt(value);
        } else if(name.equals("getMaxNumOfRetries")) {
            getMaxNumOfRetries= Integer.parseInt(value);
        } else if(name.equals("getRetryTimeout")) {
            getRetryTimeout= Long.parseLong(value);
        } else if(name.equals("getMaxRunningBySameOwner")) {
            getMaxRunningBySameOwner= Integer.parseInt(value);
        } else if(name.equals("getRequestRestorePolicy")) {
            if(value.equalsIgnoreCase(ON_RESTART_FAIL_REQUEST) ||
                    value.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ||
                    value.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ) {
                getRequestRestorePolicy = value;
            } else {
                throw new IllegalArgumentException("getRequestRestorePolicy value must be one of "+
                        "\""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" or \""+
                        ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" "+
                        " but received value="+value);
            }
        } else if(name.equals("putReqTQueueSize")) {
            putReqTQueueSize= Integer.parseInt(value);
        } else if(name.equals("putThreadPoolSize")) {
            putThreadPoolSize= Integer.parseInt(value);
        } else if(name.equals("putMaxWaitingRequests")) {
            putMaxWaitingRequests= Integer.parseInt(value);
        } else if(name.equals("putReadyQueueSize")) {
            putReadyQueueSize= Integer.parseInt(value);
        } else if(name.equals("putMaxReadyJobs")) {
            putMaxReadyJobs= Integer.parseInt(value);
        } else if(name.equals("putMaxNumOfRetries")) {
            putMaxNumOfRetries = Integer.parseInt(value);
        } else if(name.equals("putRetryTimeout")) {
            putRetryTimeout= Long.parseLong(value);
        } else if(name.equals("putMaxRunningBySameOwner")) {
            putMaxRunningBySameOwner= Integer.parseInt(value);
        } else if(name.equals("putRequestRestorePolicy")) {
            if(value.equalsIgnoreCase(ON_RESTART_FAIL_REQUEST) ||
                    value.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ||
                    value.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ) {
                putRequestRestorePolicy = value;
            } else {
                throw new IllegalArgumentException("putRequestRestorePolicy value must be one of "+
                        "\""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" or \""+
                        ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" "+
                        " but received value="+value);
            }
        } else if(name.equals("copyReqTQueueSize")) {
            copyReqTQueueSize= Integer.parseInt(value);
        } else if(name.equals("copyThreadPoolSize")) {
            copyThreadPoolSize= Integer.parseInt(value);
        } else if(name.equals("copyMaxWaitingRequests")) {
            copyMaxWaitingRequests= Integer.parseInt(value);
        } else if(name.equals("copyMaxNumOfRetries")) {
            copyMaxNumOfRetries= Integer.parseInt(value);
        } else if(name.equals("copyRetryTimeout")) {
            copyRetryTimeout=Long.parseLong(value);
        } else if(name.equals("copyMaxRunningBySameOwner")) {
            copyMaxRunningBySameOwner= Integer.parseInt(value);
        } else if(name.equals("copyRequestRestorePolicy")) {
            if(value.equalsIgnoreCase(ON_RESTART_FAIL_REQUEST) ||
                    value.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ||
                    value.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ) {
                copyRequestRestorePolicy = value;
            } else {
                throw new IllegalArgumentException("copyRequestRestorePolicy value must be one of "+
                        "\""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" or \""+
                        ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" "+
                        " but received value="+value);
            }
        } else if(name.equals("getLifetime")) {
            getLifetime=Long.parseLong(value);
        } else if(name.equals("putLifetime")) {
            putLifetime=Long.parseLong(value);
        } else if(name.equals("copyLifetime")) {
            copyLifetime=Long.parseLong(value);
        } else if(name.equals("defaultSpaceLifetime")) {
            defaultSpaceLifetime=Long.parseLong(value);
        } else if(name.equals("x509ServiceKey")) {
            x509ServiceKey= value;
        } else if(name.equals("x509ServiceCert")) {
            x509ServiceCert= value;
        } else if(name.equals("x509TrastedCACerts")) {
            x509TrastedCACerts= value;
        } else if(name.equals("useUrlcopyScript")) {
            useUrlcopyScript= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("useDcapForSrmCopy")) {
            useDcapForSrmCopy= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("useGsiftpForSrmCopy")) {
            useGsiftpForSrmCopy= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("useHttpForSrmCopy")) {
            useHttpForSrmCopy= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("useFtpForSrmCopy")) {
            useFtpForSrmCopy= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("recursiveDirectoryCreation")) {
            recursiveDirectoryCreation= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("advisoryDelete")) {
            advisoryDelete= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("removeFile")) {
            removeFile= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("removeDirectory")) {
            removeDirectory= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("createDirectory")) {
            createDirectory= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("moveEntry")) {
            moveEntry= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("saveMemory")) {
            saveMemory= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("jdbcUrl")) {
            jdbcUrl= value;
        } else if(name.equals("jdbcClass")) {
            jdbcClass= value;
        } else if(name.equals("jdbcUser")) {
            jdbcUser= value;
        } else if(name.equals("jdbcPass")) {
            jdbcPass= value;
        } else if(name.equals("jdbcPwdfile")) {
            jdbcPwdfile= value;
        } else if(name.equals("nextRequestIdStorageTable")) {
            nextRequestIdStorageTable= value;
        } else if(name.equals("connect_to_wsdl")) {
            connect_to_wsdl = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("reserve_space_implicitely")) {
            reserve_space_implicitely = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("space_reservation_strict")) {
            space_reservation_strict = Boolean.valueOf(value).booleanValue();
        } else if(name.equals("storage_info_update_period")) {
            storage_info_update_period = Long.parseLong(value);
        } else if(name.equals("vacuum")) {
            vacuum= Boolean.valueOf(value).booleanValue();
        } else if(name.equals("vacuum_period_sec")) {
            vacuum_period_sec = Long.parseLong(value);
        } else if(name.equals("qosPluginClass")) { 
        	qosPluginClass = value;
        } else if(name.equals("qosConfigFile")) { 
        	qosConfigFile = value;
        }  
        
        
    }
    
    
    
    /** Getter for property urlcopy.
     * @return Value of property urlcopy.
     */
    public String getUrlcopy() {
        return urlcopy;
    }
    
    /** Setter for property urlcopy.
     * @param urlcopy New value of property urlcopy.
     */
    public void setUrlcopy(String urlcopy) {
        this.urlcopy = urlcopy;
    }
    
    /** Getter for property gsiftpclinet.
     * @return Value of property gsiftpclinet.
     */
    public String getGsiftpclinet() {
        return gsiftpclinet;
    }
    
    /** Setter for property gsiftpclinet.
     * @param gsiftpclinet New value of property gsiftpclinet.
     */
    public void setGsiftpclinet(String gsiftpclinet) {
        this.gsiftpclinet = gsiftpclinet;
    }
    
    /** Getter for property gsissl.
     * @return Value of property gsissl.
     */
    public boolean isGsissl() {
        return gsissl;
    }
    
    /** Setter for property gsissl.
     * @param gsissl New value of property gsissl.
     */
    public void setGsissl(boolean gsissl) {
        this.gsissl = gsissl;
    }
    
    /** Getter for property glue_mapfile.
     * @return Value of property glue_mapfile.
     */
    public String getGlue_mapfile() {
        return glue_mapfile;
    }
    
    /** Setter for property glue_mapfile.
     * @param glue_mapfile New value of property glue_mapfile.
     */
    public void setGlue_mapfile(String glue_mapfile) {
        this.glue_mapfile = glue_mapfile;
    }
    
    /** Getter for property webservice_path.
     * @return Value of property webservice_path.
     */
    public String getWebservice_path() {
        return webservice_path;
    }
    
    /** Setter for property webservice_path.
     * @param webservice_path New value of property webservice_path.
     */
    public void setWebservice_path(String webservice_path) {
        this.webservice_path = webservice_path;
    }
    
    
    /** Getter for property debug.
     * @return Value of property debug.
     */
    public boolean isDebug() {
        return debug;
    }
    
    /** Setter for property debug.
     * @param debug New value of property debug.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }
    
    
    /** Getter for property webservice_protocol.
     * @return Value of property webservice_protocol.
     */
    public java.lang.String getwebservice_protocol() {
        return webservice_protocol;
    }
    
    /** Setter for property webservice_protocol.
     * @param webservice_protocol New value of property webservice_protocol.
     */
    public void setwebservice_protocol(java.lang.String webservice_protocol) {
        this.webservice_protocol = webservice_protocol;
    }
    
    /** Getter for property buffer_size.
     * @return Value of property buffer_size.
     */
    public int getBuffer_size() {
        return buffer_size;
    }
    
    /** Setter for property buffer_size.
     * @param buffer_size New value of property buffer_size.
     */
    public void setBuffer_size(int buffer_size) {
        this.buffer_size = buffer_size;
    }
    
    /** Getter for property tcp_buffer_size.
     * @return Value of property tcp_buffer_size.
     */
    public int getTcp_buffer_size() {
        return tcp_buffer_size;
    }
    
    /** Setter for property tcp_buffer_size.
     * @param tcp_buffer_size New value of property tcp_buffer_size.
     */
    public void setTcp_buffer_size(int tcp_buffer_size) {
        this.tcp_buffer_size = tcp_buffer_size;
    }
    
    /** Getter for property webservice_protocol.
     * @return Value of property webservice_protocol.
     */
    public java.lang.String getWebservice_protocol() {
        return webservice_protocol;
    }
    
    /** Setter for property webservice_protocol.
     * @param webservice_protocol New value of property webservice_protocol.
     */
    public void setWebservice_protocol(java.lang.String webservice_protocol) {
        this.webservice_protocol = webservice_protocol;
    }
    
    /** Getter for property protocols.
     * @return Value of property protocols.
     */
    public java.lang.String[] getProtocols() {
        return this.protocols;
    }
    
    /** Setter for property protocols.
     * @param protocols New value of property protocols.
     */
    public void setProtocols(java.lang.String[] protocols) {
        this.protocols = protocols;
    }
    
    /** Getter for property port.
     * @return Value of property port.
     */
    public int getPort() {
        return port;
    }
    
    /** Setter for property port.
     * @param port New value of property port.
     */
    public void setPort(int port) {
        this.port = port;
    }
    
    /** Getter for property kpwdFile.
     * @return Value of property kpwdFile.
     */
    public java.lang.String getKpwdfile() {
        return kpwdfile;
    }
    
    /** Setter for property kpwdFile.
     * @param kpwdfile New value of property kpwdFile.
     */
    public void setKpwdfile(String kpwdfile) {
        this.kpwdfile = kpwdfile;
    }
    
    /**
     * Getter for property use_gplazmaAuthzCellFlag.
     * @return Value of property use_gplazmaAuthzCellFlag.
     */
    public boolean getUseGplazmaAuthzCellFlag() {
        return use_gplazmaAuthzCellFlag;
    }
    
    /**
     * Setter for property use_gplazmaAuthzCellFlag.
     * @param use_gplazmaAuthzCellFlag New value of property use_gplazmaAuthzCellFlag.
     */
    public void setUseGplazmaAuthzCellFlag(boolean use_gplazmaAuthzCellFlag) {
        this.use_gplazmaAuthzCellFlag = use_gplazmaAuthzCellFlag;
    }
    
    /**
     * Getter for property delegateToGplazmaFlag.
     * @return Value of property delegateToGplazmaFlag.
     */
    public boolean getDelegateToGplazmaFlag() {
        return delegateToGplazmaFlag;
    }
    
    /**
     * Setter for property delegateToGplazmaFlag.
     * @param delegateToGplazmaFlag New value of property delegateToGplazmaFlag.
     */
    public void setDelegateToGplazmaFlag(boolean delegateToGplazmaFlag) {
        this.delegateToGplazmaFlag = delegateToGplazmaFlag;
    }
    
    /**
     * Getter for property use_gplazmaAuthzModuleFlag.
     * @return Value of property use_gplazmaAuthzModuleFlag.
     */
    public boolean getUseGplazmaAuthzModuleFlag() {
        return use_gplazmaAuthzModuleFlag;
    }

    /**
     * Setter for property use_gplazmaAuthzModuleFlag.
     * @param use_gplazmaAuthzModuleFlag New value of property use_gplazmaAuthzModuleFlag.
     */
    public void setUseGplazmaAuthzModuleFlag(boolean use_gplazmaAuthzModuleFlag) {
        this.use_gplazmaAuthzModuleFlag = use_gplazmaAuthzModuleFlag;
    }
    

    /**
     * Getter for property authzCacheLifetime.
     * @return Value of property authzCacheLifetime.
     */
    public String getAuthzCacheLifetime() {
        return authzCacheLifetime;
    }

    /** Setter for property authzCacheLifetime.
     * @param authzCacheLifetime New value of property authzCacheLifetime.
     */
    public void setAuthzCacheLifetime(java.lang.String authzCacheLifetime) {
        this.authzCacheLifetime = authzCacheLifetime;
    }

    /** Getter for property gplazmaPolicy.
     * @return Value of property gplazmaPolicy.
     */
    public java.lang.String getGplazmaPolicy() {
        return gplazmaPolicy;
    }
    
    /** Setter for property gplazmaPolicy.
     * @param gplazmaPolicy New value of property gplazmaPolicy.
     */
    public void setGplazmaPolicy(java.lang.String gplazmaPolicy) {
        this.gplazmaPolicy = gplazmaPolicy;
    }
    
    /** Setter for property srm_url_path_prefix.
     * @param srm_root New value of property srm_root.
     */
    public void setSrm_root(String srm_root) {
        this.srm_root = srm_root;
    }
    
    /** Getter for property proxies_directory.
     * @return Value of property proxies_directory.
     */
    public java.lang.String getProxies_directory() {
        return proxies_directory;
    }
    
    /** Setter for property proxies_directory.
     * @param proxies_directory New value of property proxies_directory.
     */
    public void setProxies_directory(java.lang.String proxies_directory) {
        this.proxies_directory = proxies_directory;
    }
    
    /** Getter for property timeout.
     * @return Value of property timeout.
     */
    public int getTimeout() {
        return timeout;
    }
    
    /** Setter for property timeout.
     * @param timeout New value of property timeout.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
    
    /** Getter for property timeout_script.
     * @return Value of property timeout_script.
     */
    public java.lang.String getTimeout_script() {
        return timeout_script;
    }
    
    /** Setter for property timeout_script.
     * @param timeout_script New value of property timeout_script.
     */
    public void setTimeout_script(java.lang.String timeout_script) {
        this.timeout_script = timeout_script;
    }
    
    /** Getter for property srmhost.
     * @return Value of property srmhost.
     */
    public java.lang.String getSrmhost() {
        return srmhost;
    }
    
    /** Setter for property srmhost.
     * @param srmhost New value of property srmhost.
     */
    public void setSrmhost(java.lang.String srmhost) {
        this.srmhost = srmhost;
    }
    
    /** Getter for property storage.
     * @return Value of property storage.
     */
    public org.dcache.srm.AbstractStorageElement getStorage() {
        return storage;
    }
    
    /** Setter for property storage.
     * @param storage New value of property storage.
     */
    public void setStorage(org.dcache.srm.AbstractStorageElement storage) {
        this.storage = storage;
    }
    
    /** Getter for property authorization.
     * @return Value of property authorization.
     */
    public SRMAuthorization getAuthorization() {
        return authorization;
    }
    
    /** Setter for property authorization.
     * @param authorization New value of property authorization.
     */
    public void setAuthorization(SRMAuthorization authorization) {
        this.authorization = authorization;
    }
    
    /** Getter for property localSRM.
     * @return Value of property localSRM.
     */
    public org.dcache.srm.SRM getLocalSRM() {
        return localSRM;
    }
    
    /** Setter for property localSRM.
     * @param localSRM New value of property localSRM.
     */
    public void setLocalSRM(org.dcache.srm.SRM localSRM) {
        this.localSRM = localSRM;
    }
    
    /** Getter for property connect_to_wsdl.
     * @return Value of property connect_to_wsdl.
     */
    public boolean isConnect_to_wsdl() {
        return connect_to_wsdl;
    }
    
    /** Setter for property connect_to_wsdl.
     * @param connect_to_wsdl New value of property connect_to_wsdl.
     */
    public void setConnect_to_wsdl(boolean connect_to_wsdl) {
        this.connect_to_wsdl = connect_to_wsdl;
    }
    
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("SRM Configuration:");
        sb.append("\n\t\"defaultSpaceLifetime\"  request lifetime: ").append(this.defaultSpaceLifetime );
        sb.append("\n\t\"get\"  request lifetime: ").append(this.getLifetime );
        sb.append("\n\t\"put\"  request lifetime: ").append(this.putLifetime );
        sb.append("\n\t\"copy\" request lifetime: ").append(this.copyLifetime);
        sb.append("\n\tdebug=").append(this.debug);
        sb.append("\n\tgsissl=").append(this.gsissl);
        sb.append("\n\tgridftp buffer_size=").append(this.buffer_size);
        sb.append("\n\tgridftp tcp_buffer_size=").append(this.tcp_buffer_size);
        sb.append("\n\tgridftp parallel_streams=").append(this.parallel_streams);
        sb.append("\n\tglue_mapfile=").append(this.glue_mapfile);
        sb.append("\n\twebservice_path=").append(this.webservice_path);
        sb.append("\n\twebservice_protocol=").append(this.webservice_protocol);
        sb.append("\n\tgsiftpclinet=").append(this.gsiftpclinet);
        sb.append("\n\turlcopy=").append(this.urlcopy);
        sb.append("\n\tsrm_root=").append(this.srm_root);
        sb.append("\n\ttimeout_script=").append(this.timeout_script);
        sb.append("\n\turlcopy timeout in seconds=").append(this.timeout);
        sb.append("\n\tproxies directory=").append(this.proxies_directory);
        sb.append("\n\tport=").append(this.port);
        sb.append("\n\tsrmhost=").append(this.srmhost);
        sb.append("\n\tkpwdfile=").append(this.kpwdfile);
        sb.append("\n\tuse_gplazmaAuthzCellFlag=").append(this.use_gplazmaAuthzCellFlag);
        sb.append("\n\tdelegateToGplazmaFlag=").append(this.delegateToGplazmaFlag);
        sb.append("\n\tuse_gplazmaAuthzModuleFlag=").append(this.use_gplazmaAuthzModuleFlag);
        sb.append("\n\tgplazmaPolicy=").append(this.gplazmaPolicy);
        sb.append("\n\twebservice_path=").append(this.webservice_path);
        sb.append("\n\twebservice_protocol=").append(this.webservice_protocol);
        sb.append("\n\tx509ServiceKey=").append(this.x509ServiceKey);
        sb.append("\n\tx509ServiceCert=").append(this.x509ServiceCert);
        sb.append("\n\tx509TrastedCACerts=").append(this.x509TrastedCACerts);
        sb.append("\n\tuseUrlcopyScript=").append(this.useUrlcopyScript);
        sb.append("\n\tuseGsiftpForSrmCopy=").append(this.useGsiftpForSrmCopy);
        sb.append("\n\tuseHttpForSrmCopy=").append(this.useHttpForSrmCopy);
        sb.append("\n\tuseDcapForSrmCopy=").append(this.useDcapForSrmCopy);
        sb.append("\n\tuseFtpForSrmCopy=").append(this.useFtpForSrmCopy);
        sb.append("\n\tjdbcUrl=").append(this.jdbcUrl);
        sb.append("\n\tjdbcClass=").append(this.jdbcClass);
        sb.append("\n\tjdbcUser=").append(this.jdbcUser);
        for(int i = 0; i<this.protocols.length; ++i) {
            sb.append("\n\tprotocols["+i+"]=").append(this.protocols[i]);
        }
        sb.append("\n\t\t *** GetRequests Scheduler  Parameters **");
        sb.append("\n\t\t request Lifetime in miliseconds =").append(this.getLifetime);
        sb.append("\n\t\t max thread queue size =").append(this.getReqTQueueSize);
        sb.append("\n\t\t max number of threads =").append(this.getThreadPoolSize);
        sb.append("\n\t\t max number of waiting file requests =").append(this.getMaxWaitingRequests);
        sb.append("\n\t\t max ready queue size =").append(this.getReadyQueueSize);
        sb.append("\n\t\t max number of ready file requests =").append(this.getMaxReadyJobs);
        sb.append("\n\t\t maximum number of retries = ").append(this.getMaxNumOfRetries);
        sb.append("\n\t\t retry timeout in miliseconds =").append(this.getRetryTimeout);
        sb.append("\n\t\t maximum number of jobs running created");
        sb.append("\n\t\t by the same owner if other jobs are queued =").append(this.getMaxRunningBySameOwner);
        sb.append("\n\t\t getRequestRestorePolicy=").append(this.getRequestRestorePolicy);
        
        
        sb.append("\n\t\t *** PuRequests Scheduler  Parameters **");
        sb.append("\n\t\t request Lifetime in miliseconds =").append(this.putLifetime);
        sb.append("\n\t\t max thread queue size =").append(this.putReqTQueueSize);
        sb.append("\n\t\t max number of threads =").append(this.putThreadPoolSize);
        sb.append("\n\t\t max number of waiting file requests =").append(this.putMaxWaitingRequests);
        sb.append("\n\t\t max ready queue size =").append(this.putReadyQueueSize);
        sb.append("\n\t\t max number of ready file requests =").append(this.putMaxReadyJobs);
        sb.append("\n\t\t maximum number of retries = ").append(this.putMaxNumOfRetries);
        sb.append("\n\t\t retry timeout in miliseconds =").append(this.putRetryTimeout);
        sb.append("\n\t\t maximum number of jobs running created");
        sb.append("\n\t\t by the same owner if other jobs are queued =").append(this.putMaxRunningBySameOwner);
        sb.append("\n\t\t putRequestRestorePolicy=").append(this.putRequestRestorePolicy);
        
        sb.append("\n\t\t *** CopyRequests Scheduler  Parameters **");
        sb.append("\n\t\t request Lifetime in miliseconds =").append(this.copyLifetime);
        sb.append("\n\t\t max thread queue size =").append(this.copyReqTQueueSize);
        sb.append("\n\t\t max number of threads =").append(this.copyThreadPoolSize);
        sb.append("\n\t\t max number of waiting file requests =").append(this.copyMaxWaitingRequests);
        sb.append("\n\t\t maximum number of retries = ").append(this.copyMaxNumOfRetries);
        sb.append("\n\t\t retry timeout in miliseconds =").append(this.copyRetryTimeout);
        sb.append("\n\t\t maximum number of jobs running created");
        sb.append("\n\t\t by the same owner if other jobs are queued =").append(this.copyMaxRunningBySameOwner);
        sb.append("\n\t\t copyRequestRestorePolicy=").append(this.copyRequestRestorePolicy);
        
        sb.append("\n\tconnect_to_wsdl=").append(this.connect_to_wsdl);
        sb.append("\n\treserve_space_implicitely=").append(this.reserve_space_implicitely);
        sb.append("\n\tspace_reservation_strict=").append(this.space_reservation_strict);
        sb.append("\n\tstorage_info_update_period=").append(this.storage_info_update_period);
        sb.append("\n\tvacuum=").append(this.vacuum);
        sb.append("\n\tvacuum_period_sec=").append(this.vacuum_period_sec);
        sb.append("\n\tqosPluginClass=").append(this.qosPluginClass);
        sb.append("\n\tqosConfigFile=").append(this.qosConfigFile);
        sb.append("\n\tjdbcMonitoringEnabled=").append(this.jdbcMonitoringEnabled);
        sb.append("\n\tjdbcMonitoringDebugLevel=").append(this.jdbcMonitoringDebugLevel);
        sb.append("\n\tcleanPendingRequestsOnRestart=").append(this.cleanPendingRequestsOnRestart);
        
        return sb.toString();
    }
    
    /** Getter for property parallel_streams.
     * @return Value of property parallel_streams.
     */
    public int getParallel_streams() {
        return parallel_streams;
    }
    
    /** Setter for property parallel_streams.
     * @param parallel_streams New value of property parallel_streams.
     */
    public void setParallel_streams(int parallel_streams) {
        this.parallel_streams = parallel_streams;
    }
    
    
    /** Getter for property x509ServiceKey.
     * @return Value of property x509ServiceKey.
     */
    public java.lang.String getX509ServiceKey() {
        return x509ServiceKey;
    }
    
    /** Setter for property x509ServiceKey.
     * @param x509ServiceKey New value of property x509ServiceKey.
     */
    public void setX509ServiceKey(java.lang.String x509ServiceKey) {
        this.x509ServiceKey = x509ServiceKey;
    }
    
    /** Getter for property x509ServiceCert.
     * @return Value of property x509ServiceCert.
     */
    public java.lang.String getX509ServiceCert() {
        return x509ServiceCert;
    }
    
    /** Setter for property x509ServiceCert.
     * @param x509ServiceCert New value of property x509ServiceCert.
     */
    public void setX509ServiceCert(java.lang.String x509ServiceCert) {
        this.x509ServiceCert = x509ServiceCert;
    }
    
    /** Getter for property x509TrastedCACerts.
     * @return Value of property x509TrastedCACerts.
     */
    public java.lang.String getX509TrastedCACerts() {
        return x509TrastedCACerts;
    }
    
    /** Setter for property x509TrastedCACerts.
     * @param x509TrastedCACerts New value of property x509TrastedCACerts.
     */
    public void setX509TrastedCACerts(java.lang.String x509TrastedCACerts) {
        this.x509TrastedCACerts = x509TrastedCACerts;
    }
    
    
    /** Getter for property getLifetime.
     * @return Value of property getLifetime.
     *
     */
    public long getGetLifetime() {
        return getLifetime;
    }
    
    /** Setter for property getLifetime.
     * @param getLifetime New value of property getLifetime.
     *
     */
    public void setGetLifetime(long getLifetime) {
        this.getLifetime = getLifetime;
    }
    
    /** Getter for property putLifetime.
     * @return Value of property putLifetime.
     *
     */
    public long getPutLifetime() {
        return putLifetime;
    }
    
    /** Setter for property putLifetime.
     * @param putLifetime New value of property putLifetime.
     *
     */
    public void setPutLifetime(long putLifetime) {
        this.putLifetime = putLifetime;
    }
    
    /** Getter for property copyLifetime.
     * @return Value of property copyLifetime.
     *
     */
    public long getCopyLifetime() {
        return copyLifetime;
    }
    
    /** Setter for property copyLifetime.
     * @param copyLifetime New value of property copyLifetime.
     *
     */
    public void setCopyLifetime(long copyLifetime) {
        this.copyLifetime = copyLifetime;
    }
    
    /** Getter for property useUrlcopyScript.
     * @return Value of property useUrlcopyScript.
     *
     */
    public boolean isUseUrlcopyScript() {
        return useUrlcopyScript;
    }
    
    /** Setter for property useUrlcopyScript.
     * @param useUrlcopyScript New value of property useUrlcopyScript.
     *
     */
    public void setUseUrlcopyScript(boolean useUrlcopyScript) {
        this.useUrlcopyScript = useUrlcopyScript;
    }
    
    /** Getter for property useDcapForSrmCopy.
     * @return Value of property useDcapForSrmCopy.
     *
     */
    public boolean isUseDcapForSrmCopy() {
        return useDcapForSrmCopy;
    }
    
    /** Setter for property useDcapForSrmCopy.
     * @param useDcapForSrmCopy New value of property useDcapForSrmCopy.
     *
     */
    public void setUseDcapForSrmCopy(boolean useDcapForSrmCopy) {
        this.useDcapForSrmCopy = useDcapForSrmCopy;
    }
    
    /** Getter for property useGsiftpForSrmCopy.
     * @return Value of property useGsiftpForSrmCopy.
     *
     */
    public boolean isUseGsiftpForSrmCopy() {
        return useGsiftpForSrmCopy;
    }
    
    /** Setter for property useGsiftpForSrmCopy.
     * @param useGsiftpForSrmCopy New value of property useGsiftpForSrmCopy.
     *
     */
    public void setUseGsiftpForSrmCopy(boolean useGsiftpForSrmCopy) {
        this.useGsiftpForSrmCopy = useGsiftpForSrmCopy;
    }
    
    /** Getter for property useHttpForSrmCopy.
     * @return Value of property useHttpForSrmCopy.
     *
     */
    public boolean isUseHttpForSrmCopy() {
        return useHttpForSrmCopy;
    }
    
    /** Setter for property useHttpForSrmCopy.
     * @param useHttpForSrmCopy New value of property useHttpForSrmCopy.
     *
     */
    public void setUseHttpForSrmCopy(boolean useHttpForSrmCopy) {
        this.useHttpForSrmCopy = useHttpForSrmCopy;
    }
    
    /** Getter for property useFtpForSrmCopy.
     * @return Value of property useFtpForSrmCopy.
     *
     */
    public boolean isUseFtpForSrmCopy() {
        return useFtpForSrmCopy;
    }
    
    /** Setter for property useFtpForSrmCopy.
     * @param useFtpForSrmCopy New value of property useFtpForSrmCopy.
     *
     */
    public void setUseFtpForSrmCopy(boolean useFtpForSrmCopy) {
        this.useFtpForSrmCopy = useFtpForSrmCopy;
    }
    
    /** Getter for property recursiveDirectoryCreation.
     * @return Value of property recursiveDirectoryCreation.
     *
     */
    public boolean isRecursiveDirectoryCreation() {
        return recursiveDirectoryCreation;
    }
    
    /** Setter for property recursiveDirectoryCreation.
     * @param recursiveDirectoryCreation New value of property recursiveDirectoryCreation.
     *
     */
    public void setRecursiveDirectoryCreation(boolean recursiveDirectoryCreation) {
        this.recursiveDirectoryCreation = recursiveDirectoryCreation;
    }
    
    /** Getter for property advisoryDelete.
     * @return Value of property advisoryDelete.
     *
     */
    public boolean isAdvisoryDelete() {
        return advisoryDelete;
    }
    
    /** Setter for property advisoryDelete.
     * @param advisoryDelete New value of property advisoryDelete.
     *
     */
    public void setAdvisoryDelete(boolean advisoryDelete) {
        this.advisoryDelete = advisoryDelete;
    }
    
    /** Getter for property removeFile
     * @return Value of property removeFile.
     *
     */
    public boolean isRemoveFile() {
        return removeFile;
    }
    
    /** Setter for property removeFile.
     * @param removeFile New value of property removefile.
     *
     */
    public void setRemoveFile(boolean removeFile) {
        this.removeFile = removeFile;
    }
    
    
    /** Getter for property removeDirectory
     * @return Value of property removeDirectory.
     *
     */
    public boolean isRemoveDirectory() {
        return removeDirectory;
    }
    
    /** Setter for property removeDirectory.
     * @param removeDirectory New value of property removeDirectory.
     *
     */
    public void setRemoveDirectory(boolean removeDirectory) {
        this.removeDirectory = removeDirectory;
    }
    
    /** Getter for property createDirectory
     * @return Value of property createDirectory.
     *
     */
    public boolean isCreateDirectory() {
        return createDirectory;
    }
    
    /** Setter for property createDirectory.
     * @param createDirectory New value of property createDirectory.
     *
     */
    public void setCreateDirectory(boolean createDirectory) {
        this.createDirectory = createDirectory;
    }
    
    
    /** Getter for property moveEntry
     * @return Value of property moveEntry
     *
     */
    public boolean isMoveEntry() {
        return moveEntry;
    }
    
    /** Setter for property moveEntry.
     * @param moveEntry New value of property createDirectory.
     *
     */
    public void setMoveEntry(boolean moveEntry) {
        this.moveEntry = moveEntry;
    }
    
    /** Getter for property saveMemory.
     * @return Value of property saveMemory.
     *
     */
    public boolean isSaveMemory() {
        return saveMemory;
    }
    
    /** Setter for property saveMemory.
     * @param saveMemory New value of property saveMemory.
     *
     */
    public void setSaveMemory(boolean saveMemory) {
        this.saveMemory = saveMemory;
    }
    
    /**
     * Getter for property jdbcUrl.
     * @return Value of property jdbcUrl.
     */
    public java.lang.String getJdbcUrl() {
        return jdbcUrl;
    }
    
    /**
     * Setter for property jdbcUrl.
     * @param jdbcUrl New value of property jdbcUrl.
     */
    public void setJdbcUrl(java.lang.String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }
    
    /**
     * Getter for property jdbcClass.
     * @return Value of property jdbcClass.
     */
    public java.lang.String getJdbcClass() {
        return jdbcClass;
    }
    
    /**
     * Setter for property jdbcClass.
     * @param jdbcClass New value of property jdbcClass.
     */
    public void setJdbcClass(java.lang.String jdbcClass) {
        this.jdbcClass = jdbcClass;
    }
    
    /**
     * Getter for property user.
     * @return Value of property user.
     */
    public java.lang.String getJdbcUser() {
        return jdbcUser;
    }
    
    /**
     * Setter for property user.
     * @param user New value of property user.
     */
    public void setJdbcUser(java.lang.String user) {
        this.jdbcUser = user;
    }
    
    /**
     * Getter for property pass.
     * @return Value of property pass.
     */
    public java.lang.String getJdbcPass() {
        if (this.jdbcPwdfile==null) {
            return jdbcPass;
        } else if (this.jdbcPwdfile.equals("")) {
            return jdbcPass;
        } else {
            Pgpass pgpass = new Pgpass(jdbcPwdfile);      //VP
            return pgpass.getPgpass(jdbcUrl, jdbcUser);   //VP
        }
    }
    
    /**
     * Setter for property pass.
     * @param pass New value of property pass.
     */
    public void setJdbcPass(java.lang.String pass) {
        this.jdbcPass = pass;
    }
    
    /**
     * Getter for property pwdfile.
     * @return Value of property pwdfile.
     */
    public java.lang.String getJdbcPwdfile() {
        return jdbcPwdfile;
    }
    
    /**
     * Setter for property pwdfile.
     * @param name New value of property pwdfile.
     */
    public void setJdbcPwdfile(java.lang.String name) {
        this.jdbcPwdfile = name;
    }
    
    /**
     * Getter for property nextRequestIdStorageTable.
     * @return Value of property nextRequestIdStorageTable.
     */
    public java.lang.String getNextRequestIdStorageTable() {
        return nextRequestIdStorageTable;
    }
    
    /**
     * Setter for property nextRequestIdStorageTable.
     * @param nextRequestIdStorageTable New value of property nextRequestIdStorageTable.
     */
    public void setNextRequestIdStorageTable(java.lang.String nextRequestIdStorageTable) {
        this.nextRequestIdStorageTable = nextRequestIdStorageTable;
    }
    
    /**
     * Getter for property getReqTQueueSize.
     * @return Value of property getReqTQueueSize.
     */
    public int getGetReqTQueueSize() {
        return getReqTQueueSize;
    }
    
    /**
     * Setter for property getReqTQueueSize.
     * @param getReqTQueueSize New value of property getReqTQueueSize.
     */
    public void setGetReqTQueueSize(int getReqTQueueSize) {
        this.getReqTQueueSize = getReqTQueueSize;
    }
    
    /**
     * Getter for property getThreadPoolSize.
     * @return Value of property getThreadPoolSize.
     */
    public int getGetThreadPoolSize() {
        return getThreadPoolSize;
    }
    
    /**
     * Setter for property getThreadPoolSize.
     * @param getThreadPoolSize New value of property getThreadPoolSize.
     */
    public void setGetThreadPoolSize(int getThreadPoolSize) {
        this.getThreadPoolSize = getThreadPoolSize;
    }
    
    /**
     * Getter for property getMaxWaitingRequests.
     * @return Value of property getMaxWaitingRequests.
     */
    public int getGetMaxWaitingRequests() {
        return getMaxWaitingRequests;
    }
    
    /**
     * Setter for property getMaxWaitingRequests.
     * @param getMaxWaitingRequests New value of property getMaxWaitingRequests.
     */
    public void setGetMaxWaitingRequests(int getMaxWaitingRequests) {
        this.getMaxWaitingRequests = getMaxWaitingRequests;
    }
    
    /**
     * Getter for property getReadyQueueSize.
     * @return Value of property getReadyQueueSize.
     */
    public int getGetReadyQueueSize() {
        return getReadyQueueSize;
    }
    
    /**
     * Setter for property getReadyQueueSize.
     * @param getReadyQueueSize New value of property getReadyQueueSize.
     */
    public void setGetReadyQueueSize(int getReadyQueueSize) {
        this.getReadyQueueSize = getReadyQueueSize;
    }
    
    /**
     * Getter for property getMaxReadyJobs.
     * @return Value of property getMaxReadyJobs.
     */
    public int getGetMaxReadyJobs() {
        return getMaxReadyJobs;
    }
    
    /**
     * Setter for property getMaxReadyJobs.
     * @param getMaxReadyJobs New value of property getMaxReadyJobs.
     */
    public void setGetMaxReadyJobs(int getMaxReadyJobs) {
        this.getMaxReadyJobs = getMaxReadyJobs;
    }
    
    /**
     * Getter for property getMaxNumOfRetries.
     * @return Value of property getMaxNumOfRetries.
     */
    public int getGetMaxNumOfRetries() {
        return getMaxNumOfRetries;
    }
    
    /**
     * Setter for property getMaxNumOfRetries.
     * @param getMaxNumOfRetries New value of property getMaxNumOfRetries.
     */
    public void setGetMaxNumOfRetries(int getMaxNumOfRetries) {
        this.getMaxNumOfRetries = getMaxNumOfRetries;
    }
    
    /**
     * Getter for property getRetryTimeout.
     * @return Value of property getRetryTimeout.
     */
    public long getGetRetryTimeout() {
        return getRetryTimeout;
    }
    
    /**
     * Setter for property getRetryTimeout.
     * @param getRetryTimeout New value of property getRetryTimeout.
     */
    public void setGetRetryTimeout(long getRetryTimeout) {
        this.getRetryTimeout = getRetryTimeout;
    }
    
    /**
     * Getter for property getMaxRunningBySameOwner.
     * @return Value of property getMaxRunningBySameOwner.
     */
    public int getGetMaxRunningBySameOwner() {
        return getMaxRunningBySameOwner;
    }
    
    /**
     * Setter for property getMaxRunningBySameOwner.
     * @param getMaxRunningBySameOwner New value of property getMaxRunningBySameOwner.
     */
    public void setGetMaxRunningBySameOwner(int getMaxRunningBySameOwner) {
        this.getMaxRunningBySameOwner = getMaxRunningBySameOwner;
    }
    
    /**
     * Getter for property putReqTQueueSize.
     * @return Value of property putReqTQueueSize.
     */
    public int getPutReqTQueueSize() {
        return putReqTQueueSize;
    }
    
    /**
     * Setter for property putReqTQueueSize.
     * @param putReqTQueueSize New value of property putReqTQueueSize.
     */
    public void setPutReqTQueueSize(int putReqTQueueSize) {
        this.putReqTQueueSize = putReqTQueueSize;
    }
    
    /**
     * Getter for property putThreadPoolSize.
     * @return Value of property putThreadPoolSize.
     */
    public int getPutThreadPoolSize() {
        return putThreadPoolSize;
    }
    
    /**
     * Setter for property putThreadPoolSize.
     * @param putThreadPoolSize New value of property putThreadPoolSize.
     */
    public void setPutThreadPoolSize(int putThreadPoolSize) {
        this.putThreadPoolSize = putThreadPoolSize;
    }
    
    /**
     * Getter for property putMaxWaitingRequests.
     * @return Value of property putMaxWaitingRequests.
     */
    public int getPutMaxWaitingRequests() {
        return putMaxWaitingRequests;
    }
    
    /**
     * Setter for property putMaxWaitingRequests.
     * @param putMaxWaitingRequests New value of property putMaxWaitingRequests.
     */
    public void setPutMaxWaitingRequests(int putMaxWaitingRequests) {
        this.putMaxWaitingRequests = putMaxWaitingRequests;
    }
    
    /**
     * Getter for property putReadyQueueSize.
     * @return Value of property putReadyQueueSize.
     */
    public int getPutReadyQueueSize() {
        return putReadyQueueSize;
    }
    
    /**
     * Setter for property putReadyQueueSize.
     * @param putReadyQueueSize New value of property putReadyQueueSize.
     */
    public void setPutReadyQueueSize(int putReadyQueueSize) {
        this.putReadyQueueSize = putReadyQueueSize;
    }
    
    /**
     * Getter for property putMaxReadyJobs.
     * @return Value of property putMaxReadyJobs.
     */
    public int getPutMaxReadyJobs() {
        return putMaxReadyJobs;
    }
    
    /**
     * Setter for property putMaxReadyJobs.
     * @param putMaxReadyJobs New value of property putMaxReadyJobs.
     */
    public void setPutMaxReadyJobs(int putMaxReadyJobs) {
        this.putMaxReadyJobs = putMaxReadyJobs;
    }
    
    /**
     * Getter for property putMaxNumOfRetries.
     * @return Value of property putMaxNumOfRetries.
     */
    public int getPutMaxNumOfRetries() {
        return putMaxNumOfRetries;
    }
    
    /**
     * Setter for property putMaxNumOfRetries.
     * @param putMaxNumOfRetries New value of property putMaxNumOfRetries.
     */
    public void setPutMaxNumOfRetries(int putMaxNumOfRetries) {
        this.putMaxNumOfRetries = putMaxNumOfRetries;
    }
    
    /**
     * Getter for property putRetryTimeout.
     * @return Value of property putRetryTimeout.
     */
    public long getPutRetryTimeout() {
        return putRetryTimeout;
    }
    
    /**
     * Setter for property putRetryTimeout.
     * @param putRetryTimeout New value of property putRetryTimeout.
     */
    public void setPutRetryTimeout(long putRetryTimeout) {
        this.putRetryTimeout = putRetryTimeout;
    }
    
    /**
     * Getter for property putMaxRunningBySameOwner.
     * @return Value of property putMaxRunningBySameOwner.
     */
    public int getPutMaxRunningBySameOwner() {
        return putMaxRunningBySameOwner;
    }
    
    /**
     * Setter for property putMaxRunningBySameOwner.
     * @param putMaxRunningBySameOwner New value of property putMaxRunningBySameOwner.
     */
    public void setPutMaxRunningBySameOwner(int putMaxRunningBySameOwner) {
        this.putMaxRunningBySameOwner = putMaxRunningBySameOwner;
    }
    
    /**
     * Getter for property copyReqTQueueSize.
     * @return Value of property copyReqTQueueSize.
     */
    public int getCopyReqTQueueSize() {
        return copyReqTQueueSize;
    }
    
    /**
     * Setter for property copyReqTQueueSize.
     * @param copyReqTQueueSize New value of property copyReqTQueueSize.
     */
    public void setCopyReqTQueueSize(int copyReqTQueueSize) {
        this.copyReqTQueueSize = copyReqTQueueSize;
    }
    
    /**
     * Getter for property copyThreadPoolSize.
     * @return Value of property copyThreadPoolSize.
     */
    public int getCopyThreadPoolSize() {
        return copyThreadPoolSize;
    }
    
    /**
     * Setter for property copyThreadPoolSize.
     * @param copyThreadPoolSize New value of property copyThreadPoolSize.
     */
    public void setCopyThreadPoolSize(int copyThreadPoolSize) {
        this.copyThreadPoolSize = copyThreadPoolSize;
    }
    
    /**
     * Getter for property copyMaxWaitingRequests.
     * @return Value of property copyMaxWaitingRequests.
     */
    public int getCopyMaxWaitingRequests() {
        return copyMaxWaitingRequests;
    }
    
    /**
     * Setter for property copyMaxWaitingRequests.
     * @param copyMaxWaitingRequests New value of property copyMaxWaitingRequests.
     */
    public void setCopyMaxWaitingRequests(int copyMaxWaitingRequests) {
        this.copyMaxWaitingRequests = copyMaxWaitingRequests;
    }
    
    /**
     * Getter for property copyMaxNumOfRetries.
     * @return Value of property copyMaxNumOfRetries.
     */
    public int getCopyMaxNumOfRetries() {
        return copyMaxNumOfRetries;
    }
    
    /**
     * Setter for property copyMaxNumOfRetries.
     * @param copyMaxNumOfRetries New value of property copyMaxNumOfRetries.
     */
    public void setCopyMaxNumOfRetries(int copyMaxNumOfRetries) {
        this.copyMaxNumOfRetries = copyMaxNumOfRetries;
    }
    
    /**
     * Getter for property copyRetryTimeout.
     * @return Value of property copyRetryTimeout.
     */
    public long getCopyRetryTimeout() {
        return copyRetryTimeout;
    }
    
    /**
     * Setter for property copyRetryTimeout.
     * @param copyRetryTimeout New value of property copyRetryTimeout.
     */
    public void setCopyRetryTimeout(long copyRetryTimeout) {
        this.copyRetryTimeout = copyRetryTimeout;
    }
    
    /**
     * Getter for property copyMaxRunningBySameOwner.
     * @return Value of property copyMaxRunningBySameOwner.
     */
    public int getCopyMaxRunningBySameOwner() {
        return copyMaxRunningBySameOwner;
    }
    
    /**
     * Setter for property copyMaxRunningBySameOwner.
     * @param copyMaxRunningBySameOwner New value of property copyMaxRunningBySameOwner.
     */
    public void setCopyMaxRunningBySameOwner(int copyMaxRunningBySameOwner) {
        this.copyMaxRunningBySameOwner = copyMaxRunningBySameOwner;
    }
    
    
    public static final void main( String[] args) throws Exception {
        if(args == null || args.length !=2 ||
                args[0].equalsIgnoreCase("-h")  ||
                args[0].equalsIgnoreCase("-help")  ||
                args[0].equalsIgnoreCase("--h")  ||
                args[0].equalsIgnoreCase("--help")
                ) {
            System.err.println("Usage: Configuration load <file>\n or Configuration save <file>");
            return;
        }
        
        String command = args[0];
        String file = args[1];
        
        if(command.equals("load")) {
            System.out.println("reading configuration from file "+file);
            Configuration config = new Configuration(file);
            System.out.println("read configuration successfully:");
            System.out.print(config.toString());
        } else if(command.equals("save")) {
            Configuration config = new Configuration();
            System.out.print(config.toString());
            System.out.println("writing configuration to a file "+file);
            config.write(file);
            System.out.println("done");
        } else {
            System.err.println("Usage: Co<nfiguration load <file>\n or Configuration save <file>");
            return;
            
        }
    }
    
    /**
     * Getter for property reserve_space_implicitely.
     * 
     * @return Value of property reserve_space_implicitely.
     */
    public boolean isReserve_space_implicitely() {
        return reserve_space_implicitely;
    }
    
    /**
     * Setter for property reserve_space_implicitely.
     * 
     * @param reserve_space_implicitely New value of property reserve_space_implicitely.
     */
    public void setReserve_space_implicitely(boolean reserve_space_implicitely) {
        this.reserve_space_implicitely = reserve_space_implicitely;
    }
    
    /**
     * Getter for property space_reservation_strict.
     * @return Value of property space_reservation_strict.
     */
    public boolean isSpace_reservation_strict() {
        return space_reservation_strict;
    }
    
    /**
     * Setter for property space_reservation_strict.
     * @param space_reservation_strict New value of property space_reservation_strict.
     */
    public void setSpace_reservation_strict(boolean space_reservation_strict) {
        this.space_reservation_strict = space_reservation_strict;
    }
    
    /**
     * Getter for property storage_info_update_period.
     * @return Value of property storage_info_update_period.
     */
    public long getStorage_info_update_period() {
        return storage_info_update_period;
    }
    
    /**
     * Setter for property storage_info_update_period.
     * @param storage_info_update_period New value of property storage_info_update_period.
     */
    public void setStorage_info_update_period(long storage_info_update_period) {
        this.storage_info_update_period = storage_info_update_period;
    }
    
    /**
     * Getter for property vacuum_period_sec.
     * @return Value of property vacuum_period_sec.
     */
    public long getVacuum_period_sec() {
        return vacuum_period_sec;
    }
    
    /**
     * Setter for property vacuum_period_sec.
     * @param vacuum_period_sec New value of property vacuum_period_sec.
     */
    public void setVacuum_period_sec(long vacuum_period_sec) {
        this.vacuum_period_sec = vacuum_period_sec;
    }
    
    /**
     * Getter for property vacuum.
     * @return Value of property vacuum.
     */
    public boolean isVacuum() {
        return vacuum;
    }
    
    /**
     * Setter for property vacuum.
     * @param vacuum New value of property vacuum.
     */
    public void setVacuum(boolean vacuum) {
        this.vacuum = vacuum;
    }
    
    /**
     * Getter for property putRequestRestorePolicy.
     * @return Value of property putRequestRestorePolicy.
     */
    public java.lang.String getPutRequestRestorePolicy() {
        return putRequestRestorePolicy;
    }
    
    /**
     * Setter for property putRequestRestorePolicy.
     * @param putRequestRestorePolicy New value of property putRequestRestorePolicy.
     */
    public void setPutRequestRestorePolicy(java.lang.String putRequestRestorePolicy) {
        if(putRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_FAIL_REQUEST) ||
                putRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ||
                putRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_WAIT_FOR_UPDATE_REQUEST) ) {
            this.putRequestRestorePolicy = putRequestRestorePolicy;
        } else {
            throw new IllegalArgumentException("putRequestRestorePolicy value must be one of "+
                    "\""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" or \""+
                    ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" "+
                    " but received value="+putRequestRestorePolicy);
        }
        
    }
    
    /**
     * Getter for property getRequestRestorePolicy.
     * @return Value of property getRequestRestorePolicy.
     */
    public java.lang.String getGetRequestRestorePolicy() {
        return getRequestRestorePolicy;
    }
    
    /**
     * Setter for property getRequestRestorePolicy.
     * @param getRequestRestorePolicy New value of property getRequestRestorePolicy.
     */
    public void setGetRequestRestorePolicy(java.lang.String getRequestRestorePolicy) {
        if(getRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_FAIL_REQUEST) ||
                getRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ||
                getRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_WAIT_FOR_UPDATE_REQUEST) ) {
            this.getRequestRestorePolicy = getRequestRestorePolicy;
        } else {
            throw new IllegalArgumentException("getRequestRestorePolicy value must be one of "+
                    "\""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" or \""+
                    ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" "+
                    " but received value="+getRequestRestorePolicy);
        }
        
    }
    
    /**
     * Getter for property copyRequestRestorePolicy.
     * @return Value of property copyRequestRestorePolicy.
     */
    public java.lang.String getCopyRequestRestorePolicy() {
        return copyRequestRestorePolicy;
    }
    
    /**
     * Setter for property copyRequestRestorePolicy.
     * @param copyRequestRestorePolicy New value of property copyRequestRestorePolicy.
     */
    public void setCopyRequestRestorePolicy(java.lang.String copyRequestRestorePolicy) {
        if(copyRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_FAIL_REQUEST) ||
                copyRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_RESTORE_REQUEST) ||
                copyRequestRestorePolicy.equalsIgnoreCase(ON_RESTART_WAIT_FOR_UPDATE_REQUEST) ) {
            this.copyRequestRestorePolicy = copyRequestRestorePolicy;
        } else {
            throw new IllegalArgumentException("copyRequestRestorePolicy value must be one of "+
                    "\""+ON_RESTART_FAIL_REQUEST+"\", \""+ON_RESTART_RESTORE_REQUEST+"\" or \""+
                    ON_RESTART_WAIT_FOR_UPDATE_REQUEST+"\" "+
                    " but received value="+copyRequestRestorePolicy);
        }
    }
    
    /**
     * Getter for property start_server.
     * @return Value of property start_server.
     */
    public boolean isStart_server() {
        return start_server;
    }
    
    /**
     * Setter for property start_server.
     * @param start_server New value of property start_server.
     */
    public void setStart_server(boolean start_server) {
        this.start_server = start_server;
    }
    

    public String getQosPluginClass() {
    	return qosPluginClass;
    }
    public void setQosPluginClass(String qosPluginClass) {
    	this.qosPluginClass = qosPluginClass;
    }
    public String getQosConfigFile() {
    	return qosConfigFile;
    }
    public void setQosConfigFile(String qosConfigFile) {
    	this.qosConfigFile = qosConfigFile;
    }

    public int getNumDaysHistory() {
        return numDaysHistory;
    }

    public void setNumDaysHistory(int numDaysHistory) {
        this.numDaysHistory = numDaysHistory;
    }

    public long getOldRequestRemovePeriodSecs() {
        return oldRequestRemovePeriodSecs;
    }

    public void setOldRequestRemovePeriodSecs(long oldRequestRemovePeriodSecs) {
        this.oldRequestRemovePeriodSecs = oldRequestRemovePeriodSecs;
    }
    
    public long getDefaultSpaceLifetime() {
        return defaultSpaceLifetime;
    }
    
    public void setDefaultSpaceLifetime(long defaultSpaceLifetime) {
        this.defaultSpaceLifetime = defaultSpaceLifetime;
    }
    
    public void setGetPriorityPolicyPlugin(String txt) {
        getPriorityPolicyPlugin=txt;
    }
    
    public String getGetPriorityPolicyPlugin() {
        return getPriorityPolicyPlugin;
    }
    
    
    public void setPutPriorityPolicyPlugin(String txt) {
        putPriorityPolicyPlugin=txt;
    }
    
    public String getPutPriorityPolicyPlugin() {
        return putPriorityPolicyPlugin;
    }
    
    public void setCopyPriorityPolicyPlugin(String txt) {
        putPriorityPolicyPlugin=txt;
    }
    
    public String getCopyPriorityPolicyPlugin() {
        return putPriorityPolicyPlugin;
    }

     public Integer getJdbcExecutionThreadNum() {
        return jdbcExecutionThreadNum;
    }

    public void setJdbcExecutionThreadNum(Integer jdbcExecutionThreadNum) {
        this.jdbcExecutionThreadNum = jdbcExecutionThreadNum;
    }
    
     public Integer getMaxQueuedJdbcTasksNum() {
        return maxQueuedJdbcTasksNum;
    }

    public void setMaxQueuedJdbcTasksNum(Integer maxQueuedJdbcTasksNum) {
        this.maxQueuedJdbcTasksNum = maxQueuedJdbcTasksNum;
    }

    public String getCredentialsDirectory() {
        return credentialsDirectory;
    }

    public void setCredentialsDirectory(String credentialsDirectory) {
        this.credentialsDirectory = credentialsDirectory;
    }

    public boolean isJdbcMonitoringEnabled() {
        return jdbcMonitoringEnabled;
    }

    public void setJdbcMonitoringEnabled(boolean jdbcMonitoringEnabled) {
        this.jdbcMonitoringEnabled = jdbcMonitoringEnabled;
    }
    
    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public int getSizeOfSingleRemoveBatch() {
	    return sizeOfSingleRemoveBatch;
    }

    public void setSizeOfSingleRemoveBatch(int size) {
	    sizeOfSingleRemoveBatch=size;
    }

    public boolean isOverwrite_by_default() {
        return overwrite_by_default;
    }

    public void setOverwrite_by_default(boolean overwrite_by_default) {
        this.overwrite_by_default = overwrite_by_default;
    }

    public boolean isJdbcMonitoringDebugLevel() {
        return jdbcMonitoringDebugLevel;
    }

    public void setJdbcMonitoringDebugLevel(boolean jdbcMonitoringDebugLevel) {
        this.jdbcMonitoringDebugLevel = jdbcMonitoringDebugLevel;
    }

    public SRMUserPersistenceManager getSrmUserPersistenceManager() {
        return srmUserPersistenceManager;
    }

    public void setSrmUserPersistenceManager(SRMUserPersistenceManager srmUserPersistenceManager) {
        this.srmUserPersistenceManager = srmUserPersistenceManager;
    }

    public boolean isCleanPendingRequestsOnRestart() {
        return cleanPendingRequestsOnRestart;
    }

    public void setCleanPendingRequestsOnRestart(boolean cleanPendingRequestsOnRestart) {
        this.cleanPendingRequestsOnRestart = cleanPendingRequestsOnRestart;
    }
    
}
