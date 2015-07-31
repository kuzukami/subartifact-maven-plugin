package jp.co.iidev.subartifact1.divider1;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.model.Dependency;
import org.codehaus.plexus.util.SelectorUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Ordering;
import com.google.common.collect.Sets;
import com.google.common.io.CharStreams;

//import javassist.ClassPool;
//import javassist.CtClass;
//import javassist.NotFoundException;
import jp.co.iidev.subartifact1.api.SubArtifactDefinition;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ArtifactFragment;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanAcceptor;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.AFPredicateInconsitency;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.CyclicArtifact;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ReferenceInspector;
import jp.co.iidev.subartifact1.divider1.DivisionExecutor.RelocatablesClassPathUnit.Relocatable;
import jp.co.iidev.subartifact1.divider1.JARIndex.MyJarEntry;
import jp.co.iidev.subartifact1.divider1.mojo.OptionalPropagation;
import jp.co.iidev.subartifact1.divider1.mojo.RootSet;
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

	// Supplier<Set<FragmentName>> naturalDependsX(
	// Set<FragmentName> names) {
	// hoge
	// return () -> {
	// if (names.size() == 1)
	// return Sets.difference(
	// Sets.newHashSet( depF.apply( Iterables.get(names, 0) ) ),
	// names);
	//
	// return names.stream()
	// .flatMap((c) ->
	// StreamSupport.stream( depF.apply(c).spliterator(), false )
	// )
	// .filter((c) -> ! names.contains(c) )
	// .collect(Collectors.toSet());
	// };
	// }

	public LinkedHashMap<SubArtifactDefinition, SubArtifactDeployment> planDivision(
			File targetJarInClasspath, String rootSubartifactId,
			Iterable<? extends SubArtifact> subartifactsInTargetJar,
			LinkedHashMap<File, Dependency> compiletimeClasspath,
			Predicate<File> outputClasspathAliveFilter,
			OptionalPropagation[] defaultRootTransitivePropagations,
			OptionalPropagation[] defaultRootSourceRefernecePropagations,
			OptionalPropagation[] defaultSubartifactSourceReferencePropagations,
			OptionalPropagation[] globalReferencePropagations,
			LoggableFactory lgf)
					throws CyclicArtifact, AFPredicateInconsitency {
		// ClassPool loader = new ClassPool(false);
		// for (File jf : compiletimeClasspath.keySet())
		// loader.appendClassPath(jf.getAbsolutePath());

		// Clazzpath clazzpath = new Clazzpath();
		// LinkedHashMap<File, ClazzpathUnit> jars = StreamSupport
		// .stream(compiletimeClasspath.keySet().spliterator(), false)
		// .collect(Collectors.toMap((f) -> f,
		// (f) -> addcu(clazzpath, f), (v, v2) -> v2,
		// LinkedHashMap::new));
		// ;

		List<File> jarsByClasspathOrder =  Lists.newArrayList(
				Sets.filter(
						compiletimeClasspath.keySet(),
						(f) -> f.getName().endsWith(".jar") ));
				
		LoadingCache<File, JARIndex> jindexCache =
				CacheBuilder.newBuilder()
				.weakValues()
				.build( CacheLoader.from( (jf) -> {
					try {
						return JARIndex.index(jf);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}) );
		
		File mainjar = targetJarInClasspath;

		MainJarManager pc = new MainJarManager(
				new DetailJarAnalysis(jindexCache.getUnchecked(mainjar)));
		pc.propagateGlobalReference(globalReferencePropagations);
		

		SubArtifact rootSubart;
		{
			rootSubart = new SubArtifact();
			rootSubart.setArtifactId(rootSubartifactId);
			rootSubart.setExtraDependencies(new Dependency[0]);
			rootSubart.setRootSets(new RootSet[0]);
			// rootSubart.setOmittableIfEmpty(true);
		}

		FluentIterable<? extends DivisionExecutor.SubArtifactRoot> subartifacts_FirstOneIsRoot =
				FluentIterablesIID
				.copy(FluentIterablesIID
						.ofConcat(
								Arrays.asList(rootSubart)
								, subartifactsInTargetJar)
						.transform((jm) ->
						SubArtifactRoot.forDetailTrace(
								jm
								,
								pc
								,
								defaultRootTransitivePropagations
								,
								defaultRootSourceRefernecePropagations
								,
								defaultSubartifactSourceReferencePropagations
								)));
		
		
		List<DivisionExecutor.LibArtifact> libs = Lists.newArrayList();
		{
			
			Set<FragmentName> externalDep = 
					pc.getExternallyDependentNames();
			
			for (  File jarf : jarsByClasspathOrder ){
				if ( jarf.equals(mainjar) ) continue;//passthru
				
				Set<FragmentName> nameinjar =
						jindexCache.getUnchecked(jarf).getEntries().keySet();
								
				LibArtifact la =
						LibArtifact.forDetailTrace(
								compiletimeClasspath.get(jarf)
								, jarf
								, Sets.newHashSet( Sets.intersection( externalDep, nameinjar ) )
								);
				
				libs.add(la);
				
				externalDep =
						Sets.newHashSet( Sets.difference( externalDep, nameinjar ) );
			}
			
			if ( !externalDep.isEmpty()  ){
				error( "There are {} unkonwn classes. They will be reported in detail later. : {}" ,
						externalDep.size(),
						Joiner.on(", ").join(externalDep)
						);
			}
		}

		DivisionExecutor.RelocatablesClassPathUnit relocatableClasses =
				RelocatablesClassPathUnit.forResourceSet(
						pc.getLeftAll().keySet(),
						pc );

		MyClazzpath clzpath = new MyClazzpath(
				FluentIterablesIID.ofConcat(
						subartifacts_FirstOneIsRoot
						, Arrays.asList(relocatableClasses)
						, libs)
				.toList());
		
		try (FullTracablePlanAcceptor pa = new FullTracablePlanAcceptor()) {
			new ArtifactDivisionPlanner(lgf).computeAndReportDeploymentUnits(
					Sets.<ArtifactFragment> newHashSet(
							subartifacts_FirstOneIsRoot.skip(1)),
					adjacencyFunction(clzpath),
					subartifacts_FirstOneIsRoot.get(0), pa);
			LinkedHashMap<SubArtifactDefinition, SubArtifactDeployment> deployments
			=  pa.integrateFullPlanOrderedByBuildSequence(
					(art) -> {
						//lib artifact filter
						if (art instanceof LibArtifact) {
							LibArtifact la = (LibArtifact) art;
							return outputClasspathAliveFilter.apply(la.jarFile);
						}
						return true;
					}).stream().collect(
							//collect to LinkedHashMap
							Collectors.toMap((v) -> v.target, (v) -> v, (v1, v2) -> {
								throw new RuntimeException("duplicated reports. bug?:"
										+ v1 + " <=> " + v2);
							} , LinkedHashMap::new));
			
			
			return  deployments;

			
		} catch (CyclicArtifact e) {
			e.logReasonAsError(log);
			throw e;
		} catch (AFPredicateInconsitency e) {
			e.logReasonAsError(log);
			throw e;
		}
	}

	public static enum ReportSortOrder {
		RO1_SUBMODULE,
		RO2_JAR,
		RO3_RELOCATABLE,
//		RO4_RESOURCE,
		RO5_DEBUG,;
	}

	/**
	 * 名前{@link FragmentName}と実体{@link MyFragment}を結びつける。
	 * ここで結びつけできなければ、もう結びつけできないことが確定する。
	 * 
	 * @author kuzukami_user
	 *
	 */
	public static interface AuthorizedNameResolver {
		MyFragment resolve(MyFragment source, FragmentName fragmentName);
	}

	public static interface MyFragment extends ArtifactFragment {
		/**
		 * このFragmentの依存先フラグメント
		 * 
		 * @param r
		 * @return
		 */
		Set<MyFragment> dependencyTargets(AuthorizedNameResolver r);
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

		/**
		 * このFragmentParityが必要としている依存先リソース名
		 * 
		 * @return
		 */
		public Set<FragmentName> getDependencyTargetNames();

		public DivisionExecutor.ReportSortOrder getOrder();

		/**
		 * {@link #getDependencyTargetNames()}の名前解決を行ったFragmentのSetと、
		 * 引き続きの引数で指定されたフラグメントのUnionを取って依存先を計算する。
		 * 
		 * @param r
		 * @param extraDeps
		 * @return
		 */
		default Set<MyFragment> dependencyTargets_UnionOfResolvedDependencyNamesAndExtraFragments(
				AuthorizedNameResolver r, MyFragment... extraDeps) {
			Set<MyFragment> x = Sets.newHashSet();
			for (FragmentName c : getDependencyTargetNames()) {
				MyFragment rx = r.resolve(this, c);
				if (rx == null) {
					// resolve error => unknown class or resource name
				} else {
					x.add(rx);
				}
			}
			x.addAll(Arrays.asList(extraDeps));
			return x;
		}

	}

	static abstract class FragmentSet
			implements ArtifactFragment, MyFragment, FragmentParty {
		// private final Set<FragmentName> memberNameSet;
		// private final Supplier<Set<FragmentName>> dependencyTargetName;
		private final DivisionExecutor.ReportSortOrder order;

		protected FragmentSet(
				// Set<FragmentName> memberNameSet,
				// Supplier<Set<FragmentName>> depends,
				DivisionExecutor.ReportSortOrder ro) {
			super();
			// this.memberNameSet = memberNameSet;
			// this.dependencyTargetName = depends;
			this.order = ro;
		}

		@Override
		public int compareTo(ArtifactFragment o) {
			return FragmentParty.super.compareTo(o);
		}

		// public Set<FragmentName> getMemberNames() {
		// return memberNameSet;
		// }

		// public Set<FragmentName> getDependencyTargetNames() {
		// return dependencyTargetName.get();
		// }

		public DivisionExecutor.ReportSortOrder getOrder() {
			return order;
		}

	}

	static abstract class FragmentUnit
			implements ArtifactFragment, MyFragment, FragmentParty {
		private final FragmentName myName;
		private final DivisionExecutor.ReportSortOrder order;

		protected FragmentUnit(FragmentName myclazzset,
				DivisionExecutor.ReportSortOrder ro) {
			super();
			this.myName = myclazzset;
			this.order = ro;
		}

		@Override
		public int compareTo(ArtifactFragment o) {
			return FragmentParty.super.compareTo(o);
		}

		public abstract Set<FragmentName> getDependencyTargetNames();

		// public Set<FragmentName> getMemberNames() {
		// return Collections.singleton( getMyName() );
		// }

		public DivisionExecutor.ReportSortOrder getOrder() {
			return order;
		}

		FragmentName getMyName() {
			return myName;
		}

	}

	/**
	 * 許可されている範囲で、 名前{@link FragmentName}と実体{@link FragmentParty}を結びつけるもの
	 * 
	 * @see AuthorizedNameResolver 完全に名前をと実体を結びつけるもの。
	 * @author kuzukami_user
	 *
	 */
	static interface PartialNameResolver/* ArtifactFragamentFinder */ {
		/**
		 * 許可されている範囲で最大限の解決結果を返す
		 * 
		 * @param resolveRequired
		 * @return
		 */
		public Map<FragmentName, DivisionExecutor.FragmentParty> resolveOnlyAuthorized(
				Set<FragmentName> resolveRequired);

		static interface Standard extends DivisionExecutor.PartialNameResolver {
			/**
			 * このレゾルバでの解決を許可されている名前
			 * 
			 * @return
			 */
			public Set<FragmentName> getAuthorizedNames();

			/**
			 * 一つの名前をレゾルバで解決
			 * 
			 * @param authorizedNameForMe
			 *            かならず{@link #getAuthorizedNames()}に含まれている必要あり
			 * @return
			 */
			public DivisionExecutor.FragmentParty resolveOne(
					FragmentName authorizedNameForMe);

			@Override
			public default Map<FragmentName, DivisionExecutor.FragmentParty> resolveOnlyAuthorized(
					Set<FragmentName> resolveRequired) {
				return Maps.transformEntries(
						MapsIID.forSet(Sets.newHashSet(Sets.intersection(
								resolveRequired, getAuthorizedNames()))),
						(clz, voidx) -> resolveOne(clz));
			}

			static interface Resourced extends Standard {
				MainJarManager getJarManager();

				default Set<FragmentName> naturalDepedencyTargets(
						FragmentName memberEnsured) {
					return getJarManager().getDependentNames(memberEnsured);
				}

			}

		}

		// public static interface HasExtraDependencies {
		// public void resolveAndAppendExtraDependenciesOnFinish(
		// MyClazzpath fullclasspath,
		// Multimap<ArtifactFragment, ArtifactFragment> extraDB);
		// }
	}

	public class MyClazzpath implements AuthorizedNameResolver {
		private final List<DivisionExecutor.PartialNameResolver> partialResolvers;
		// private final Multimap<ArtifactFragment, ArtifactFragment>
		// extractAdjacent = HashMultimap.create();
		private final Map<FragmentName, FragmentParty> resolvCache = Maps
				.newHashMap();

		protected MyClazzpath(
				List<? extends DivisionExecutor.PartialNameResolver> units) {
			this.partialResolvers = Lists.newArrayList(units);
			// fix();
		}

		public Set<ArtifactFragment> getAdjacent(ArtifactFragment a) {
			// if ( false ){
			// Iterable<? extends ArtifactFragment> ia = Lists.newArrayList();
			// if (a instanceof DivisionExecutor.ClazzSet) {
			// DivisionExecutor.ClazzSet czs = (DivisionExecutor.ClazzSet) a;
			// ia = resolve(czs.getDependingName(),
			// (clz) -> {
			// List<String> k = Lists.newArrayList();
			// for ( Clazz refc : czs.getMyNames() ){
			// if ( refc.getDependencies().contains(clz) ){
			// k.add( refc.getName() );
			// }}
			// return k;
			// } ).values();
			// }
			//
			// return Sets.newHashSet(
			// Iterables.concat(extractAdjacent.get(a), ia));
			// }else{
			Iterable<? extends ArtifactFragment> ia = Lists.newArrayList();
			if (a instanceof FragmentParty) {
				FragmentParty b = (FragmentParty) a;
				ia = b.dependencyTargets(this);
			}
			return Sets.newHashSet(ia);
			// }
		}

		@Override
		public MyFragment resolve(MyFragment source,
				FragmentName fragmentName) {
			return resolve(Collections.singleton(fragmentName),
					(x) -> Arrays.asList(source.toString())).get(fragmentName);
		}

		private Map<FragmentName, DivisionExecutor.FragmentParty> resolve(
				Set<FragmentName> resolveRequiredO,
				Function<FragmentName, List<String>> inverseReferencerLookupF) {
			{
				// cache operation
				Set<FragmentName> resolveRequired = resolveRequiredO;
				resolveRequired = Sets.newHashSet(
						Sets.difference(resolveRequired, resolvCache.keySet()));
				// ensure to load into the cache
				for (DivisionExecutor.PartialNameResolver m : partialResolvers) {
					if (resolveRequired.isEmpty())
						break;
					Map<FragmentName, DivisionExecutor.FragmentParty> res = m
							.resolveOnlyAuthorized(resolveRequired);
					resolvCache.putAll(res);
					resolveRequired = Sets.newHashSet(
							Sets.difference(resolveRequired, res.keySet()));
				}

				if (!resolveRequired.isEmpty())
					for (FragmentName unknownClz : resolveRequired) {
						// http://stackoverflow.com/questions/18769282/does-anyone-have-background-on-the-java-annotation-java-lang-synthetic
						if ("java.lang.Synthetic"
								.equals(unknownClz.getResName())) {
							// see org/objectweb/asm/ClassReader.java
							// workaround for a bug in javac (javac compiler
							// generates a parameter
							// annotation array whose size is equal to the
							// number of parameters in
							// the Java source file, while it should generate an
							// array whose size is
							// equal to the number of parameters in the method
							// descriptor - which
							// includes the synthetic parameters added by the
							// compiler). This work-
							// around supposes that the synthetic parameters are
							// the first ones.
							debug("{} is found in ( {} )",
									unknownClz.getResName(),
									Joiner.on(", ")
											.join(inverseReferencerLookupF
													.apply(unknownClz)));
						} else {
							error("There is an unknown class {} in ( {} )",
									unknownClz.getResName(),
									Joiner.on(", ")
											.join(inverseReferencerLookupF
													.apply(unknownClz)));
						}
					}
			}

			Map<FragmentName, DivisionExecutor.FragmentParty> s = Maps
					.newHashMap();
			for (FragmentName c : resolveRequiredO) {
				if (resolvCache.containsKey(c))
					s.put(c, resolvCache.get(c));
			}
			// s.putAll( Maps.filterKeys(resolvCache,
			// Predicates.in(resolveRequiredO) )); //slow...

			return s;
		}

		// public void fix() {
		// for (DivisionExecutor.PartialNameResolver m : partialResolvers) {
		// if (m instanceof PartialNameResolver.HasExtraDependencies) {
		// PartialNameResolver.HasExtraDependencies ed =
		// (PartialNameResolver.HasExtraDependencies) m;
		// ed.resolveAndAppendExtraDependenciesOnFinish(this,
		// extractAdjacent);
		// }
		// }
		//
		// }
	}

	/**
	 * メインのjarのうち、プロジェクトのルートとして指定を受けていないクラスばっかり入ったjarと同等の動きをする。 ここから出ていく
	 * ClazzSetは、Fluidタイプで、Artifact依存関係の先に移動できるようになっている。(Fluid Vertex)
	 * 
	 * @author kuzukami_user
	 *
	 */
	static class RelocatablesClassPathUnit
			implements PartialNameResolver.Standard.Resourced {
		final MainJarManager jarmanager;
		final Map<FragmentName, FragmentParty> authorizedNames;

		protected RelocatablesClassPathUnit(Set<FragmentName> authorizedNames,
				MainJarManager resource) {
			this.authorizedNames = Maps.newHashMap(
					Maps.asMap(authorizedNames, (nm) -> forSingleClass(nm)));
			this.jarmanager = resource;
		}
		protected static RelocatablesClassPathUnit forResourceSet(
				Set<FragmentName> targets,
				MainJarManager resource) {
			return new RelocatablesClassPathUnit(targets, resource);
		}

		@Override
		public Set<FragmentName> getAuthorizedNames() {
			return authorizedNames.keySet();
		}

		@Override
		public DivisionExecutor.FragmentParty resolveOne(
				FragmentName authorizedNameForMe) {
			return authorizedNames.get(authorizedNameForMe);
		}

		private RelocatablesClassPathUnit.Relocatable forSingleClass(
				FragmentName clazz) {
			return new Relocatable(clazz);
		}



		@Override
		public MainJarManager getJarManager() {
			return jarmanager;
		}



		class Relocatable extends DivisionExecutor.FragmentUnit
				implements ArtifactFragment.RelocatableFragment {

			private Relocatable(FragmentName myclazzset) {
				super(myclazzset, ReportSortOrder.RO3_RELOCATABLE);
			}

//			private FragmentRef getName() {
//				return getMyName();
//			}

			@Override
			public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
				return dependencyTargets_UnionOfResolvedDependencyNamesAndExtraFragments(
						r);
			}



			@Override
			public String toString() {
				return  Relocatable.class.getSimpleName() +  " [" + getMyName() + "]";
			}

			@Override
			public Set<FragmentName> getDependencyTargetNames() {
				return naturalDepedencyTargets(getMyName());
			}

		}

	}

	/**
	 * １つの新規されるサブアーチファクトについて、 ルートとされるクラス群を受け持つクラスパス要素でかつ、OutputArtifact Vertex
	 * 
	 * @author kuzukami_user
	 *
	 */
	public static abstract class SubArtifactRoot
			extends DivisionExecutor.FragmentSet implements
			ArtifactFragment.OutputArtifact, PartialNameResolver.Standard {
		protected final SubArtifact artifactDefinition;

		protected SubArtifactRoot(SubArtifact submodule
		// , Set<FragmentName> myclazzes, Supplier<Set<FragmentName>> deps
		) {
			super(/* myclazzes , deps, */ ReportSortOrder.RO1_SUBMODULE);
			this.artifactDefinition = submodule;
		}

		@Override
		public String toString() {
			return artifactDefinition.toString();
//			return "SubArtifactRoot [" + artifactDefinition + "]";
		}

		// public static SubArtifactRoot.LessMemory forLessMemory(SubArtifact
		// submodule,
		// Set<Clazz> myclazzes) {
		// return new LessMemory(submodule, myclazzes);
		// }

		public static SubArtifactRoot.DetailTrace forDetailTrace(
				SubArtifact submodule,
				MainJarManager resource,
				OptionalPropagation[] outerDefaultRootTranstivePropagations,
				OptionalPropagation[] outerDefaultRootReferencePropagations,
				OptionalPropagation[] outerDefaultSubartifactReferencePropagations
				) {
			
			Set<FragmentName> marked =
			resource
			.newMarker()
			.mark(submodule.getRootSets(),
					submodule.getDefaultRootTransitivePropagations(outerDefaultRootTranstivePropagations)
					,
					submodule.getDefaultRootSourceReferencePropagations(outerDefaultRootReferencePropagations)
					)
			.commitAndCut(
					submodule.getSubartifactSourceReferencePropagations(outerDefaultSubartifactReferencePropagations ));
			
			
			return new DetailTrace(submodule, marked, resource);
		}

		// public static class LessMemory extends
		// DivisionExecutor.SubArtifactRoot {
		// protected LessMemory(SubArtifact submodule,
		// Set<Clazz> myclazzes) {
		// super(submodule, myclazzes, naturalDepends(myclazzes));
		// }
		//
		// @Override
		// public DivisionExecutor.ClazzSet forClazzSet(Clazz myclz) {
		// return this;
		// }
		//
		// }

		/**
		 * RootClassのメンバーネーム
		 * 
		 * @return
		 */
		public abstract Set<FragmentName> getRootMemberNames();

		static class DetailTrace extends DivisionExecutor.SubArtifactRoot
				implements PartialNameResolver.Standard.Resourced {
			private final Map<FragmentName, Root> authorizedContainingFraments;
			private final MainJarManager jarmanager;

			@Override
			public Set<FragmentName> getRootMemberNames() {
				return authorizedContainingFraments.keySet();
			}

			// fragment
			@Override
			public Set<FragmentName> getDependencyTargetNames() {
				return authorizedContainingFraments.keySet();
			}

			@Override
			public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
				return dependencyTargets_UnionOfResolvedDependencyNamesAndExtraFragments(
						r);
			}
			// fragment

			// partial resolver
			@Override
			public Set<FragmentName> getAuthorizedNames() {
				return authorizedContainingFraments.keySet();
			}

			@Override
			public DivisionExecutor.FragmentParty resolveOne(
					FragmentName myclz) {
				return authorizedContainingFraments.get(myclz);
			}

			@Override
			public MainJarManager getJarManager() {
				return jarmanager;
			}

			protected DetailTrace(SubArtifact submodule,
					Set<FragmentName> myclazzes,
					 MainJarManager jarmanager) {
				super(submodule/* , myclazzes, () -> myclazzes */);

				this.jarmanager = jarmanager;
				this.authorizedContainingFraments = Maps.newHashMap(
						Maps.transformEntries(MapsIID.forSet(myclazzes),
								(clx, vd) -> new Root(clx)));

			}

			// @Override
			// public void resolveAndAppendExtraDependenciesOnFinish(
			// MyClazzpath fullclasspath,
			// Multimap<ArtifactFragment, ArtifactFragment> extraDB) {
			// for (SubArtifactRootClass m : clazzesForTrace.values())
			// extraDB.put(m, this);
			// }

			public class Root extends
					DivisionExecutor.FragmentUnit implements DebugContractable {
				private Root(FragmentName name) {
					super(name, ReportSortOrder.RO5_DEBUG);
				}

				private SubArtifactDefinition getSubArtifact() {
					return DetailTrace.this.artifactDefinition;
				}

				private FragmentName getName() {
					return getMyName();
				}

				@Override
				public String toString() {
					return Root.class.getSimpleName() +  " [" + getName()
							+ " of " + getSubArtifact() + "]";
				}

				@Override
				public ArtifactFragment getContractDestination() {
					return DetailTrace.this;
				}

				@Override
				public Set<MyFragment> dependencyTargets(
						AuthorizedNameResolver r) {
					return dependencyTargets_UnionOfResolvedDependencyNamesAndExtraFragments(
							r, DetailTrace.this);
				}

				@Override
				public Set<FragmentName> getDependencyTargetNames() {
					return naturalDepedencyTargets(super.getMyName());
				}
			}

		}
	}

	public abstract static class LibArtifact
			extends DivisionExecutor.FragmentSet implements
			ArtifactFragment.LibraryArtifact, PartialNameResolver.Standard {
		protected final File jarFile;
		private final Dependency pomDependency;

		protected LibArtifact(File jarFile,
				// Set<FragmentName> myClazz, Supplier<Set<FragmentName>>
				// dependencies,
				Dependency dep) {
			super(/* myClazz, dependencies, */ ReportSortOrder.RO2_JAR);
			this.jarFile = jarFile;
			this.pomDependency = dep;
		}

		@Override
		public String toString() {
			return  LibArtifact.class.getSimpleName()  +  " [" + pomDependency.toString() + "]";
		}

		// @Override
		// public Set<FragmentName> getAuthorizedNames() {
		// return getMemberNames();
		// }

		// protected static DivisionExecutor.LibArtifact forLessMemory(
		// Dependency dep, File jarFile,
		// ClazzpathUnit jarUnit, Predicate<Clazz> targetFilter) {
		// Set<Clazz> myclz = Sets.filter(jarUnit.getClazzes(),
		// targetFilter);
		// return new LessMemory(jarFile, myclz, () -> Sets.newHashSet(), dep);
		// }

		public static DivisionExecutor.LibArtifact forDetailTrace(
				Dependency dep, File jarFile, Set<FragmentName> usedNames) {
			Set<FragmentName> myclz = usedNames;
			return new DetailTrace(jarFile, myclz, dep);
		}

		// public static class LessMemory extends DivisionExecutor.LibArtifact {
		// protected LessMemory(File jarFile, Set<Clazz> myClazz,
		// Supplier<Set<Clazz>> dependencies, Dependency dep ) {
		// super(jarFile, myClazz, dependencies, dep);
		// }
		//
		// @Override
		// public DivisionExecutor.ClazzSet forClazzSet(Clazz myclz) {
		// return this;
		// }
		//
		// }

		public static class DetailTrace extends DivisionExecutor.LibArtifact
		// implements PartialNameResolver.HasExtraDependencies
		{

			private Set<FragmentName> authorizedName;
			private Map<FragmentName, LibReferencee> apiUsage = Maps.newConcurrentMap();

			protected DetailTrace(File jarFile,
					Set<FragmentName> authorizedNames, Dependency dep) {
				super(jarFile, dep );
				this.authorizedName = authorizedNames;
			}

			@Override
			public DivisionExecutor.FragmentParty resolveOne(
					FragmentName myclz) {
				return apiUsage.computeIfAbsent(myclz,
						(c) -> new LibReferencee(c, ReportSortOrder.RO5_DEBUG)
						);
			}

			@Override
			public Set<FragmentName> getDependencyTargetNames() {
				return authorizedName;
			}

			@Override
			public Set<MyFragment> dependencyTargets(AuthorizedNameResolver r) {
				return dependencyTargets_UnionOfResolvedDependencyNamesAndExtraFragments(
						r);
			}

			@Override
			public Set<FragmentName> getAuthorizedNames() {
				return authorizedName;
			}

			// @Override
			// public Set<FragmentName> getMemberNames() {
			// return apiClass.keySet();
			// }

			public class LibReferencee extends DivisionExecutor.FragmentUnit
					implements DebugContractable {

				protected LibReferencee(FragmentName myclazz,
						DivisionExecutor.ReportSortOrder ro) {
					super(myclazz, ro);
				}

				private FragmentName getName() {
					return super.getMyName();
				}
				
				private String getLibname() {
					return DetailTrace.this.jarFile.getName();
				}

				@Override
				public String toString() {
					return LibReferencee.class.getSimpleName() +  " [" + getName()
							+ " of " + getLibname() + "]";
				}

				@Override
				public ArtifactFragment getContractDestination() {
					return DetailTrace.this;
				}

				@Override
				public Set<MyFragment> dependencyTargets(
						AuthorizedNameResolver r) {
					return dependencyTargets_UnionOfResolvedDependencyNamesAndExtraFragments(
							r, DetailTrace.this);
				}

				@Override
				public Set<FragmentName> getDependencyTargetNames() {
					return Collections.emptySet();
				}

			}

			// @Override
			// public void resolveAndAppendExtraDependenciesOnFinish(
			// MyClazzpath fullclasspath,
			// Multimap<ArtifactFragment, ArtifactFragment> extraDB) {
			// for (LibClass c : apiClass.values())
			// extraDB.put(c, this);
			// }

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

//	private static ClazzpathUnit addcu(Clazzpath cp, File jar) {
//		try {
//			return cp.addClazzpathUnit(jar);
//		} catch (IOException e) {
//			throw new RuntimeException(e);
//		}
//	}

	public static class SubArtifactDeployment {
		private final SubArtifactDefinition target;
		private final ArtifactFragment targetFragment;
		private final List<SubArtifactDefinition> subartDeps;
		private final LinkedHashMap<File, Dependency> jarDeps;
//		private final List<String> deployClassNames;
		private final List<String> resources;
		private final String traceLog;

		protected SubArtifactDeployment(SubArtifactDefinition target,
				ArtifactFragment targetFragment,
				List<SubArtifactDefinition> subartDeps,
				LinkedHashMap<File, Dependency> jarDeps,
//				List<String> deployClassNames,
				List<String> resources,
				FullTracablePlanAcceptor forTraceLog
				) {
			super();
			this.target = target;
			this.subartDeps = subartDeps;
			this.jarDeps = jarDeps;
//			this.deployClassNames = deployClassNames;
			this.resources = resources;
			this.targetFragment = targetFragment;
			this.traceLog = traceLog(forTraceLog);
		}

		public SubArtifactDefinition getTarget() {
			return target;
		}

		public String getTraceLog() {
			return traceLog;
		}

		public List<SubArtifactDefinition> getSubartDeps() {
			return subartDeps;
		}

		public LinkedHashMap<File, Dependency> getJarDeps() {
			return jarDeps;
		}

//		public List<String> getDeployClassNames() {
//			return deployClassNames;
//		}

		public List<String> getResources() {
			return resources;
		}
		
		private String traceLog( FullTracablePlanAcceptor f){
			try ( StringWriter sw = new StringWriter(); PrintWriter pw = new PrintWriter(sw) ){
				printTraceLog(f, pw);
				pw.flush();;
				return sw.toString();
			} catch (IOException e) {
				//would not happen
				throw new RuntimeException(e);
			}
		}
		
		private void printTraceLog( FullTracablePlanAcceptor f, PrintWriter out ){
			f.reportStream()
			.filter( (deployLocation2deploymentFragment) -> 
					targetFragment.equals( deployLocation2deploymentFragment.getKey() ) )
			.forEach((ent) -> {
				ArtifactFragment deployAnchor = ent.getKey();
				List<jp.co.iidev.subartifact1.divider1.DivisionExecutor.FullTracablePlanAcceptor.DeploymentUnit> list_of_deployee = ent.getValue();

				out.println( "Sub-Artifact Deployment Description : " + deployAnchor.toString());
				
				if (deployAnchor instanceof DivisionExecutor.SubArtifactRoot) {
					DivisionExecutor.SubArtifactRoot submod = (DivisionExecutor.SubArtifactRoot) deployAnchor;
					submod.getRootMemberNames().stream().sorted()
							.forEach((c) -> {
						out.println("  **Sub-Artifact Root : " + c.toString());
					});

				}
				for (jp.co.iidev.subartifact1.divider1.DivisionExecutor.FullTracablePlanAcceptor.DeploymentUnit deployee : list_of_deployee) {
					out.println("  Sub-Artifact Dependency : "
							+ deployee.deployeeFragment.toString());
					for (String reasonline : deployee.getReason(2))
						out.println(reasonline);
				}
			});
		}

	}

	private final class FullTracablePlanAcceptor implements PlanAcceptor {
		private BiMap<ArtifactFragment, Integer> buildingOrderOfOutputArtifacts = HashBiMap
				.create();
		private List<DeploymentUnit> deployments = Lists.newArrayList();
		String tabPad = "  ";
		

		public Stream<Entry<ArtifactFragment,List<DeploymentUnit>>> reportStream() {
			return
			deployments.stream().sorted(ducmp())
			.collect(Collectors
					.groupingBy((du) -> du.deploymentLocationArtifact))
			.entrySet().stream()
			// building order
			.sorted(Comparator
					.comparing((e) -> buildingOrderOfOutputArtifacts
							.get(e.getKey())))
			;
		}
		//will be removed
		@Deprecated
		private void finalReport() {

//			deployments.stream().sorted(ducmp())
//					.collect(Collectors
//							.groupingBy((du) -> du.deploymentLocationArtifact))
//					.entrySet().stream()
//					// building order
//					.sorted(Comparator
//							.comparing((e) -> buildingOrderOfOutputArtifacts
//									.get(e.getKey())))
			reportStream()
					.forEach((ent) -> {
						ArtifactFragment deployAnchor = ent.getKey();
						List<DeploymentUnit> list_of_deployee = ent.getValue();

						System.out.println(
								"Sub-Artifact Deployment Description : "
										+ deployAnchor.toString());
						if (deployAnchor instanceof DivisionExecutor.SubArtifactRoot) {
							DivisionExecutor.SubArtifactRoot submod = (DivisionExecutor.SubArtifactRoot) deployAnchor;
							submod.getRootMemberNames().stream().sorted()
									.forEach((c) -> {
								System.out.println("  **Sub-Artifact Root : " + c.toString());
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
				ArtifactDivisionPlanner.axisIndexOf(e,
						buildingOrderOfOutputArtifacts);
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
					List<ReferenceInspector> forEachDirectDependingArtifact) {
				super(deploymentAnchor, deployeeFragment);
				this.forEachDirectDependingArtifact = forEachDirectDependingArtifact;
			}

			@Override
			List<String> getReason(int tabIndent) {
				List<String> k = Lists.newArrayList();
				for (ReferenceInspector riByDirectDependingArtifact : forEachDirectDependingArtifact) {
					List<ArtifactFragment> dependingDirGraphFromDirectArt = riByDirectDependingArtifact
							.computeDependingShortestPathFromDirectlyDependingArtifactToRelocatableFrament();

					List<ArtifactFragment> dependedDirGraphFromDeployToDirectArtifacts = riByDirectDependingArtifact
							.computeDependedShortestPathFromLocatedToDirectlyDependingArtifact();

					List<String> pathvis = DependencyPathVisualizer
							.visualizeReversedDepedencyPath(
									dependingDirGraphFromDirectArt,
									dependedDirGraphFromDeployToDirectArtifacts,
									tabIndent - 1);

					Iterables.addAll(k, Iterables.skip(pathvis, 1)// skip me
																	// because
																	// already
																	// displayed.
					);
				}
				return k;
			}
		}

		private final class ArtifactDeployment extends DeploymentUnit {

			final com.google.common.base.Supplier<List<ArtifactFragment>> computer_Deploy2dependeeShortestPath;

			private ArtifactDeployment(ArtifactFragment deploymentAnchor,
					ArtifactFragment deployeeFragment,
					com.google.common.base.Supplier<List<ArtifactFragment>> computer_Deploy2dependeeShortestPath) {
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
					k.add(StringsIID.replaceTemplateAsSLF4J("{}{}{}",
							StringUtils.repeat(tabPad, i), "<=",
							af.toString()));
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
			deployments.add(new ArtifactDeployment(dependingArtifact,
					dependedArtifact, computer_Deploy2dependeeShortestPath));
		}

		@Override
		public void acceptRelocatableFragmentDeploymentPlan(
				ArtifactFragment deploymentArtifactLocated,
				ArtifactFragment fuildFramentDepended,
				List<ReferenceInspector> forEachDirectDependingArtifact) {
			deployments.add(new RelocatableDeployment(deploymentArtifactLocated,
					fuildFramentDepended, forEachDirectDependingArtifact));
		}

		public List<SubArtifactDeployment> integrateFullPlanOrderedByBuildSequence(
				Predicate<ArtifactFragment> aliveFragments) {
			LinkedHashMap<ArtifactFragment, List<DeploymentUnit>> subartifacts = Maps
					.newLinkedHashMap();

			ArtifactDivisionPlanner
					.axisOrderedStream(buildingOrderOfOutputArtifacts)
					.forEach((a) -> {
						subartifacts.put(a, Lists.newArrayList());
					});

			deployments.forEach((v) -> {
				subartifacts.get(v.deploymentLocationArtifact).add(v);
			});
			// .collect(
			// Collectors.groupingBy(
			// (du) -> du.deploymentAnchor
			// , LinkedHashMap::new
			// , Collectors.toList()
			// )
			// )
			// ;

			List<SubArtifactDeployment> l = Lists.newArrayList();
			Set<ArtifactFragment> emptyOmitSet = Sets.newHashSet();

			for (Map.Entry<ArtifactFragment, List<DeploymentUnit>> a : subartifacts
					.entrySet()) {
				SubArtifactRoot main = (SubArtifactRoot) a.getKey();
				LinkedHashMap<File, Dependency> jardeps = Maps
						.newLinkedHashMap();
				List<SubArtifactDefinition> subart_deps = Lists.newArrayList();
				List<String> deployResources = Lists.newArrayList();

				for (DeploymentUnit du : a.getValue()) {
					ArtifactFragment tgt = du.deployeeFragment;
					if (!aliveFragments.apply(tgt)) {
						info("{} is filterd out from {} because it's not passed for the 'alive predicate'.",
								tgt, main);
					} else if (emptyOmitSet.contains(tgt)) {
						info("{} is filterd out from {} because it's 'empty'.",
								tgt, main);
					} else if (tgt instanceof Relocatable) {
						Relocatable fc = (Relocatable) tgt;
						deployResources.add(fc.getMyName().getResName());
					} else if (tgt instanceof LibArtifact) {
						LibArtifact la = (LibArtifact) tgt;
						jardeps.put(la.jarFile, la.pomDependency);
					} else if (tgt instanceof SubArtifactRoot) {
						SubArtifactRoot sa = (SubArtifactRoot) tgt;
						subart_deps.add(sa.artifactDefinition);
					} else {
						throw new RuntimeException(
								"unknown artifact fragment:" + tgt);
					}
				}

				for (FragmentName rootCls : main.getRootMemberNames())
					deployResources.add(rootCls.getResName());

				boolean fullEmpty = jardeps.isEmpty() && subart_deps.isEmpty()
						&& deployResources.isEmpty();

				if (fullEmpty /* && main.submodule.isOmittableIfEmpty() */ ) {
					info("The artifact {} is not deployed because it's fully 'empty'.",
							main);
					emptyOmitSet.add(main);
				} else {
					SubArtifactDeployment sa = new SubArtifactDeployment(
							main.artifactDefinition,
							main,
							subart_deps, jardeps,
//							deployResources
							Ordering.natural().sortedCopy(deployResources)
							,
							this
							);
					l.add(sa);
				}
			}

			return l;
		}

		@Override
		public void close() {
//			finalReport();
		}
	}

	// helper class
	class MainJarManager {
		// final ClassPool pool;
		// final Set<Clazz> searchSpace;
		// final Multimap<String, Clazz> annotatedClazzes =
		// LinkedListMultimap.create();
		// final Multimap<String, Clazz> toplevelClazzesByPackage =
		// LinkedListMultimap.create();
		// final BiMap<Clazz, CtClass> classMap;
		final DetailJarAnalysis index;
		BiMap<FragmentName, MyJarEntry> leftNames;
		HashMultimap<FragmentName, FragmentName> extraMarkRelations = HashMultimap.create();

		MainJarManager(DetailJarAnalysis jidx) {
			super();
			this.index = jidx;
			this.leftNames = jidx.getEntries();
		}

		private Map<FragmentName, MyJarEntry> getAndCutByName(Set<FragmentName> annotnames) {
			Map<FragmentName, MyJarEntry> jx = Maps.filterKeys(leftNames, Predicates.in(annotnames));
			this.leftNames = Maps.filterKeys(leftNames, Predicates.not( Predicates.in(annotnames) ));
			return jx;
		}
//		private Map<FragmentName, MyJarEntry> getAndCut(Set<MyJarEntry> annotnames) {
//			Map<FragmentName, MyJarEntry> jx = Maps.filterValues(leftNames, Predicates.in(annotnames));
//			this.leftNames = Maps.filterValues(leftNames, Predicates.not( Predicates.in(annotnames) ));
//			return jx;
//		}
//		
		public Set<FragmentName> getExternallyDependentNames(){
			return
			index.getEntries().keySet().stream()//.parallel()
			.flatMap((e) -> getDependentNames(e).stream())
			.filter( (e) -> !index.getEntries().containsKey(e))
			.collect(Collectors.toSet());
		}
		
		public Set<FragmentName> getDependentNames( FragmentName fn ){
			if ( extraMarkRelations.containsKey(fn) ){
				return Sets.union(
								index.getDependency(fn)
								,
								extraMarkRelations.get(fn)
								)
								;
			}
			return index.getDependency(fn);
		}
		
		public  Map<FragmentName, MyJarEntry>  getLeftAll() {
			return leftNames;
		}
		
		public SubsetMarker newMarker(){
			return new SubsetMarker();
		}
		
		public class SubsetMarker{
			private Set<FragmentName> currentRoot = Sets.newHashSet();
			
			public Set<FragmentName> commitAndCut(
					OptionalPropagation[] forSubsetSourceReference ){
				
		 		addAllPropagatedReferences(
		 				computePropagatedReferencesExpandingRootSubeffectively(
		 						currentRoot, null, forSubsetSourceReference)
		 				);
		 		
				return getAndCutByName( currentRoot ).keySet();
			}
			




			public SubsetMarker mark( 
					RootSet[] markORs, OptionalPropagation[] externalRootTransitives, 
					OptionalPropagation[] externalReferences ){
				if ( markORs == null ) return this;
				
//				while(true) {
//					int os = currentRoot.size();
					for ( RootSet r : markORs )
						mark(r
								, r.getRootTransitivePropagations( externalRootTransitives )
								, r.getRootSourceReferencePropagations( externalReferences ));
//					int ns = currentRoot.size();
//					if ( os == ns ) break;
//				}
				return this;
			}
			

			
			private SubsetMarker mark( 
					RootSet mark, OptionalPropagation[] forRootTransitive , OptionalPropagation[] forSourceReference ){
				Set<FragmentName> rootsExpanding = Sets.newHashSet();
				if ( mark.getByAnnotation() != null ){
					rootsExpanding.addAll( getAnnotated( Arrays.asList( mark.getByAnnotation() ) ) );
//					markAnnotated( Arrays.asList( mark.getByAnnotation() ));
				}
				
				if ( mark.getByIncludeResourcePattern() != null ){
					String incpat = mark.getByIncludeResourcePattern();
					rootsExpanding.addAll(getIncludePatternMatched(incpat));
				}
				
				Multimap<FragmentName, FragmentName> refs = 
				computePropagatedReferencesExpandingRootSubeffectively(
						rootsExpanding
						, forRootTransitive
						, forSourceReference
						);
				
				currentRoot.addAll(rootsExpanding);
				addAllPropagatedReferences(refs);
				
				return this;
			}
			



//			public RootMarker propagateOptionally(
//					OptionalPropagation[] markPropagateOptions) {
//				currentMarked.addAll(
//						MainJarManager.this.
//						propagateOptionally(currentMarked, markPropagateOptions).values() );
//				return this;
//			}

		}
		
		public Set<FragmentName>  getAnnotated(
				Iterable<? extends String> annotnames) {
			Set<String> l = Sets.newHashSet(annotnames);
			if (l.size() == 0)
				return Sets.newHashSet();// empty

			Set<FragmentName> m =
					index.annotatedClasses(l)
					.map( (x) -> index.getFragmentName(x) )
					.flatMap( (x) -> propagateInnerClassReferencesInJarWhenTopLeveClass(x) )
					.collect(Collectors.toSet())
					;
			return m;
		}
		
		private Set<FragmentName> getIncludePatternMatched(String incpat) {
			return Sets.filter(
					index.getEntries().keySet()
					,
					(f) -> SelectorUtils.match(incpat, f.getResName() , false) );
		}
		
		private Stream<FragmentName>  propagateInnerClassReferencesInJarWhenTopLeveClass(
				FragmentName classfile ) {
			if ( !classfile.isClassFileResource() )
				return Stream.of(classfile);
			if ( ! DetailJarAnalysis.isToplevelClass(index.getEntry(classfile)) )
				return Stream.of(classfile);
				
			ClassNode markstcn = index.getClassNode(classfile).get();

			return TraversersV0
					.stackTraverse(
							markstcn,
							(cx) ->
								ClassNodes.getNestCanonicalClassNames(cx)
								.stream()
								.flatMap((canClsnamOfNested) -> {
									return index.getClassNode(FragmentName.forCanoicalClassName( canClsnamOfNested ))
											.map(Stream::of)
											.orElseGet(() -> {
												debug( "There is an unresolved inner class signature '{}' in '{}'."
														, canClsnamOfNested, cx.name
														);
												return Stream.empty();
											});
								})
								.collect(Collectors.toList()),
							Collectors.toSet()
							).stream()
					.map((cn) -> FragmentName.forCanoicalClassName(cn.name))
//					.collect(Collectors.toSet())
					;
		}
		
	 	public Set<FragmentName> getPackageResources(
				FragmentName classfile ) {
			String packagename = index.getEntry(classfile).getDiretoryPathAlikePackage(".");
			return index.packageResource(packagename).keySet();
		}
	 	
	 	void addAllPropagatedReferences(
	 			Multimap<FragmentName, FragmentName> m ){
	 		extraMarkRelations.putAll(
	 				Multimaps.filterEntries(
	 						m
	 						, (k2v) -> k2v.getKey() != k2v.getValue() )
	 				);
	 	}
	 	public void propagateGlobalReference(
	 			OptionalPropagation[] forGlobalReference
	 			){
	 		HashSet<FragmentName> globalSet = 
	 				Sets.newHashSet(
	 						index.getEntries().keySet()
	 						);
	 		
	 		addAllPropagatedReferences(
	 				computePropagatedReferencesExpandingRootSubeffectively( globalSet , null, forGlobalReference ) );
	 	}
	 	
		private Multimap<FragmentName, FragmentName> computePropagatedReferencesExpandingRootSubeffectively(
				Set<FragmentName> rootSubeffectivelyExpanding,
				OptionalPropagation[] forRootTransitive,
				OptionalPropagation[] forRootSourceReference
				) {
			
			if ( forRootTransitive == null )
				forRootTransitive = new OptionalPropagation[0];
			if ( forRootSourceReference == null )
				forRootSourceReference = new OptionalPropagation[0];
			
			
			//initially propagate roots
			while( true ){
				int os = rootSubeffectivelyExpanding.size();
				
				Multimap<FragmentName, FragmentName> rootExtension =  HashMultimap.create();
				for ( OptionalPropagation r : forRootTransitive ){
					propagateOptionallyPrivate(
							Collections.unmodifiableSet( rootSubeffectivelyExpanding )
							, r,  rootExtension);
				}
				//subeffectively expand the root.
				rootSubeffectivelyExpanding.addAll(rootExtension.values());
				
				int ns = rootSubeffectivelyExpanding.size();
				if ( os == ns ) break;
			}
			
			Set<FragmentName> rootFixed = rootSubeffectivelyExpanding;
			
			//then propagate references
			{
				Multimap<FragmentName, FragmentName> references =  HashMultimap.create();
				for ( OptionalPropagation r : forRootSourceReference ){
					propagateOptionallyPrivate(
							Collections.unmodifiableSet( rootFixed )
							, r,  references );
				}
				return references;
			}
			
		}
		private void propagateOptionallyPrivate(
				Set<FragmentName> markOrigin,
				OptionalPropagation markPropagateOptionO,
				Multimap<FragmentName, FragmentName> seen
//				, boolean transitivePropagate
				) {
			
			OptionalPropagation markPropagateOption;
			
			if ( markPropagateOptionO.getByPredefined() != null )
				markPropagateOption = markPropagateOptionO.getByPredefined().getAsOption();
			else
				markPropagateOption = markPropagateOptionO;
			
			Multimap<FragmentName, FragmentName> myexapnd = 
					HashMultimap.create();
			
			if ( markPropagateOption.isByInnerClassSignature() ){
				for ( FragmentName o : markOrigin ){
					myexapnd.putAll(o, propagateInnerClassReferencesInJarWhenTopLeveClass(o).collect(Collectors.toSet()) );
				}
			}
			
			if ( markPropagateOption.isByServicesFileContents() ){
				for ( FragmentName o : markOrigin ){
					String contents = new String( index.getBytes(o), Charsets.UTF_8 );
					try {
						for ( String ln : CharStreams.readLines(new StringReader(contents)) ){
							FragmentName fnx = FragmentName.forClassName(ln);
							if (  !index.getEntries().containsKey(fnx) ){
								error("The mark propagation of #isBySercieFileContents() from {} finds an unkonwn class {} in the jar.", o.toString(), ln);
							}
							myexapnd.put(o, fnx);
						}
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				}
			}
			
			{
				String packageMarkerSimpleResource = null;
				if ( markPropagateOption.getBySimpleClassNameInPacakge() != null ){
					packageMarkerSimpleResource = markPropagateOption.getBySimpleClassNameInPacakge() + ".class";
				}else if ( markPropagateOption.getBySimpleResourceNameInPacakge() != null ){
					packageMarkerSimpleResource = markPropagateOption.getBySimpleResourceNameInPacakge();
				}
				
				if ( packageMarkerSimpleResource != null ){
					for ( FragmentName o : markOrigin ){
						MyJarEntry m = index.getEntry(o);
						if ( m.getBasename().equals(packageMarkerSimpleResource) ){
							String markPackage = m.getDiretoryPathAlikePackage(".");
							myexapnd.putAll(o, index.packageResource(markPackage).keySet() );
						}
					}
				}
			}
			
			myexapnd = 
			Multimaps.filterValues(
					myexapnd, 
					(v) -> {
						return markPropagateOption.getTargetResourceTypeOR().contains( index.getResourceType(v) );
					} );
			
			
			seen.putAll(myexapnd);
			
//			if ( transitivePropagate ){
//				markOrigin.addAll(myexapnd.values());
//			}
			
		}


	}

}