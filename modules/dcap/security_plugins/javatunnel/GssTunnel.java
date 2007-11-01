/*
 * $Id: GssTunnel.java,v 1.5.30.2 2006-10-11 09:49:19 tigran Exp $
 */

package javatunnel;

import java.net.*;
import java.io.*;
import org.ietf.jgss.*;


class GssTunnel extends TunnelConverter {
    
    // GSS Kerberos context and others
    private GSSManager _gManager = null;
    protected GSSContext _context = null;
    private GSSCredential _myCredential = null;
    private GSSCredential _userCredential = null;
    protected GSSName _userPrincipal = null;
    protected GSSName _myPrincipal = null;
    protected GSSName _peerPrincipal = null;
    private boolean _authDone = false;
    MessageProp _prop =  new MessageProp(true);
    private String _principalStr = null;
    private boolean _useChannelBinding = true;
    
    // quick hack for VOMS
    protected String _group = null;
    protected String _role = null;    
         
    protected GssTunnel() {}        
    
    // Creates a new instance of GssTunnel    
    public GssTunnel(String principalStr ) {
    	_principalStr   = principalStr;
    }
    
    public GssTunnel(String principalStr, boolean init) {


        if ( init) {
            try {
                Oid krb5Mechanism = new Oid("1.2.840.113554.1.2.2");

                _gManager = GSSManager.getInstance();
                _myPrincipal = _gManager.createName(principalStr, null);

                _myCredential = _gManager.createCredential(_myPrincipal,
                GSSCredential.INDEFINITE_LIFETIME,
                krb5Mechanism,
                GSSCredential.ACCEPT_ONLY);

                _context = _gManager.createContext(_myCredential);
            }
            catch( Exception e ) {
                e.printStackTrace();
            }
        }
        
   }
        
    
    
    public GssTunnel(String principalStr, String peerName) {
        
        try {
            Oid krb5Mechanism = new Oid("1.2.840.113554.1.2.2");
            
            _gManager = GSSManager.getInstance();
            _myPrincipal = _gManager.createName(principalStr, null);
            
            _myCredential = _gManager.createCredential(_myPrincipal,
            GSSCredential.DEFAULT_LIFETIME,
            krb5Mechanism,
            GSSCredential.INITIATE_ONLY);
            
            _peerPrincipal = _gManager.createName(peerName, null);
            
            _context = _gManager.createContext(_peerPrincipal,
            krb5Mechanism,
            _myCredential,
            GSSContext.DEFAULT_LIFETIME);
        }
        catch( Exception e ) {
            e.printStackTrace();
        }
        
    }
    
    
    // subclasses allowed to disable cannel binding
    protected void useChannelBinding(boolean use) {
    	_useChannelBinding = use;
    }
    
    public byte[] decode(InputStream in) throws java.io.IOException {
        byte[] retValue;
        
        retValue = super.decode(in);
        
        if(_authDone) {
            try {
                retValue = _context.unwrap(retValue, 0, retValue.length, _prop);
            } catch (Exception ge) {
                retValue =  null;
            }
        }
        
        return retValue;
    }
    
    public void encode(byte[] buf, int len, OutputStream out) throws java.io.IOException {
        byte[] nb = null;
        int nlen =0;
        
        
        if(_authDone) {
            try{
                nb = _context.wrap(buf, 0, len, _prop);
            } catch (GSSException ge) {
                ge.printStackTrace();
            }
            nlen = nb.length;
        }else{
            nb = buf;
            nlen = len;
        }
        super.encode(nb, nlen, out);
    }
    
    public boolean auth( InputStream in, OutputStream out, Object addon) {
        
        boolean established = false;
        
        try {
                        
            byte[] inToken = new byte[0];
            byte[] outToken = null;
            Socket socket = (Socket)addon;
            
            _context.requestMutualAuth(true);
            
            if( _useChannelBinding ) {
                 ChannelBinding cb = new ChannelBinding(socket.getInetAddress(), InetAddress.getLocalHost(), null);
            	_context.setChannelBinding(cb);
            }
            
            while(!established) {
                outToken = _context.initSecContext(inToken, 0, inToken.length);
                
                if( outToken != null) {
                    this.encode(outToken, outToken.length, out);
                }
                
                if( !_context.isEstablished() ) {
                    inToken = this.decode(in);
                }else {
                    established = true;
                }
                
            }
            
        } catch( Exception e) {
            e.printStackTrace();
        }
        
        _authDone = established ;
        return established;
    }
    
    
    public boolean verify( InputStream in, OutputStream out, Object addon) {
        
        try {
            
            byte[] inToken = null;
            byte[] outToken = null;
            Socket  socket = (Socket)addon;
            
            if(_useChannelBinding) {
            	ChannelBinding cb = new ChannelBinding(socket.getInetAddress(),   InetAddress.getLocalHost(), null);
            	_context.setChannelBinding(cb);
            }
            
            while(  !_context.isEstablished() ) {
                
                inToken = this.decode(in);
                
                outToken = _context.acceptSecContext(inToken, 0, inToken.length);
                
                if( outToken != null) {
                    this.encode(outToken, outToken.length, out);
                }
                
            }
            
        } catch( Exception e) {
            e.printStackTrace();
        }
        
        try {
            _userPrincipal = _context.getSrcName();
        } catch( Exception e) {
            e.printStackTrace();
            _userPrincipal = null;
        }
        
        _authDone = _context.isEstablished();
        return _authDone;
    }
    
    
    public String getUserPrincipal() {        
        return _userPrincipal == null ? "nobody@SOME.WHERE" : _userPrincipal.toString();
    }
    
    public Convertable makeCopy() {
        return new GssTunnel( _principalStr, true);
    }
    
    public String getTunnelPrincipal() {
        return _myPrincipal.toString();
    }
    
	public String getGroup() {
		return _group;
	}

	public String getRole() {
		return _role;
	}    
    
}
