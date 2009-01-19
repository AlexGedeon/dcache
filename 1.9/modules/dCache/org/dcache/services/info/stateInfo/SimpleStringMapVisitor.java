package org.dcache.services.info.stateInfo;

import java.util.HashMap;
import java.util.Map;

import org.dcache.services.info.base.State;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateTransition;
import org.dcache.services.info.base.StringStateValue;

/**
 * Build a Map<String,String> that maps between a particular entry in a list of items
 * and some StringStateValue a fixed relative path from each list item.  For example,
 * if the state tree contains entries like:
 * <pre>
 *   aa.bb.item1.cc.dd.stringMetric = StringStateValue( "foo1")
 *   aa.bb.item2.cc.dd.stringMetric = StringStateValue( "foo2")
 *   aa.bb.item3.cc.dd.stringMetric = StringStateValue( "foo3")
 * </pre>
 * then using this class with pathToList of StatePath.parsePath( "aa.bb" and
 * StatePath.parsePath( "cc.dd.stringMetric") will yield a Map like:
 * <pre>
 *   "item1" --> "foo1"
 *   "item2" --> "foo2"
 *   "item3" --> "foo3"
 * </pre>
 * 
 * @author Paul Millar <paul.millar@desy.de>
 */
public class SimpleStringMapVisitor extends SimpleSkeletonMapVisitor {

	/**
	 * Build a mapping between list items and some StringStateValue value for dCache's current state.
	 * @param pathToList the StatePath of the list's parent StateComposite.
	 * @param pathToMetric the StatePath, relative to the list item, of the StringStateValue
	 * @return the mapping between list items and the metric values.
	 */
	public static final Map<String,String> buildMap( StatePath pathToList, StatePath pathToMetric) {
		SimpleStringMapVisitor visitor = new SimpleStringMapVisitor( pathToList, pathToMetric);
		State.getInstance().visitState( visitor, pathToList);
		return visitor.getMap();
	}
	
	/**
	 * Build a mapping between list items and some StringStateValue value for dCache's state after
	 * a transition has taken place.
	 * @param transition the StateTransition to consider.
	 * @param pathToList the StatePath of the list's parent StateComposite.
	 * @param pathToMetric the StatePath, relative to the list item, of the StringStateValue
	 * @return the mapping between list items and the metric values.
	 */
	public static final Map<String,String> buildMap( StateTransition transition, StatePath pathToList, StatePath pathToMetric) {
		SimpleStringMapVisitor visitor = new SimpleStringMapVisitor( pathToList, pathToMetric);
		State.getInstance().visitState(transition, visitor, pathToList);
		return visitor.getMap();
	}
	
	Map <String,String> _map;
	
	public SimpleStringMapVisitor( StatePath pathToList, StatePath pathToMetric) {
		super( pathToList, pathToMetric);
		
		_map = new HashMap<String,String>();
	}
	
	@Override
	public void visitString(StatePath path, StringStateValue value) {
		if( path.equals( getPathToMetric()))
			_map.put( getKey(), value.toString());
	}

	Map<String,String> getMap() {
		return _map;
	}
}
