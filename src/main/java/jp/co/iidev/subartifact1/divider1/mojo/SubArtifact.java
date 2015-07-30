package jp.co.iidev.subartifact1.divider1.mojo;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.maven.model.Dependency;

import jp.co.iidev.subartifact1.api.SubArtifactDefinition;


public class SubArtifact implements SubArtifactDefinition {
	private String artifactId;
	//    private Set<String> rootClassAnnotations;
	private Dependency[] extraDependencies;

	private boolean inheritsOuterDefaultRootTrantisivePropagations = true;
	private boolean inheritsOuterDefaultRootSourceReferencePropagations = true;
	private boolean inheritsOuterDefaultSubartifactSourceReferencePropagations = true;
	
	private RootMark[] rootMarks = new RootMark[0];
	
	private OptionalPropagation[] defaultRootTransitivePropagations = new OptionalPropagation[0];
	
	private OptionalPropagation[] defaultRootSourceReferencePropagations = new OptionalPropagation[0];

	private OptionalPropagation[] subartifactSourceReferencePropagations = new OptionalPropagation[0];

	public String getArtifactId() {
		return artifactId;
	}
	//	public Set<String> getRootClassAnnotations() {
	//		return rootClassAnnotations;
	//	}
	public Dependency[] getExtraDependencies() {
		return extraDependencies;
	}
	@Override
	public String toString() {
		return "SubArtifact [artifactId=" + artifactId + "]";
	}
	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}
	/*	public void setRootClassAnnotations(Set<String> rootClassAnnotations) {
		this.rootClassAnnotations = rootClassAnnotations;
	}
	 */	public void setExtraDependencies(Dependency[] extraDependencies) {
		 this.extraDependencies = extraDependencies;
	 }
	 @Override
	 public RootMark[] getRootMarks() {
		 return rootMarks == null ? new RootMark[0] : rootMarks;
	 }
	 public void setRootMarks( RootMark[] x ) {
		 this.rootMarks = x;
	 }
	 
	public OptionalPropagation[] getDefaultRootTransitivePropagations( OptionalPropagation[] externalTranstive ) {
		if ( inheritsOuterDefaultRootTrantisivePropagations )
			return  ArrayUtils.addAll(defaultRootTransitivePropagations, externalTranstive);
		return defaultRootTransitivePropagations;
	}
	
	public OptionalPropagation[] getDefaultRootSourceReferencePropagations( OptionalPropagation[] externalReference ) {
		if ( inheritsOuterDefaultRootSourceReferencePropagations )
			return  ArrayUtils.addAll(defaultRootSourceReferencePropagations, externalReference);
		return defaultRootSourceReferencePropagations;
	}

	public OptionalPropagation[] getSubartifactSourceReferencePropagations( OptionalPropagation[] externalSubartRef ) {
		if ( inheritsOuterDefaultSubartifactSourceReferencePropagations )
			return  ArrayUtils.addAll(subartifactSourceReferencePropagations, externalSubartRef);
		return subartifactSourceReferencePropagations;
	}
}
