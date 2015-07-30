package jp.co.iidev.subartifact1.divider1;


import jp.co.iidev.subartifact1.divider1.JARIndex.MyJarEntry;

interface FragmentName extends Comparable<FragmentName>{
	public String getResName();
	public default boolean isClassFileResource(){
		return JARIndex.isClassfileResourceName(getResName());
	}
	

	
	
	static class ResourceName implements FragmentName{
		
		private final String resName;
		@Override
		public String getResName() {
			return resName;
		}
		protected ResourceName(String resourcePath) {
			String x = resourcePath;
			this.resName = x;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((resName == null) ? 0 : resName.hashCode());
			return result;
		}
		

		@Override
		public String toString() {
			return  ResourceName.class.getSimpleName() +  " [" + resName + "]";
		}
		
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ResourceName other = (ResourceName) obj;
			if (resName == null) {
				if (other.resName != null)
					return false;
			} else if (!resName.equals(other.resName))
				return false;
			return true;
		}
		
		@Override
		public int compareTo(FragmentName o) {
			return toString().compareTo(o.toString());
		}

	}
	
	static class ClassName  extends ResourceName implements FragmentName{

		ClassName(String resourcePath) {
			super( resourcePath );
		}

		public String getClassName(){
			String x =  getResName().replace("/", ".");
			return x.substring(0, x.length() - ".class".length() );
		}

		@Override
		public String toString() {
			return  ClassName.class.getSimpleName() + " [" + getClassName() + "]";
		}
	}
	
	public static FragmentName forClassName( String classname ){
		String x = classname.replace(".", "/") + ".class";
		return new ClassName(x);
	}
	
//	private static FragmentName forResource( String resourcepath ){
//		return new Resource(resourcepath);
//	}
	
	public static FragmentName forJarEntryName( String resourceName ){
		if ( JARIndex.isClassfileResourceName(resourceName) )
			return new ClassName(resourceName);
		else
			return new ResourceName(resourceName);
	}
	
	public static FragmentName forJarEntry( MyJarEntry je ){
		return forJarEntryName(je.getJarEntryName());
	}
}