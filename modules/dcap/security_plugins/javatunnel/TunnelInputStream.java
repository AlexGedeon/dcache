/*
 * $Id: TunnelInputStream.java,v 1.3.2.1 2006-10-11 07:56:26 tigran Exp $
 */

package javatunnel;

import java.io.IOException;
import java.io.InputStream;

class TunnelInputStream extends InputStream {

    private InputStream _in = null;

    private Convertable _converter = null;

    private byte[] _buffer = null;

    int _pos = 0;

    public TunnelInputStream(InputStream in, Convertable converter) {
        _in = in;
        _converter = converter;
    }

    public int read() throws java.io.IOException {

        byte b;

        if ((_buffer == null) || (_pos >= _buffer.length)) {
            _buffer = _converter.decode(_in);
            _pos = 0;
        }

        if (_buffer == null) {
            return -1;
        }

        b = _buffer[_pos];
        ++_pos;

        return (int) b;
    }
	
	public void close() throws IOException {
		_in.close();
	}
    
}
