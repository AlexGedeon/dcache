// $Id: LoginManager.java,v 1.46 2007-10-22 12:30:38 behrmann Exp $
//
package  dmg.cells.services.login ;

import java.lang.reflect.* ;
import java.net.* ;
import java.io.* ;
import java.nio.channels.*;
import java.util.*;

import org.apache.log4j.Logger;

import dmg.cells.nucleus.*;
import dmg.util.*;
import dmg.protocols.ssh.* ;
import dmg.protocols.telnet.* ;

/**
 **
  *
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  *
 */
public class       LoginManager
       extends     CellAdapter
       implements  UserValidatable {

  private final String       _cellName ;
  private final CellNucleus  _nucleus ;
  private final Args         _args ;
  private final ListenThread _listenThread ;
  private int          _connectionRequestCounter   = 0 ;
  private int          _connectionAcceptionCounter = 0 ;
  private int          _connectionDeniedCounter    = 0 ;
  private String       _locationManager   = null ;
  private int          _loginCounter = 0 , _loginFailures = 0 ;
  private boolean      _sending = false ;
  private Class        _loginClass        = Object.class ;
  private Constructor  _loginConstructor  = null ;
  private Constructor  _authConstructor   = null ;
  private Method       _loginPrintMethod  = null ;
  private int          _maxLogin          = -1 ;
  private final Map<String,Object>      _childHash   = new HashMap<String,Object>() ;

  /**
   * actually, _childCount have to be equal to _childHash.size(). But while
   * cells needs some time to die, _childHash contains cells which are in removing state,
   * while  _childCount shows active cells only.
   */
  private int          _childCount        = 0 ;
  private String       _authenticator     = null ;
  private KeepAliveThread _keepAlive      = null  ;

  private LoginBrokerHandler _loginBrokerHandler = null ;

  private static Logger _logSocketIO = Logger.getLogger("logger.dev.org.dcache.io.socket");

  private Class [][] _loginConSignature = {
    {  java.lang.String.class ,
       dmg.util.StreamEngine.class } ,
    {  java.lang.String.class  ,
       dmg.util.StreamEngine.class ,
       dmg.util.Args.class           }
  } ;

  private Class [] _authConSignature = {
     dmg.cells.nucleus.CellNucleus.class , dmg.util.Args.class
  } ;


  private  Class [] _loginPntSignature = { int.class     } ;
  private  int      _loginConType      = -1 ;

  private  String _protocol ;
  private  String _authClassName ;
  private  Class  _authClass ;
  /**
  *<pre>
  *   usage   &lt;listenPort&gt; &lt;loginCellClass&gt;
  *           [-prot=ssh|telnet|raw]
  *                    default : telnet
  *           [-auth=&lt;authenticationClass&gt;]
  *                    default : ssh    : dmg.cells.services.login.SshSAuth_A
  *                              telnet : dmg.cells.services.login.TelnetSAuth_A
  *                              raw    : none
  *
  *         all residual arguments and all options are sent to
  *         the &lt;loginCellClass&gt; :
  *            &lt;init&gt;(String name , StreamEngine engine , Args args )
  *
  *         and to the Authentication module (class)
  *
  *            &lt;init&gt;(CellNucleus nucleus , Args args )
  *
  *         Both get their own copy.
  *</pre>
  */
  public LoginManager( String name , String argString ) throws Exception {

      super( name , argString , false ) ;

      _cellName = name ;
      _nucleus  = getNucleus() ;
      _args     = getArgs() ;
      try{
         Args args = _args ;
         if( args.argc() < 2 )
           throw new
           IllegalArgumentException(
           "USAGE : ... <listenPort> <loginCellClass>"+
           " [-prot=ssh|telnet|raw] [-auth=<authCell>]"+
           " [-maxLogin=<n>|-1]"+
           " [-keepAlive=<seconds>]"+
           " [-acceptErrorWait=<msecs>]"+
           " [args givenToLoginClass]" ) ;
         //
         // get the protocol
         //
         _protocol = args.getOpt("prot") ;
         if( _protocol == null )_protocol = "telnet" ;

         if( ! ( _protocol.equals("ssh")     ||
                 _protocol.equals("telnet" ) ||
                 _protocol.equals("raw" )        ) )
                 throw new
                 IllegalArgumentException(
                 "Protocol must be telnet or ssh or raw" ) ;

         say( "Using Protocol : "+_protocol ) ;
         //
         // get the listen port.
         //
         int listenPort    = Integer.parseInt( args.argv(0) ) ;
         args.shift() ;
         //
         // which cell to start
         //
         if( args.argc() > 0 ){
            _loginClass = Class.forName( args.argv(0) ) ;
            say( "Using login class : "+_loginClass.getName() ) ;
            args.shift() ;
         }
         //
         // get the authentication
         //
         _authenticator = args.getOpt("authenticator") ;
         _authenticator = _authenticator == null ? "pam" : _authenticator ;

         if( ( _authClassName = args.getOpt("auth") ) == null ){
            if( _protocol.equals( "ssh" ) ){
               _authClass = dmg.cells.services.login.SshSAuth_A.class ;
            }else if( _protocol.equals( "raw" ) ){
               _authClass = null ;
            }else if( _protocol.equals( "telnet" ) ){
               _authClass = dmg.cells.services.login.TelnetSAuth_A.class ;
            }
            if( _authClass != null )
               say( "Using authentication Module : "+_authClass ) ;
         }else if( _authClassName.equals( "none" ) ){
//            _authClass = dmg.cells.services.login.NoneSAuth.class ;
         }else{
            say( "Using authentication Module : "+_authClassName ) ;
            _authClass = Class.forName(_authClassName) ;
         }
         if( _authClass != null ){
            _authConstructor = _authClass.getConstructor( _authConSignature ) ;
            say( "Using authentication Constructor : "+_authConstructor ) ;
         }else{
            _authConstructor = null ;
            say( "No authentication used" ) ;
         }
         try{
            _loginConstructor = _loginClass.getConstructor( _loginConSignature[1] ) ;
            _loginConType     = 1 ;
         }catch( NoSuchMethodException nsme ){
            _loginConstructor = _loginClass.getConstructor( _loginConSignature[0] ) ;
            _loginConType     = 0 ;
         }
         say( "Using constructor : "+_loginConstructor ) ;
         try{

            _loginPrintMethod = _loginClass.getMethod(
                                   "setPrintoutLevel" ,
                                   _loginPntSignature ) ;

         }catch( NoSuchMethodException pr ){
            say( "No setPrintoutLevel(int) found in "+_loginClass.getName() ) ;
            _loginPrintMethod = null ;
         }
         String maxLogin = args.getOpt("maxLogin") ;
         if( maxLogin != null ){
            try{
               _maxLogin = Integer.parseInt(maxLogin);
            }catch(NumberFormatException ee){/* bad values ignored */}
         }
         //
         //  using the LoginBroker ?
         //
         _loginBrokerHandler = new LoginBrokerHandler() ;
         addCommandListener( _loginBrokerHandler ) ;
         //
         // enforce 'maxLogin' if 'loginBroker' is defined
         //
         if( ( _loginBrokerHandler.isActive() ) &&
             ( _maxLogin < 0                  )    ) _maxLogin=100000 ;
         //
         if( _maxLogin < 0 ){
            say("MaxLogin feature disabled") ;
         }else{

            _nucleus.addCellEventListener( new LoginEventListener() ) ;

            say("Maximum Logins set to :"+_maxLogin ) ;
         }
         //
         // keep alive
         //
         String keepAliveValue = args.getOpt("keepAlive");
         long   keepAlive      = 0L ;
         try{
            keepAlive = keepAliveValue == null ? 0L :
                        Long.parseLong(keepAliveValue);
         }catch(NumberFormatException ee ){
            esay("KeepAlive value not valid : "+keepAliveValue ) ;
         }
         say("Keep Alive set to "+keepAlive+" seconds") ;
         keepAlive *= 1000L ;
         _keepAlive = new KeepAliveThread(keepAlive) ;
         //
         // get the location manager
         //
         _locationManager = args.getOpt("lm") ;
         //

         _listenThread  = new ListenThread( listenPort ) ;
         say( "Listening on port "+_listenThread.getListenPort() ) ;


         _nucleus.newThread( _listenThread , "listen" ).start() ;

         _nucleus.newThread( new LocationThread() , "Location" ).start() ;

         _nucleus.newThread( _keepAlive , "KeepAlive" ).start() ;

      }catch( Exception e ){
         esay( "LoginManger >"+getCellName()+"< got exception : "+e ) ;
         esay(e);
         start() ;
         kill() ;
         throw e ;
      }

      start() ;

  }
  @Override
public CellVersion getCellVersion(){
     try{

       Method m = _loginClass.getMethod( "getStaticCellVersion" , (Class[])null ) ;

       return (CellVersion)m.invoke( (Object)null , (Object[])null ) ;

     }catch(Exception ee ){
         return super.getCellVersion() ;
     }
  }
  public class LoginBrokerHandler implements Runnable {

     private String _loginBroker        = null ;
     private String _protocolFamily     = null ;
     private String _protocolVersion    = null ;
     private long   _brokerUpdateTime   = 5*60*1000 ;
     private double _brokerUpdateOffset = 0.1 ;
     private LoginBrokerInfo _info      = null ;
     private double _currentLoad        = 0.0 ;
     private LoginBrokerHandler(){

        _loginBroker = _args.getOpt( "loginBroker" ) ;
        if( _loginBroker == null )return;

        _protocolFamily    = _args.getOpt("protocolFamily" ) ;
        if( _protocolFamily == null )_protocolFamily = _protocol ;
        _protocolVersion = _args.getOpt("protocolVersion") ;
        if( _protocolVersion == null )_protocolVersion = "0.1" ;
        String tmp = _args.getOpt("brokerUpdateTime") ;
        try{
           _brokerUpdateTime = Long.parseLong(tmp) * 1000 ;
        }catch(NumberFormatException e ){/* bad values ignored */ }
        tmp = _args.getOpt("brokerUpdateOffset") ;
        if(tmp != null) {
            try{
               _brokerUpdateOffset = Double.parseDouble(tmp) ;
            }catch(NumberFormatException e ){/* bad values ignored */ }
        }

        _info = new LoginBrokerInfo(
                     _nucleus.getCellName() ,
                     _nucleus.getCellDomainName() ,
                     _protocolFamily ,
                     _protocolVersion ,
                     _loginClass.getName() ) ;

        _info.setUpdateTime( _brokerUpdateTime ) ;

        _nucleus.newThread( this , "loginBrokerHandler" ).start() ;

     }
     public void run(){
        try{
          synchronized(this){
             while( ! Thread.interrupted() ){
                try{
                   runUpdate() ;
                }catch(Exception ie){
                   esay("Login Broker Thread reports : "+ie);
                }
                wait( _brokerUpdateTime ) ;
             }
          }
        }catch( Exception io ){
          say( "Login Broker Thread terminated due to "+io ) ;
        }
     }
     public String hh_get_children = "[-binary]" ;
     public Object ac_get_children( Args args ){
        boolean binary = args.getOpt("binary") != null ;
        synchronized( _childHash ){
           if( binary ){
              String [] list = new String[_childHash.size()] ;
              list = _childHash.keySet().toArray(list);
              return new LoginManagerChildrenInfo( getCellName() , getCellDomainName(), list ) ;
           }else{
              StringBuilder sb = new StringBuilder() ;
              for(String child : _childHash.keySet() ){
                 sb.append(child).append("\n");
              }
              return sb.toString();
           }
        }
     }
     public String hh_lb_set_update = "<updateTime/sec>" ;
     public String ac_lb_set_update_$_1( Args args ){
        long update = Long.parseLong( args.argv(0) )*1000 ;
        if( update < 2000 )
           throw new
           IllegalArgumentException("Update time out of range") ;

        synchronized(this){
           _brokerUpdateTime = update ;
           _info.setUpdateTime(update) ;
           notifyAll() ;
        }
        return "" ;
     }
     private synchronized void runUpdate(){

        if( _listenThread == null ) return;

        InetAddress[] addresses = _listenThread.getInetAddress();

        if( (addresses == null) || ( addresses.length == 0 ) ) return;

        String[] hosts = new String[addresses.length];
        for( int i = 0; i < addresses.length; i++ ) {
            hosts[i] = addresses[i].getHostAddress();
        }

        _info.setHosts(hosts);
        _info.setPort(_listenThread.getListenPort());
        _info.setLoad(_currentLoad);
        try{
           sendMessage(new CellMessage(new CellPath(_loginBroker),_info));
//           say("Updated : "+_info);
        }catch(Exception ee){}
     }
     public void getInfo( PrintWriter pw ){
        if( _loginBroker == null ){
           pw.println( "    Login Broker : DISABLED" ) ;
           return ;
        }
        pw.println( "    LoginBroker      : "+_loginBroker ) ;
        pw.println( "    Protocol Family  : "+_protocolFamily ) ;
        pw.println( "    Protocol Version : "+_protocolVersion ) ;
        pw.println( "    Update Time      : "+(_brokerUpdateTime/1000)+" seconds" ) ;
        pw.println( "    Update Offset    : "+
                    ((int)(_brokerUpdateOffset*100.))+" %" ) ;

     }
     private boolean isActive(){ return _loginBroker != null ; }
     private void loadChanged( int children , int maxChildren ){
       if( _loginBroker == null )return ;
       synchronized( this ){
          _currentLoad = (double)children / (double) maxChildren ;
          if(  Math.abs( _info.getLoad() - _currentLoad ) > _brokerUpdateOffset ){
            notifyAll() ;
          }
       }
     }
  }
  private class LoginEventListener implements CellEventListener {
     public void cellCreated( CellEvent ce ) { /* forced by interface */  }
     public void cellDied( CellEvent ce ) {
        synchronized( _childHash ){
           String removedCell = ce.getSource().toString() ;
           if( ! removedCell.startsWith( getCellName() ) )return ;

       	/*
       	 *  while in some cases remove may be issued prior cell is inserted into _childHash
       	 *  following trick is used:
       	 *  if there is no mapping for this cell, we create a 'dead' mapping, which will
       	 *  allow following put to identify it as a 'dead' and remove it.
       	 *
       	 */

           Object newCell = _childHash.remove( removedCell ) ;
           if( newCell == null ) {
        	   // it's a dead cell, put it back
        	   _childHash.put(removedCell, new Object() );
        	   esay("LoginEventListener : removing DEAD cell: "+removedCell);
           }
           say("LoginEventListener : removing : "+removedCell);
           _childCount -- ;
           childrenCounterChanged() ;
        }
     }
     public void cellExported( CellEvent ce ) { /* forced by interface */ }
     public void routeAdded( CellEvent ce )   { /* forced by interface */ }
     public void routeDeleted( CellEvent ce ) { /* forced by interface */ }
  }
  //
  // the 'send to location manager thread'
  //
  private class LocationThread implements Runnable {
     public void run(){

        int listenPort = _listenThread.getListenPort() ;

        say("Sending 'listeningOn "+getCellName()+" "+listenPort+"'") ;
        _sending = true ;
        String dest = _locationManager;
        if( dest == null )return ;
        CellPath path = new CellPath( dest ) ;
        CellMessage msg =
           new CellMessage(
                 path ,
                 "listening on "+getCellName()+" "+listenPort ) ;

        for( int i = 0 ; ! Thread.interrupted() ; i++ ){
          say("Sending ("+i+") 'listening on "+getCellName()+" "+listenPort+"'") ;

          try{
             if( sendAndWait( msg , 5000 ) != null ){
                say("Portnumber successfully sent to "+dest ) ;
                _sending = false ;
                break ;
             }
             esay( "No reply from "+dest ) ;
          }catch( InterruptedException ie ){
             esay( "'send portnumber thread' interrupted");
             break ;
          }catch(Exception ee ){
             esay( "Problem sending portnumber "+ee ) ;
          }
          try{
             Thread.sleep(10000) ;
          }catch(InterruptedException ie ){
             esay( "'send portnumber thread' (sleep) interrupted");
             break ;

          }
        }
     }
  }
  private class KeepAliveThread implements Runnable {
     private long   _keepAlive = 0L ;
     private final Object _lock      = new Object() ;
     private KeepAliveThread( long keepAlive ){
        _keepAlive = keepAlive ;
     }
     public void run(){
        synchronized( _lock ){
          say("KeepAlive Thread started");
          while( ! Thread.interrupted() ){
             try{
                if( _keepAlive < 1 ){
                   _lock.wait() ;
                }else{
                   _lock.wait( _keepAlive ) ;
                }
             }catch(InterruptedException ie ){
                say("KeepAlive thread done (interrupted)");
                break ;
             }

             if( _keepAlive > 0 )
               try{
                  runKeepAlive();
               }catch(Throwable t ){
                  esay("runKeepAlive reported : "+t);
               }
          }

        }

     }
     private void setKeepAlive( long keepAlive ){
        synchronized( _lock ){
           _keepAlive = keepAlive ;
           say("Keep Alive value changed to "+_keepAlive);
           _lock.notifyAll() ;
        }
     }
     private long getKeepAlive(){
        return _keepAlive ;
     }
  }
  public String hh_set_keepalive = "<keepAliveValue/seconds>";
  public String ac_set_keepalive_$_1( Args args ){
     long keepAlive = Long.parseLong( args.argv(0) ) ;
     _keepAlive.setKeepAlive( keepAlive * 1000L ) ;
     return "keepAlive value set to "+keepAlive+" seconds" ;
  }

  public void runKeepAlive(){
     List<Object> list = null ;
     synchronized( _childHash ){
        list = new ArrayList<Object>( _childHash.values() ) ;
     }

     for( Object o : list ){

        if( ! ( o instanceof KeepAliveListener ) )continue ;
        try{
           ((KeepAliveListener)o).keepAlive() ;
        }catch(Throwable t ){
           esay("Problem reported by : "+o+" : "+t);
        }
     }
  }
  //
  // the cell implementation
  //
  @Override
public String toString(){
     return
        "p="+(_listenThread==null?"???":(""+_listenThread.getListenPort()))+
        ";c="+_loginClass.getName() ;
  }
  @Override
public void getInfo( PrintWriter pw ){
    pw.println( "  -- Login Manager $Revision: 1.46 $") ;
    pw.println( "  Listen Port    : "+_listenThread.getListenPort() ) ;
    pw.println( "  Login Class    : "+_loginClass ) ;
    pw.println( "  Protocol       : "+_protocol ) ;
    pw.println( "  NioChannel     : "+( _listenThread._serverSocket.getChannel() != null ) ) ;
    pw.println( "  Auth Class     : "+_authClass ) ;
    pw.println( "  Logins created : "+_loginCounter ) ;
    pw.println( "  Logins failed  : "+_loginFailures ) ;
    pw.println( "  Logins denied  : "+_connectionDeniedCounter ) ;
    pw.println( "  KeepAlive      : "+(_keepAlive.getKeepAlive()/1000L) ) ;

    if( _maxLogin > -1 )
    pw.println( "  Logins/max     : "+_childHash.size()+"("+_childCount+")/"+_maxLogin ) ;

    if( _locationManager != null )
    pw.println( "  Location Mgr   : "+_locationManager+
                " ("+(_sending?"Sending":"Informed")+")" ) ;

    if( _loginBrokerHandler != null ){
       pw.println( "  LoginBroker Info :" ) ;
       _loginBrokerHandler.getInfo( pw ) ;
    }
    return ;
  }
  public String hh_set_max_logins = "<maxNumberOfLogins>|-1" ;
  public String ac_set_max_logins_$_1( Args args )throws Exception {
      int n = Integer.parseInt( args.argv(0) ) ;
      if( ( n > -1 ) && ( _maxLogin < 0 ) )
         throw new
         IllegalArgumentException("Can't switch off maxLogin feature" ) ;
      if( ( n < 0 ) && ( _maxLogin > -1 ) )
         throw new
         IllegalArgumentException( "Can't switch on maxLogin feature" ) ;

      synchronized( _childHash ){
         _maxLogin = n ;
         childrenCounterChanged() ;
      }
      return "" ;
  }
  @Override
public void cleanUp(){
     say( "cleanUp requested by nucleus, closing listen socket" ) ;
     if( _listenThread != null )_listenThread.shutdown() ;
     say( "Bye Bye" ) ;
  }
  @Override
public void say( String str ){ pin( str ) ; super.say( str ) ; }
  @Override
public void esay( String str ){ pin( str ) ; super.esay( str ) ; }

  private class ListenThread implements Runnable {
     private int          _listenPort   = 0 ;
     private ServerSocket _serverSocket = null ;
     private boolean      _shutdown     = false ;
     private boolean      _active       = true ;
     private Thread       _this         = null ;
     private long         _acceptErrorTimeout = 0L ;
     private boolean      _isDedicated  = false;

     private ListenThread( int listenPort) throws Exception {
        _listenPort   = listenPort ;

        try{
           _acceptErrorTimeout = Long.parseLong(_args.getOpt("acceptErrorWait"));
        }catch(NumberFormatException ee ){ /* bad values ignored */};

        openPort() ;
     }
     private void openPort() throws Exception {

        String ssf = _args.getOpt("socketfactory") ;
        String local   = _args.getOpt("listen");

        if( ssf == null ){
           String context = (String)getDomainContext().get("niochannel");
           String channel = _args.getOpt("niochannel") ;
           channel = channel != null ? channel : context ;

           SocketAddress socketAddress = null;

           if ( (local == null ) || local.equals("*") || local.equals("")  ) {
               socketAddress =  new InetSocketAddress( _listenPort ) ;
           }else{
               socketAddress = new InetSocketAddress( InetAddress.getByName(local) , _listenPort ) ;
               _isDedicated = true;
           }

           _serverSocket =
              ( channel != null ) && ( channel.equals("") ||  channel.equals("true") )  ?
              ServerSocketChannel.open().socket() :
              new ServerSocket() ;

           _serverSocket.bind( socketAddress );
           _listenPort   = _serverSocket.getLocalPort() ;

        }else{
           StringTokenizer st = new StringTokenizer(ssf,",");
           List<String> list = new ArrayList<String>() ;
           while( st.hasMoreTokens() )list.add(st.nextToken());
           if( list.size() == 0 )
              throw new
              IllegalArgumentException( "Invalid Arguments for 'socketfactory'");

           Class []  constructorArgClassA = { java.lang.String[].class , java.util.Map.class } ;
           Class []  constructorArgClassB = { java.lang.String[].class } ;
           Class []        methodArgClass = { int.class } ;

           Class     ssfClass = Class.forName(list.remove(0));
           Object [] args     = null ;

           Constructor ssfConstructor = null ;
           try{
              ssfConstructor = ssfClass.getConstructor(constructorArgClassA) ;
              args = new Object[2] ;
              args[0] = list.toArray(new String[list.size()]);
              Map map = new HashMap((Map)getDomainContext()) ;
              map.put( "UserValidatable" , LoginManager.this ) ;
              args[1] = map ;
           }catch( Exception ee ){
              ssfConstructor = ssfClass.getConstructor(constructorArgClassB) ;
              args = new Object[1] ;
              args[0] = list.toArray(new String[list.size()]);
           }
           Object     obj = ssfConstructor.newInstance(args) ;

           Method meth = ssfClass.getMethod("createServerSocket",methodArgClass) ;
           _serverSocket = (ServerSocket)meth.invoke( obj ) ;

           if ( (local == null ) || local.equals("*") || local.equals("")  ) {
               _serverSocket.bind(new InetSocketAddress( _listenPort ) );
           }else{
               _serverSocket.bind(new InetSocketAddress(InetAddress.getByName(local), _listenPort ) );
               _isDedicated = true;
           }

           say("ListenThread : got serverSocket class : "+_serverSocket.getClass().getName());
        }

        if( _logSocketIO.isDebugEnabled() ) {
            _logSocketIO.debug("Socket BIND local = " + _serverSocket.getInetAddress() + ":" + _serverSocket.getLocalPort() );
        }
        say("Nio Socket Channel : "+(_serverSocket.getChannel()!=null));
     }
     public int getListenPort(){ return _listenPort ; }
     public InetAddress[] getInetAddress(){
         InetAddress[] addresses = null;
         if( _isDedicated ) {
             if( _serverSocket != null ) {
                 addresses = new InetAddress[1];
                 addresses[0] =  _serverSocket.getInetAddress() ;
             }
         }else{

             /**
              *  put all local Ip addresses, except loopback
              */

             try {
                 Enumeration<NetworkInterface> ifList = NetworkInterface.getNetworkInterfaces();

                 Vector<InetAddress> v = new Vector<InetAddress>();
                 while( ifList.hasMoreElements() ) {

                     NetworkInterface ne = ifList.nextElement();

                     Enumeration<InetAddress> ipList = ne.getInetAddresses();
                     while( ipList.hasMoreElements() ) {
                        InetAddress ia = ipList.nextElement();
                        // Currently we do not handle ipv6
                        if( ! (ia instanceof Inet4Address) ) continue;
                        if( ! ia.isLoopbackAddress() ) {
                            v.add( ia ) ;
                        }
                     }
                 }
                 addresses = v.toArray( new InetAddress[ v.size() ] );
             }catch(SocketException se_ignored) {}
         }

         return addresses;
     }

     public void run(){
         _this = Thread.currentThread() ;
         while( true ){
            Socket socket = null ;
            try{
               socket = _serverSocket.accept() ;
               socket.setKeepAlive(true);
            	if( _logSocketIO.isDebugEnabled() ) {
            		_logSocketIO.debug("Socket OPEN (ACCEPT) remote = " + socket.getInetAddress() + ":" + socket.getPort() +
            					" local = " +socket.getLocalAddress() + ":" + socket.getLocalPort() );
            	}
               say("Nio Channel (accept) : "+(socket.getChannel()!=null));


               _connectionRequestCounter ++ ;
               int currentChildHash = 0 ;
                synchronized( _childHash ){ currentChildHash = _childCount ; }
               say("New connection : "+currentChildHash);
               if ((_maxLogin > 0) && (currentChildHash > _maxLogin)) {
						_connectionDeniedCounter++;
						esay("Connection denied " + currentChildHash + " > "
								+ _maxLogin);
						_logSocketIO.warn("number of allowed logins exceeded.");
						new ShutdownEngine(socket);
						continue;
					}
               say( "Connection request from "+socket.getInetAddress() ) ;
                synchronized( _childHash ){ _childCount ++; }
               _nucleus.newThread(
                   new RunEngineThread(socket) ,
                   "ClinetThread-" + socket.getInetAddress() + ":" + socket.getPort()    ).start() ;

            }catch( InterruptedIOException ioe ){
               esay("Listen thread interrupted") ;
               try{ _serverSocket.close() ; }catch(Exception ee){}
               break ;
            }catch( IOException ioe ){
                if (_serverSocket.isClosed()) {
                    break;
                }

               esay( "Got an IO Exception ( closing server ) : "+ioe ) ;
               try{ _serverSocket.close() ; }catch(IOException ee){}
               if( _acceptErrorTimeout <= 0L )break ;
               esay( "Waiting "+_acceptErrorTimeout+" msecs");
               try{
                  Thread.sleep(_acceptErrorTimeout) ;
               }catch(InterruptedException ee ){
                  esay("Recovery halt interrupted");
                  break ;
               }
               esay( "Resuming listener");
               try{

                  openPort() ;

               }catch(Exception ee ){
                  esay( "openPort reported : "+ee ) ;
                  esay( "Waiting "+_acceptErrorTimeout+" msecs");
                  try{
                     Thread.sleep(_acceptErrorTimeout) ;
                  }catch(InterruptedException eee ){
                     esay("Recovery halt interrupted");
                     break ;
                  }
               }
            }

         }
         say( "Listen thread finished");
     }
     public class ShutdownEngine implements Runnable {
         private Socket _socket = null ;
         public ShutdownEngine( Socket socket ){
           _socket = socket ;
           _nucleus.newThread( this , "Shutdown" ).start() ;
         }
         public void run(){
           InputStream inputStream = null ;
           OutputStream outputStream = null ;
           try{
              inputStream  = _socket.getInputStream() ;
              outputStream = _socket.getOutputStream() ;
              outputStream.close() ;
              byte [] buffer = new byte[1024] ;
              /*
               * eat the outstanding date from socket and close it
               */
              while( inputStream.read(buffer,0,buffer.length) > 0 ) ;
              inputStream.close() ;
           }catch(Exception ee ){
              esay("Shutdown : "+ee.getMessage() ) ;
           }finally{
        	   try {
               	if( _logSocketIO.isDebugEnabled() ) {
            		_logSocketIO.debug("Socket CLOSE (ACCEPT) remote = " + _socket.getInetAddress() + ":" + _socket.getPort() +
            					" local = " +_socket.getLocalAddress() + ":" + _socket.getLocalPort() );
            	}
				_socket.close() ;
			} catch (IOException e) {
				// ignore
			}
           }

           say( "Shutdown : done");
         }
     }
     public synchronized void shutdown(){

        say("Listen thread shutdown requested") ;
        //
        // it is still hard to stop an Pending I/O call.
        //
        if( _shutdown || ( _serverSocket == null ) )return ;
        _shutdown = true ;

        try{
         	if( _logSocketIO.isDebugEnabled() ) {
        		_logSocketIO.debug("Socket SHUTDOWN local = " + _serverSocket.getInetAddress() + ":" + _serverSocket.getLocalPort() );
        	}
            _serverSocket.close() ; }
        catch(Exception ee){
            esay( "ServerSocket close : "+ee  ) ;
        }

        if (_serverSocket.getChannel() == null) {
            say("Using faked connect to shutdown listen port");
            try {
                new Socket("localhost", _listenPort).close();
            } catch (Exception e) {
                esay("ServerSocket faked connect : " + e.getMessage());
            }
        }

        _this.interrupt() ;

        say("Shutdown sequence done");
     }
     public synchronized void open(){

     }
     public synchronized void close(){

     }
  }
  private class RunEngineThread implements Runnable {
     private Socket _socket = null ;
     private RunEngineThread( Socket socket ){
        _socket = socket ;
     }
     public void run(){
       Thread t = Thread.currentThread() ;
       try{
          say( "acceptThread ("+t+"): creating protocol engine" ) ;

          Object [] argList = new Object[2] ;
          Object auth = null ;
          if( _authConstructor != null ){
             argList[0]  = _nucleus ;
             argList[1]  = getArgs().clone() ;
             auth        = _authConstructor.newInstance( argList ) ;
          }
          StreamEngine engine = null ;
          if( _protocol.equals( "ssh" ) ){
              engine = new SshStreamEngine( _socket , (SshServerAuthentication)auth ) ;
          }else if( _protocol.equals( "raw" ) ){
              engine = new DummyStreamEngine( _socket ) ;
          }else if( _protocol.equals( "telnet" ) ){
              engine = new TelnetStreamEngine( _socket , (TelnetServerAuthentication)auth ) ;
          }
          say( "acceptThread ("+t+"): connection created for user "+engine.getUserName() ) ;
          Object [] args ;
          //
          //
          String userName = engine.getUserName().getName() ;
          int p = userName.indexOf('@');

          if( p > -1 )userName = p == 0 ? "unknown" : userName.substring(0,p);

          if( _loginConType == 0 ){
             args =  new Object[2] ;
             args[0] = getCellName()+"-"+userName+"*" ;
             args[1] = engine ;
          }else{
             args =  new Object[3] ;
             args[0] = getCellName()+"-"+userName+"*" ;
             args[1] = engine ;
             args[2] = getArgs().clone() ;
          }

          Object cell = _loginConstructor.newInstance( args ) ;
          if( _loginPrintMethod != null ){
             try{
                Object [] a = new Object[1] ;
                a[0] = Integer.valueOf( _nucleus.getPrintoutLevel() ) ;
                _loginPrintMethod.invoke( cell , a ) ;
             }catch( Exception eee ){
                esay( "Can't setPritoutLevel of " +args[0] ) ;
             }
          }
          if( _maxLogin > -1 ){
             try{
                Method m = cell.getClass().getMethod( "getCellName" , new Class[0] ) ;
                String cellName = (String)m.invoke( cell , new Object[0] ) ;
                say("Invoked cell name : "+cellName ) ;
                synchronized( _childHash ){

                	/*
                     *  while cell may be already gone do following trick:
                     *  if put return an old cell, then it's a dead cell and we
                     *  have to remove it. Dead cell is inserted by cleanup procedure:
                     *  if a remove for non existing cells issued, then cells is dead, and
                     *  we put it into _childHash.
                     */

                   Object deadCell = _childHash.put(cellName,cell) ;
                      if(deadCell != null ) {
                         _childHash.remove(cellName);
                         esay("Cell died, removing " + cellName) ;
                      }

                   childrenCounterChanged() ;
                }
             }catch( Exception ee ){
                esay("Can't determine child name " + ee) ;
                esay(ee);
             }
          }
          _loginCounter ++ ;

       }catch( Exception e ){
          try{ _socket.close() ; }catch(IOException ee ){/* dead any way....*/}
          esay( "Exception in secure protocol : "+e ) ;
          esay(e);
          _loginFailures ++ ;
          synchronized( _childHash ){ _childCount -- ; }
       }


     }
  }
  private void childrenCounterChanged(){
      int children = _childHash.size() ;
      say( "New child count : "+children ) ;
      if( _loginBrokerHandler != null )
        _loginBrokerHandler.loadChanged( children , _maxLogin ) ;
  }
  public boolean validateUser( String userName , String password ){
     String [] request = new String[5] ;

     request[0] = "request" ;
     request[1] = userName ;
     request[2] = "check-password" ;
     request[3] = userName ;
     request[4] = password ;

     try{
        CellMessage msg = new CellMessage( new CellPath(_authenticator) ,
                                           request ) ;

        msg = sendAndWait( msg , 10000 ) ;
        if( msg == null )
           throw new
           Exception("Pam request timed out");

        Object [] r = (Object [])msg.getMessageObject() ;

        return ((Boolean)r[5]).booleanValue() ;

     }catch(Exception ee){
        esay(ee);
        return false ;
     }

  }
}
