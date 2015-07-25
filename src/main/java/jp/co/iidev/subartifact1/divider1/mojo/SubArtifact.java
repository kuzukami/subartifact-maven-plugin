package jp.co.iidev.subartifact1.divider1.mojo;

import java.util.Set;

import org.apache.maven.model.Dependency;

import jp.co.iidev.subartifact1.api.SubArtifactDefinition;


public class SubArtifact implements SubArtifactDefinition {
	private String artifactId;
    private Set<String> rootClassAnnotations;
    private Dependency[] extraDependencies;
//    private boolean omittableIfEmpty;
    
	public String getArtifactId() {
		return artifactId;
	}
	public Set<String> getRootClassAnnotations() {
		return rootClassAnnotations;
	}
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
	public void setRootClassAnnotations(Set<String> rootClassAnnotations) {
		this.rootClassAnnotations = rootClassAnnotations;
	}
	public void setExtraDependencies(Dependency[] extraDependencies) {
		this.extraDependencies = extraDependencies;
	}
//	public boolean isOmittableIfEmpty() {
//		return omittableIfEmpty;
//	}
//	public void setOmittableIfEmpty(boolean omitsIfEmpty) {
//		this.omittableIfEmpty = omitsIfEmpty;
//	}
//	

}
