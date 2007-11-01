package org.dcache.xrootd.protocol;


public interface XrootdProtocol {

    public final static byte      CLIENT_REQUEST_LEN = 24;
    public final static byte	CLIENT_HANDSHAKE_LEN = 20;
    public final static byte     SERVER_RESPONSE_LEN = 8;
    public final static byte           LOAD_BALANCER = 0;
    public final static byte             DATA_SERVER = 1;
    
    public final static byte[] HANDSHAKE_REQUEST = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 7, (byte) 220};
    public final static byte[] HANDSHAKE_RESPONSE_LOADBALANCER = {0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, LOAD_BALANCER};
    public final static byte[] HANDSHAKE_RESPONSE_DATASERVER = {0, 0, 0, 0, 0, 0, 0, 8, 0, 0, 0, 0, 0, 0, 0, DATA_SERVER};

    public final static int   kXR_ok       = 0;
    public final static int   kXR_authmore = 4002;
    public final static int   kXR_error    = 4003;
    public final static int   kXR_oksofar  = 4000;
    public final static int   kXR_wait     = 4005;
    public final static int   kXR_redirect = 4004;

    public final static int   kXR_ArgInvalid     = 3000;
    public final static int   kXR_ArgMissing     = 3001;
    public final static int   kXR_ArgTooLong     = 3002;
    public final static int   kXR_FileLockedr    = 3003;
    public final static int   kXR_FileNotOpen    = 3004;
    public final static int   kXR_FSError        = 3005;
    public final static int   kXR_InvalidRequest = 3006;
    public final static int   kXR_IOError        = 3007;
    public final static int   kXR_NoMemory       = 3008;
    public final static int   kXR_NoSpace        = 3009;
    public final static int   kXR_NotAuthorized  = 3010;
    public final static int   kXR_NotFound       = 3011;
    public final static int   kXR_ServerError    = 3012;
    public final static int   kXR_Unsupported    = 3013;
    public final static int   kXR_noserver       = 3014;
	
    public final static int   kXR_chmod = 3002;
    public final static int   kXR_close = 3003;
    public final static int   kXR_login = 3007;
    public final static int   kXR_mkdir = 3008;
    public final static int   kXR_mv    = 3009;
    public final static int   kXR_open  = 3010;
    public final static int   kXR_read  = 3013;
    public final static int   kXR_rm    = 3014;
    public final static int   kXR_rmdir = 3015;
    public final static int   kXR_sync  = 3016;
    public final static int   kXR_write = 3019;
    public final static int   kXR_stat  = 3017;

    public final static short kXR_ur = 0x100;
    public final static short kXR_uw = 0x080;
    public final static short kXR_ux = 0x040;
    public final static short kXR_gr = 0x020;
    public final static short kXR_gw = 0x010;
    public final static short kXR_gx = 0x008;
    public final static short kXR_or = 0x004;
    public final static short kXR_ow = 0x002;
    public final static short kXR_ox = 0x001;

    public final static short kXR_compress  = 1;
    public final static short kXR_delete    = 2;
    public final static short kXR_force     = 4;
    public final static short kXR_new       = 8;
    public final static short kXR_open_read = 16;
    public final static short kXR_open_updt = 32;
    public final static short kXR_async     = 64;
    public final static short kXR_refresh   = 128;  
    public final static short kXR_mkpath   	= 256;
    public final static short kXR_open_apnd	= 512;
    public final static short kXR_retstat  	= 1024;
    
    
    public final static byte kXR_useruser = 0;
    public final static byte kXR_useradmin = 1;
    
    public final static int DEFAULT_PORT = 1094;
}
