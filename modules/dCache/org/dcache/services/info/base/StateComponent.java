package org.dcache.services.info.base;

import java.util.*;

/**
 * All nodes within the State composition must implement this interface.  Nodes are
 * either StateComposite class (i.e., branches) or sub-classes of the abstract StateValue
 * (i.e., data).
 * <p>
 * StateComponents may be mortal, ephemeral or immortal.  If mortal, then the
 * <code>getExpiryDate()</code> method specifies when this StateComponent should be removed.  Ephemeral
 * StateComponents do not have a built-in expire time but do not affect the lifetime of their parent
 * StateComposite.  Immortal StateComponents never expire.   
 * <p>
 * This class implements the Composite pattern and includes support for the visitor pattern. 
 * @author Paul Millar <paul.millar@desy.de>
 */
interface StateComponent {

	/**
	 * Needed for the Visitor pattern: this method performs actions on the StateVisitor
	 * object via the methods defined in the interface.
	 * <p>
	 * Visiting a tree involves iterating over all StateComponent and invoking the corresponding
	 * method in the StateVisitor object.
	 * <p>
	 * The start parameter allows an initial skip to a predefined location, before iterating over
	 * all sub-StateComponents.  In effect, this allows visiting over only a sub-tree.
	 * 
	 * @param path this parameter informs an StateComponent of its location within the tree so the visit methods can include this information. 
	 * @param start the point in the tree to start visiting all children, or null to visit the whole tree.
	 * @param visitor the object that operations should be preformed on.
	 */
	void acceptVisitor( StatePath path, StatePath start, StateVisitor visitor);

	/**
	 * As above, but after the effects of a transition.
	 * @param transition
	 * @param path
	 * @param start
	 * @param visitor
	 */
	void acceptVisitor( StateTransition transition, StatePath path, StatePath start, StateVisitor visitor);

	/**
	 *  Check whether the change to newValue should trigger a StateWatcher.
	 *  This method has broadly the same semantics as equals(). 
	 */
	boolean shouldTriggerWatcher( StateComponent newValue);
	

	/**
	 * Check whether a predicate has been triggered
	 * @param ourPath  The StatePath to this StateComponent
	 * @param predicate The predicate under consideration
	 * @param transition the StateTransition under effect.
	 * @return true if the StatePathPredicate has been triggered, false otherwise
	 * @throws MetricStatePathException
	 */
	boolean predicateHasBeenTriggered( StatePath ourPath, StatePathPredicate predicate, StateTransition transition) throws MetricStatePathException;

	/**
	 * Apply a transformation, updating the live set of data.
	 * @param ourPath the StatePath to this StateComponent
	 * @param transition the StateTransition that should be applied.
	 */
	void applyTransition( StatePath ourPath, StateTransition transition);

	/**
	 * Update a StateTransition based adding a new metric. 
	 * @param ourPath the StatePath of this component
	 * @param childPath the StatePath, relative to this component, of the new metric
	 * @param newChild the new metric value.
	 * @param transition the StateTransition to update.
	 * @throws MetricStatePathException
	 */
	void buildTransition( StatePath ourPath, StatePath childPath, StateComponent newChild, StateTransition transition) throws MetricStatePathException;

	/**
	 * This method returns the Date at which this StateComponent should be removed from the state.
	 * @return the Date when an object should be removed, or null indicating that either is never to
	 * be removed or else the removal time cannot be predicted in advance. 
	 */
	public Date getExpiryDate();
	
	
	/**
	 *  This method returns the earliest Date that any Mortal StateComponent underneath this
	 *  StateComponent will expire.  This includes children of children and so on.  If this
	 *  StateComponent contains no Mortal children then null is returned.  
	 * @return the earliest Date when a Mortal child will expire. 
	 */
	public Date getEarliestChildExpiryDate();

	/**
	 * Update a StateTransition based on this StateComponent's children.
	 * @param ourPath this StateComponent's path within dCache tree.  For the top-most
	 * StateComponent this isnull
	 * @param transition the StateTransition object within which we should register children to be deleted.
	 * @param forced whether we should simply remove our children, or test whether they are to be deleted
	 */
	public void buildRemovalTransition( StatePath ourPath, StateTransition transition, boolean forced);

	
	/**
	 * Whether this component has expired.
	 * @return true if the parent object should remove this object, false otherwise.
	 */
	boolean hasExpired();
	
	boolean isEphemeral();
	boolean isImmortal();
	boolean isMortal();
}
