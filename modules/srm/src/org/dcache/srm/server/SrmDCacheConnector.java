/**
 * SrmConnector.java
 *
 * Authors:  LH - Leo Heska
 *
 * History:
 *    2005/07/11 LH Extracted this code from SrmSoapBindingImpl.java
 *    2005/07/11 LH Eliminated threads that sabotaged running in servlet
 */

/*
COPYRIGHT STATUS:
Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
software are sponsored by the U.S. Department of Energy under Contract
No. DE-AC02-76CH03000. Therefore, the U.S. Government retains a
world-wide non-exclusive, royalty-free license to publish or reproduce
these documents and software for U.S. Government purposes.  All
documents and software available from this server are protected under
the U.S. and Foreign Copyright Laws, and FNAL reserves all rights.
 
 
 Distribution of the software available from this server is free of
 charge subject to the user following the terms of the Fermitools
 Software Legal Information.
 
 Redistribution and/or modification of the software shall be accompanied
 by the Fermitools Software Legal Information  (including the copyright
 notice).
 
 The user is asked to feed back problems, benefits, and/or suggestions
 about the software to the Fermilab Software Providers.
 
 
Neither the name of Fermilab, the  URA, nor the names of the
contributors may be used to endorse or promote products derived from
this software without specific prior written permission.
 
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
cost, or damages arising out of or resulting from the use of the
software available from this server.
 
 
Export Control:
 
All documents and software available from this server are subject to
U.S. export control laws.  Anyone downloading information from this
server is obligated to secure any necessary Government licenses before
exporting documents or software obtained from this server.
 */


package org.dcache.srm.server;

public class SrmDCacheConnector {
    
    // Leave these at package level?  Orthodoxy says make 'em
    // private and provide getters...
    private org.dcache.srm.SRM srm = null;
    private String logFile;
    static org.dcache.srm.util.Configuration configuration;
    
    private org.apache.log4j.Logger log;
    private static SrmDCacheConnector instance = null;
    
    
   String error_web_xml_prefix =
          "Please insert following into your axis' web.xml:\n"+
          " <env-entry>\n"+
          "   <env-entry-name> ";
   String error_web_xml_postfix =" </env-entry-name>\n"+
          "     <env-entry-value> ACTUAL VALUE OR FILE NAME GOES HERE </env-entry-value>\n"+
          "   <env-entry-type>java.lang.String</env-entry-type>\n"+
          " </env-entry>";

    public synchronized static SrmDCacheConnector getInstance(String srmConfigFile)  throws Exception{
        
        if (instance != null ) return instance;
       
        instance = new SrmDCacheConnector(srmConfigFile);
        return instance;
    }
    
    
    private SrmDCacheConnector(String configFileName) throws Exception{
            log = org.apache.log4j.Logger.getLogger(this.getClass().getName());
     org.jdom.input.SAXBuilder builder = new org.jdom.input.SAXBuilder();
     System.out.println("srmConfigFile is "+configFileName);     
     java.io.File srmConfigFile = new java.io.File(configFileName);
     if(!srmConfigFile.exists() || !srmConfigFile.canRead()) {
         System.err.println("can't find srmConfigFile at "+configFileName);
         throw new IllegalArgumentException("can't find srmConfigFile at "+configFileName);
     }
     org.jdom.Document doc = builder.build(srmConfigFile);
     org.jdom.Element root = doc.getRootElement();
     logFile = root.getChildText("logFile");
     if(logFile == null) {
         System.err.println("logFile value not specified");
          System.err.println("Please insert following into "+configFileName+":\n"+
          "<SRMConfigInfo>\n"+
          "  <dCacheParameter> ACTUAL LOCATION OF DCACHE PARAMS XML GOES HERE, example: /opt/d-cache/libexec/apache-tomcat-5.5.20/webapps/axis/WEB-INF/dCacheParams.xml </dCacheParameter>\n"+
          "  <logFile> ACTUAL LOCATION OF SRM LOG FILE GOES HERE, example:  /opt/d-cache/libexec/apache-tomcat-5.5.20/webapps/axis/WEB-INF/logconfig </logFile>\n"+
          "  </SRMConfigInfo>");
         throw new IllegalArgumentException ("logFile value not specified");
     }
     logFile = logFile.trim();
     System.out.println("logFile: " + logFile);
     String storageClassName = root.getChildText("storageClassName");
     if(storageClassName == null) {
         System.err.println("storageClassName value \n"+
         " ( diskCacheV111.srm.dcache.Storage or \n"+
         "   org.dcache.fnal.unixfs.Storage ) not specified");
          System.err.println(
          error_web_xml_prefix + "storageClassName"+error_web_xml_postfix);
         throw new IllegalArgumentException ("storageClassName value not specified");
         
     }
     storageClassName = storageClassName.trim();
     System.out.println("storageClassName: " + storageClassName);
     String dCacheParamFileName = root.getChildText("dCacheParametersFileName");
     if(storageClassName == null) {
         System.err.println("dCacheParametersFileName  value not specified");
          System.err.println(
          error_web_xml_prefix + "dCacheParametersFileName"+error_web_xml_postfix);
         throw new IllegalArgumentException ("dCacheParametersFileName value not specified");
         
     }
     dCacheParamFileName = dCacheParamFileName.trim();
     System.out.println("dCacheParamFileName: " + dCacheParamFileName);
     java.io.File dCacheParamFile = new java.io.File(dCacheParamFileName);
     if(!dCacheParamFile.exists() || !dCacheParamFile.canRead()) {
         System.err.println("can't find dCacheParamFile at "+dCacheParamFileName);
         throw new IllegalArgumentException("can't find dCacheParamFile at "+dCacheParamFileName);
     }

     javax.naming.Context logctx;

     log = org.apache.log4j.Logger.getLogger(this.getClass().getName());
     logctx = new javax.naming.InitialContext();

     // Now build dCacheParams.

     builder = new org.jdom.input.SAXBuilder();
     doc = builder.build(dCacheParamFile);
     root = doc.getRootElement();
     java.util.List children = root.getChildren();
     int numChildren = children.size();
     String [] dCacheParams = new String[numChildren]; 
     int aPos = 0;  // used to keep track of where we are in tsa
     // System.out.println(
     //    "This XML file has "+ children.size() +" top elements:");
     java.util.Iterator it = children.iterator();

     while (it.hasNext()) {
        org.jdom.Element te = (org.jdom.Element) it.next();
        System.out.println(
           "dCacheParams Element name: " + te.getName() +
           "; Element value: " + te.getText());
        dCacheParams[aPos++] = te.getText();
     }
            try
            {
            Class clazz = Class.forName(storageClassName);
            java.lang.reflect.Method tm =
            clazz.getMethod("getSRMInstance", new Class[]{String[].class,
            Long.TYPE});
            
            
            // First Parameter is a config file name 
            // And second is timeout in millis
            srm = (org.dcache.srm.SRM)tm.invoke(null,
            new Object[]{dCacheParams, 
            new Long(60*1000)});
            
            System.out.println("Got instance of srm.");
            instance.configuration = srm.getConfiguration();
            }
            catch(Exception e) {
                e.printStackTrace();
                throw e;
            }
   }
  
    public org.dcache.srm.SRM getSrm() { return srm; }
    
    /**
     * Getter for property logFile.
     * @return Value of property logFile.
     */
    public java.lang.String getLogFile() {
        return logFile;
    }    
    
    
}
