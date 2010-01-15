package dmg.cells.nucleus ;
import  dmg.util.* ;
import  dmg.cells.network.PingMessage ;
import  java.util.*;
import  java.io.* ;
import  java.net.* ;
import  java.lang.reflect. * ;

/**
  *
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
public class      CellShell
       extends    CommandInterpreter
       implements Replaceable, ClassDataProvider
{
   private final CellNucleus  _nucleus ;
   private StringBuilder _contextString  = null ;
   private String       _contextName    = null ;
   private String       _contextDelimiter = null ;
   private StringBuilder _envString      = null ;
   private String       _envName        = null ;
   private String       _envDelimiter   = null ;
   private int          _helpMode       = 1 ;
   private int          _errorCode      = 0 ;
   private String       _errorMsg       = null ;
   private String       _doOnExit       = null ;
   private final Map<String, Object> _environment = CollectionFactory.newConcurrentHashMap();
   private final ClassLoaderFactory _classLoaderFactory  = new ClassLoaderFactory() ;
   private CommandInterpreter _externalInterpreter = null  ;
   private String             _classProvider       = null ;
   private List<String>       _argumentVector      = new Vector<String>() ;

   public CellShell( CellNucleus nucleus ){
      _nucleus = nucleus ;
      try {
         objectCommand( "exec context shellProfile" ) ;
      } catch (CommandExitException e) {
      }
   }
   public String getReplacement( String name ){
      Object o = getDictionaryEntry( name ) ;
      return o == null ? null : o.toString() ;

   }
   private static long __sequenceNumber = 1000000L ;
   private static synchronized long nextSequenceNumber(){
      return __sequenceNumber ++ ;
   }
   public Object getDictionaryEntry( String name ){
      if( name.equals( "rc" ) ){
         return ""+_errorCode ;
      }else if( name.equals( "rmsg" ) ){
         return (_errorMsg==null?"(0)":_errorMsg) ;
      }else if( name.equals( "thisDomain" ) ){
         return _nucleus.getCellDomainName()  ;
      }else if( name.equals( "thisCell" ) ){
         return _nucleus.getCellName()  ;
      }else if( name.equals( "nextSequenceNumber" ) ){
         return ""+nextSequenceNumber() ;
      }else if( name.equals( "thisHostname" ) ){
         try{
           String xname = InetAddress.getLocalHost().getHostName() ;
           return new StringTokenizer( xname , "." ).nextToken() ;
         } catch (UnknownHostException e) {
           return "UnknownHostname";
         }
      }else if( name.equals( "thisFqHostname" ) ){
         try{
           return InetAddress.getLocalHost().getCanonicalHostName() ;
         } catch (UnknownHostException e) {
           return "UnknownHostname";
         }
      }else {
         try {
            int position = Integer.parseInt(name);
            if (position >= 0 && position < _argumentVector.size()) {
                Object o = _argumentVector.get(position);
                if (o == null)
                    throw new IllegalArgumentException("");
                return o;
            }
         } catch (NumberFormatException e) {
         }
         Object o = _environment.get(name);
         if (o == null)
             o = _nucleus.getDomainContext().get(name);
         return o;
      }
   }
   private String prepareCommand( String string ){
      //
      // replace the variables ${...}
      //
      String str = Formats.replaceKeywords( string , this ) ;

      if( _contextString != null  ){
         //
         // are we in the define context ...
         //
         if( ( str.length()   > 0          ) &&
             ( str.equals(_contextDelimiter)    ) ){

             _nucleus.getDomainContext().
                      put( _contextName , _contextString.toString() ) ;
             _contextString = null ;
             return null  ;

         }
         _contextString.append( str ).append("\n");
         return null ;
      }else if( _envString != null  ){
         //
         // are we in the define environment
         //
         if( ( str.length()    > 0         ) &&
             ( str.equals(_envDelimiter)   ) ){

             _environment.put( _envName , _envString.toString() ) ;
             _envString = null ;
             return null  ;

         }
         _envString.append( str ).append("\n");
         return null ;
      }
      return str ;
   }
   public Object objectCommand2( String strin ) throws CommandExitException {
      String str = null ;
      if( ( str = prepareCommand( strin ) ) == null )return "" ;
      try{

         Object o = null ;
         if( _externalInterpreter != null ){
            o =  _externalInterpreter.command( new Args( str ) ) ;
         }else{
            o =  command( new Args( str ) ) ;
         }
         _errorCode = 0 ;
         _errorMsg  = null ;
         if( o == null )return "" ;
         return o ;
      }catch( CommandExitException cee ){
         throw cee ;
      }catch( CommandException ce ){
         _errorCode = ce.getErrorCode() ;
         _errorMsg  = ce.getErrorMessage() ;

         if( _doOnExit != null ){
             if (_doOnExit.equals("shutdown")) {
                 throw new CommandExitException(ce.toString(), 666, ce);
             } else {
                 throw new CommandExitException(ce.getErrorMessage(),
                                                ce.getErrorCode(), ce);
             }
         }
         return ce ;
      }
   }
   public Object objectCommand( String strin ) throws CommandExitException {
      String str = null ;
      if( ( str = prepareCommand( strin ) ) == null )return "" ;
      try{

         Object o = null ;
         if( _externalInterpreter != null ){
            o =  _externalInterpreter.command( new Args( str ) ) ;
         }else{
            o =  command( new Args( str ) ) ;
         }
         _errorCode = 0 ;
         _errorMsg  = null ;
         if( o == null )return "" ;
         return o ;
      }catch( CommandException ce ){
         _errorCode = ce.getErrorCode() ;
         _errorMsg  = ce.getErrorMessage() ;

         if( _doOnExit != null ){
            if( _doOnExit.equals( "shutdown" ) )
               throw new CommandExitException( ce.toString() , 666 ) ;

            else
               throw new CommandExitException( ce.getErrorMessage() ,
                                               ce.getErrorCode()   ) ;

         }
         if( ce instanceof CommandSyntaxException ){
            CommandSyntaxException cse = (CommandSyntaxException)ce ;

            StringBuilder sb = new StringBuilder() ;
            sb.append( "Syntax Error : " ).
               append( cse.getMessage() ) ;
            if( _helpMode == 1 ){
               sb.append( "\nUse 'help' for more information\n" ) ;
            }else if( _helpMode == 2 ){
               String help = cse.getHelpText() ;
               if( help != null )
                  sb.append( "\n").append( help ).append("\n") ;
            }
            return sb.toString() ;
         }else if( ce instanceof CommandExitException ){
            if( _externalInterpreter != null ){
               _externalInterpreter = null ;
               return "external shell exited ... " ;
            }else{
               throw (CommandExitException) ce ;
            }
         }else if( ce instanceof CommandThrowableException ){
            CommandThrowableException cte = (CommandThrowableException)ce ;
            StringBuilder sb = new StringBuilder() ;
            sb.append( cte.getMessage()+" -> " ) ;
            Throwable t = cte.getTargetException() ;
            sb.append( t.getClass().getName()+" : "+t.getMessage()+"\n" ) ;
            return sb.toString() ;
         }else if( ce instanceof CommandPanicException ){
            CommandPanicException cpe = (CommandPanicException)ce ;
            StringBuilder sb = new StringBuilder() ;
            sb.append( "Panic : "+cpe.getMessage()+"\n" ) ;
            Throwable t = cpe.getTargetException() ;
            sb.append( t.getClass().getName()+" : "+t.getMessage()+"\n" ) ;
            return sb.toString() ;
         }else{
            return "CommandException  :"+ce.getMessage() ;
         }
      }
   }

   @Override
public String command( String c ) throws CommandExitException {
      StringTokenizer st = new StringTokenizer( c , "\n" ) ;
      StringBuilder    sb = new StringBuilder();
      for( ; st.hasMoreTokens() ; ){
         sb.append( commandLine( st.nextToken() ) ) ;
      }
      return sb.toString() ;
   }
   private String commandLine( String c ) throws CommandExitException {
      if( _contextString != null ){
         _contextString.append( c ).append("\n");
         return "" ;
      }else
         return super.command( c ) ;
   }
   public Object binCommand( String c ){
      Args args = new Args( c ) ;
      if( args.argc() == 0 )return "" ;
      String cs = args.argv(0) ;
      if( cs.equals( ".getroutes" ) ){
        return _nucleus.getRoutingList() ;
      }else if( cs.equals( ".getcelltunnelinfos" ) ){
        return  _nucleus.getCellTunnelInfos() ;
      }else if( cs.equals( ".getcellinfos" ) ){
         String []    list = _nucleus.getCellNames() ;
         CellInfo []  info = new CellInfo[list.length] ;
         for( int i = 0 ; i < list.length ; i ++ ){
            info[i] = _nucleus.getCellInfo( list[i] ) ;
         }
        return  info ;
      }else{
        return null ;
      }

   }
   ////////////////////////////////////////////////////////////
   //
   //  version
   //
   public String hh_version = "[<package>] ; package info of dmg/cells/nucleus" ;
   public Object ac_version_$_0_1( Args args ){
      Package p = Package.getPackage( args.argc() == 0 ? "dmg.cells.nucleus" : args.argv(0) );
      StringBuilder sb = new StringBuilder();
      if( p != null ){
          String tmp = p.getSpecificationTitle() ;
          sb.append("SpecificationTitle:   ").append(tmp==null?"(Unknown)":tmp).append("\n");
          tmp = p.getSpecificationVendor() ;
          sb.append("SpecificationVendor:  ").append(tmp==null?"(Unknown)":tmp).append("\n");
          tmp = p.getSpecificationVersion() ;
          sb.append("SpecificationVersion: ").append(tmp==null?"(Unknown)":tmp).append("\n");
      }else{
          sb.append("No version version found");
      }
      return sb.toString() ;

   }
   ////////////////////////////////////////////////////////////
   //
   //   getroutes, getcelltunnelinfos, getcellinfos
   //
   public Object ac_getroutes( Args args ){
       return _nucleus.getRoutingList() ;
   }
   public Object ac_getcelltunnelinfos( Args args ){
       return _nucleus.getCellTunnelInfos() ;
   }
   public Object ac_getcellinfo_$_1( Args args ) throws CommandException {
      CellInfo info = _nucleus.getCellInfo( args.argv(0) ) ;
      if( info == null )
         throw new CommandException( 68 , "not found : "+args.argv(0) ) ;

      return info ;
   }
   public Object ac_getcellinfos( Args args ){
       String []    list = _nucleus.getCellNames() ;
       CellInfo []  info = new CellInfo[list.length] ;
       for( int i = 0 ; i < list.length ; i ++ ){
          info[i] = _nucleus.getCellInfo( list[i] ) ;
       }
       return  info ;
   }
   public Object ac_getcontext_$_0_1( Args args ) throws CommandException {
      if( args.argc() == 0 ){
          return _nucleus.getDomainContext().keySet().toArray();
      }else{
        Object o = _nucleus.getDomainContext( args.argv(0) ) ;
        if( o == null )
          throw new CommandException( "Context not found : "+args.argv(0) ) ;
        return o ;
      }
   }
   ////////////////////////////////////////////////////////////
   //
   //   waitfor cell/domain/context
   //
   public String hh_waitfor=
       "context|cell|domain <objectName> [<domain>] [-i=<checkInterval>] [-wait=<maxTime>]" ;
   public String fh_waitfor =
       "waitfor [options]  context  <contextName> [<domainName]\n" +
       "waitfor [options]  cell     <cellPath>\n" +
       "waitfor [options]  domain   <domainName>\n"+
       "    Options : -i=<probeInterval   -wait=<maxWaitSeconds>\n" ;

   public String ac_waitfor_$_2_3( Args args ) throws CommandException{
      int waitTime = 0 ;
      int check    = 1 ;
      for( int i = 0 ; i < args.optc() ; i ++ ){
        if( args.optv(i).startsWith("-i=") )
           check = Integer.parseInt( args.optv(i).substring(3) ) ;
        else if( args.optv(i).startsWith("-wait=") )
           waitTime = Integer.parseInt( args.optv(i).substring(6) ) ;
      }
      if( waitTime < 0 )waitTime = 0 ;
      String what = args.argv(0) ;
      String name = args.argv(1) ;

      if( what.equals("cell" ) )
          return _waitForCell( name , waitTime , check , null ) ;
      else if( what.equals( "domain" ) )
          return _waitForCell( "System@"+name , waitTime ,check , null ) ;
      else if( what.equals( "context" ) ){
         if( args.argc() > 2 ){
            return _waitForCell( "System@"+args.argv(2) ,
                                 waitTime ,check , "test context "+name ) ;
         }else{
            return _waitForContext( name , waitTime ,check ) ;
         }
      }

      throw new CommandException( "Unknown Observable : "+what ) ;
   }
   private String _waitForContext( String contextName , int waitTime , int check )
           throws CommandException {


      if( check <= 0 )check = 1 ;
      long finish = System.currentTimeMillis() + ( waitTime * 1000 ) ;
      while( true ){
         Object o = _nucleus.getDomainContext( contextName ) ;
         if( o != null )break ;
         if( ( waitTime == 0 ) || ( finish > System.currentTimeMillis()  ) ){
            try{ Thread.sleep(((long)check)*1000) ; }
            catch( InterruptedException ie ){
               throw new
               CommandException( 2 , "Command Was interrupted" ) ;
            }
            continue ;
         }
         throw new
         CommandException( 1 , "Command Timed Out" ) ;
      }
      return "" ;
   }
   private String _waitForCell( String cellName ,
                               int waitTime , int check ,
                               String command  )
           throws CommandException {

      if( check <= 4 )check = 5 ;
      CellPath destination = new CellPath( cellName ) ;
      long finish = System.currentTimeMillis() + ( waitTime * 1000 ) ;
      CellMessage answer = null ;
      //
      // creating the message now and send it forever does not
      // allow time messurements.
      //
      CellMessage request  =
          new CellMessage( destination ,
                           (command == null ?
                                    (Object) new PingMessage() : (Object)command ) ) ;

      Object o = null ;
      boolean noRoute = false ;
      while( true ){
         try{
            noRoute = false ;
            answer = null ;
            _nucleus.esay( "waitForCell : Sending request" ) ;
            answer = _nucleus.sendAndWait( request , ((long)check)*1000 ) ;
            _nucleus.esay( "waitForCell : got "+answer ) ;
         }catch( NoRouteToCellException nrtce ){
            noRoute = true ;
         }catch( InterruptedException e ){
            throw new
            CommandException( 66 , "sendAndWait problem : "+e.toString() ) ;
         }
         if( ( answer != null ) &&
             ( ( o = answer.getMessageObject() ) != null ) &&
             ( ( o instanceof PingMessage ) || (o instanceof String) )
           )break ;

         if( ( waitTime == 0 ) ||
             ( finish > System.currentTimeMillis() )  ){

            //
            // not to waste cpu time, we should distinquish between
            // between timeout and NoRouteToCellException
            //
            if( ( ! noRoute ) && ( answer == null ) )continue ;
            //
            // this answer was to fast to try it again, so we wait
            //
            try{ Thread.sleep(((long)check)*1000) ; }
            catch( InterruptedException ie ){
               throw new
               CommandException( 2 , "Command Was interrupted" ) ;
            }
            continue ;
         }
         throw new
         CommandException( 1 , "Command Timed Out" ) ;
      }
      return "" ;
   }
   ////////////////////////////////////////////////////////////
   //
   //   set printout <cellname> <level>
   //
   public String hh_set_printout = "<cellname>|CellGlue <level>" ;
   public String fh_set_printout =
       "Syntax: set printout <cellname> <level(hex)>\n\n"+
       "Obsolete: Replaced by the log4j command set, see help in the\n" +
       "          System cell. The printout level now only controls the\n" +
       "          log level at which messages generated through the old\n" +
       "          logging system are logged to log4j.\n\n" +
       "  <cellname> \n"+
       "          Name of the target cell or 'CellGlue' for the kernel or\n"+
       "          'default' for the printout level of new cells.\n"+
       "  <level> Bitmask of the following fields:\n"+
       "            1 -> log cell messages at WARN when set\n"+
       "            2 -> log cell errors at ERROR when set\n"+
       "            4 -> log nucleus messages at WARN when set\n"+
       "            8 -> log nucleus error at ERROR when set\n"+
       "          If a field is not set, then the corresponding messages\n"+
       "          are logged at INFO level.\n";
   public String ac_set_printout_$_2( Args args ){
       String cellName = args.argv(0) ;
       String ls = args.argv(1) ;
       int level = 0 ;
       if( ls.equals( "errors" ) ){
          level = CellNucleus.PRINT_ERROR_CELL | CellNucleus.PRINT_ERROR_NUCLEUS ;
       }else if( ls.equals( "all" ) ){
          level = CellNucleus.PRINT_EVERYTHING ;
       }else if( ls.equals( "none" ) ){
          level = 0 ;
       }else{
          level = Integer.parseInt( args.argv(1) , 16 ) ;
       }
       _nucleus.setPrintoutLevel( cellName , level ) ;
       return "Obsolete, see help for details\n" ;
   }
   ////////////////////////////////////////////////////////////
   //
   //   route
   //
   public String fh_route =
          " Syntax : route      # show all routes\n"+
          "          route add|delete [options] <source> <destination>\n" ;

   public String ac_route_$_0( Args args ){
       return  _nucleus.getRoutingTable().toString() ;
   }
   public String hh_route_add = "-options <source> <destination>" ;
   public String fh_route_add = fh_route ;
   public String ac_route_add_$_1_2(Args args)
       throws IllegalArgumentException
   {
       _nucleus.routeAdd( new CellRoute( args ) );
       return "Done\n" ;
   }
   public String hh_route_delete = "-options <source> <destination>" ;
   public String fh_route_delete = fh_route ;
   public String ac_route_delete_$_1_2(Args args)
       throws IllegalArgumentException
   {
       _nucleus.routeDelete( new CellRoute( args ) );
       return "Done\n" ;
   }
   public String hh_route_find = "<address>" ;
   public String ac_route_find_$_1( Args args )
       throws IllegalArgumentException
   {
       CellAddressCore addr = new CellAddressCore( args.argv(0) ) ;
       CellRoute route = _nucleus.routeFind( addr );
       if( route != null )return  route.toString()+"\n" ;
       else return "No Route To cell : "+addr.toString()+"\n"  ;
   }
   ////////////////////////////////////////////////////////////
   //
   //   ps -af <cellname>
   //
   public String hh_ps = "[-f] [<cellName> ...]" ;
   public String fh_ps =
          " Syntax : ps [-f] [<cellName> ...]\n" +
          "          ps displays various attibutes of active cells\n"+
          "          or the full attributes of a particular cell\n" +
          "          Options :  -f   displays a one line comment if\n"+
          "                          <cellName> is specified, otherwise\n"+
          "                          all available informations (theads,...\n"+
          "                          will be shown\n" ;
   public String ac_ps_$_0_99( Args args ){
      StringBuilder sb = new StringBuilder() ;
      if( args.argc() == 0 ){
         sb.append( "  Cell List\n------------------\n" ) ;
         String [] list = _nucleus.getCellNames() ;
         if( args.optc() > 0 ){
            for( int i = 0 ; i < list.length ; i ++ ){
               CellInfo info = _nucleus.getCellInfo( list[i] ) ;
               if( info == null ){
                  sb.append( list[i] + " (defunc)\n" ) ;
               }else{
                  sb.append( info.toString() + "\n" ) ;
               }
            }
         }else{
            for( int i = 0 ; i < list.length ; i ++ )
               sb.append( list[i] + "\n" ) ;
         }

       }else{
         boolean full = ( args.optc() > 0 ) &&
                        ( args.optv(0).indexOf('f') > -1 ) ;

         for( int i = 0 ; i < args.argc() ; i++ ){
             String cellName = args.argv(i) ;
             CellInfo info   = _nucleus.getCellInfo( cellName ) ;
             if( info == null ){
                sb.append( cellName + " Not found\n" ) ;
                continue ;
             }
             if( full ){
                sb.append( "  -- Short Info about Cell "+cellName+" --\n" ) ;
                sb.append( info.toString() + "\n" ) ;
                CellVersion version = info.getCellVersion() ;
                if( version != null )
                sb.append( "  -- Version : ").append(version.toString()).append("\n") ;
                sb.append( "  -- Threads --\n" ) ;
                Thread [] threads = _nucleus.getThreads(cellName) ;
                for( int j = 0 ;
                     ( j < threads.length ) && ( threads[j] != null ) ; j++ ){
                    boolean isAlive = threads[j].isAlive() ;
                    sb.append( CellInfo.f(        threads[j].getName() , 20 ) +
                               CellInfo.f( ""+threads[j].getPriority() , 2  ) +
                               ( isAlive ? "  Alive" : "  Dead" ) +
                               "\n" ) ;
                }
                sb.append( "  -- Private Infos --\n" ) ;
             }
             sb.append( info.getPrivatInfo() + "\n" ) ;
          }
       }
       return sb.toString() ;

   }
   ////////////////////////////////////////////////////////////
   //
   //   kill
   //
   public String hh_kill= "<cellName>" ;
   public String fh_kill =
          " Syntax : kill <cellName>\n"+
          "          Starts the killl mechanism on the specified cell\n"+
          "         and removes it from the cell list\n" ;
   public String ac_kill_$_1( Args args )
       throws IllegalArgumentException
   {
      _nucleus.kill( args.argv(0) );
      return "\n" ;
   }
   ////////////////////////////////////////////////////////////
   //
   //   send [-w] <cellAddress> <message>
   //
   public String hh_send = "[-w] <cellAddress> <message>" ;
   public String fh_send =
          "  Syntax : send [options] <cellAddress> <message>\n"+
          "           Sends the message <message> to the specified\n"+
          "           <cellAddress>.\n"+
          "           -w        :  wait 10 second for the answer to arrive\n"+
          "           -nolocal  :  don't deliver locally\n"+
          "           -noremote :  don't deliver remotely\n" ;
   public String ac_send_$_2( Args args )
       throws IllegalArgumentException,
              InterruptedException,
              NoRouteToCellException
   {
      CellMessage msg = new CellMessage(
                                new CellPath( args.argv(0) ) ,
                                args.argv(1) ) ;
      boolean wait     = false ;
      boolean locally  = true ;
      boolean remotely = true ;
      for( int i = 0 ; i < args.optc() ; i++ ){
          if( args.optv(i).equals("-w" ) )wait = true ;
          else if( args .optv(i).equals("-nolocal" ) )locally = false ;
          else if( args .optv(i).equals("-noremote" ) )remotely = false ;
      }
      if( wait ){
          msg = _nucleus.sendAndWait( msg , locally , remotely , 10000 )  ;
          if( msg == null )return "Timeout ... \n";
          Object obj = msg.getMessageObject() ;
          if( obj == null )return msg.toString()+"\n" ;
          String output = obj.toString() ;
          if( output.charAt(output.length()-1) == '\n' )
            return output ;
          else
            return output+"\n" ;
      }else{
          _nucleus.sendMessage( msg , locally , remotely )  ;
          return "Msg UOID ="+msg.getUOID().toString()+"\n" ;
      }

   }
   ////////////////////////////////////////////////////////////
   //
   //   sleep
   //
   public String hh_sleep = "<secondsToSleep>" ;
   public String ac_sleep_$_1( Args args ) throws InterruptedException {
      int s = Integer.valueOf( args.argv(0) ).intValue() ;
      Thread.sleep( s*1000) ;
      return "Ready\n" ;

   }
   ////////////////////////////////////////////////////////////
   //
   //   ping
   //
   public String hh_ping = "<destinationCell>  [<packetSize>] [-count=numOfPackets]" ;
   public String ac_ping_$_1_2( Args args )
       throws NoRouteToCellException
   {
      String countString = args.getOpt("count") ;
      int count = 1 ;
      int size  = 0 ;
      if( countString != null ){
         try{
            count = Integer.parseInt(countString) ;
         }catch(NumberFormatException ee){ /* ignore bad values */ }
      }
      if( args.argc() > 1 ){
         try{
           size = Integer.parseInt(args.argv(1)) ;
         }catch(NumberFormatException ee){ /* ignore bad values */ }
      }
      CellPath path = new CellPath( args.argv(0) ) ;
      for( int i = 0 ; i < count ; i ++ )
         _nucleus.sendMessage(new CellMessage(path,new PingMessage(size)))  ;
//      return "Msg UOID ="+msg.getUOID().toString()+"\n" ;
      return "Done\n" ;
   }
   ////////////////////////////////////////////////////////////
   //
   //   create
   //
    public String hh_create = "<cellClass> <cellName> [<Arguments>]";
    public String ac_create_$_2_3(Args args)
        throws Throwable
    {
        try {
            if( ( args.optc() > 0 ) && ( args.optv(0).equals("-c") ) ){
                String [] argClasses = new String[1] ;
                Object [] argObjects = new Object[1] ;

                argClasses[0] = "java.lang.String" ;
                argObjects[0] = args.argc()>2?args.argv(2):"" ;

                Cell cell = (Cell)_nucleus.createNewCell(args.argv(0),
                                                         args.argv(1),
                                                         argClasses,
                                                         argObjects);
                return "created : "+cell.toString() ;
            }else{
                Cell cell = _nucleus.createNewCell(args.argv(0),
                                                   args.argv(1),
                                                   args.argc()>2?args.argv(2):"",
                                                   true);
                return "created : "+cell.toString() ;
            }
        } catch (InvocationTargetException e) {
            throw e.getTargetException();
        }
    }
   ////////////////////////////////////////////////////////////
   //
   //   domain class loader routines
   //
   public String fh_set_classloader =
      "  set classloader <packageSelection> <provider>\n"+
      "     <packageSelection> : e.g. java.lang.*\n"+
      "     <provider>         :   \n"+
      "          cell:class@domain \n"+
      "          dir:/../.../../   \n"+
      "          system, none\n" ;

   public String hh_set_classloader = "<packageSelection> <provider>" ;
   public String ac_set_classloader_$_2( Args args ){
      _nucleus.setClassProvider( args.argv(0) , args.argv(1) ) ;
      return "" ;
   }
   public String ac_show_classloader( Args args ){
      String [] [] out =  _nucleus.getClassProviders() ;
      StringBuilder sb = new StringBuilder() ;
      for( int j = 0 ; j < out.length ; j++ )
         sb.append( Formats.field( out[j][0] , 20 , Formats.LEFT ) ).
            append( out[j][1] ).
            append( "\n" ) ;
      return sb.toString() ;
   }
   ////////////////////////////////////////////////////////////
   //
   //   private class loader routines
   //
   public String hh_load_cellprinter = "<cellprinterClassName> # Obsolete" ;
   public String ac_load_cellprinter_$_1( Args args )
   {
       return "Obsolete; use log4j instead." ;
   }
   public String hh_load_interpreter = "<interpreterClassName>" ;
   public String ac_load_interpreter_$_1( Args args ) throws CommandException {
      Object o = getDictionaryEntry( "classProvider" ) ;

      if( ( o == null ) || ( ! ( o instanceof String ) ) )
        throw new CommandException( 34 , "<classProvider> not set, or not a String" ) ;

      Class  c             = null ;
      String className     = args.argv(0) ;
      String classProvider = (String) o ;
      String providerType  = null ;

      int pos = classProvider.indexOf( ':' ) ;
      if( pos < 0 ){
          providerType = "file" ;
      }else{
          providerType  = classProvider.substring( 0 , pos ) ;
          classProvider = classProvider.substring( pos+1 ) ;
      }

      if( providerType.equals( "file" ) ){
         File directory = new File( classProvider ) ;
         if( ! directory.isDirectory() )
           throw new CommandException( 34 , "<classDirectory> not a directory" ) ;

         if( (c = _classLoaderFactory.loadClass( className , directory )) == null )
            throw new
            CommandException( 35 , "class not found in <"+
                                   _classLoaderFactory+"> : "+className ) ;
      }else if( providerType.equals( "cell" ) ){
         _classProvider = classProvider ;
         if( (c = _classLoaderFactory.loadClass( className , this )) == null )
            throw new
            CommandException( 35 , "class not found in <"+
                                   _classLoaderFactory+"> : "+className ) ;
      }else{
         throw new CommandException( 37, "Unknown class provider type : "+providerType ) ;
      }
      //
      // try to find an constructor who knows what a _nucleus is.
      //
      Class [] paraList1 = { dmg.cells.nucleus.CellNucleus.class } ;
      Class [] paraList2 = { dmg.cells.nucleus.CellNucleus.class ,
                             dmg.cells.nucleus.CellShell.class   } ;
      Object      [] paras  ;
      Constructor    con ;
      Object         interObject ;
      StringBuilder answer = new StringBuilder();
      try{
         con         = c.getConstructor( paraList2 ) ;
         paras       = new Object[2] ;
         paras[0]    = _nucleus ;
         paras[1]    = this ;
         interObject = con.newInstance( paras ) ;
      }catch(Exception e0 ){
         answer.append( e0.toString() ).append( '\n' ) ;
         try{
            con         = c.getConstructor( paraList1 ) ;
            paras       = new Object[1] ;
            paras[0]    = _nucleus ;
            interObject = con.newInstance( paras ) ;
         }catch(Exception e1 ){
            answer.append( e1.toString() ).append( '\n' ) ;
            try{
               interObject = c.newInstance() ;
               if( interObject == null )
                  throw new CommandException( 36 , answer.toString() ) ;
            }catch(Throwable e2 ){
               answer.append( e2.toString() ).append( '\n' ) ;
               throw new CommandException( 36 , answer.toString() ) ;
            }
         }
      }
      _externalInterpreter = new CommandInterpreter( interObject ) ;
      return " !!! Your are now in a new Shell !!! " ;
   }
   public byte [] getClassData( String className ) throws IOException {
       _nucleus.say( "getClassData("+className+") send to classProvider" ) ;
       CellMessage answer = null ;
       try{
           answer = _nucleus.sendAndWait(
                           new CellMessage(
                                 new CellPath( _classProvider ) ,
                                 "getclass "+className
                               ) ,
                           4000
                       ) ;
      }catch( InterruptedException e ){
         _nucleus.say( "getClassData Exception : "+e ) ;
         return null ;
      }catch( NoRouteToCellException e ){
         _nucleus.say( "getClassData Exception : "+e ) ;
         return null ;
      }
      if( answer == null ){
         _nucleus.say( "getClassData sendAndWait timed out" ) ;
         return null ;
      }
      Object answerObject = answer.getMessageObject() ;
      if( answerObject == null )return null ;

      if( ! ( answerObject instanceof byte [] ) ){
          _nucleus.say( "getClassData sendAndWait got : "+answerObject.toString() ) ;
          return null ;
      }

      return (byte [] )answerObject ;

   }
   ////////////////////////////////////////////////////////////
   //
   //   this and that
   //
   public String ac_dumpHelp( Args args ){
       dumpCommands() ;
       return "" ;
   }
   public String hh_onerror = "shutdown|exit|continue" ;
   public String ac_onerror_$_1( Args args ){
      if( args.argv(0).equals( "continue" ) )_doOnExit = null ;
      else _doOnExit = args.argv(0) ;
      return "" ;
   }
   public String ac_show_onexit( Args args ){
      return _doOnExit != null ? _doOnExit : "" ;
   }
   public String hh_say = "<things to echo ...> [-level=<level>]" ;
   public String fh_say =
                  "<things to echo ...> [-level=<level>]\n"+
                  " Levels :\n" +
                  "   say,esay,fsay\n"+
                  "   PRINT_CELL          =    1\n" +
                  "   PRINT_ERROR_CELL    =    2\n" +
                  "   PRINT_NUCLEUS       =    4\n" +
                  "   PRINT_ERROR_NUCLEUS =    8\n" +
                  "   PRINT_FATAL         = 0x10" ;

   public String ac_say_$_1_99( Args args )
   {
      StringBuilder sb = new StringBuilder() ;

      for( int i = 0 ; i < args.argc() ; i++ )
          sb.append( args.argv(i) ).append(' ') ;

      String msg = sb.toString() ;

      String levelString = args.getOpt("level") ;

      if( ( levelString != null ) && ( levelString.length() > 0 ) ){
          if( levelString.equals("say") ){
             _nucleus.say(msg) ;
          }else if( levelString.equals("esay") ){
             _nucleus.esay(msg) ;
          }else if( levelString.equals("fsay") ){
             _nucleus.fsay(msg) ;
          }else{
             try {
                _nucleus.say(Integer.parseInt(levelString), msg);
             } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Illegal Level string: " + levelString);
             }
          }
       }
      return msg ;
   }
   public String hh_echo = "<things to echo ...>" ;
   public String ac_echo_$_1_99( Args args ){
      StringBuilder sb = new StringBuilder() ;
      for( int i = 0 ; i < args.argc() ; i++ )
          sb.append( args.argv(i) ).append(' ') ;
      return sb.toString() ;
   }
   public String hh_show_error = "   # shows last errorCode and Message ";
   public String ac_show_error( Args args ){
     if( _errorCode == 0 )return "No Error found" ;
     return "errorCode="+_errorCode+"; Msg = "+
           (_errorMsg==null?"None":_errorMsg) ;
   }
   public String hh_set_helpmode = "none|full" ;
   public String ac_set_helpmode_$_1( Args args ) throws CommandException {
      String mode = args.argv(0) ;
      if( mode.equals( "none" ) )_helpMode = 0 ;
      else if( mode.equals( "full" ) )_helpMode = 2 ;
      else
        throw new CommandException( 22 ,"Illegal Help Mode : "+mode ) ;
      return "" ;
   }
   public String ac_id( Args args ){
      return _nucleus.getCellDomainName()+"\n" ;
   }
   ////////////////////////////////////////////////////////////
   //
   // setting the context/environment
   //
   public Object ac_set_context_$_2( CommandRequestable request )
          throws CommandException {

        Map<String,Object> dict = _nucleus.getDomainContext() ;
        Object contextName = request.getArgv(0) ;
        if( ! ( contextName instanceof String ) )
           throw new CommandException( 67 , "ContextName not a string" ) ;

        dict.put((String) contextName , request.getArgv(1) ) ;

        Object o = dict.get( contextName ) ;
        if( o == null )
           throw new CommandException( 68 , "Setting of "+contextName+" failed");

        Object [] answer = new Object[2] ;
        answer[0] = contextName ;
        answer[1] = o ;

        return answer ;

   }
   public String fh_check =
      " check [-strong] <var1> [<var2> [] ... ]\n"+
      "        checks if all of the specified variables are set.\n"+
      "        Returns an error it not.\n"+
      "        The -strong option requires that all variables must not be\n"+
      "        the zero string and must not only contain blanks\n" ;

   public String hh_check = "[-strong] <var1> [<var2> [] ... ]" ;
   public String ac_check_$_1_99( Args args )throws CommandException {

      boolean strong = args.getOpt("strong") != null ;

      String varName = null ;
      Object value   = null ;
      for( int i= 0 ;i < args.argc() ; i++ ){
         varName = args.argv(i) ;
         if( ( value = _environment.get( varName ) ) == null )
               value = _nucleus.getDomainContext().get( varName)  ;
         if( value == null )
           throw new
           CommandException( 1 , "variable not define : "+varName ) ;

         if( strong ){
             String strValue = value.toString() ;
             if( strValue.trim().equals("") )
                throw new
                CommandException( 2 , "variable defined but empty : "+varName ) ;
         }
      }
      return "" ;

   }
   public String fh_import_context =
     "  import  context|env  [options] <variableName>\n" +
     "           options :\n"+
     "               -c                  : don't overwrite\n"+
     "               -source=env|context : only check the specifed\n"+
     "                                     source for the variableName\n"+
     "               -check=strong       : requires '=' sign\n"+
     "               -nr                 : don't run the variable resolver\n"+
     "\n"+
     "      The source is interpreted as a set of lines separated by\n"+
     "      newlines. Each line is assumed to contain a key value pair\n"+
     "      separated by the '=' sign.\n"+
     "      The context/environment variables are set according to\n"+
     "      the assignment.\n" ;
   public String fh_import_env = fh_import_context ;

   public String hh_import_context = "[-source=context|env] [-nr]"+
                                     "[-check[=strong]] "+
                                     "<contextVariableName>" ;
   public String hh_import_env     = "[-source=context|env] [-nr]"+
                                     "[-check[=strong]] "+
                                     "<environmentVariableName>" ;

   public String ac_import_context_$_1( Args args )throws CommandException {
       return imprt_dict( args , _nucleus.getDomainContext() ) ;
   }
   public String ac_import_env_$_1( Args args )throws CommandException {
       return imprt_dict( args , _environment ) ;
   }

    private String imprt_dict(Args args, Map<String,Object> dict)
        throws CommandException
    {
      String  varName        = args.argv(0) ;
      boolean opt_overwrite  = args.getOpt("c") == null ;
      String  check          = args.getOpt( "check" )  ;
      boolean checkSyntax    = check != null ;
      boolean resolve        = args.getOpt( "nr" ) == null ;

      String     src     = args.getOpt( "source" ) ;
      Map<String,Object> srcDict = src == null ? null  :
                           src.equals("env")     ? _environment :
                           src.equals("context") ? _nucleus.getDomainContext() :
                           null ;

      Object value = null ;
      if( srcDict == null ){
         if( ( value = _environment.get( varName ) ) == null )
         value = _nucleus.getDomainContext().get( varName)  ;
      }else{
         value = srcDict.get( varName ) ;
      }
      if( value == null )
         throw new
         CommandException( "variable >"+varName+"< not found" ) ;

      BufferedReader br = new BufferedReader(
                          new StringReader( value.toString() ) ) ;
      String line = null ;
      StringTokenizer st = null ;
      String key = null ;
      String val = null ;
      try{
         while( ( line = br.readLine() ) != null ){

            if( ( line.length()        == 0   ) ||
                ( line.charAt(0)       == '#' ) ||
                ( line.trim().length() == 0   )    )continue ;

            if( resolve )line = prepareCommand( line ) ;
            try{
               st  = new StringTokenizer( line , "=" ) ;
               key = st.nextToken() ;
               try{
                  val = st.nextToken() ;
               } catch (NoSuchElementException ee) {
                  if( checkSyntax && ( check != null && check.equals("strong") ) ){
                     throw new
                     CommandException( 1 , "Nothing assigned to : "+key ) ;
                  }else{
                     val = "" ;
                  }

               }

            } catch (CommandException e) {
               if( checkSyntax ){
                  throw new
                  CommandException( 2 , "Command syntax exception : "+e ) ;
               }else{
                  continue ;
               }
            }
            if( opt_overwrite ||
                ( dict.get( key ) == null ) )dict.put( key , val ) ;
         }
      }catch( IOException ioe ){
         if( checkSyntax )
         throw new
         CommandException( 3 , "Command syntax exception : "+ioe ) ;
      }
      return "" ;
   }
   public String fh_set_context =
      "set context|env  [options]  <variableName>  <value>\n"+
      "        options :\n"+
      "          -c   :  do not overwrite the variable if it's already set\n"+
      "          -s   :  run the value through the interpreter and\n"+
      "                  convert  '\\n' to a real newline" ;
   public String fh_set_env = fh_set_context ;
   public String hh_set_context = "[-c][-s] <contextName> <value>" ;
   public String hh_set_env     = "[-c][-s] <environmentName> <value>" ;
   public String ac_set_context_$_2( Args args )throws CommandException{
      return set_dict( args , _nucleus.getDomainContext() ) ;
   }
   public String ac_set_env_$_2( Args args )throws CommandException{
      return set_dict( args , _environment ) ;
   }
    private String set_dict(Args args, Map<String,Object> dict)
        throws CommandException
    {
      String name  = args.argv(0) ;
      String value = args.argv(1) ;
      boolean opt_overwrite   = args.getOpt("c") == null ;
      boolean opt_interpreter = args.getOpt("s") != null ;

      if( ( ! opt_overwrite ) && ( dict.get( name ) != null ) )
         throw new
         CommandEvaluationException ( 1 , "Variable "+name+" is already set and can't be overwritten due to '-c'" ) ;


      if( opt_interpreter ){
         final int I_IDLE = 0 ;
         final int I_BS   = 1 ;
         int state = I_IDLE ;
         StringBuilder sb = new StringBuilder();
         for( int i = 0 ; i < value.length() ; i++ ){
            char c = value.charAt(i) ;
            switch( state ){
               case I_IDLE :
                  if( c == '\\' ){
                     state = I_BS ;
                  }else{
                     sb.append( c ) ;
                  }
               break ;
               case I_BS :
                  if( c == 'n' ){
                     state = I_IDLE ;
                     sb.append( '\n' ) ;
                  }else{
                     sb.append( '\\' ) ;
                     sb.append( c ) ;
                  }
               break ;

            }
         }
         value = sb.toString() ;

      }
      dict.put( name , value ) ;
      return "" ;

   }
   ////////////////////////////////////////////////////////////
   //
   // unsetting the context/environment
   //
   public String hh_unset_context="<contextName>" ;
   public String hh_unset_env    ="<environmentName>" ;
   public String ac_unset_context_$_1( Args args )throws CommandException {
      return unset_dict( args , _nucleus.getDomainContext() ) ;
   }
   public String ac_unset_env_$_1( Args args )throws CommandException {
      return unset_dict( args , _environment ) ;
   }

    private String unset_dict(Args args, Map<String,Object> dict)
           throws CommandException
    {
      String name = args.argv(0) ;
      Object o = dict.remove( name ) ;
      if( o == null ){
         throw new
         CommandException ( "Not found : "+name ) ;
      }else{
         return name+"<"+o.getClass().getName()+"> removed\n" ;
      }
   }
   ////////////////////////////////////////////////////////////
   //
   // displaying the context/environment variables
   //
   public String hh_ls = "[-l] [-ll] [-e] [-list]" ;
   public String fh_ls =
     " ls [options]\n"+
     "        Prints context/environment\n"+
     "    Options\n"+
     "       -l adds class name\n"+
     "       -ll adds first 40 chars of content\n"+
     "       -e  list environment instead of context\n"+
     "       -list  prints simple list instead of formatted one\n"+
     "\n";
   public String hh_show_context = "[<contextName>]" ;
   public String hh_show_env     = "[<environmentName>]" ;
   public String hh_test_context = "[<contextName>]" ;
   public String hh_test_env     = "[<environmentName>]" ;
   public String ac_ls_$_0_1( Args args ) throws CommandException {
      return ls_dict( args , args.getOpt("e") != null  ?
                             _environment :
                             _nucleus.getDomainContext()  ) ;
   }
   public String ac_show_context_$_0_1( Args args ) throws CommandException {
      return show_dict( args , _nucleus.getDomainContext() ) ;
   }
   public String ac_show_env_$_0_1( Args args ) throws CommandException {
      return show_dict( args , _environment ) ;
   }
   public String ac_test_context_$_0_1( Args args ) throws CommandException {
      return test_dict( args , _nucleus.getDomainContext() ) ;
   }
   public String ac_test_env_$_0_1( Args args ) throws CommandException {
      return test_dict( args , _environment ) ;
   }
    private String test_dict(Args args, Map<String,Object> dict)
        throws CommandException
    {
      String name  = args.argv(0) ;
      if( dict.get( name ) == null ){
         throw new
         CommandException( 66 , "not found : "+name );
      }return "" ;
   }
    private String show_dict(Args args, Map<String,Object> dict)
        throws CommandException
    {
      StringBuilder sb = new StringBuilder();
      if( args.argc() == 0 ){
          for (Map.Entry<String,Object> e: dict.entrySet()) {
              String name = e.getKey();
              Object o = e.getValue();
            if( o instanceof String ){
               sb.append(name).append("=") ;
               String line = (String)o ;
               int len = line.length() ;
               len = len > 40 ? 40 : len ;
               for( int i = 0 ; i < len ; i++ )
                  sb.append( line.charAt(i) == '\n' ? '$' : line.charAt(i) ) ;
               if( len == 40 )sb.append("...\n") ;
               else sb.append("\n") ;
            }else
              sb.append( name+"=<"+o.getClass().getName()+">\n" ) ;
         }
      }else{
         String name  = args.argv(0) ;
         Object o = dict.get( name ) ;
         if( o == null )
          throw new
          CommandException( 23 , "Context name "+name+" not found" ) ;
         sb.append( o.toString() ) ;
      }
      return sb.toString() ;
   }
    private String ls_dict(Args args, Map<String,Object> dict)
        throws CommandException
    {
      StringBuilder sb = new StringBuilder();
      if( args.argc() == 0 ){
          int maxLength = 0 ;
          SortedSet<String> set = CollectionFactory.newTreeSet();

          for (String name: dict.keySet()) {
            maxLength = Math.max( maxLength , name.length() ) ;
            set.add(name);
          }
          boolean detail = args.getOpt("l") != null ;
          boolean moreDetail = args.getOpt("ll") != null ;
          if( moreDetail )detail =true ;
          boolean list   = args.getOpt("list") != null ;
          for( Iterator e = set.iterator() ; e.hasNext() ; ){
            String name = (String)e.next() ;
            sb.append(name) ;
            if( detail ){
                sb.append("   ") ;
                if( ! list ){
                    int diff = maxLength - name.length() ;
                    for( int i = 0 ; i < diff ; i++ )sb.append(".");
                }
                Object o = dict.get(name) ;
                sb.append("  ").append(o.getClass().getName()) ;
                if( moreDetail ){
                   sb.append("\n          ");
                   String line = o.toString() ;
                   int len = line.length() ;
                   len = len > 40 ? 40 : len ;
                   for( int i = 0 ; i < len ; i++ )
                      sb.append( line.charAt(i) == '\n' ? '$' : line.charAt(i) ) ;
                   if( len == 40 )sb.append("...") ;
                }
            }
            sb.append("\n");
         }
      }else{
          throw new
          CommandSyntaxException("Not yet supported");
      }
      return sb.toString() ;
   }

   public final static String fh_exec =
      "exec [<options>] <url> [<args>]\n" +
      "exec [<options>] context <contextName> [<args>]\n" +
      "exec [<options>] env <envName> [<args>]\n"+
      "\n"+
      "   Executes the content of an env or context variable or the\n" +
      "   resource identified by the URL.\n"+
      "    (the -run option will be the default in future releases)\n"+
      "     -shell : opens a new shell for the execution\n"+
      "     -run   : displays the output of the executed commands\n"+
      "     Option which can only be used together with -run\n"+
      "       -nooutput : discard the output of the executed commands\n"+
      "       okOptions :\n"+
      "          The ok options determine if or ifnot the command should\n"+
      "          be executed depending on the value of variables\n"+
      "       -loop=<variableContextName> : \n"+
      "          Executes the context/env for each line in <varContextName> as arg\n"+
      "       -ifok[=<varName>] : run the context/env ONLY if the \n"+
      "                           specified value of <varName> is '0'\n"+
      "                           The default <varName> is 'rc'\n"+
      "       -ifnotok[=<varName>]  : negation of -ifok\n\n" ;
   public final static String fh_exec_context = fh_exec;
   public final static String fh_exec_env = fh_exec;
   public final static String hh_exec_env =
       "-shell [-run [-ifok|-ifnotok]] <envName> [<args>]" ;

    public String ac_exec_$_1_99(Args args)
        throws CommandException
    {
        try {
            String url = args.argv(0);
            Reader reader = open(new URL(url));
            try {
                return args.getOpt("run") == null ?
                    execute_reader(url, reader , args ) :
                    run_reader(url, reader , args , null ) ;
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new CommandException(43 , e.getMessage());
        }
    }

   public String ac_exec_env_$_1_99( Args args ) throws CommandException {
      String env     = args.argv(0);
      Object o       = _environment.get( env ) ;
      if( o == null )
         throw new CommandException( 66 , "Environment not found : "+env );

      Reader reader  = new StringReader( o.toString() ) ;

      return args.getOpt("run") == null ?
          execute_reader(env, reader , args ) :
          run_reader(env, reader , args , null ) ;
   }

   public String hh_exec_context =
       "-shell -loop=<contextName> [-run [-ifok|-ifnotok]] <contextName> [<args>]" ;
   public String ac_exec_context_$_1_99( Args args ) throws CommandException {
      String loopName = args.getOpt("loop");
      Reader reader  = null ;
      String context = args.argv(0);
      if( loopName == null ){
         try{
            reader = _nucleus.getDomainContextReader(context) ;
         }catch(FileNotFoundException e ){
            throw new CommandException( 66 , "Context not found : "+context )  ;
         }
         return args.getOpt("run") == null ?
             execute_reader(context, reader , args ) :
             run_reader(context, reader , args , null ) ;
      }else{

           Reader loopReader = null;
           StringBuilder result = new StringBuilder();
			try {
				loopReader = _nucleus.getDomainContextReader(loopName);

				String line = null;

				BufferedReader bRead = null;
				try {
					bRead = new BufferedReader(loopReader);
					while ((line = bRead.readLine()) != null) {
						Args altArgs = new Args(line);
						try {
							reader = _nucleus.getDomainContextReader(context);
							result.append(run_reader(context, reader, args, altArgs))
									.append("\n");
						} catch (FileNotFoundException e) {
							throw new CommandException(66,
									"Context not found : " + context);
						}
					}
				} catch (IOException ioe) {
					result.append("Problem : ").append(ioe.toString()).append(
							"\n");
				} finally {
					if (bRead != null) try { bRead.close(); } catch (IOException eee) {}
				}

			} catch (FileNotFoundException e) {
				throw new CommandException(66, "Context not found : "+ loopName);
			} finally {
				if (loopReader != null) try {	loopReader.close();} catch (IOException eee) {}
			}

           return result.toString() ;
      }
   }

    private void println(Writer out, String s)
        throws IOException
    {
        out.append(s);
        if ((s.length() > 0) && (s.charAt(s.length() - 1) != '\n')) {
            out.append('\n');
        }
    }

    public void execute(String source, Reader in, Writer out, Writer err, Args args)
        throws CommandExitException, IOException
    {
        List<String> store = _argumentVector;
        try {
            _argumentVector  = new Vector<String>();
            for (int i = 0; i < args.argc(); i++) {
                _argumentVector.add(args.argv(i));
            }

            int no = 0;
            String line;
            StringBuilder sb = null;
            BufferedReader input = new BufferedReader(in);
            while ((line = input.readLine()) != null) {
                no = no + 1;

                /* Skip empty and comment lines.
                 */
                String s = line.trim();
                if (s.length() == 0 || s.charAt(0) == '#')
                    continue;

                /* Handle line continuation.
                 */
                int len = line.length();
                if (line.charAt(len - 1) == '\\') {
                    if (sb == null) {
                        sb = new StringBuilder();
                    }
                    sb.append(line.substring(0, len - 1)).append(' ');
                    continue;
                } else if (sb != null) {
                    sb.append(line);
                    line = sb.toString();
                    sb = null;
                }

                /* Execute command.
                 */
                Object answer = objectCommand2(line);

                /* CommandEvaluationException does not generate output
                 * since it is not really an error. Runtime exceptions
                 * other than IllegalArgumentException are
                 * logged. Other exceptions are printed to the error
                 * output.
                 */
                if (!(answer instanceof Throwable)) {
                    println(out, answer.toString());
                } else if (answer instanceof IllegalArgumentException) {
                    String msg =
                        String.format("%s: %d: Illegal argument (%s)",
                                      source, no,
                                      ((Exception) answer).getMessage());
                    println(err, msg);
                } else if (answer instanceof RuntimeException) {
                    _nucleus.esay((Throwable) answer);
                } else if (!(answer instanceof CommandEvaluationException)) {
                    String msg =
                        Exceptions.getMessageWithCauses((Throwable) answer);
                    println(err, String.format("%s: %d: Command failed (%s)",
                                               source, no, msg));
                }
            }
        } finally {
            _argumentVector = store;
        }
    }

    private String execute_reader(String source, Reader in, Args args)
        throws CommandException
    {
        try {
            StringWriter out = new StringWriter();
            CellShell shell  =
                (args.getOpt("shell") != null)
                ? new CellShell(_nucleus)
                : this;

            shell.execute(source, in, out, out, args);

            return (args.getOpt("output") == null) ? null : out.toString();
        } catch (IOException e) {
            throw new CommandExitException("I/O error: " + e.getMessage(), 11);
        }
    }

    private String run_reader(String source, Reader in, Args args, Args altArgs)
        throws CommandExitException
    {
        String var;
        if ((var = args.getOpt("ifok")) != null) {
            if (var.equals("")) {
                if (_errorCode != 0) {
                    return "";
                }
            } else {
                Object x = getDictionaryEntry(var) ;
                if ((x == null) || (!x.toString().equals("0"))) {
                    return "";
                }
            }
        }
        if ((var = args.getOpt("ifnotok")) != null) {
            if (var.equals("")) {
                if (_errorCode == 0) {
                    return "";
                }
            } else {
                Object x = getDictionaryEntry(var) ;
                if ((x != null) && (x.toString().equals("0"))) {
                    return "";
                }
            }
        }

        try {
            StringWriter out = new StringWriter();
            CellShell shell =
                (args.getOpt("shell") != null)
                ? new CellShell(_nucleus)
                : this ;

            shell.execute(source, in, out, out, (altArgs == null ? args : altArgs));

            return (args.getOpt("nooutput") != null) ? "" : out.toString();
        } catch (IOException e) {
            throw new CommandExitException("I/O error: " + e.getMessage(), 11);
        }
    }

   public String hh_eval = "upn expression" ;
   public String ac_eval_$_1_99( Args args )throws CommandException{
       Stack<String> v = new Stack<String>() ;
       for( int i = 0 ; i < args.argc() ; i++ ){

          if( args.argv(i).equals("==") ){
          //                   -------------
             Object right = v.pop() ;
             Object left  = v.pop() ;
             v.push(right.equals(left) ?"0" :"1") ;

          }else if( args.argv(i).equals("!=") ){
          //                   -------------------
             Object right = v.pop() ;
             Object left  = v.pop() ;
             v.push(right.equals(left)?"1":"0") ;

          }else if( args.argv(i).equals("&&") ){
          //                   -------------------
             Object right = v.pop() ;
             Object left  = v.pop() ;
             v.push(
                 right.equals("0")&&left.equals("0")?
                 "0":"1") ;

          }else if( args.argv(i).equals("||") ){
          //                   -------------------
             Object right = v.pop() ;
             Object left  = v.pop() ;
             v.push(
                 right.equals("0")||left.equals("0")?
                 "0":"1") ;

          }else if( args.argv(i).equals("!") ){
          //                   -------------------
             Object right = v.pop() ;
             v.push(right.equals("0")?"1":"0") ;

          }else{
             v.push( args.argv(i).trim() ) ;
          }

       }
       if( v.size() != 1 ) {
          throw new
          CommandException( 2 , "Stack position violation ("+v.size()+")" ) ;
       }

       String result = v.firstElement() ;
       if( result.equals("0") )return "" ;

       int rc = 0 ;
       try{
          rc = Integer.parseInt(result) ;
       }catch(NumberFormatException nfe){
          rc = 3 ;
       }

       throw new
       CommandEvaluationException( rc , "Eval Result : "+result ) ;

   }
   public String hh_define_context = "<contextName> [<delimiter>]" ;
   public String ac_define_context_$_1_2( Args args ){
       _contextName      = args.argv(0) ;
       _contextDelimiter = args.argc() > 1 ? args.argv(1) : "." ;
       _contextString    = new StringBuilder() ;
       return "" ;
   }
   public String hh_define_env = "<environmentName>" ;
   public String ac_define_env_$_1_2( Args args ){
       _envName      = args.argv(0) ;
       _envDelimiter = args.argc() > 1 ? args.argv(1) : "." ;
       _envString    = new StringBuilder();
       return "" ;
   }
   public String hh_load_context = "[-b] <contextName> <fileName>" ;
   public String ac_load_context_$_2( Args args ) throws CommandException {
      String name = args.argv(0) ;
      File   file = new File( args.argv(1) ) ;

      if( ! file.canRead()  )
         throw new CommandException( "File not found : "+args.argv(1) ) ;

      if( ( args.optc() != 0 ) && ( args.optv(0).equals("-b") ) ){
         FileInputStream in = null ;
         try{
            long fileLength = file.length() ;
            byte [] buffer = new byte[(int)fileLength] ;
            in = new FileInputStream( file ) ;
            in.read( buffer ) ;
            in.close() ;
            _nucleus.getDomainContext().put( name , buffer ) ;
         }catch( IOException ioe ){

            throw new CommandException( 11 ,
                       "Problem with file : "+file+" : "+ioe ) ;
         }finally{
        	 if(in != null) try{in.close();}catch(IOException eeee){}
         }
      }else{
         StringBuilder sb = new StringBuilder();
         BufferedReader reader = null ;
         String         line   = null ;
         try{
            reader = new BufferedReader( new FileReader( file ) ) ;
            while( ( line = reader.readLine() ) != null )
                sb.append( line ).append( "\n" ) ;
         }catch( IOException ioe ){

            throw new CommandException( 11 ,
                       "Problem with file : "+file+" : "+ioe ) ;
         }finally{
        	 if(reader != null) try{reader.close();}catch(IOException eeee){}
         }
         _nucleus.getDomainContext().put( name , sb.toString() ) ;
      }
      return "Loaded ... " ;
   }
   ////////////////////////////////////////////////////////////
   //
   // the incredible copy command
   //
   public String fh_copy =
      "   copy  <fromCellURL>  <toCellURL>\n"+
      "       <fromCellURL> : <extendedCellURL>\n"+
      "                        Protocols : env/context/cell/http/file/ftp\n"+
      "       <toCellURL>   : <env/context CellURL>\n"+
      "                        Protocols : env/context\n\n" +
      "       Protocols :\n"+
      "          env:<environmentVariable>\n"+
      "          context:<contextVariable>\n"+
      "          context://<cellPath>/<contextVaraible>\n"+
      "          cell://<cellPath>/<requestString>\n" ;

   public String hh_copy = "<fromCellURL> <toCellURL>" ;
   public String ac_copy_$_2( Args args ) throws CommandException {
      URI from;
      URI to;
      try {
         from = new URI(args.argv(0));
         to = new URI(args.argv(1));
      } catch (URISyntaxException e) {
         throw new CommandException(43, "Invalid URL: "+ e.toString());
      }
      if (from.equals(to)) {
         throw new CommandException(43, "Source and destination URL must not be the same");
      }

      String source;
      try {
          BufferedReader in = new BufferedReader(open(from.toURL()));
          try {
              String line;
              StringBuilder sb = new StringBuilder();
              while ((line = in.readLine()) != null) {
                  sb.append(line).append("\n");
              }
              source = sb.toString();
          } finally {
              in.close();
          }
      } catch (IOException e) {
          throw new CommandException(43, e.toString());
      }

      String scheme = to.getScheme();
      if (scheme == null) {
          scheme = "env";
      }

      String destination = to.getSchemeSpecificPart();
      if (destination == null) {
         throw new CommandException( 43 , "Destination missing");
      }

      if (scheme.equals("env")) {
          _environment.put(destination, source);
      } else if (scheme.equals("context")) {
          _nucleus.getDomainContext().put(destination, source);
      } else {
          throw new CommandException(43, "Unsupported scheme for destination:" + scheme);
      }
      return "" ;
   }

   ////////////////////////////////////////////////////////////
   //
   // ----------------------------------------------
   //
   public String hh_exit = "[<exitCode> [<exitMessage>]]" ;
   public String ac_exit_$_0_2( Args args ) throws CommandExitException {
       String msg = "" ;
       int    code = 0 ;
       if( args.argc() > 0 ){
         try{
            code = new Integer(args.argv(0)).intValue() ;
         }catch( Exception e ){
            code = 0 ;
         }
         if( args.argc() > 1 ){
            msg = args.argv(1) ;
         }
       }
       throw new CommandExitException( msg , code ) ;
   }

    private Reader open(URL url)
        throws IOException
    {
        Reader reader;
        URLConnection con = url.openConnection();
        if (con instanceof CellUrl.DomainUrlConnection) {
            CellUrl.DomainUrlConnection dc =
                (CellUrl.DomainUrlConnection)con;
            dc.setNucleus(_nucleus) ;
            dc.setEnvironment(_environment);
            return dc.getReader();
        } else {
            return new InputStreamReader(con.getInputStream());
        }
    }
}
