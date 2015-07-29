package jp.co.iidev.subartifact1.divider1;

import java.util.Set;
import java.util.jar.JarInputStream;

import jp.co.iidev.subartifact1.divider1.JARIndex.MyJarEntry;

interface FragmentName{
	public String getAddressName();
	public default boolean isClassFileResource(){
		return JARIndex.isClassfileResourceName(getAddressName());
	}
	

	
	
	static class Resource implements FragmentName{
		
		private final String addressName;
		@Override
		public String getAddressName() {
			return addressName;
		}
		protected Resource(String resourcePath) {
			String x = resourcePath;
			this.addressName = x;
		}
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result
					+ ((addressName == null) ? 0 : addressName.hashCode());
			return result;
		}
		
		@Override
		public String toString() {
			return "Resource [addressName=" + addressName + "]";
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Resource other = (Resource) obj;
			if (addressName == null) {
				if (other.addressName != null)
					return false;
			} else if (!addressName.equals(other.addressName))
				return false;
			return true;
		}

	}
	
	static class Clazz  extends Resource implements FragmentName{

		Clazz(String resourceName) {
			super( resourceName );
		}

		public String getClassName(){
			return getAddressName().replace("/", ".");
		}
		
		
		@Override
		public String toString() {
			return "Clazz [addressName=" + getAddressName() + "]";
		}

	}
	
	public static FragmentName forClassName( String classname ){
		String x = classname.replace(".", "/") + ".class";
		return new Clazz(x);
	}
	
//	private static FragmentName forResource( String resourcepath ){
//		return new Resource(resourcepath);
//	}
	
	public static FragmentName forJarEntryName( String resourceName ){
		if ( JARIndex.isClassfileResourceName(resourceName) )
			return new Clazz(resourceName);
		else
			return new Resource(resourceName);
	}
	
	public static FragmentName forJarEntry( MyJarEntry je ){
		return forJarEntryName(je.getJarEntryName());
	}
}