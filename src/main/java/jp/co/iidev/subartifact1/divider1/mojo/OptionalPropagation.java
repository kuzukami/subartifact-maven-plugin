package jp.co.iidev.subartifact1.divider1.mojo;

import java.util.EnumSet;
import java.util.Set;

public class OptionalPropagation{
//	Set<Modifier> targetClassModifierOR = EnumSet.allOf(Modifier.class);	
	PredefinedPropagateOption byPredefined = null;
	
	Set<ResourceType> targetResourceTypeOR = EnumSet.allOf(ResourceType.class);
	
//	boolean transitvePropagate = false;
	
	boolean byServicesFileContents = false;
	boolean byInnerClassSignature = false;
	
	String bySimpleResourceNameInPacakge = null;
	String bySimpleClassNameInPacakge = null;


	public boolean isByInnerClassSignature() {
		return byInnerClassSignature;
	}
//	public boolean isTransitvePropagate() {
//		return transitvePropagate;
//	}
	public boolean isByServicesFileContents() {
		return byServicesFileContents;
	}
	public String getBySimpleResourceNameInPacakge() {
		return bySimpleResourceNameInPacakge;
	}
	public String getBySimpleClassNameInPacakge() {
		return bySimpleClassNameInPacakge;
	}
	public Set<ResourceType> getTargetResourceTypeOR() {
		return targetResourceTypeOR;
	}
	public PredefinedPropagateOption getByPredefined() {
		return byPredefined;
	}

}