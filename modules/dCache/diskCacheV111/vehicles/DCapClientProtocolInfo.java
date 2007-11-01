package diskCacheV111.vehicles;
/**
 * @author Patrick F.
 * @author Timur Perelmutov. timur@fnal.gov
 * @version 0.0, 28 Jun 2002
 */

public class DCapClientProtocolInfo implements IpProtocolInfo 
{
  private String name  = "Unkown" ;
  private int    minor = 0 ;
  private int    major = 0 ;
  private String [] hosts  = null ;
  private String gsiftpUrl;
  private int    port  = 0 ;
  private long   transferTime     = 0 ;
  private long   bytesTransferred = 0 ;
  private int    sessionId        = 0 ;
  private String initiatorCellName;
  private String initiatorCellDomain;
  private long id;
  private int bufferSize = 0;
  private int tcpBufferSize = 0;
  
  private static final long serialVersionUID = -8861384829188018580L;
  
  public DCapClientProtocolInfo(String protocol, 
    int major, 
    int minor, 
    String[] hosts, 
    String initiatorCellName, 
    String initiatorCellDomain, 
    long id, 
    int bufferSize, 
    int tcpBufferSize)
  {
    this.name  = protocol ;
    this.minor = minor ;
    this.major = major ; 
    this.hosts = hosts ;
    this.port  = port ;
    this.initiatorCellName = initiatorCellName;
    this.initiatorCellDomain = initiatorCellDomain;
    this.id = id;
    this.bufferSize =bufferSize;
    this.tcpBufferSize = tcpBufferSize;
     
  }
  
  public String getGsiftpUrl()
  {
      return gsiftpUrl;
  }
  public int getBufferSize()
  {
      return bufferSize;
  }
   //
  //  the ProtocolInfo interface
  //
  public String getProtocol()
  { 
      return name ; 
  }
  
  public int    getMinorVersion()
  { 
    return minor ; 
  }
  
  public int    getMajorVersion()
  { 
    return major ; 
  }
  
  public String getVersionString()
  {
    return name+"-"+major+"."+minor ;
  }
  
  //
  // and the private stuff
  //
  public int    getPort()
  {
      return port ; 
  }
  public String [] getHosts()
  { 
      return hosts ; 
  }
  
  
  public String toString()
  {  
    StringBuffer sb = new StringBuffer() ;
    sb.append(getVersionString()) ;
    for(int i = 0 ; i < hosts.length ; i++ )
    {
      sb.append(',').append(hosts[i]) ;
    }
    sb.append(':').append(port) ;
         
    return sb.toString() ; 
  }
   
  /** Getter for property gsiftpTranferManagerName.
   * @return Value of property gsiftpTranferManagerName.
   */
  public java.lang.String getInitiatorCellName() {
      return initiatorCellName;
  }
  
  /** Getter for property gsiftpTranferManagerDomain.
   * @return Value of property gsiftpTranferManagerDomain.
   */
  public java.lang.String getInitiatorCellDomain() {
      return initiatorCellDomain;
  }
  
  /** Getter for property id.
   * @return Value of property id.
   */
  public long getId() {
      return id;
  }
  
  
  /** Getter for property tcpBufferSize.
   * @return Value of property tcpBufferSize.
   */
  public int getTcpBufferSize() {
      return tcpBufferSize;
  }
  
  /** Setter for property tcpBufferSize.
   * @param tcpBufferSize New value of property tcpBufferSize.
   */
  public void setTcpBufferSize(int tcpBufferSize) {
      this.tcpBufferSize = tcpBufferSize;
  }

  public boolean isFileCheckRequired() {
      return true;
  }
  
}



