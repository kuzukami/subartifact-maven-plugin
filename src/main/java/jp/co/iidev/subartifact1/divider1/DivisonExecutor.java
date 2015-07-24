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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ReferenceInspector;
import jp.co.iidev.subartifact1.divider1.DivisonExecutor.FluidClassPathUnit.FluidClass;

public class DivisonExecutor {
	private static Logger log = LoggerFactory.getLogger(DivisonExecutor.class);

	private static void error(String msg, Object... names) {
		log.error(msg, names);
	}

	public static enum ReportSortOrder {
		RO1_SUBMODULE,
		RO2_JAR,
		RO3_ClazzSet,
		RO4_DEBUG
	}

	public static abstract class ClazzSet implements ArtifactFragment {
		private final Set<Clazz> myclazzes;
		private final Supplier<Set<Clazz>> depends;
		private final DivisonExecutor.ReportSortOrder order;

		protected ClazzSet(Set<Clazz> myclazzset,
				Supplier<Set<Clazz>> depends, DivisonExecutor.ReportSortOrder ro) {
			super();
			this.myclazzes = myclazzset;
			this.depends = depends;
			this.order = ro;
		}

		@Override
		public int compareTo(ArtifactFragment o) {
			if (o instanceof DivisonExecutor.ClazzSet) {
				DivisonExecutor.ClazzSet other = (DivisonExecutor.ClazzSet) o;
				int oi = getOrder().compareTo(other.getOrder());
				if (oi != 0)
					return oi;

				return toString().compareTo(other.toString());
			}
			return -1;
		}

		protected Set<Clazz> getMyclazzes() {
			return myclazzes;
		}

		Set<Clazz> getDepends() {
			return depends.get();
		}

		DivisonExecutor.ReportSortOrder getOrder() {
			return order;
		}

	}

	public static interface MyClazzpathUnit/* ArtifactFragamentFinder */ {
		public Map<Clazz, DivisonExecutor.ClazzSet> resolve(Set<Clazz> resolveRequired);

		public static interface Standard extends DivisonExecutor.MyClazzpathUnit {
			public Set<Clazz> getResolvClazzes();

			public DivisonExecutor.ClazzSet forClazzSet(Clazz myclz);

			@Override
			public default Map<Clazz, DivisonExecutor.ClazzSet> resolve(
					Set<Clazz> resolveRequired) {
				return Maps.transformEntries(
						MapsIID.forSet(Sets.newHashSet(Sets.intersection(
								resolveRequired, getResolvClazzes()))),
						(clz, voidx) -> forClazzSet(clz));
			}

		}

		public static interface HasExtraDependencies {
			public void resolveAndAppendExtraDependenciesOnFinish(
					MyClazzpath fullclasspath,
					Multimap<ArtifactFragment, ArtifactFragment> extraDB);
		}
	}

	public class MyClazzpath {
		private final List<DivisonExecutor.MyClazzpathUnit> units;
		private final Multimap<ArtifactFragment, ArtifactFragment> extractAdjacent = HashMultimap
				.create();
		private final Map<Clazz, DivisonExecutor.ClazzSet> resolvCache = Maps.newHashMap();

		protected MyClazzpath(List<? extends DivisonExecutor.MyClazzpathUnit> units) {
			this.units = Lists.newArrayList(units);
			fix();
		}

		public Set<ArtifactFragment> getAdjacent(ArtifactFragment a) {
			Iterable<? extends ArtifactFragment> ia = Lists.newArrayList();
			if (a instanceof DivisonExecutor.ClazzSet) {
				DivisonExecutor.ClazzSet czs = (DivisonExecutor.ClazzSet) a;
				ia = resolve(czs.getDepends()).values();
			}

			return Sets.newHashSet(
					Iterables.concat(extractAdjacent.get(a), ia));
		}

		public Map<Clazz, DivisonExecutor.ClazzSet> resolve(Set<Clazz> resolveRequiredO) {
			{
				// cache operation
				Set<Clazz> resolveRequired = resolveRequiredO;
				resolveRequired = Sets.newHashSet(Sets
						.difference(resolveRequired, resolvCache.keySet()));
				// ensure to load into the cache
				for (DivisonExecutor.MyClazzpathUnit m : units) {
					if (resolveRequired.isEmpty())
						break;
					Map<Clazz, DivisonExecutor.ClazzSet> res = m.resolve(resolveRequired);
					resolvCache.putAll(res);
					resolveRequired = Sets.newHashSet(
							Sets.difference(resolveRequired, res.keySet()));
				}

				if (!resolveRequired.isEmpty())
					error("There are unknown classes: {}",
							Joiner.on(", ").join(resolveRequired));
			}

			Map<Clazz, DivisonExecutor.ClazzSet> s = Maps.newHashMap();
			for (Clazz c : resolveRequiredO) {
				if (resolvCache.containsKey(c))
					s.put(c, resolvCache.get(c));
			}
			// s.putAll( Maps.filterKeys(resolvCache,
			// Predicates.in(resolveRequiredO) )); //slow...

			return s;
		}

		public void fix() {
			for (DivisonExecutor.MyClazzpathUnit m : units) {
				if (m instanceof MyClazzpathUnit.HasExtraDependencies) {
					MyClazzpathUnit.HasExtraDependencies ed = (MyClazzpathUnit.HasExtraDependencies) m;
					ed.resolveAndAppendExtraDependenciesOnFinish(this,
							extractAdjacent);
				}
			}

		}
	}

	/**
	 * メインのjarのうち、プロジェクトのルートとして指定を受けていないクラスばっかり入ったjarと同等の動きをする。
	 * ここから出ていく ClazzSetは、Fluidタイプで、Artifact依存関係の先に移動できるようになっている。(Fluid Vertex)
	 * @author kuzukami_user
	 *
	 */
	public static class FluidClassPathUnit
	implements MyClazzpathUnit.Standard {
		final Set<Clazz> myClazzes;

		protected FluidClassPathUnit( Set<Clazz> classForJar) {
			this.myClazzes = classForJar;
		}


		@Override
		public Set<Clazz> getResolvClazzes() {
			return myClazzes;
		}

		@Override
		public DivisonExecutor.ClazzSet forClazzSet(Clazz myclz) {
			return FluidClass.forSingleClass(myclz);
		}

		static class FluidClass extends DivisonExecutor.ClazzSet
		implements ArtifactFragment.Fluid {

			public static FluidClassPathUnit.FluidClass forSingleClass(Clazz clazz) {
				Set<Clazz> myclz = Collections.singleton(clazz);
				Supplier<Set<Clazz>> depsFull = naturalDepends(myclz);

				return new FluidClass(myclz, depsFull);
			}

			private FluidClass(Set<Clazz> myclazzset,
					Supplier<Set<Clazz>> depends) {
				super(myclazzset, depends, ReportSortOrder.RO3_ClazzSet);
			}
			private Clazz getClazz(){ return Iterables.get(getMyclazzes(), 0); }

			@Override
			public String toString() {
				return "FluidClass [getClazz()=" + getClazz() + "]";
			}

		}

	}

	protected static Supplier<Set<Clazz>> naturalDepends(
			Set<Clazz> myclazzset) {
		return () -> {
			if (myclazzset.size() == 1)
				return Sets.difference(
						Iterables.get(myclazzset, 0).getDependencies(),
						myclazzset);

			return Sets
					.newHashSet(
							Sets.difference(
									myclazzset.stream().flatMap((c) -> c
											.getDependencies().stream())
									.collect(Collectors.toSet()),
									myclazzset));
		};
	}

	/**
	 * １つの新規されるサブアーチファクトについて、
	 * ルートとされるクラス群を受け持つクラスパス要素でかつ、OutputArtifact Vertex
	 * 
	 * @author kuzukami_user
	 *
	 */
	public abstract static class SubArtifactRoot extends DivisonExecutor.ClazzSet
	implements ArtifactFragment.OutputArtifact,
	MyClazzpathUnit.Standard {
		protected final SubArtifactDefinition submodule;

		protected SubArtifactRoot(SubArtifactDefinition submodule,
				Set<Clazz> myclazzes, Supplier<Set<Clazz>> deps) {
			super(myclazzes, deps, ReportSortOrder.RO1_SUBMODULE);
			this.submodule = submodule;
		}

		@Override
		public Set<Clazz> getResolvClazzes() {
			return getMyclazzes();
		}


		@Override
		public String toString() {
			return "SubArtifactRoot [submodule=" + submodule + "]";
		}

		public static SubArtifactRoot.LessMemory forLessMemory(SubArtifactDefinition submodule,
				Set<Clazz> myclazzes) {
			return new LessMemory(submodule, myclazzes);
		}

		public static SubArtifactRoot.DetailTrace forDetailTrace(SubArtifactDefinition submodule,
				Set<Clazz> myclazzes) {
			return new DetailTrace(submodule, myclazzes);
		}

		public static class LessMemory extends DivisonExecutor.SubArtifactRoot {
			protected LessMemory(SubArtifactDefinition submodule,
					Set<Clazz> myclazzes) {
				super(submodule, myclazzes, naturalDepends(myclazzes));
			}

			@Override
			public DivisonExecutor.ClazzSet forClazzSet(Clazz myclz) {
				return this;
			}

		}

		public static class DetailTrace extends DivisonExecutor.SubArtifactRoot
		implements MyClazzpathUnit.HasExtraDependencies {
			private final Map<Clazz, SubArtifactRootClass> clazzesForTrace;

			protected DetailTrace(SubArtifactDefinition submodule,
					Set<Clazz> myclazzes) {
				super(submodule, myclazzes, () -> myclazzes);

				clazzesForTrace = Maps.newHashMap(
						Maps.transformEntries(MapsIID.forSet(myclazzes),
								(clx, vd) -> new SubArtifactRootClass(
										clx, Collections.singleton(clx))));

			}

			@Override
			public void resolveAndAppendExtraDependenciesOnFinish(
					MyClazzpath fullclasspath,
					Multimap<ArtifactFragment, ArtifactFragment> extraDB) {
				for (SubArtifactRootClass m : clazzesForTrace.values())
					extraDB.put(m, this);
			}

			public class SubArtifactRootClass extends DivisonExecutor.ClazzSet
			implements DebugContractable {
				private SubArtifactRootClass(Clazz myclazz, Set<Clazz> myclazzset) {
					super(myclazzset, naturalDepends(myclazzset),
							ReportSortOrder.RO4_DEBUG);
				}
				private SubArtifactDefinition getMod(){ return DetailTrace.this.submodule; }
				private Clazz getClazz(){ return Iterables.get(getMyclazzes(), 0); }


				@Override
				public String toString() {
					return "SubArtifactRootClass [getClazz()=" + getClazz()
					+ ", getMod()=" + getMod() + "]";
				}
				@Override
				public ArtifactFragment getContractDestination() {
					return DetailTrace.this;
				}
			}

			@Override
			public DivisonExecutor.ClazzSet forClazzSet(Clazz myclz) {
				return clazzesForTrace.get(myclz);
			}

		}
	}

	public abstract static class LibArtifact extends DivisonExecutor.ClazzSet implements
	ArtifactFragment.LibraryArtifact, MyClazzpathUnit.Standard {
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
		public Set<Clazz> getResolvClazzes() {
			return getMyclazzes();
		}

		protected static DivisonExecutor.LibArtifact forLessMemory( Dependency dep,  File jarFile,
				ClazzpathUnit jarUnit, Predicate<Clazz> targetFilter) {
			Set<Clazz> myclz = Sets.filter(jarUnit.getClazzes(),
					targetFilter);
			return new LessMemory(jarFile, myclz, () -> Sets.newHashSet(), dep);
		}

		public static DivisonExecutor.LibArtifact forDetailTrace( Dependency dep, File jarFile,
				ClazzpathUnit jarUnit, Predicate<Clazz> targetFilter) {
			Set<Clazz> myclz = Sets.filter(jarUnit.getClazzes(),
					targetFilter);
			return new DetailTrace(jarFile, myclz, dep );
		}

		public static class LessMemory extends DivisonExecutor.LibArtifact {
			protected LessMemory(File jarFile, Set<Clazz> myClazz,
					Supplier<Set<Clazz>> dependencies, Dependency dep ) {
				super(jarFile, myClazz, dependencies, dep);
			}

			@Override
			public DivisonExecutor.ClazzSet forClazzSet(Clazz myclz) {
				return this;
			}

		}

		public static class DetailTrace extends DivisonExecutor.LibArtifact
		implements MyClazzpathUnit.HasExtraDependencies {

			private Map<Clazz, LibClass> apiClass = Maps.newHashMap();

			protected DetailTrace(File jarFile, Set<Clazz> myClazz, Dependency dep) {
				super(jarFile, myClazz, () -> myClazz, dep );
				for (Clazz c : myClazz) {
					apiClass.put(c, new LibClass(Collections.singleton(c),
							ReportSortOrder.RO4_DEBUG));
				}
			}

			public class LibClass extends DivisonExecutor.ClazzSet
			implements DebugContractable {

				protected LibClass(Set<Clazz> myclazzset,
						DivisonExecutor.ReportSortOrder ro) {
					super(myclazzset, () -> Collections.emptySet(), ro);
				}
				private String getLibname() { return  DetailTrace.this.jarFile.getName(); }
				private Clazz getClazz() { return  Iterables.get(getMyclazzes(), 0); }


				@Override
				public String toString() {
					return "LibClass [getClazz()=" + getClazz()
					+ ", getLibname()=" + getLibname() + "]";
				}
				@Override
				public ArtifactFragment getContractDestination() {
					return DetailTrace.this;
				}

			}

			@Override
			public void resolveAndAppendExtraDependenciesOnFinish(
					MyClazzpath fullclasspath,
					Multimap<ArtifactFragment, ArtifactFragment> extraDB) {
				for (LibClass c : apiClass.values())
					extraDB.put(c, this);
			}

			@Override
			public DivisonExecutor.ClazzSet forClazzSet(Clazz myclz) {
				return apiClass.get(myclz);
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
		
		protected SubArtifactDeployment(SubArtifactDefinition target,
				List<SubArtifactDefinition> subartDeps, LinkedHashMap<File, Dependency> jarDeps
				, List<String> deployClassNames
				) {
			super();
			this.target = target;
			this.subartDeps = subartDeps;
			this.jarDeps = jarDeps;
			this.deployClassNames = deployClassNames;
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
		
	}


	public 
	LinkedHashMap<SubArtifactDefinition, SubArtifactDeployment>
	planDivision(
			File targetJarInClasspath,
			Iterable<SubArtifactDefinition> subartifactsInTargetJar,
			LinkedHashMap<File, Dependency> classpath,
			LoggableFactory lgf
			) throws Exception {
		ClassPool loader = new ClassPool(false);
		for (File jf : classpath.keySet())
			loader.appendClassPath(jf.getAbsolutePath());

		Clazzpath clazzpath = new Clazzpath();
		LinkedHashMap<File, ClazzpathUnit> jars = StreamSupport
				.stream(classpath.keySet().spliterator(), false)
				.collect(Collectors.toMap((f) -> f,
						(f) -> addcu(clazzpath, f), (v, v2) -> v2,
						LinkedHashMap::new));
		;

		File mainjar = targetJarInClasspath;


		ClazzpathUnit mainJarCpUnit = jars.get(mainjar);
		Set<Clazz> mainJarLibDependencies =
				Sets.difference( mainJarCpUnit.getTransitiveDependencies(), mainJarCpUnit.getClazzes() );

		DivisonExecutor.PackgingCache pc = new PackgingCache(loader, mainJarCpUnit.getClazzes());

		List<? extends DivisonExecutor.SubArtifactRoot> submoduleRoots =
				FluentIterable
				.from(subartifactsInTargetJar)
				.transform(
						(jm) -> SubArtifactRoot.forDetailTrace(jm,
								pc.getKeepClazzes(jm.getRootClassAnnotations())))
				.toList()
//				.toImmutableList()
				;
		List<? extends DivisonExecutor.LibArtifact> libs =
				jars.entrySet().stream()
				.filter((f2clz) -> !mainjar.equals(f2clz.getKey()))
				.map((f2clz) ->
					LibArtifact.forDetailTrace(
						classpath.get( f2clz.getKey() )
						, f2clz.getKey()
						, f2clz.getValue()
						, Predicates.in( mainJarLibDependencies ) ))
				.collect(Collectors.toList());

		DivisonExecutor.FluidClassPathUnit fluidClasses = new FluidClassPathUnit(
				mainJarCpUnit.getClazzes());

		MyClazzpath clzpath = new MyClazzpath(
				FluentIterablesIID
				.ofConcat(
						submoduleRoots
						, Arrays.asList(fluidClasses)
						, libs)
//				.toImmutableList()
				.toList()
				);
		try (FullTracablePlanAcceptor pa = new FullTracablePlanAcceptor()) {
			new ArtifactDivisionPlanner(lgf).main(
					Sets.newHashSet(submoduleRoots.subList(1, submoduleRoots.size())),
					adjacencyFunction(clzpath), submoduleRoots.get(0), pa);
			return pa.renderDeployments().stream()
					.collect(
							Collectors.toMap(
									(v) -> v.target
									, (v) -> v
									, (v1,v2) -> v1
									, LinkedHashMap::new
									));
		}
	}

	private final class FullTracablePlanAcceptor implements PlanAcceptor {
		private List<DeploymentUnit> deployment = Lists.newArrayList();
		String tabPad = "  ";
	
		public void finalReport() {
	
			deployment.stream().sorted(ducmp())
			.collect(Collectors
					.groupingBy((du) -> du.deploymentAnchor))
			.forEach((deployAnchor, list_of_deployee) -> {
				System.out.println("Sub-Artifact Deployment Description For : "
						+ deployAnchor.toString());
				if (deployAnchor instanceof DivisonExecutor.SubArtifactRoot) {
					DivisonExecutor.SubArtifactRoot submod = (DivisonExecutor.SubArtifactRoot) deployAnchor;
					submod.getMyclazzes().stream().sorted()
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
	
		private Comparator<DeploymentUnit> ducmp() {
			Comparator<DeploymentUnit> c = Comparator
					.comparing((du) -> du.deploymentAnchor);
	
			return c.thenComparing(
					Comparator.comparing((du) -> du.deployeeFragment));
		}
	
		abstract class DeploymentUnit {
			final ArtifactFragment deploymentAnchor;
			final ArtifactFragment deployeeFragment;
	
			protected DeploymentUnit(ArtifactFragment deploymentAnchor,
					ArtifactFragment deployeeFragment) {
				super();
				this.deploymentAnchor = deploymentAnchor;
				this.deployeeFragment = deployeeFragment;
			}
	
			abstract List<String> getReason(int tabIndent);
		}
	
		@Override
		public void reportAnchorDeploymentPlan(
				ArtifactFragment deployAnchor,
				ArtifactFragment dependeeAnchor,
				com.google.common.base.Supplier<List<ArtifactFragment>> computer_Deploy2dependeeShortestPath) {
			deployment.add(
					new DeploymentUnit(deployAnchor, dependeeAnchor) {
						@Override
						List<String> getReason(int tabIndent) {
							List<String> k = Lists.newArrayList();
							int i = tabIndent;
							List<ArtifactFragment> downpath = computer_Deploy2dependeeShortestPath
									.get();
							Collections.reverse(downpath);
	
							for (ArtifactFragment af : Iterables.skip(downpath,
									1)) {
								k.add(StringsIID.replaceTemplateAsSLF4J(
										"{}{}{}", StringUtils.repeat(tabPad, i),
										"<=", af.toString()));
								i++;
							}
							return k;
						}
					});
		}
	
		@Override
		public void reportFluidArtifactFragmentDeploymentPlan(
				ArtifactFragment deployAnchor,
				ArtifactFragment deployee,
				List<ReferenceInspector> forEachFluidReachingAnchor) {
			deployment.add(new DeploymentUnit(deployAnchor, deployee) {
				@Override
				List<String> getReason(int tabIndent) {
					List<String> k = Lists.newArrayList();
					for (ReferenceInspector riByDirectAnchor : forEachFluidReachingAnchor) {
						int i = tabIndent;
						List<ArtifactFragment> uppath = riByDirectAnchor
								.computeDeploymentAnchor2fuildReachingAnchorUpShortestPath();
						List<ArtifactFragment> downpath = riByDirectAnchor
								.computeFuildReachingAnchor2deploymeeFragamentDownShortestPath();
	
						Collections.reverse(downpath);
						Collections.reverse(uppath);
	
						for (ArtifactFragment af : Iterables
								.skip(downpath, 1)) {
							k.add(StringsIID
									.replaceTemplateAsSLF4J("{}{}{}",
											StringUtils.repeat(tabPad,
													i),
											"<=", af.toString()));
							i++;
						}
						for (ArtifactFragment af : Iterables
								.skip(uppath, 1)) {
							k.add(StringsIID.replaceTemplateAsSLF4J(
									"{}=>{}",
									StringUtils.repeat(tabPad, i),
									af.toString()));
							i++;
						}
					}
					return k;
				}
			});
		}
		
		
		public List<SubArtifactDeployment> renderDeployments(){
			Map<ArtifactFragment, List<DeploymentUnit>> subartifacts = 
			deployment.stream()
			.collect(Collectors.groupingBy((du) -> du.deploymentAnchor ) )
			;
			
			List<SubArtifactDeployment> l = Lists.newArrayList();
			
			for ( Map.Entry<ArtifactFragment, List<DeploymentUnit>> a : subartifacts.entrySet() ){
				SubArtifactRoot main = (SubArtifactRoot)a.getKey();
				LinkedHashMap<File, Dependency>  jardeps = Maps.newLinkedHashMap();
				List<SubArtifactDefinition> subart_deps   = Lists.newArrayList();
				List<String> deploy_classnames = Lists.newArrayList();
				
				for ( DeploymentUnit du : a.getValue() ){
					ArtifactFragment tgt = du.deployeeFragment;
					if (tgt instanceof FluidClass) {
						FluidClass fc = (FluidClass) tgt;
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
				
				for ( Clazz rootCls : main.getMyclazzes() )
					deploy_classnames.add(rootCls.getName());
						
				
				SubArtifactDeployment sa = new SubArtifactDeployment(
						main.submodule, subart_deps,
						jardeps
						,deploy_classnames );
				l.add( sa );
			}
			
			return l;
		}
	
		@Override
		public void close() throws Exception {
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