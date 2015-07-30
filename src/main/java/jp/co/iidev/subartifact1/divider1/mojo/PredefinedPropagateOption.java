package jp.co.iidev.subartifact1.divider1.mojo;

public enum PredefinedPropagateOption {
	TOPLEVEL_CLASSS_MARKS_INNER_CLASSES_IN_SIGNATURE_TRANSITIVELY {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.byInnerClassSignature = true;
			m.transitvePropagate = true;
			return m;
		}
	},
	PACKAGE_INFO_MARKS_PACKAGE_RESOURCES_AND_CLASSES {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			return m;
		}
	},
	PACKAGE_INFO_MARKS_PACKAGE_CLASSES {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			m.targetResourceTypeOR.clear();
			m.targetResourceTypeOR.add( ResourceType.TopLevelClass );
			return m;
		}
	},
	PACKAGE_INFO_MARKS_PACKAGE_CLASSES_TRANSITIVELY {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "package-info";
			m.targetResourceTypeOR.clear();
			m.targetResourceTypeOR.add( ResourceType.TopLevelClass );
			m.transitvePropagate = true;
			return m;
		}
	},
	IID_JRES_MARKS_PACKAGE_RESOURCES {
		@Override
		public OptionalPropagation getAsOption() {
			OptionalPropagation m = new OptionalPropagation();
			m.bySimpleClassNameInPacakge = "JRes";
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
			m.targetResourceTypeOR.add( ResourceType.TopLevelClass );
			m.targetResourceTypeOR.add( ResourceType.InnerClass );
			return m;
		}
	},
	;
	
	public abstract OptionalPropagation getAsOption();

}
