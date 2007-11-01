// $Id: SpreadAndWait.java,v 1.6 2007-07-08 17:02:48 tigran Exp $
package diskCacheV111.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.CellNucleus;

public class SpreadAndWait implements CellMessageAnswerable {

	private final CellNucleus _nucleus;
	private final long _timeout;
	
	private int _pending = 0;

	private final List<CellMessage> _replies = new ArrayList<CellMessage>();

	public SpreadAndWait(CellNucleus nucleus, long timeout) {

		_nucleus = nucleus;
		_timeout = timeout;
	}

	public synchronized void send(CellMessage msg) throws Exception {
		_nucleus.sendMessage(msg, true, true, this, _timeout);
		_pending++;
	}

	public synchronized void waitForReplies() throws InterruptedException {
		while (_pending > 0)
			wait();
	}

	public synchronized void answerArrived(CellMessage request, CellMessage answer) {
		_pending--;
		_replies.add(answer);
		notifyAll();
	}

	public synchronized void exceptionArrived(CellMessage request, Exception exception) {
		_pending--;
		notifyAll();
	}

	public synchronized void answerTimedOut(CellMessage request) {
		_pending--;
		notifyAll();
	}

	public Iterator<CellMessage> getReplies() {
		return _replies.iterator();
	}

	public int getReplyCount() {
		return _replies.size();
	}

	public List<CellMessage> getReplyList() {
		return _replies;
	}

	public synchronized CellMessage next() throws InterruptedException {
		//
		// pending replies what
		// yes == 0 wait
		// yes > 0 return elementAt(0)
		// no == 0 null
		// no > 0 return elementAt(0)
		//
		while ((_pending > 0) && _replies.isEmpty() )
			wait();

		if ((_pending == 0) && _replies.isEmpty() )
			return null;


		return _replies.remove(0);
	}
}
