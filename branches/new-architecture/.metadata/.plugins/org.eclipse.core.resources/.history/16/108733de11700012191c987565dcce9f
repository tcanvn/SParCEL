package org.dllearner.core.owl;

import java.util.Map;

/**
 * This class implements the minimal integer range value restriction
 *  
 * @author An C. Tran
 *
 */
public class IntMinValue implements IntDataRange {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private int value;
	
	public IntMinValue(int value) {
		this.value = value;
	}
	
	
	/**
	 * 
	 * @return Minimal value of the restriction
	 */
	public int getValue() {
		return this.value;
	}
	
	/**
	 * Get length of the expression, always = 2
	 * @return
	 */
	public int getLength() {
		return 2;	// including >= and <value>
	}
	
	
	/**
	 * 
	 */
	public String toString() {
		return " >= " + value;
	}
	
	
	public void accept(KBElementVisitor visitor) {
		visitor.visit(this);
	}
	
	
	/**
	 * @see org.dllearner.core.owl.KBElement.toKBSyntaxString
	 * 
	 */
	@Override
	public String toKBSyntaxString(String baseURI, Map<String, String> prefixes) {
		return " >=" + value;
	}


	/**
	 * 
	 */
	@Override
	public String toString(String baseURI, Map<String, String> prefixes) {
		return " >=" + value;
	}


	/**
	 * @see org.dllearner.core.owl.KBElement.toManchesterSyntaxString
	 */
	@Override
	public String toManchesterSyntaxString(String baseURI, Map<String, String> prefixes) {
		return " >= " + value;
	}
	
}
