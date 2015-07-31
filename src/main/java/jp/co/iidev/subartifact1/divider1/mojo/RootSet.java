package jp.co.iidev.subartifact1.divider1.mojo;

import org.apache.commons.lang3.ArrayUtils;

public class RootSet {
	
	 String byAnnotation = null;
	 String byIncludeResourcePattern = null;
	 boolean inheritsOuterDefaultRootTransitivePropagations = true;
	 boolean inheritsOuterDefaultRootSourceReferencePropagations = true;
	
	 OptionalPropagation[] rootTransitivePropagations = new OptionalPropagation[0];
	 OptionalPropagation[] rootSourceReferencePropagations = new OptionalPropagation[0];

	public String getByAnnotation() {
		return byAnnotation;
	}

	public String getByIncludeResourcePattern() {
		return byIncludeResourcePattern;
	}

	void setByAnnotation(String byAnnotation) {
		this.byAnnotation = byAnnotation;
	}

	void setByIncludeResourcePattern(String byIncludeResourcePattern) {
		this.byIncludeResourcePattern = byIncludeResourcePattern;
	}


	public boolean inheritsOuterRootDefaultRootTransitivePropagations() {
		return inheritsOuterDefaultRootTransitivePropagations;
	}

	public boolean inheritsOuterRootDefaultReferencePropagations() {
		return inheritsOuterDefaultRootSourceReferencePropagations;
	}

	public OptionalPropagation[] getRootTransitivePropagations( OptionalPropagation [] outerTransitive ) {
		if ( inheritsOuterRootDefaultRootTransitivePropagations() )
			return ArrayUtils.addAll(rootTransitivePropagations, outerTransitive);
		return rootTransitivePropagations;
	}
	
	public OptionalPropagation[] getRootSourceReferencePropagations( OptionalPropagation[] outerReference ) {
		if ( inheritsOuterRootDefaultReferencePropagations() )
			return ArrayUtils.addAll(rootSourceReferencePropagations, outerReference);
		return rootSourceReferencePropagations;
	}

}