package org.dllearner.core.owl;

import java.util.Map;

public class IntMaxValue implements IntDataRange {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private int value;
	
	public IntMaxValue(int value) {
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
		return " <= " + value;
	}
	
	
	public void accept(KBElementVisitor visitor) {
		visitor.visit(this);
	}
	
	
	/**
	 * 
	 * @param baseURI
	 * @param prefixes
	 * @return
	 */
	public String toKBSyntaxString(String baseURI, Map<String, String> prefixes) {
		return " <= " + value;
	}


	@Override
	public String toString(String baseURI, Map<String, String> prefixes) {
		return " <= " + value;
	}


	@Override
	public String toManchesterSyntaxString(String baseURI, Map<String, String> prefixes) {
		return " <= " + value;
	}
	
}
