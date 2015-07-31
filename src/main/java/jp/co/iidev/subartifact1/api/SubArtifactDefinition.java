package jp.co.iidev.subartifact1.api;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.Set;

import jp.co.iidev.subartifact1.divider1.mojo.RootSet;



public interface SubArtifactDefinition extends DependableArtifact{

//	public abstract Set<ModuleDependency> getTotalDependencies();
//	public abstract Set<ModuleDependency> getMergeDependenciesNotRecursively();
//	public abstract Set<ModuleDependency> getExternalDependencies();
	
	public abstract RootSet[] getRootSets();
//	public abstract String getSubModuleUniquePackage();
//	public abstract String getSubModuleUniqueClassnamePrefix();
	
//	public abstract void createResourceOnlyJar( Iterable<ResourceInfo> normalResources, File destFile ) throws IOException;

}