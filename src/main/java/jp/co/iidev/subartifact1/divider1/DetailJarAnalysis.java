package jp.co.iidev.subartifact1.divider1;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;

import jp.co.iidev.subartifact1.divider1.JARIndex.MyJarEntry;
import jp.co.iidev.subartifact1.divider1.asmhack.CollectingRemapper;

class DetailJarAnalysis {
	private final JARIndex index;
	private final LoadingCache<MyJarEntry, byte[]> binaryCache;
	private final LoadingCache<MyJarEntry, ClassNode> classNodeCache;
	private final LoadingCache<FragmentName, Set<FragmentName>> classDependencyCache;
	private final Supplier<Map<String, Map<FragmentName, MyJarEntry>>> package2EntrynameIndexWhereRootPackageIsZeroLengthString;
			


	public DetailJarAnalysis( JARIndex jr ) {
		this.index = jr;
		this.binaryCache = 
				CacheBuilder.newBuilder()
				.softValues()
				.build(CacheLoader.from( (e) -> e.getBytes() ) );
		
		this.classNodeCache =
				CacheBuilder.newBuilder()
				.softValues()
				.build(CacheLoader.from(
						(e) -> {
							ClassNode cn = new ClassNode();
							ClassReader cr = new ClassReader(binaryCache.getUnchecked(e));
							cr.accept(cn, 0);
							return cn;
						}
						));
		
		this.classDependencyCache = 
				CacheBuilder.newBuilder()
				.softValues()
				.build( CacheLoader.from(
						(e) -> {
							if ( ! e.isClassFileResource() )
								return Collections.emptySet();
							byte[] classfile = getBytes( e );
							
							return
							ResourceAnalysis.getDependentDistinctClassNamesAsFragmentName(classfile)
							.toSet();
						}));
		LoadingCache<String, Map<String, Map<FragmentName, MyJarEntry>>> x = 
				CacheBuilder.newBuilder()
				.softValues()
				.build( CacheLoader.from(
						(e) -> {
							return groupByPackage();
						}));
		this.package2EntrynameIndexWhereRootPackageIsZeroLengthString =
			() -> x.getUnchecked("anythingok");
	}
	
	public Set<FragmentName> getDependency( FragmentName resource ){
		return classDependencyCache.getUnchecked(resource);
	}
	
	public byte[] getBytes(FragmentName o) {
		return binaryCache.getUnchecked(getEntry(o));
	}


	public Stream<JARIndex.MyJarEntry> annotatedClasses(
			Set<String> annotationClassName) {
		return annotatedClassesByCannonicalAnnotatedName(
				annotationClassName.stream()
				.map((an) -> JARIndex.fromObjectClassNameToCannonicalName(an) )
				.collect(Collectors.toSet())
				);
	}
	public FragmentName getFragmentName(MyJarEntry x) {
		return getEntries().inverse().get(x);
	}
	
	public MyJarEntry getEntry( String resourcePath ){
		return getEntry(FragmentName.forJarEntryName(resourcePath) );
	}
	
	public MyJarEntry getEntry( FragmentName fn ){
		return index.getEntry(fn.getResName());
	}
	
	public BiMap<FragmentName,MyJarEntry> getEntries(){
		return index.getEntries();
	}
	public Optional<ClassNode> getClassNode( InnerClassNode classname ){
		return getClassNode(FragmentName.forJarEntryName(classname.name + ".class" ));
	}
	
	public Optional<ClassNode> getClassNode( String classname ){
		return getClassNode(FragmentName.forClassName(classname));
	}
	public Optional<ClassNode> getClassNode( FragmentName classfragment ){
		MyJarEntry je = getEntry(classfragment);
		if ( je == null ) return Optional.empty();
		return getClassNode(je);
	}
	
	public Optional<ClassNode> getClassNode(  MyJarEntry entry ){
		if ( entry .isClassFile() ){
			return Optional.of( classNodeCache.getUnchecked(entry));
		}else{
			return Optional.empty();
		}
	}

	private Multimap<String, FragmentName> computePackage2EntrynameIndexWhereRootPackageIsZeroLengthString( String packageSep ) {
		Multimap<String, FragmentName> indx = HashMultimap.create();
		index.getEntries().forEach((entname, ent) -> {
			indx.put(Joiner.on(packageSep).join(ent.getDiretoryPath()), entname);
		});
		return indx;
	}
	
	public Map<FragmentName, MyJarEntry> packageResource( String packageName_sep_by_dot ) {
		return package2EntrynameIndexWhereRootPackageIsZeroLengthString.get().get(packageName_sep_by_dot);
	}

	private Map<String, Map<FragmentName, MyJarEntry>> groupByPackage() {
		return
		Maps.transformValues(
				computePackage2EntrynameIndexWhereRootPackageIsZeroLengthString(".").asMap()
				,
				(entnames) -> {
					return Maps.filterKeys( index.getEntries(), Predicates.in(entnames) );
				});
	}
	

	public Stream<JARIndex.MyJarEntry> annotatedClassesByCannonicalAnnotatedName(
			Set<String> annotationJVMName) {
		return index.getEntries().values()
				.stream()
				.parallel()
				.filter((e) -> {
			Optional<ClassNode> cn = getClassNode(e);
			if ( !cn.isPresent() ) return false;
//			
			ClassNode cnx = cn.get();
			List<AnnotationNode> ln = Lists.newArrayList();
			if ( cnx.visibleAnnotations != null ) ln.addAll(cnx.visibleAnnotations);
			if ( cnx.invisibleAnnotations != null ) ln.addAll(cnx.invisibleAnnotations);
			
			return ln.stream()
//					.parallel()
					.anyMatch((annoO) -> {
				AnnotationNode an = annoO;
				CollectingRemapper rx = new CollectingRemapper();
				rx.mapSignature(an.desc, true);

				return rx.classesByClassName.stream()
						// choose the longest name as the annotation class =>
						// seems adequate
						.map( (x) -> JARIndex.fromObjectClassNameToCannonicalName(x) )
						.sorted(Comparator.comparing((s) -> -s.length()))
						.findFirst()
						.filter((x) -> annotationJVMName.contains(x))
						.isPresent();
			});
		});
	}

	static boolean isToplevelClass(JARIndex.MyJarEntry c) {
		return c.isClassFile() && !c.getBasename().contains("$");
	}


//	static boolean isPackageDeclaration(JARIndex.MyJarEntry c) {
//		return DetailJarAnalysis.isToplevelClass(c) && "package-info".equals(c.getBasename());
//	}



}
