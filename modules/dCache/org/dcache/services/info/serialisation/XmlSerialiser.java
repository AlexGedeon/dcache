package org.dcache.services.info.serialisation;

import java.util.Map;

import org.dcache.services.info.base.BooleanStateValue;
import org.dcache.services.info.base.FloatingPointStateValue;
import org.dcache.services.info.base.IntegerStateValue;
import org.dcache.services.info.base.State;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateVisitor;
import org.dcache.services.info.base.StringStateValue;


/**
 * This serialiser maps the dCache state directly into an XML InfoSet.
 * <p>
 * For the most part, this is a simple mapping with some support for handling
 * branch-nodes with a known special parent branch differently.
 * <p>
 * NB, instances of this Class are not thread-safe: the caller is responsible for
 * ensuring no concurrent calls to serialise().
 * 
 * @author Paul Millar <paul.millar@desy.de>
 */
public class XmlSerialiser implements StateVisitor, StateSerialiser {
	
	public static final String NAME = "xml";
	
	/** The types used within the XML structure */
	private static final String _newline = "\n";
	
	private static final String _xmlns = "http://www.dcache.org/2008/01/dCache/Info";

	private StringBuilder _out;
	private int _indentationLevel = 0;
	private String _indentationPrefix = ""; 
	private boolean _isTopBranch;

	private StatePath _lastBranchPath;
	private String _lastBranchElementName;
	private String _lastBranchIdName;
	private boolean _haveLastBranch;
	
	
	private static class Attribute {
		final String name, value;
		Attribute( String iName, String iValue) {
			name = iName;
			value = iValue;
		}
	}
		
	
	/**
	 *  Serialise the current dCache state into XML;
	 *  @return a String containing dCache current state as XML data.
	 */
	public String serialise() {
		return serialise( null);
	}
	
	public String serialise( StatePath start) {
		_out = new StringBuilder();
		_isTopBranch = true;
		_haveLastBranch = false;
		_indentationLevel = 0;
		updateIndentPrefix();
		
		addElement( "<?xml version=\"1.0\"?>");
		
		State.getInstance().visitState( this, start);
		
		return _out.toString();		
	}

	
	public String getName() {
		return NAME;
	}

	/* Deal with branch movement */
	public void visitCompositePreDescend( StatePath path, Map<String,String> metadata)      { enteringBranch( path, metadata); }
	public void visitCompositePreSkipDescend( StatePath path, Map<String,String> metadata)  { enteringBranch( path, metadata); }	
	public void visitCompositePreLastDescend( StatePath path, Map<String,String> metadata)  {}
	public void visitCompositePostDescend( StatePath path, Map<String,String> metadata)     { exitingBranch( path, metadata); }
	public void visitCompositePostSkipDescend( StatePath path, Map<String,String> metadata) { exitingBranch( path, metadata); }
	
	/* Deal with metric values */
	public void visitInteger( StatePath path, IntegerStateValue value) {
		emitLastBeginElement( false);
		addElement( buildMetricElement( path.getLastElement(), value.getTypeName(), value.toString()));
	}

	public void visitString( StatePath path, StringStateValue value) {
		emitLastBeginElement( false);
		addElement( buildMetricElement( path.getLastElement(), value.getTypeName(), xmlTextMarkup(value.toString())));
	}
	
	public void visitBoolean( StatePath path, BooleanStateValue value) {
		emitLastBeginElement( false);
		addElement( buildMetricElement( path.getLastElement(), value.getTypeName(), value.toString()));
	}
	
	public void visitFloatingPoint( StatePath path, FloatingPointStateValue value) {
		emitLastBeginElement( false);
		addElement( buildMetricElement( path.getLastElement(), value.getTypeName(), value.toString()));		
	}
	
	/**
	 *  Provide all appropriate activity when entering a new branch.
	 *  <p>
	 *  When dealing with lists, we use the branch metadata:
	 *  <ul>
	 *  <li> METADATA_BRANCH_CLASS_KEY is the name of the list item class (e.g.,
	 *       for items under the dCache.pools branch, this is "pool")
	 *  <li> METADATA_BRANCH_IDNAME_KEY is the name of identifier (e.g., "name")
	 *  </ul>  
	 *  <p>
	 *  We mostly push information onto a (single item) stack so we can
	 *  emit empty branches like:
	 *  <pre>
	 *    <branchname attr1="value1" />
	 *  </pre>
	 *  
	 *  @param path The path of the new branch
	 *  @param metadata The keyword-value pairs for this branch. 
	 */
	private void enteringBranch( StatePath path, Map<String,String> metadata) {
		emitLastBeginElement( false);
		
		/* Build info and store it */
		
		_lastBranchPath = path;
		
		String branchClass = null;
		
		if( metadata != null)
			branchClass = metadata.get( State.METADATA_BRANCH_CLASS_KEY);
		
		if( branchClass != null) {
			_lastBranchElementName = branchClass;
			_lastBranchIdName = metadata.get( State.METADATA_BRANCH_IDNAME_KEY);
		} else {
			_lastBranchElementName = getBranchLabel( path); 
			_lastBranchIdName = null;
		}

		_haveLastBranch = true;
	}
	
	
	/**
	 * Method for handling the generic case when iterating out of a branch.
	 * @param path
	 * @param metadata
	 */
	private void exitingBranch( StatePath path, Map<String,String> metadata) {

		if( _haveLastBranch && ((path == null && _lastBranchPath == null) || (path != null && path.equals( _lastBranchPath))) ) {
			emitLastBeginElement( true);
			return;
		}
		
		emitLastBeginElement( false); // this should be a no-op: we should have no last-branch to emit.
			
		_indentationLevel--;
		updateIndentPrefix();
						
		String branchClass = metadata != null ? metadata.get( State.METADATA_BRANCH_CLASS_KEY) : null;
		String label = branchClass != null ? branchClass : getBranchLabel( path);
		addElement( endElement( label));
	}
	
	
	/**
	 * emit XML for the previous branch.  If the previous element was not a branch then
	 * this method does nothing.
	 */
	private void emitLastBeginElement( boolean isEmpty) {

		if( !_haveLastBranch)
			return;

		_haveLastBranch = false;
		
		Attribute[] attrs = null;
		
		if( _isTopBranch) {
			attrs = new Attribute[1];
			attrs[0] = new Attribute( "xmlns", _xmlns);
			_isTopBranch = false;
		} else {
			if( _lastBranchIdName != null) {
				attrs = new Attribute[1];
				attrs[0] = new Attribute( _lastBranchIdName, getBranchLabel( _lastBranchPath));			
			}
		}
		
		addElement( beginElement( _lastBranchElementName, attrs, isEmpty));

		if( !isEmpty) {
			_indentationLevel++;
			updateIndentPrefix();
		}

	}
	
	
	
	/**
	 * Add an element to the output stream with correct indentation.
	 * @param element the text (element, PI, ...) to add.
	 */
	private void addElement( String element) {
		_out.append( _indentationPrefix);
		_out.append( element);
		_out.append( _newline);
	}
	
	/**
	 * Build an XML metric element based on information.
	 * @param name the name of the metric
	 * @param type the type of the metric
	 * @param value the value
	 */
	private String buildMetricElement( String name, String type, String value) {
		StringBuilder sb = new StringBuilder();
		Attribute attr[] = new Attribute[2];
		attr[0] = new Attribute( "name", name);
		attr[1] = new Attribute( "type", type);
		
		sb.append( beginElement( "metric", attr, false));
		sb.append( value);
		sb.append( endElement("metric"));

		return sb.toString();
	}
	
		
	
	/**
	 * Build a String that opens an element
	 * @param name the element's name
	 * @param attr either an array of attributes for this element, or null.
	 * @param isEmpty whether the element contains no data.
	 * @return a String representing the start of this element
	 */
	private String beginElement( String name, Attribute[] attr, boolean isEmpty) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("<" + name);
		
		if( attr != null) {			
			for( int i = 0; i < attr.length; i++) {
				sb.append( " ");
				sb.append( attr[i].name);
				sb.append( "=\"");
				sb.append( xmlAttrMarkup( attr[i].value));
				sb.append( "\"");
			}
		}
		
		if( isEmpty)
			sb.append( "/");
		
		sb.append(">");
		
		return sb.toString();		
	}

	
	/**
	 * Build a string that closes an element
	 * @param name the name of the element to open
	 * @return a String.
	 */
	private String endElement( String name) {
		return "</"+name+">";
	}

	/**
	 * Mark-up an String so it can be included as XML data.  Specifically, we
	 * mark-up any occurrences of '<', '&' and '>'
	 * 
	 * @param value the string value to mark-up
	 * @return value that is safe to include in as an XML text-node.
	 */
	private String xmlTextMarkup( String value) {
		return value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll( ">", "&gt;");
	}
	
	
	/**
	 * Mark-up an attribute's value. 
	 * @param value before marking up
	 * @return the marked-up value.
	 */
	private String xmlAttrMarkup( String value) {
		return value.replaceAll("\"", "&quot;");
	}

	
	/**
	 * Update our stored prefix for indentation.
	 */
	private void updateIndentPrefix() {
		StringBuilder sb = new StringBuilder();
		
		for( int i = 0; i < _indentationLevel; i++)
			sb.append( "  ");
		
		_indentationPrefix = sb.toString();
	}
	
	
	/**
	 * Return the suitable label to use for this branch
	 * @param path the StatePath under consideration
	 * @return the label for this branch.
	 */
	private String getBranchLabel( StatePath path) {
		return path != null ? path.getLastElement() : "dCache"; 
	}


}
