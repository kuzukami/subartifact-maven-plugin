package jp.co.iidev.subartifact1.divider1.mojo;

public class RootMark {
	
	private String byAnnotation = null;
	private String[] byIncludeResourcePatterns = null;
	private boolean inheritsDefaultOptionalPropagations = true;
	
	private OptionalPropagation[] optionalPropagations = new OptionalPropagation[0];

	public String getByAnnotation() {
		return byAnnotation;
	}

	public String[] getByIncludeResourcePatterns() {
		return byIncludeResourcePatterns;
	}

	void setByAnnotation(String byAnnotation) {
		this.byAnnotation = byAnnotation;
	}

	void setByIncludeResourcePatterns(String[] byIncludeResourcePatterns) {
		this.byIncludeResourcePatterns = byIncludeResourcePatterns;
	}

	public OptionalPropagation[] getOptionalPropagations() {
		return optionalPropagations;
	}

	public boolean inheritsDefaultOptionalPropagations() {
		return inheritsDefaultOptionalPropagations;
	}

}