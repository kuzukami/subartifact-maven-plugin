package jp.co.iidev.subartifact1.divider1;

import java.util.Arrays;
import java.util.List;
//import java.util.regex.Pattern;

//import javax.annotation.Nonnull;
//import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;



public class ObjectTypeClassName{
	private final List<String> packageHierarchy;
	private final List<String> innerClassHierarchy;
	
	
	private static Splitter packageSepPat = 
			Splitter.on(".");
	private static Splitter innerClassSepPat = 
			Splitter.on("$");
	
	
	public static ObjectTypeClassName forClass(
			Class cls ){
		return forClassName(cls.getName());
	}
	public static ObjectTypeClassName forPackageNameAndSimpleName(
			String packageName, String simpleName ){
		return forClassName(packageName + "." + simpleName);
	}
	public static ObjectTypeClassName forClassName(
			String className ){
		
		List<String> packhir = Lists.newArrayList( packageSepPat.split(className)  );
		List<String> javahir = Lists.newArrayList( innerClassSepPat.split(
				Iterables.getLast(packhir) ) );
		
		return new ObjectTypeClassName(
				(packhir).subList(0, packhir.size() -1)
				,  ( javahir )
				);
	}
	public static Function<String, ObjectTypeClassName> forClassNameFunc(){
		return new Function<String,ObjectTypeClassName>(){
			@Override
			/*@Nullable*/
			public ObjectTypeClassName apply(/*@Nullable*/ String input) {
				return ObjectTypeClassName.forClassName(input);
			}
		};
	}
	
	public ObjectTypeClassName(List<String> packageHieralchy,
			List<String> innerClassHieralchy) {
		super();
		this.packageHierarchy = packageHieralchy;
		this.innerClassHierarchy = innerClassHieralchy;
	}

	/**
	 * @return jp.co.iidev.Debug => { "jp", "co", "iidev" } 
	 */
	public List<String> getPackageHierarchy() {
		return packageHierarchy;
	}

	/**
	 * 
	 * @return jp.co.iidev.Debug$Test => { "Debug" , "Test" } 
	 */
	public List<String> getInnerClassHierarchy() {
		return innerClassHierarchy;
	}
	
	public boolean isInnnerClass(){
		return getInnerClassHierarchy().size() != 1;
	}
	
	public String getName(){
		return Joiner.on(".").join( Iterables.concat(
				packageHierarchy, Arrays.asList( Joiner.on("$").join(innerClassHierarchy) )
				) );
	}
//	public static enum NameType{
//		VM(){
//			@Override
//			public String getName( ObjectTypeClassName c) {
//				return c.getName();
//			}
//		},
//		JAVALANG(){
//			@Override
//			public String getName( ObjectTypeClassName c ) {
//				return c.getLangFullName();
//			}
//		},
//		;
//		public abstract String getName( ObjectTypeClassName c );
//		
//		public static NameType MAIN_ARG_CLASSNAME = VM;
//	}
//	
//	public String getName( /*@Nonnull*/ NameType nt )
//	{
//		return nt.getName(this);
//	}
	
//	public RelativeFile getRealtiveSourceFile(){
//		return RelativeFile.pathOf( getPackageHierarchy(), getInnerClassHierarchy().get(0) + ".java" );
//	}
	
//	public String getPackageName(){
//		return ClassesIID.packageName(this);
//	}
//	
//	public String getVMShortName(){
//		return ClassesIID.vmShortName(this);
//	}
//	public String getLangShortName(){
//		return ClassesIID.langShortName(this);
//	}
//	public String getLangFullName(){
//		return getPackageName() + "." + ClassesIID.langShortName(this);
//	}
//	public String getPackageResourcePath(){
//		return "/" + Joiner.on("/").join(getPackageHierarchy());
//	}
//	
	public String getResourcePath(){
		return Joiner.on("/").join(getPackageHierarchy()) + "/" +
				Joiner.on("$").join(getInnerClassHierarchy()) + ".class";
				
	}
	
}