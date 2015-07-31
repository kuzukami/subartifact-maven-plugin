package jp.co.iidev.subartifact1.divider1.mojo;

import org.apache.maven.plugins.annotations.Parameter;

/**
 * This function  be added in the near future.
 * @author kuzukami_user
 *
 */
@Deprecated
public class ServiceProvider implements RootSetConvertable{
	@Parameter(required=true)
	private String interfaceName;
	
	private boolean inheritsOuterDefaultRootTransitivePropagations = false;
	private boolean inheritsOuterDefaultRootSourceReferencePropagations = false;
	
	public ServiceProvider() {
	}

	@Override
	public RootSet asRootSet() {
		RootSet r = new RootSet();
		r.byIncludeResourcePattern = ("META-INF/services/" + interfaceName);
		r.inheritsOuterDefaultRootTransitivePropagations = inheritsOuterDefaultRootTransitivePropagations;
		r.inheritsOuterDefaultRootSourceReferencePropagations = inheritsOuterDefaultRootSourceReferencePropagations;
		r.rootSourceReferencePropagations = new OptionalPropagation[]{
				PredefinedPropagateOption.META_INF_SERVICES_FILE_MARKS_SERVICE_CLASSES.getAsOption()
		};
		return r;
	}

}
