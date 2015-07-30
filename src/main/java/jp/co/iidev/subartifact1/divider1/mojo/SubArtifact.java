package jp.co.iidev.subartifact1.divider1.mojo;

import org.apache.maven.model.Dependency;

import jp.co.iidev.subartifact1.api.SubArtifactDefinition;


public class SubArtifact implements SubArtifactDefinition {
	private String artifactId;
	//    private Set<String> rootClassAnnotations;
	private Dependency[] extraDependencies;

	private OptionalPropagation[] defaultOptionalPropagations = new OptionalPropagation[0];
	
	private RootMark[] rootMarks = new RootMark[0];

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
	 public OptionalPropagation[] getDefaultPropagateOptions() {
		 return defaultOptionalPropagations;
	 }

}
