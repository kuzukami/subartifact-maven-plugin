package jp.co.iidev.subartifact1.divider1.mojo;

import java.util.Set;

import org.apache.maven.model.Dependency;

import jp.co.iidev.subartifact1.api.SubArtifactDefinition;


public class SubArtifact implements SubArtifactDefinition {
	private String artifactId;
    private Set<String> rootClassAnnotations;
    private Dependency[] extraDependencies;
    
	public String getArtifactId() {
		return artifactId;
	}
	public Set<String> getRootClassAnnotations() {
		return rootClassAnnotations;
	}
	public Dependency[] getExtraDependencies() {
		return extraDependencies;
	}

}
