package jp.co.iidev.subartifact1.divider1;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.vafer.jdependency.Clazz;
import org.vafer.jdependency.Clazzpath;
import org.vafer.jdependency.ClazzpathUnit;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import jp.co.iidev.subartifact1.api.SubArtifactDefinition;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ArtifactFragment;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanAcceptor;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.AFPredicateInconsitency;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.CyclicArtifact;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ReferenceInspector;
import jp.co.iidev.subartifact1.divider1.DivisionExecutor.RelocatableClassPathUnit.RelocatableClass;
import jp.co.iidev.subartifact1.divider1.mojo.SubArtifact;

public class DivisionExecutor {
	private final Loggable log;

	private void info(String msg, Object... names) {
		log.info(msg, names);
	}

	private void debug(String msg, Object... names) {
		log.debug(msg, names);
	}
	
	private void error(String msg, Object... names) {
		log.error(msg, names);
	}

	public DivisionExecutor(Loggable log) {
		super();
		this.log = log;
	}

	public static enum ReportSortOrder {
		RO1_SUBMODULE,
		RO2_JAR,
		RO3_ClazzSet,
		RO4_RESOURCE,
		RO5_DEBUG,
		;
	}
	
	public static interface AuthorizedNameResolver{
		MyFragment resolve( MyFragment source, Clazz fragmentName );
	}
	
	public static interface MyFragment extends ArtifactFragment{
		Set<MyFragment> dependencyTargets( AuthorizedNameResolver r );
	}

	static interface FragmentParty extends ArtifactFragment, MyFragment {
		@Override
		public default int compareTo(ArtifactFragment o) {
			if (o instanceof DivisionExecutor.FragmentParty) {
				DivisionExecutor.FragmentParty other = (DivisionExecutor.FragmentParty) o;
				int oi = getOrder().compareTo(other.getOrder());
				if (oi != 0)
					return oi;

				return toString().compareTo(other.toString());
			}
			return -1;
		}

		public Set<Clazz> getMemberNames() ;
		public Set<Clazz> getDependencyTargetNames() ;
		public DivisionExecutor.ReportSortOrder getOrder();
		
		default Set<MyFragment> dependencyTargetsSTD( AuthorizedNameResolver r, MyFragment ...extraDeps ){
			Set<MyFragment> x = Sets.newHashSet();
			for ( Clazz c : getDependencyTargetNames() ){
				MyFragment rx = r.resolve( this,  c);
				if ( rx == null ){
					//resolve error => unknown class or resource name
				}else{
					x.add(  rx );
				}
			}
			x.addAll(Arrays.asList(extraDeps));
			return x;
		}

	}
	
	static abstract class FragmentSet implements ArtifactFragment, MyFragment, FragmentParty {
		private final Set<Clazz> memberNameSet;
		private final Supplier<Set<Clazz>> dependencyTargetName;
		private final DivisionExecutor.ReportSortOrder order;

		protected FragmentSet(Set<Clazz> memberNameSet,
				Supplier<Set<Clazz>> depends, DivisionExecutor.ReportSortOrder ro) {
			super();
			this.memberNameSet = memberNameSet;
			this.dependencyTargetName = depends;
			this.order = ro;
		}

		@Override
		public int compareTo(ArtifactFragment o) {
			if (o instanceof DivisionExecutor.FragmentParty) {
				DivisionExecutor.FragmentParty other = (DivisionExecutor.FragmentParty) o;
				int oi = getOrder().compareTo(other.getOrder());
				if (oi != 0)
					return oi;

				return toString().compareTo(other.toString());
			}
			return -1;
		}

		public Set<Clazz> getMemberNames() {
			return memberNameSet;
		}

		public Set<Clazz> getDependencyTargetNames() {
			return dependencyTargetName.get();
		}

		public DivisionExecutor.ReportSortOrder getOrder() {
			return order;
		}

	}
	
	
	static abstract class FragmentUnit implements ArtifactFragment, MyFragment, FragmentParty {
		private final Clazz myName;
		private final DivisionExecutor.ReportSortOrder order;

		protected FragmentUnit(Clazz myclazzset,
				 DivisionExecutor.ReportSortOrder ro) {
			super();
			this.myName = myclazzset;
			this.order = ro;
		}

		@Override
		public int compareTo(ArtifactFragment o) {
			if (o instanceof DivisionExecutor.FragmentParty) {
				DivisionExecutor.FragmentParty other = (DivisionExecutor.FragmentParty) o;
				int oi = getOrder().compareTo(other.getOrder());
				if (oi != 0)
					return oi;

				return toString().compareTo(other.toString());
			}
			return -1;
		}

		public Set<Clazz> getMemberNames() {
			return Collections.singleton( myName );
		}

		public abstract Set<Clazz> getDependencyTargetNames();

		public DivisionExecutor.ReportSortOrder getOrder() {
			return order;
		}

	}


	public static interface PartialNameResolver/* ArtifactFragamentFinder */ {
		public Map<Clazz, DivisionExecutor.FragmentParty> resolveOnlyAuthorized(Set<Clazz> resolveRequired);

		public static interface Standard extends DivisionExecutor.PartialNameResolver {
			public Set<Clazz> getAuthorizedNames();

			public DivisionExecutor.FragmentParty resolveOne(Clazz authorizedNameForMe);

			@Override
			public default Map<Clazz, DivisionExecutor.FragmentParty> resolveOnlyAuthorized(
					Set<Clazz> resolveRequired) {
				return Maps.transformEntries(
						MapsIID.forSet(Sets.newHashSet(Sets.intersection(
								resolveRequired, getAuthorizedNames()))),
						(clz, voidx) -> resolveOne(clz));
			}

		}

//		public static interface HasExtraDependencies {
//			public void resolveAndAppendExtraDependenciesOnFinish(
//					MyClazzpath fullclasspath,
//					Multimap<ArtifactFragment, ArtifactFragment> extraDB);
//		}
	}

	public class MyClazzpath implements AuthorizedNameResolver{
		private final List<DivisionExecutor.PartialNameResolver> partialResolvers;
//		private final Multimap<ArtifactFragment, ArtifactFragment> extractAdjacent = HashMultimap.create();
		private final Map<Clazz, DivisionExecutor.FragmentParty> resolvCache = Maps.newHashMap();

		protected MyClazzpath(List<? extends DivisionExecutor.PartialNameResolver> units) {
			this.partialResolvers = Lists.newArrayList(units);
//			fix();
		}

		public Set<ArtifactFragment> getAdjacent(ArtifactFragment a) {
//			if ( false ){
//				Iterable<? extends ArtifactFragment> ia = Lists.newArrayList();
//				if (a instanceof DivisionExecutor.ClazzSet) {
//					DivisionExecutor.ClazzSet czs = (DivisionExecutor.ClazzSet) a;
//					ia = resolve(czs.getDependingName(),
//							(clz) -> {
//								List<String> k = Lists.newArrayList();
//								for ( Clazz refc : czs.getMyNames() ){
//									if ( refc.getDependencies().contains(clz) ){
//										k.add( refc.getName() );
//									}}
//								return k;
//							} ).values();
//				}
//
//				return Sets.newHashSet(
//						Iterables.concat(extractAdjacent.get(a), ia));
//			}else{
				Iterable<? extends ArtifactFragment> ia = Lists.newArrayList();
				if ( a instanceof FragmentParty ){
					FragmentParty b = (FragmentParty)a;
					ia = b.dependencyTargets(this);
				}
				return Sets.newHashSet( ia );
//			}
		}

		@Override
		public MyFragment resolve( MyFragment source, Clazz fragmentName) {
			return
					resolve(
					Collections.singleton(fragmentName),
					(x) -> Arrays.asList( source.toString() ) 
					).get(fragmentName)
					;
		}

		private Map<Clazz, DivisionExecutor.FragmentParty> resolve(
				Set<Clazz> resolveRequiredO
				,
				Function<Clazz, List<String>> inverseReferencerLookupF
				) {
			{
				// cache operation
				Set<Clazz> resolveRequired = resolveRequiredO;
				resolveRequired = Sets.newHashSet(Sets
						.difference(resolveRequired, resolvCache.keySet()));
				// ensure to load into the cache
				for (DivisionExecutor.PartialNameResolver m : partialResolvers) {
					if (resolveRequired.isEmpty())
						break;
					Map<Clazz, DivisionExecutor.FragmentParty> res = m.resolveOnlyAuthorized(resolveRequired);
					resolvCache.putAll(res);
					resolveRequired = Sets.newHashSet(
							Sets.difference(resolveRequired, res.keySet()));
				}

				if (!resolveRequired.isEmpty())
					for ( Clazz unknownClz : resolveRequired ) {
						//http://stackoverflow.com/questions/18769282/does-anyone-have-background-on-the-java-annotation-java-lang-synthetic
						if ( "java.lang.Synthetic".equals( unknownClz.getName() ) ){
							//see org/objectweb/asm/ClassReader.java 
						    // workaround for a bug in javac (javac compiler generates a parameter
						    // annotation array whose size is equal to the number of parameters in
						    // the Java source file, while it should generate an array whose size is
						    // equal to the number of parameters in the method descriptor - which
						    // includes the synthetic parameters added by the compiler). This work-
						    // around supposes that the synthetic parameters are the first ones.
							debug("{} is found in ( {} )",
									unknownClz.getName()
									,
									Joiner.on(", ").join(inverseReferencerLookupF.apply(unknownClz))
									);
						}else{
							error("There is an unknown class {} in ( {} )",
									unknownClz.getName()
									,
									Joiner.on(", ").join(inverseReferencerLookupF.apply(unknownClz))
									);
						}
					}
			}

			Map<Clazz, DivisionExecutor.FragmentParty> s = Maps.newHashMap();
			for (Clazz c : resolveRequiredO) {
				if (resolvCache.containsKey(c))
					s.put(c, resolvCache.get(c));
			}
			// s.putAll( Maps.filterKeys(resolvCache,
			// Predicates.in(resolveRequiredO) )); //slow...

			return s;
		}

//		public void fix() {
//			for (DivisionExecutor.PartialNameResolver m : partialResolvers) {
//				if (m instanceof PartialNameResolver.HasExtraDependencies) {
//					PartialNameResolver.HasExtraDependencies ed = (PartialNameResolver.HasExtraDependencies) m;
//					ed.resolveAndAppendExtraDependenciesOnFinish(this,
//							extractAdjacent);
//				}
//			}
//
//		}
	}

	/**
	 * メインのjarのうち、プロジェクトのルートとして指定を受けていないクラスばっかり入ったjarと同等の動きをする。
	 * ここから出ていく ClazzSetは、Fluidタイプで、Artifact依存関係の先に移動できるようになっている。(Fluid Vertex)
	 * @author kuzukami_user
	 *
	 */
	public static class RelocatableClassPathUnit
	implements PartialNameResolver.Standard {
		final Set<Clazz> authorizedNames;

		protected RelocatableClassPathUnit( Set<Clazz> authorizedNames) {
			this.authorizedNames = authorizedNames;
		}


		@Override
		public Set<Clazz> getAuthorizedNames() {
			return authorizedNames;
		}

		@Override
		public DivisionExecutor.FragmentParty resolveOne(Clazz authorizedNameForMe) {
			return RelocatableClass.forSingleClass(authorizedNameForMe);
		}

		static class RelocatableClass extends DivisionExecutor.FragmentUnit
		implements ArtifactFragment.RelocatableFragment {

			public static RelocatableClassPathUnit.RelocatableClass forSingleClass(Clazz clazz) {
				return new RelocatableClass(clazz);
			}

			private RelocatableClass(Clazz myclazzset ) {
				super(myclazzset,  ReportSortOrder.RO3_ClazzSet);
			}
			private Clazz getClazz(){ return Iterables.get(getMemberNames(), 0); }

			@Override
			public String toString() {
				return "RelocatableClass [getClazz()=" + getClazz() + "]";
			}

			@Override
			public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
				return dependencyTargetsSTD(r);
			}

			@Override
			public Set<Clazz> getDependencyTargetNames() {
				return naturalDepends(getMemberNames()).get() ;
			}

		}

	}

	protected static Supplier<Set<Clazz>> naturalDepends(
			Set<Clazz> names) {
		return () -> {
			if (names.size() == 1)
				return Sets.difference(
						Iterables.get(names, 0).getDependencies(),
						names);

			return Sets
					.newHashSet(
							Sets.difference(
									names.stream().flatMap((c) -> c
											.getDependencies().stream())
									.collect(Collectors.toSet()),
									names));
		};
	}

	/**
	 * １つの新規されるサブアーチファクトについて、
	 * ルートとされるクラス群を受け持つクラスパス要素でかつ、OutputArtifact Vertex
	 * 
	 * @author kuzukami_user
	 *
	 */
	public abstract static class SubArtifactRoot extends DivisionExecutor.FragmentSet
	implements ArtifactFragment.OutputArtifact,
	PartialNameResolver.Standard {
		protected final SubArtifact submodule;

		protected SubArtifactRoot(SubArtifact submodule,
				Set<Clazz> myclazzes, Supplier<Set<Clazz>> deps) {
			super(myclazzes, deps, ReportSortOrder.RO1_SUBMODULE);
			this.submodule = submodule;
		}

		@Override
		public Set<Clazz> getAuthorizedNames() {
			return getMemberNames();
		}


		@Override
		public String toString() {
			return "SubArtifactRoot [submodule=" + submodule + "]";
		}

//		public static SubArtifactRoot.LessMemory forLessMemory(SubArtifact submodule,
//				Set<Clazz> myclazzes) {
//			return new LessMemory(submodule, myclazzes);
//		}

		public static SubArtifactRoot.DetailTrace forDetailTrace(SubArtifact submodule,
				Set<Clazz> myclazzes) {
			return new DetailTrace(submodule, myclazzes);
		}

//		public static class LessMemory extends DivisionExecutor.SubArtifactRoot {
//			protected LessMemory(SubArtifact submodule,
//					Set<Clazz> myclazzes) {
//				super(submodule, myclazzes, naturalDepends(myclazzes));
//			}
//
//			@Override
//			public DivisionExecutor.ClazzSet forClazzSet(Clazz myclz) {
//				return this;
//			}
//
//		}

		public static class DetailTrace extends DivisionExecutor.SubArtifactRoot
//		implements PartialNameResolver.HasExtraDependencies
		{
			private final Map<Clazz, SubArtifactRootClass> resolveMap;

			protected DetailTrace(SubArtifact submodule,
					Set<Clazz> myclazzes) {
				super(submodule, myclazzes, () -> myclazzes);

				resolveMap = Maps.newHashMap(
						Maps.transformEntries(MapsIID.forSet(myclazzes),
								(clx, vd) -> new SubArtifactRootClass(clx)));

			}

//			@Override
//			public void resolveAndAppendExtraDependenciesOnFinish(
//					MyClazzpath fullclasspath,
//					Multimap<ArtifactFragment, ArtifactFragment> extraDB) {
//				for (SubArtifactRootClass m : clazzesForTrace.values())
//					extraDB.put(m, this);
//			}

			public class SubArtifactRootClass extends DivisionExecutor.FragmentUnit
			implements DebugContractable {
				private SubArtifactRootClass(Clazz myclazz) {
					super(myclazz, ReportSortOrder.RO5_DEBUG);
				}
				private SubArtifactDefinition getMod(){ return DetailTrace.this.submodule; }
				private Clazz getClazz(){ return Iterables.get(getMemberNames(), 0); }


				@Override
				public String toString() {
					return "SubArtifactRootClass [getClazz()=" + getClazz()
					+ ", getMod()=" + getMod() + "]";
				}
				@Override
				public ArtifactFragment getContractDestination() {
					return DetailTrace.this;
				}
				@Override
				public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
					return dependencyTargetsSTD(r, DetailTrace.this);
				}
				@Override
				public Set<Clazz> getDependencyTargetNames() {
					return naturalDepends(getMemberNames()).get();
				}
			}

			@Override
			public DivisionExecutor.FragmentParty resolveOne(Clazz myclz) {
				return resolveMap.get(myclz);
			}

			@Override
			public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
				return dependencyTargetsSTD(r);
			}

		}
	}

	public abstract static class LibArtifact extends DivisionExecutor.FragmentSet implements
	ArtifactFragment.LibraryArtifact, PartialNameResolver.Standard {
		protected final File jarFile;
		private final Dependency pomDependency;

		protected LibArtifact(File jarFile, Set<Clazz> myClazz,
				Supplier<Set<Clazz>> dependencies, Dependency dep) {
			super(myClazz, dependencies, ReportSortOrder.RO2_JAR);
			this.jarFile = jarFile;
			this.pomDependency = dep;
		}

		@Override
		public String toString() {
			return "LibJAR [jarFile=" + jarFile.getName() + "]";
		}

		@Override
		public Set<Clazz> getAuthorizedNames() {
			return getMemberNames();
		}

//		protected static DivisionExecutor.LibArtifact forLessMemory( Dependency dep,  File jarFile,
//				ClazzpathUnit jarUnit, Predicate<Clazz> targetFilter) {
//			Set<Clazz> myclz = Sets.filter(jarUnit.getClazzes(),
//					targetFilter);
//			return new LessMemory(jarFile, myclz, () -> Sets.newHashSet(), dep);
//		}

		public static DivisionExecutor.LibArtifact forDetailTrace( Dependency dep, File jarFile,
				ClazzpathUnit jarUnit, Predicate<Clazz> targetFilter) {
			Set<Clazz> myclz = Sets.filter(jarUnit.getClazzes(),
					targetFilter);
			return new DetailTrace(jarFile, myclz, dep );
		}

//		public static class LessMemory extends DivisionExecutor.LibArtifact {
//			protected LessMemory(File jarFile, Set<Clazz> myClazz,
//					Supplier<Set<Clazz>> dependencies, Dependency dep ) {
//				super(jarFile, myClazz, dependencies, dep);
//			}
//
//			@Override
//			public DivisionExecutor.ClazzSet forClazzSet(Clazz myclz) {
//				return this;
//			}
//
//		}

		public static class DetailTrace extends DivisionExecutor.LibArtifact
//		implements PartialNameResolver.HasExtraDependencies
		{

			private Map<Clazz, LibClass> apiClass = Maps.newHashMap();

			protected DetailTrace(File jarFile, Set<Clazz> myClazz, Dependency dep) {
				super(jarFile, myClazz, () -> myClazz, dep );
				for (Clazz c : myClazz) {
					apiClass.put(c, new LibClass(c, ReportSortOrder.RO5_DEBUG));
				}
			}

			public class LibClass extends DivisionExecutor.FragmentUnit
			implements DebugContractable {

				protected LibClass(Clazz myclazz,
						DivisionExecutor.ReportSortOrder ro) {
					super(myclazz, ro);
				}
				private String getLibname() { return  DetailTrace.this.jarFile.getName(); }
				private Clazz getClazz() { return  Iterables.get(getMemberNames(), 0); }


				@Override
				public String toString() {
					return "LibClass [getClazz()=" + getClazz()
					+ ", getLibname()=" + getLibname() + "]";
				}
				@Override
				public ArtifactFragment getContractDestination() {
					return DetailTrace.this;
				}
				@Override
				public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
					return dependencyTargetsSTD(r, DetailTrace.this);
				}
				@Override
				public Set<Clazz> getDependencyTargetNames() {
					return Collections.emptySet();
				}

			}

//			@Override
//			public void resolveAndAppendExtraDependenciesOnFinish(
//					MyClazzpath fullclasspath,
//					Multimap<ArtifactFragment, ArtifactFragment> extraDB) {
//				for (LibClass c : apiClass.values())
//					extraDB.put(c, this);
//			}

			@Override
			public DivisionExecutor.FragmentParty resolveOne(Clazz myclz) {
				return apiClass.get(myclz);
			}
			
			@Override
			public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
				return dependencyTargetsSTD(r);
			}

		}
	}



	public Function<ArtifactFragment, Iterable<ArtifactFragment>> adjacencyFunction(
			MyClazzpath clazzpath) {
		return (source) -> {
			return clazzpath.getAdjacent(source);

			// if (source instanceof ClazzSet) {
			// ClazzSet new_name = (ClazzSet) source;
			// return Sets.newHashSet( clazzpath.resolve(
			// new_name.getDepends() ).values() );
			// }
			// error( "Unknown Type of Artifact Fragment: {}", source );
			// return Collections.emptyList();
		};
	}

	private static ClazzpathUnit addcu(Clazzpath cp, File jar) {
		try {
			return cp.addClazzpathUnit(jar);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}


	public static class SubArtifactDeployment{
		private final SubArtifactDefinition target;
		private final List<SubArtifactDefinition> subartDeps;
		private final LinkedHashMap<File, Dependency> jarDeps;
		private final List<String> deployClassNames;
		private final List<String> resources;
		
		protected SubArtifactDeployment(SubArtifactDefinition target,
				List<SubArtifactDefinition> subartDeps, LinkedHashMap<File, Dependency> jarDeps
				, List<String> deployClassNames
				, List<String> resources
				) {
			super();
			this.target = target;
			this.subartDeps = subartDeps;
			this.jarDeps = jarDeps;
			this.deployClassNames = deployClassNames;
			this.resources = resources;
		}

		public SubArtifactDefinition getTarget() {
			return target;
		}

		public List<SubArtifactDefinition> getSubartDeps() {
			return subartDeps;
		}

		public LinkedHashMap<File, Dependency> getJarDeps() {
			return jarDeps;
		}

		public List<String> getDeployClassNames() {
			return deployClassNames;
		}

		public List<String> getResources() {
			return resources;
		}
		
		
	}


	public 
	LinkedHashMap<SubArtifactDefinition, SubArtifactDeployment>
	planDivision(
			File targetJarInClasspath,
			String rootSubartifactId,
			Iterable<? extends SubArtifact> subartifactsInTargetJar,
			LinkedHashMap<File, Dependency> compiletimeClasspath,
			Predicate<File> outputClasspathAliveFilter,
			LoggableFactory lgf
			) throws CyclicArtifact, NotFoundException, AFPredicateInconsitency{
		ClassPool loader = new ClassPool(false);
		for (File jf : compiletimeClasspath.keySet())
			loader.appendClassPath(jf.getAbsolutePath());

		Clazzpath clazzpath = new Clazzpath();
		LinkedHashMap<File, ClazzpathUnit> jars = StreamSupport
				.stream(compiletimeClasspath.keySet().spliterator(), false)
				.collect(Collectors.toMap((f) -> f,
						(f) -> addcu(clazzpath, f), (v, v2) -> v2,
						LinkedHashMap::new));
		;

		File mainjar = targetJarInClasspath;


		ClazzpathUnit mainJarCpUnit = jars.get(mainjar);
		Set<Clazz> mainJarLibDependencies =
				Sets.difference( mainJarCpUnit.getTransitiveDependencies(), mainJarCpUnit.getClazzes() );

		DivisionExecutor.PackgingCache pc = new PackgingCache(loader, mainJarCpUnit.getClazzes());
		
		SubArtifact rootSubart;
		{
			rootSubart = new SubArtifact();
			rootSubart.setArtifactId(rootSubartifactId);
			rootSubart.setExtraDependencies(new Dependency[0]);
			rootSubart.setRootClassAnnotations(Sets.newHashSet());
//			rootSubart.setOmittableIfEmpty(true);
		}
		
		FluentIterable<? extends DivisionExecutor.SubArtifactRoot> subartifacts_FirstOneIsRoot =
				FluentIterablesIID.copy(
						FluentIterablesIID.ofConcat(
								Arrays.asList(rootSubart),
								subartifactsInTargetJar)
						.transform(
								(jm) -> SubArtifactRoot.forDetailTrace(jm,
										pc.getKeepClazzes(jm.getRootClassAnnotations())))
						);
//				.toImmutableList()
				;
		

		
		List<? extends DivisionExecutor.LibArtifact> libs =
				jars.entrySet().stream()
				.filter((f2clz) -> !mainjar.equals(f2clz.getKey()))
				.map((f2clz) ->
					LibArtifact.forDetailTrace(
						compiletimeClasspath.get( f2clz.getKey() )
						, f2clz.getKey()
						, f2clz.getValue()
						, Predicates.in( mainJarLibDependencies ) ))
				.collect(Collectors.toList());

		DivisionExecutor.RelocatableClassPathUnit relocatableClasses =
				new RelocatableClassPathUnit(
						mainJarCpUnit.getClazzes());

		MyClazzpath clzpath = new MyClazzpath(
				FluentIterablesIID
				.ofConcat(
						subartifacts_FirstOneIsRoot
						, Arrays.asList(relocatableClasses)
						, libs)
//				.toImmutableList()
				.toList()
				);
		try (FullTracablePlanAcceptor pa = new FullTracablePlanAcceptor()) {
			new ArtifactDivisionPlanner(lgf)
			.computeAndReportDeploymentUnits(
					Sets.<ArtifactFragment>newHashSet(subartifacts_FirstOneIsRoot.skip(1)),
					adjacencyFunction(clzpath), subartifacts_FirstOneIsRoot.get(0), pa);
			return pa.integrateFullPlanOrderedByBuildSequence(
					(art) -> {
						if (art instanceof LibArtifact) {
							LibArtifact la = (LibArtifact) art;
							return outputClasspathAliveFilter.apply( la.jarFile );
						}
						return true;
					}
					).stream()
					.collect(
							Collectors.toMap(
									(v) -> v.target
									, (v) -> v
									, (v1,v2) -> {
										throw new RuntimeException("duplicated reports. bug?:" + v1 + " <=> " + v2 );
									}
									, LinkedHashMap::new
									));
		} catch (CyclicArtifact e) {
			e.logReasonAsError(log);
			throw e;
		} catch (AFPredicateInconsitency e) {
			e.logReasonAsError(log);
			throw e;
		}
	}

	private final class FullTracablePlanAcceptor implements PlanAcceptor {
		private BiMap<ArtifactFragment, Integer> buildingOrderOfOutputArtifacts = HashBiMap.create();
		private List<DeploymentUnit> deployments = Lists.newArrayList();
		String tabPad = "  ";
	
		public void finalReport() {
	
			deployments.stream().sorted(ducmp())
			.collect(Collectors
					.groupingBy((du) -> du.deploymentLocationArtifact))
			.entrySet().stream()
			//building order
			.sorted(Comparator.comparing((e) -> buildingOrderOfOutputArtifacts.get(e.getKey())))
			.forEach((ent) -> {
				ArtifactFragment deployAnchor = ent.getKey();
				List<DeploymentUnit> list_of_deployee = ent.getValue();
				
				System.out.println("Sub-Artifact Deployment Description For : "
						+ deployAnchor.toString());
				if (deployAnchor instanceof DivisionExecutor.SubArtifactRoot) {
					DivisionExecutor.SubArtifactRoot submod = (DivisionExecutor.SubArtifactRoot) deployAnchor;
					submod.getMemberNames().stream().sorted()
					.forEach((c) -> {
						System.out.println("    Sub-Artifact Root Class: "
								+ c.getName());
					});
	
				}
				for (DeploymentUnit deployee : list_of_deployee) {
					System.out.println("  Sub-Artifact Dependency : "
							+ deployee.deployeeFragment.toString());
					for (String reasonline : deployee.getReason(2))
						System.out.println(reasonline);
				}
			});
		}
	
		@Override
		public void acceptArtifactBuildingOrder(
				List<ArtifactFragment> buildingOrderForOutput) {
			buildingOrderForOutput.forEach((e) -> {
				ArtifactDivisionPlanner.axisIndexOf(e, buildingOrderOfOutputArtifacts);
			});
		}

		private Comparator<DeploymentUnit> ducmp() {
			Comparator<DeploymentUnit> c = Comparator
					.comparing((du) -> du.deploymentLocationArtifact);
	
			return c.thenComparing(
					Comparator.comparing((du) -> du.deployeeFragment));
		}
	
		private final class RelocatableDeployment extends DeploymentUnit {
			final List<ReferenceInspector> forEachDirectDependingArtifact;
			private RelocatableDeployment(ArtifactFragment deploymentAnchor,
					ArtifactFragment deployeeFragment,
					List<ReferenceInspector> forEachDirectDependingArtifact
					) {
				super(deploymentAnchor, deployeeFragment);
				this.forEachDirectDependingArtifact =forEachDirectDependingArtifact;
			}

			@Override
			List<String> getReason(int tabIndent) {
				List<String> k = Lists.newArrayList();
				for (ReferenceInspector riByDirectDependingArtifact : forEachDirectDependingArtifact) {
					List<ArtifactFragment> dependingDirGraphFromDirectArt = riByDirectDependingArtifact
							.computeDependingShortestPathFromDirectlyDependingArtifactToRelocatableFrament();
					
					List<ArtifactFragment> dependedDirGraphFromDeployToDirectArtifacts = riByDirectDependingArtifact
							.computeDependedShortestPathFromLocatedToDirectlyDependingArtifact();
					
					
					List<String> pathvis = 
					DependencyPathVisualizer.visualizeReversedDepedencyPath(
							dependingDirGraphFromDirectArt
							, dependedDirGraphFromDeployToDirectArtifacts
							, tabIndent -1);
					
					Iterables.addAll(
							k,
							Iterables.skip(pathvis, 1)//skip me because already displayed.
							);
				}
				return k;
			}
		}

		private final class ArtifactDeployment extends DeploymentUnit {
			
			final com.google.common.base.Supplier<List<ArtifactFragment>> computer_Deploy2dependeeShortestPath;
			private ArtifactDeployment(ArtifactFragment deploymentAnchor,
					ArtifactFragment deployeeFragment,
					com.google.common.base.Supplier<List<ArtifactFragment>> computer_Deploy2dependeeShortestPath
					) {
				super(deploymentAnchor, deployeeFragment);
				this.computer_Deploy2dependeeShortestPath = computer_Deploy2dependeeShortestPath;
			}

			@Override
			List<String> getReason(int tabIndent) {
				List<String> k = Lists.newArrayList();
				int i = tabIndent;
				List<ArtifactFragment> depndingDirGraph = computer_Deploy2dependeeShortestPath
						.get();
				Collections.reverse(depndingDirGraph);

				for (ArtifactFragment af : Iterables.skip(depndingDirGraph,
						1)) {
					k.add(StringsIID.replaceTemplateAsSLF4J(
							"{}{}{}", StringUtils.repeat(tabPad, i),
							"<=", af.toString()));
					i++;
				}
				return k;
			}
		}

		abstract class DeploymentUnit {
			final ArtifactFragment deploymentLocationArtifact;
			final ArtifactFragment deployeeFragment;
	
			protected DeploymentUnit(ArtifactFragment deploymentAnchor,
					ArtifactFragment deployeeFragment) {
				super();
				this.deploymentLocationArtifact = deploymentAnchor;
				this.deployeeFragment = deployeeFragment;
			}
	
			abstract List<String> getReason(int tabIndent);
		}

	
		@Override
		public void acceptArtifactDeploymentPlan(
				ArtifactFragment dependingArtifact,
				ArtifactFragment dependedArtifact,
				com.google.common.base.Supplier<List<ArtifactFragment>> computer_Deploy2dependeeShortestPath) {
			deployments.add(
					new ArtifactDeployment(dependingArtifact, dependedArtifact, computer_Deploy2dependeeShortestPath));
		}
	
		@Override
		public void acceptRelocatableFragmentDeploymentPlan(
				ArtifactFragment deploymentArtifactLocated,
				ArtifactFragment fuildFramentDepended,
				List<ReferenceInspector> forEachDirectDependingArtifact) {
			deployments.add(
					new RelocatableDeployment(deploymentArtifactLocated, fuildFramentDepended, forEachDirectDependingArtifact));
		}
		
		
		public List<SubArtifactDeployment> integrateFullPlanOrderedByBuildSequence(
				Predicate<ArtifactFragment> aliveFragments
				){
			LinkedHashMap<ArtifactFragment, List<DeploymentUnit>> subartifacts = 
					Maps.newLinkedHashMap();
			
			ArtifactDivisionPlanner.axisOrderedStream(buildingOrderOfOutputArtifacts).forEach((a) -> {
				subartifacts.put(a, Lists.newArrayList());
			});
			
			deployments.forEach((v) -> {
				subartifacts.get(v.deploymentLocationArtifact).add( v ) ;
			});
//			.collect(
//					Collectors.groupingBy(
//							(du) -> du.deploymentAnchor
//							, LinkedHashMap::new
//							, Collectors.toList()
//							)
//					)
//			;
			
			List<SubArtifactDeployment> l = Lists.newArrayList();
			Set<ArtifactFragment> emptyOmitSet = Sets.newHashSet();
			
			for ( Map.Entry<ArtifactFragment, List<DeploymentUnit>> a : subartifacts.entrySet() ){
				SubArtifactRoot main = (SubArtifactRoot)a.getKey();
				LinkedHashMap<File, Dependency>  jardeps = Maps.newLinkedHashMap();
				List<SubArtifactDefinition> subart_deps   = Lists.newArrayList();
				List<String> deploy_classnames = Lists.newArrayList();
				
				for ( DeploymentUnit du : a.getValue() ){
					ArtifactFragment tgt = du.deployeeFragment;
					if ( !aliveFragments.apply(tgt) ){
						info("{} is filterd out from {} because it's not passed for the 'alive predicate'.", tgt, main );
					}else
					if ( emptyOmitSet.contains(tgt) ){
						info("{} is filterd out from {} because it's 'empty'.", tgt, main );
					}else
					if (tgt instanceof RelocatableClass) {
						RelocatableClass fc = (RelocatableClass) tgt;
						deploy_classnames.add(fc.getClazz().getName());
					}else 
					if (tgt instanceof LibArtifact) {
						LibArtifact la = (LibArtifact) tgt;
						jardeps.put(la.jarFile, la.pomDependency);
					}else
					if (tgt instanceof SubArtifactRoot) {
						SubArtifactRoot sa = (SubArtifactRoot) tgt;
						subart_deps.add(sa.submodule);
					}else{
						throw new RuntimeException("unknown artifact fragment:" + tgt);
					}
				}
				
				for ( Clazz rootCls : main.getMemberNames() )
					deploy_classnames.add(rootCls.getName());
						
				boolean fullEmpty = jardeps.isEmpty() && subart_deps.isEmpty() && deploy_classnames.isEmpty();
				
				if ( fullEmpty /* && main.submodule.isOmittableIfEmpty() */ ){
					info("The artifact {} is not deployed because it's fully 'empty'.", main );
					emptyOmitSet.add( main );
				}else{
					List<String> resources = Lists.newArrayList();
					SubArtifactDeployment sa = new SubArtifactDeployment(
							main.submodule, subart_deps,
							jardeps
							,deploy_classnames
							, resources
							);
					l.add( sa );
				}
			}
			
			return l;
		}
	
		@Override
		public void close(){
			finalReport();
			;
		}
	}

	// helper class
	static class PackgingCache {
		final ClassPool pool;
		final Set<Clazz> searchSpace;
		// final Multimap<String, Clazz> annotatedClazzes =
		// LinkedListMultimap.create();
		final Multimap<String, Clazz> toplevelClazzesByPackage = LinkedListMultimap.create();
		final BiMap<Clazz, CtClass> classMap;

		PackgingCache(ClassPool pool, Set<Clazz> searchSpace)
				throws NotFoundException {
			super();
			this.pool = pool;
			this.searchSpace = searchSpace;

			final Clazz[] clz;
			clz = FluentIterable.from(searchSpace).toArray(Clazz.class);
			CtClass[]
					ctclz = pool.get(FluentIterablesIID.from(clz)
							.transform((c) -> c.getName())
							.toArray(String.class));

			classMap = HashBiMap.create();
			for (int ci = 0; ci < clz.length; ci++) {
				classMap.put(clz[ci], ctclz[ci]);
			}

			classMap.forEach((cl,ct) -> {
				if ( isToplevelClass(ct) )
					toplevelClazzesByPackage.put(
							ct.getPackageName()
							, cl);
			});


		}

		public ClassPool getPool() {
			return pool;
		}

		public Set<Clazz> getSearchSpace() {
			return searchSpace;
		}

		private static boolean isToplevelClass( CtClass c ){
			return !c.getName().contains("$");
		}
		private static boolean isPackageDeclaration( CtClass c ){
			return isToplevelClass(c) && "package-info".equals(c.getSimpleName());
		}

		public Set<Clazz> getKeepClazzes(
				Iterable<? extends String> annotnames) {
			List<? extends String> l = Lists
					.newArrayList(annotnames);
			if (l.size() == 0)
				return Sets.newHashSet();// empty

			return
					Maps.filterEntries( classMap,
							(ent) -> {
								for (String ax : l) {
									if (ent.getValue().hasAnnotation(ax))
										return true;
								}
								return false;
							}).entrySet().stream()
					.map((kv) -> kv.getKey() )
					.flatMap( ( cl ) -> {
						CtClass ct = classMap.get( cl );
						Stream<Clazz> rr = Stream.of(cl);
						if ( isPackageDeclaration( ct ) ) {
							//annotated package declaration
							rr = 
									toplevelClazzesByPackage.get( ct.getPackageName() )
									.stream()
									;
						}

						//fetch inner class
						return
								rr.flatMap( (cls)-> {
									CtClass ctcls = classMap.get( cls );
									if ( ! isToplevelClass(ctcls) )
										return Stream.of(cls);

									return
											TraversersV0.stackTraverse(
													ctcls,
													(cx) ->{
														try {
															return Arrays.<CtClass>asList(cx.getNestedClasses());
														} catch (Exception e) {
															throw new RuntimeException(e);
														}
													}
													, Collectors.toSet())
											.stream()
											.map( (k) -> classMap.inverse().get(k) )
											;
								});
						//					return rr;
					} )
					.collect(Collectors.toSet())
					;
		}

	}

}