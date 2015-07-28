package jp.co.iidev.subartifact1.divider1;

import java.util.Set;

interface FragmentName{
	public String getAdressName();
	
	
	public static class Clazz implements FragmentName{
		private final String addressName;
		@Override
		public String getAdressName() {
			return addressName;
		}
		protected Clazz(String classname) {
			super();
			String x = classname.replace(".", "/") + ".class";
			this.addressName = x;
		}

		public String getClassName(){
			return addressName.replace("/", ".");
		}
		
		
		@Override
		public String toString() {
			return "Clazz [addressName=" + addressName + "]";
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
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Clazz other = (Clazz) obj;
			if (addressName == null) {
				if (other.addressName != null)
					return false;
			} else if (!addressName.equals(other.addressName))
				return false;
			return true;
		}
	}
	
	public static FragmentName forClassName( String classname ){
		return new Clazz(classname);
	}
	
	
}