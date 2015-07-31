package jp.co.iidev.subartifact1.divider1.mojo;

public enum PredefinedPropagateOption {
	TOPLEVEL_CLASSS_MARKS_INNER_CLASSES_IN_SIGNATURE {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.byInnerClassSignature = true;
			return m;
		}
	},
	PACKAGE_INFO_MARKS_ALL_IN_PACKAGE{
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			return m;
		}
	},
	PACKAGE_INFO_MARKS_PUBLICACCESSSTOPLEVELCLASSES_AND_RESOURCES_IN_PACKAGE{
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			m.getTargetResourceTypeOR().clear();
			m.getTargetResourceTypeOR().add(ResourceType.Resource);
			m.getTargetResourceTypeOR().add(ResourceType.PublicAccessTopLevelClass);
			
			return m;
		}
	},
	PACKAGE_INFO_MARKS_PUBLICTOPLEVELCLASSES_IN_PACKAGE {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			m.targetResourceTypeOR.clear();
			m.targetResourceTypeOR.add( ResourceType.PublicAccessTopLevelClass );
//			m.targetResourceTypeOR.add( ResourceType.PackageTopLevelClass );
			return m;
		}
	},
	PACKAGE_INFO_MARKS_DEFAULTACCESSTOPLEVELCLASSES_IN_PACKAGE {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			m.targetResourceTypeOR.clear();
			m.targetResourceTypeOR.add( ResourceType.DefaultAccessTopLevelClass );
//			m.targetResourceTypeOR.add( ResourceType.PackageTopLevelClass );
			return m;
		}
	},
	PACKAGE_INFO_MARKS_RESOURCES_IN_PACKAGE{
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			m.getTargetResourceTypeOR().clear();
			m.getTargetResourceTypeOR().add(ResourceType.Resource);
//			m.getTargetResourceTypeOR().add(ResourceType.PublicTopLevelClass);
			
			return m;
		}
	},
	IID_RES_MARKS_RESOURCES_IN_PACKAGE {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "Res";
			m.targetResourceTypeOR.clear();
			m.targetResourceTypeOR.add( ResourceType.Resource );
			return m;
		}
	},
	META_INF_SERVICES_FILE_MARKS_SERVICE_CLASSES {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.byServicesFileContents = true;
			m.targetResourceTypeOR.clear();
			m.targetResourceTypeOR.add( ResourceType.PublicAccessTopLevelClass );
			m.targetResourceTypeOR.add( ResourceType.DefaultAccessTopLevelClass );
			m.targetResourceTypeOR.add( ResourceType.InnerClass );
			return m;
		}
	},
	;
	
	public abstract OptionalPropagation getAsOption();

}
