package jp.co.iidev.subartifact1.divider1;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.jgrapht.DirectedGraph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.CycleDetector;
import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedMaskSubgraph;
import org.jgrapht.graph.EdgeReversedGraph;
import org.jgrapht.graph.MaskFunctor;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.builder.DirectedGraphBuilderBase;
import org.jgrapht.traverse.BreadthFirstIterator;
import org.jgrapht.traverse.TopologicalOrderIterator;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;

import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ArtifactFragment.DebugContractable;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.AFPredicateInconsitency;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.CyclicArtifact;
import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.PlanningException.AFPredicateInconsitency.PredicateType;

public class ArtifactDivisionPlanner implements Loggable {
	private final Loggable logger;

	@Override
	public void info(String text) {
		logger.info(text);
	}

	@Override
	public void warn(String text) {
		logger.warn(text);
	}

	@Override
	public void error(String text) {
		logger.error(text);
	}

	protected ArtifactDivisionPlanner(LoggableFactory lf) {
		super();
		this.logger = lf.createLoggable(ArtifactDivisionPlanner.class);
	}

	// public static String replaceTemplateAsSLF4J( String template, Object
	// ...values ){
	// StringBuilder s = new StringBuilder();
	// String repl = "{}";
	// for ( int vsindex = 0, tsindex = 0; ; vsindex ++ , tsindex +=
	// repl.length()){
	//
	// int replindex = ( template.indexOf(repl,tsindex) );
	// if ( replindex < 0 ) {
	// //flush all template left
	// s.append(template, tsindex, template.length());
	// break;
	// }
	//
	// s.append(template, tsindex, replindex);
	// s.append(values[vsindex]);
	//
	// tsindex = replindex;
	// }
	// return s.toString();
	// }
	static <T> int axisIndexOf(T object, BiMap<T, Integer> axis) {
		if (axis.containsKey(object))
			return axis.get(object);
		int v = axis.size();
		axis.put(object, v);
		return v;
	}

	static <T> Stream<T> axisOrderedStream(BiMap<T, Integer> axis) {
		return IntStream.range(0, axis.size())
				.mapToObj((idx) -> axis.inverse().get(idx));
	}

	// static Logger logger =
	// org.slf4j.LoggerFactory.getLogger(ArtifactFragmentDeployPlanner.class);
	// private void info(String tempalte, Object ...templateArgs ){
	// System.err.println("INFO:"+ replaceTemplateAsSLF4J(tempalte,
	// templateArgs));
	// }
	// private void warn(String tempalte, Object ...templateArgs ){
	// System.err.println("WARN:" + replaceTemplateAsSLF4J(tempalte,
	// templateArgs));
	// }
	// private void error(String tempalte, Object ...templateArgs ){
	// System.err.println( "ERROR:" + replaceTemplateAsSLF4J(tempalte,
	// templateArgs));
	// }

	static <A, E> DirectedGraph<A, E> removeVerticesPreservingReachability(
			DirectedGraph<A, E> graph, Predicate<? super A> vertexAliveFilter) {
		// DirectedGraph<A, E> opGraph = (DirectedGraph<A, E>) graph.clone();
		removeVerticesPresevingReachability(graph, Sets.newHashSet(Sets
				.filter(graph.vertexSet(), Predicates.not(vertexAliveFilter))));
		return graph;
	}

	static <A, E, T> List<T> reportConnectivity(DirectedGraph<A, E> graph,
			BiFunction<A, A, T> Source2TargetReporter) {

		return FluentIterable.from(graph.edgeSet()).transform((e) -> {
			return Source2TargetReporter.apply(graph.getEdgeSource(e),
					graph.getEdgeTarget(e));
		})
				// .toImmutableList()
				.toList();
	}

	static <A, E, T> List<T> reportCycles(DirectedGraph<A, E> graph,
			Function<List<A>, T> cycleReporter) {

		// too slow...
		// JohnsonSimpleCycles<A, E> s =
		// new JohnsonSimpleCycles<>(graph);
		// SzwarcfiterLauerSimpleCycles<A,E> s =
		// new SzwarcfiterLauerSimpleCycles<>(graph);
		// TarjanSimpleCycles<A,E> s =
		// new TarjanSimpleCycles<>(graph);
		// TiernanSimpleCycles<A,E> s =
		// new TiernanSimpleCycles<>(graph);

		CycleDetector<A, E> s = new CycleDetector<>(graph);

		return FluentIterable
				.from(Arrays.asList(Lists.newArrayList(
						s.findCycles())) /* s.findSimpleCycles() */ )
				.filter((l) -> l.size() != 0).transform(cycleReporter)
				// .toImmutableList()
				.toList();

	}

	static <A, E> DirectedMaskSubgraph<A, E> createVertexIncomingEdgeDisconnectedGraph(
			DirectedGraph<A, E> graph,
			Predicate<? super A> vertex_pred_for_disconnect) {

		DirectedMaskSubgraph<A, E> anchorInEdge_is_disconnected_graph = new DirectedMaskSubgraph<>(
				graph, new MaskFunctor<A, E>() {
					// disconnect the incoming graph into anchor vertex.
					@Override
					public boolean isEdgeMasked(E e) {
						return vertex_pred_for_disconnect
								.apply(graph.getEdgeTarget(e));
					}

					// all vertices are alive
					@Override
					public boolean isVertexMasked(A vertex) {
						return false;
					}
				});
		return anchorInEdge_is_disconnected_graph;
	}

	/**
	 * 全ての頂点を、 アーチファクトと、再配置可能部品に述語で分ける。 それぞれのアーチファクトと、そこからgraph上の 再配置可能部品のみを経由して
	 * 接続している全ての移動頂点のセット(transitive closure)をタプルにして、mapする。
	 * つまり、アーチファクト経由の間接的な接続を接続として認めない。
	 * 
	 * @param graph
	 * @param artifactPred_equalsto_NotRelocatablePred
	 *            アンカー頂点と流動頂点を分ける述語
	 * @param anchor2TransitiveClosureElementReport
	 * @return
	 */
	static <A, E, T> Map<A, T> mapForEachVertexRelocableConnectedClosure(
			DirectedGraph<A, E> graph,
			Predicate<? super A> artifactPred_equalsto_NotRelocatablePred,
			BiFunction<A, LinkedHashSet<A>, T> anchor2TransitiveClosureElementReport) {

		DirectedMaskSubgraph<A, E> anchorInEdge_is_disconnected_graph =
		// アンカー頂点経由の間接的な接続を接続として認めない。
		createVertexIncomingEdgeDisconnectedGraph(graph,
				artifactPred_equalsto_NotRelocatablePred);

		return mapForEachVertex2Followables(anchorInEdge_is_disconnected_graph,
				artifactPred_equalsto_NotRelocatablePred,
				anchor2TransitiveClosureElementReport);
	}

	/**
	 * アンカー頂点を述語で指定する。 それぞれのアンカー頂点と、そこからgraphを経由して辿れる全ての頂点のセット(transitive
	 * closure)をタプルにしてmapする。
	 * 
	 * @param graph
	 * @param anchorVertexPred
	 * @param anchor2TransitiveClosureElementReport
	 * @return
	 */
	static <A, E, T> Map<A, T> mapForEachVertex2Followables(
			DirectedGraph<A, E> graph, Predicate<? super A> anchorVertexPred,
			BiFunction<A, LinkedHashSet<A>, T> anchor2TransitiveClosureElementReport) {
		return graph.vertexSet().parallelStream()
				.filter((v) -> anchorVertexPred.apply(v)).collect(Collectors
						.<A, A, T> toConcurrentMap((root) -> root, (root) -> {
							LinkedHashSet<A> vset = Sets
									.newLinkedHashSet(Lists.newArrayList(
											new BreadthFirstIterator<A, E>(
													graph, root)));
							synchronized (anchor2TransitiveClosureElementReport) {
								return anchor2TransitiveClosureElementReport
										.apply(root, vset);
							}
						}));
	}

	// static <A, E, T> Map<A,T> mapForEachVertexTransitiveClosureOrdered(
	// DirectedGraph<A, E> graph,
	// Predicate<?super A> anchorVertexPred,
	// Comparator<A> anchorVertexOrdering,
	// BiFunction<A, LinkedHashSet<A>, T> anchor2TransitiveClosureElementReport
	// ){
	// return graph.vertexSet().stream()
	// .filter( (v) -> anchorVertexPred.apply(v) )
	// .sorted(anchorVertexOrdering)
	// .collect( Collectors.<A,A,T>toMap(
	// (root) -> root
	// ,
	// (root) -> anchor2TransitiveClosureElementReport
	// .apply(root,
	// Sets.newLinkedHashSet( Lists.newArrayList( new
	// BreadthFirstIterator<A,E>(graph, root) ) ) )
	// )
	// );
	// }

	static <A, E> DirectedGraph<A, E> getCommonDescenderSubgraph(
			DirectedGraph<A, E> graph, Set<A> ascenders) {
		Map<A, Set<A>> basePoint2trclosureset = mapForEachVertex2Followables(
				graph, Predicates.in(ascenders), (r, trset) -> {
					return trset;
				});

		Set<A> commonParentVerticies = basePoint2trclosureset.values().stream()
				// .map( (alist) -> (Set<A>) Sets.<A>difference(alist,
				// ascenders) ) //bug? line will be removed.
				.reduce(Sets::<A> intersection).orElse(Sets.newHashSet());

		return new DirectedMaskSubgraph<>(graph, new MaskFunctor<A, E>() {
			@Override
			public boolean isEdgeMasked(E edge) {
				return false;
			}

			@Override
			public boolean isVertexMasked(A vertex) {
				return !commonParentVerticies.contains(vertex);
			}
		});
	}

	/**
	 * Extension to directed acyclic graph
	 * 
	 * @see https://en.wikipedia.org/wiki/Lowest_common_ancestor#
	 *      CITEREFKowalukLingas2005
	 * @param graph_MustBeDAG
	 * @param lowestDescender_Common_for_Ancestors
	 * @param anscestors
	 * @throws RuntimeException
	 *             when the 'lowestDescender_Common_for_Ancestors' is not common
	 *             for the specified 'anscestors'.
	 * @return
	 */
	static <A, E> A getHighestCommonDescender(
			DirectedGraph<A, E> graph_MustBeDAG,
			A lowestDescender_Common_for_Ancestors, Set<A> anscestors) {
		// check precondition
		{
			Map<A, Boolean> lowestDescenderChecks = mapForEachVertex2Followables(
					graph_MustBeDAG, Predicates.in(anscestors), (a, dsets) -> {
						return dsets
								.contains(lowestDescender_Common_for_Ancestors);
					});

			Set<A> illegalAcsenders = Maps.filterValues(lowestDescenderChecks,
					(contained) -> !contained).keySet();

			if (illegalAcsenders.size() != 0) {
				throw new RuntimeException(StringsIID.replaceTemplateAsSLF4J(
						"the lowest descender {} is not common for the specified anscestors: {}",
						lowestDescender_Common_for_Ancestors.toString(),
						Joiner.on(",").join(illegalAcsenders)));
			}
		}

		DirectedGraph<A, E> cg = getCommonDescenderSubgraph(graph_MustBeDAG,
				anscestors);

		// find the HCG by inDegree(cg,v) == 0
		return cg.vertexSet().stream().filter((v) -> cg.inDegreeOf(v) == 0)
				.findFirst().get();

		// .vertexSet().stream()
		// .filter(graph_MustBeDAG.)
		// return //most distant node from root in the common descender
		// subgraph.
		// Iterators.getLast(
		// new ClosestFirstIterator<>(
		// new EdgeReversedGraph<>( )
		// ,
		// lowestDescender_Common_for_Ancestors ));
	}

	public static interface ArtifactFragment
			extends Comparable<ArtifactFragment> {
		public static interface RelocatableFragment extends ArtifactFragment {
		}

		public static interface Artifact extends ArtifactFragment {
		}

		public static interface LibraryArtifact extends Artifact {
		}

		public static interface OutputArtifact extends Artifact {
		}

		public static interface DebugContractable extends ArtifactFragment {
			public ArtifactFragment getContractDestination();
		}
	}

	public static interface ReferenceInspector {
		public ArtifactFragment getDirectlyDependingArtifact();

		public List<ArtifactFragment> computeDependedShortestPathFromLocatedToDirectlyDependingArtifact();

		public List<ArtifactFragment> computeDependingShortestPathFromDirectlyDependingArtifactToRelocatableFrament();
	}

	public static interface PlanAcceptor extends AutoCloseable {
		// Comparator<Map.Entry<ArtifactFragment,ArtifactFragment>>
		// reportOrderByDeployAnchorAndDeployee();

		void acceptArtifactBuildingOrder(
				List<ArtifactFragment> buildingOrderForOutput);

		void acceptArtifactDeploymentPlan(ArtifactFragment locatedArtifact,
				ArtifactFragment dependencyArtifact,
				Supplier<List<ArtifactFragment>> computer_located2dependencyShortestPath);

		void acceptRelocatableFragmentDeploymentPlan(
				ArtifactFragment locatedArtifact,
				ArtifactFragment dependencyFluid,
				List<ReferenceInspector> forEachDirectlyDependingArtifact);
	}

	protected static class PlanningException extends Exception {
		protected PlanningException() {
			super();
		}

		protected PlanningException(String message, Throwable cause) {
			super(message, cause);
		}

		protected PlanningException(String message) {
			super(message);
		}

		protected PlanningException(Throwable cause) {
			super(cause);
		}

		/**
		 * 述語の前提条件がおかしい場合
		 * 
		 */
		public static class AFPredicateInconsitency extends PlanningException {
			public static enum PredicateType {
				/**
				 * 出力アーチファクト条件が適合していない
				 */
				OUTPUT_ARTIFACT_PREDICATE,
				/**
				 * アーチファクト条件が適合していない
				 */
				ARTIFACT_PREDICATE,
				/**
				 * デバッグ情報が適合していない。
				 */
				DEBUG_PREDICATE,;
			}

			private final PredicateType type;
			private final Set<? extends ArtifactFragment> inconsitentSets;

			public static void throwIfExists(PredicateType type,
					Set<? extends ArtifactFragment> inconsitentSets)
							throws AFPredicateInconsitency {
				if (inconsitentSets.size() != 0)
					throw new AFPredicateInconsitency(type, inconsitentSets);
			}

			protected AFPredicateInconsitency(PredicateType type,
					Set<? extends ArtifactFragment> inconsitentSets) {
				super();
				this.inconsitentSets = inconsitentSets;
				this.type = type;
			}

			public PredicateType getType() {
				return type;
			}

			public Set<? extends ArtifactFragment> getInconsitentSets() {
				return inconsitentSets;
			}

			@Override
			public String toString() {
				return "AFPredicateInconsitency [type=" + type
						+ ", inconsitentSets=" + inconsitentSets + "]";
			}

		}

		/**
		 * アーチファクト間で循環があっていけない。=>少なくともmavenで表現できない。
		 * 
		 * @author kuzukami_user
		 */
		public static class CyclicArtifact extends PlanningException {
			private final List<List<ArtifactFragment>> cycles;

			protected CyclicArtifact(List<List<ArtifactFragment>> cycles) {
				super();
				this.cycles = cycles;
			}

			protected CyclicArtifact(List<List<ArtifactFragment>> cycles,
					String message, Throwable cause) {
				super(message, cause);
				this.cycles = cycles;
			}

			protected CyclicArtifact(List<List<ArtifactFragment>> cycles,
					String message) {
				super(message);
				this.cycles = cycles;
			}

			protected CyclicArtifact(List<List<ArtifactFragment>> cycles,
					Throwable cause) {
				super(cause);
				this.cycles = cycles;
			}

			public List<List<ArtifactFragment>> getCycles() {
				return cycles;
			}

			@Override
			public String toString() {
				return "CyclicArtifact [cycles=" + cycles + "]";
			}

		}
	}

	private static <T> void foraechEdge(Iterable<? extends T> startPoints,
			Function<? super T, ? extends Iterable<T>> followFunction,
			Predicate<? super T> followOKFilter,
			BiConsumer<T, T> source2targetEdgeReport) {
		// FollowableAnalysisResult<T> a = new FollowableAnalysisResult<>();
		BiMap<T, Integer> map = HashBiMap.create();// a.nodesAndId;
		LinkedHashSet<T> a_startPoints = Sets.newLinkedHashSet(startPoints);

		for (T t : a_startPoints) {
			if (followOKFilter.apply(t))
				axisIndexOf(t, map);
		}

		for (int mi = 0; mi < map.size(); mi++) {
			T following = map.inverse().get(mi);

			for (T t : followFunction.apply(following))
				if (followOKFilter.apply(t)) {
					int k = axisIndexOf(t, map);
					// if ( k == map.size() -1 ){ //bug removed
					source2targetEdgeReport.accept(following, t);
					// }

				}
		}
	}

	/**
	 * ArtifactFragament : Maven Artifactの構成物で、分割されたArtifactに別々にデプロイ可能な単位を表す。
	 * classファイル、classファイル群、jarファイル。
	 * 一つのArtifactに入っていなければならない等、分割できないクラス群は、一つArtifactFragmentに含まれていなければならない。
	 * 
	 * グラフの頂点をArtifactFragment, 辺をそれらの依存関係として見る。
	 * 
	 * グラフの頂点(=ArtifactFragament)を、AnchorとFluidの2つに分ける。 Anchor
	 * ArtifactFragament(Vertex) :
	 * mavenのモジュールに対応する。モジュールに必ず含まれるpublicクラス群がこれに対応する。(Shrinkerだと[キープ]クラス群)
	 * Fluid ArtifactFragament(Vertex) : それ以外のclassとかjar(dependency)に対応する。
	 * 
	 * 解くべき問題は、全ての遷移接続関係を破壊しないように、いずれか一つのAnchor流下に、全頂点を配置する問題。 Transitive
	 * Reduction問題の変種といえる。 https://en.wikipedia.org/wiki/Transitive_reduction
	 * 
	 * @param outputArtifactFragments
	 * @param adjacencyFun
	 * @param rootArtifact
	 *            全てのAnchorFragmentの親に強制的に接続されるアーチファクト
	 * @param planAcceptor
	 * @throws CyclicArtifact
	 *             Artifactが循環している場合、この場合mavenでは依存関係を作成できない。
	 * @throws AFPredicateInconsitency
	 *             指定した述語や、インターフェースの前提条件に適合していない場合
	 */
	public void computeAndReportDeploymentUnits(
			Set<? extends ArtifactFragment> outputArtifactFragments,
			Function<ArtifactFragment, Iterable<ArtifactFragment>> adjacencyFun,
			ArtifactFragment rootArtifact, PlanAcceptor planAcceptor)
					throws CyclicArtifact, AFPredicateInconsitency {
		computeAndReportDeploymentUnits(rootArtifact, outputArtifactFragments,
				Predicates.in(Sets.union(outputArtifactFragments,
						Collections.singleton(rootArtifact))),
				Predicates.instanceOf(ArtifactFragment.Artifact.class),
				Predicates.instanceOf(ArtifactFragment.DebugContractable.class),
				adjacencyFun, planAcceptor);

	}

	/**
	 * TODO 依存グラフに全く接続していないアーチファクト頂点の処理.現状だと報告されない。
	 * 
	 * @param rootArtifact
	 * @param outputArtifactRootSet
	 * @param outputArtifactPred
	 * @param artifactPred
	 * @param debugPhantomPred
	 * @param adjacencyFun
	 * @param planAcceptor
	 * @throws CyclicArtifact
	 * @throws AFPredicateInconsitency
	 */
	public void computeAndReportDeploymentUnits(ArtifactFragment rootArtifact,
			Set<? extends ArtifactFragment> outputArtifactRootSet,
			Predicate<? super ArtifactFragment> outputArtifactPred,
			Predicate<? super ArtifactFragment> artifactPred,
			Predicate<? super ArtifactFragment> debugPhantomPred,
			Function<ArtifactFragment, Iterable<ArtifactFragment>> adjacencyFun,
			PlanAcceptor planAcceptor)
					throws CyclicArtifact, AFPredicateInconsitency {
		// check precondition of predicates
		{
			Set<? extends ArtifactFragment> roots = Sets.union(
					outputArtifactRootSet, Collections.singleton(rootArtifact));

			{
				Set<? extends ArtifactFragment> output_artifact_but_not_artifact_pred = Sets
						.filter(roots, Predicates.not(artifactPred));

				AFPredicateInconsitency.throwIfExists(
						PredicateType.OUTPUT_ARTIFACT_PREDICATE,
						output_artifact_but_not_artifact_pred);
			}

			{
				Set<? extends ArtifactFragment> output_artifact_bug_not_output_artifact_pred = Sets
						.filter(roots, Predicates.not(outputArtifactPred));

				AFPredicateInconsitency.throwIfExists(
						PredicateType.ARTIFACT_PREDICATE,
						output_artifact_bug_not_output_artifact_pred);
			}

			{
				Set<? extends ArtifactFragment> output_artifact_but_debug_phandom_pred = Sets
						.filter(roots, debugPhantomPred);

				AFPredicateInconsitency.throwIfExists(
						PredicateType.DEBUG_PREDICATE,
						output_artifact_but_debug_phandom_pred);
			}
		}

		final DefaultDirectedGraph<ArtifactFragment, DefaultEdge> fullGraphContainingDebug;
		{ // graph build
			DirectedGraphBuilderBase<ArtifactFragment, DefaultEdge, ? extends DefaultDirectedGraph<ArtifactFragment, DefaultEdge>, ?> db = DefaultDirectedGraph
					.<ArtifactFragment, DefaultEdge> builder(
							(v1, v2) -> new DefaultEdge());

			// build graph => done
			ArtifactDivisionPlanner.foraechEdge(outputArtifactRootSet,
					adjacencyFun, Predicates.alwaysTrue()
					// , db::addEdge
					, (source, target) -> {
						db.addEdge(source, target);
					});

			fullGraphContainingDebug = db.build();
		}

		// remove debug vertices to calculate if the graph have.
		final DirectedGraph<ArtifactFragment, DefaultEdge> fullGraphX;
		final Multimap<ArtifactFragment, ArtifactFragment> debugVerticesContraction_dst2contracted;
		{

			final Map<ArtifactFragment, ArtifactFragment> debugVerticesContraction_contracted2dst;

			Set<DebugContractable> contractVerts = Sets
					.newHashSet(
							Iterables.filter(
									Iterables.filter(
											fullGraphContainingDebug
													.vertexSet(),
											DebugContractable.class),
									debugPhantomPred));
			debugVerticesContraction_contracted2dst = contractVerts.stream()
					.collect(Collectors.toMap((dc) -> dc,
							(dc) -> dc.getContractDestination()));

			debugVerticesContraction_dst2contracted = Multimaps.invertFrom(
					Multimaps.forMap(debugVerticesContraction_contracted2dst),
					HashMultimap.create());

			if (contractVerts.size() != 0) {
				// shrink debug vertices
				fullGraphX = contractVertices(
						copyAsDefaultGraph(fullGraphContainingDebug),
						debugVerticesContraction_contracted2dst);
			} else {
				fullGraphX = fullGraphContainingDebug;
			}
		}

		// アーチファクト間の依存関係
		DirectedGraph<ArtifactFragment, DefaultEdge> artifactConnectedGraph;
		{
			artifactConnectedGraph = copyAsDefaultGraph(
					// 自己循環は切る
					maskSelfCircularGraph(
							// アーチファクト以外の頂点をcontractする
							removeVerticesPreservingReachability(
									copyAsDefaultGraph(fullGraphX),
									artifactPred)));
		}

		// 仮想ルート出力アーチファクトへ、全ての出力アーチファクトを結合する。
		{// full connection to the pseudoCommonFragment
			// anchorAF_fluidlyConnectedGraph.addVertex(pseudoCommonAnchor);
			DirectedGraph<ArtifactFragment, DefaultEdge> tgtG = artifactConnectedGraph;

			Lists.newArrayList(artifactConnectedGraph.vertexSet()).stream()
					.filter((x) -> outputArtifactPred.apply(x))
					.forEach((vtx) -> {
						Graphs.addEdgeWithVertices(tgtG, vtx, rootArtifact);
					});
		}

		// アーチファクト間の依存関係が循環を持つケースは例外。なぜならmavenデプロイ自体ができない。
		{
			List<List<ArtifactFragment>> artcycles = reportCycles(
					artifactConnectedGraph, (cycle) -> cycle);
			if (artcycles.size() != 0) {
				int i = 1;
				for (List<ArtifactFragment> flg : artcycles) {
					error("cycle {} :", (Integer) i);
					for (ArtifactFragment ce : flg) {
						error("  {} ", ce);
					}
					i++;
				}
				throw new CyclicArtifact(artcycles);
			}
		}

		{
			final DefaultDirectedGraph<ArtifactFragment, DefaultEdge> referenceeAF_2_relocatableConnectedArtifact_ReversedDependencyGraph;

			{
				// build fluidlyConnectedArtifactReversedGraph graph
				DirectedGraphBuilderBase<ArtifactFragment, DefaultEdge, ? extends DefaultDirectedGraph<ArtifactFragment, DefaultEdge>, ?> af2outartifact = DefaultDirectedGraph
						.<ArtifactFragment, DefaultEdge> builder(
								(v1, v2) -> new DefaultEdge());

				// アーチファクト頂点経由の間接的な接続を接続として認めない。 => アーチファクト頂点への流入辺のみを切る。
				// ライブラリアーチファクト頂点からは、Fluidな頂点への流出はないので、この時点でライブラリアーチファクトは切り離される
				DirectedGraph<ArtifactFragment, DefaultEdge> fluidAndOutGoingArtifactGraph = createVertexIncomingEdgeDisconnectedGraph(
						fullGraphX, artifactPred);

				// アンカー頂点からFluid頂点のみをたどって、たどり着ける頂点を全て登録する。
				mapForEachVertex2Followables(fluidAndOutGoingArtifactGraph,
						outputArtifactPred,
						(anchor, transitiveTarget) -> Sets.filter(
								transitiveTarget,
								Predicates.not(outputArtifactPred)))
										.forEach((anchor, trclosure) -> {
											trclosure.forEach((af) -> {
												af2outartifact.addEdge(af,
														anchor);
											});
										});

				// build!!
				referenceeAF_2_relocatableConnectedArtifact_ReversedDependencyGraph = af2outartifact
						.build();
			}

			// Lowest Common Ancestor の逆アルゴリズム。Highest Common Descender.
			// "いずれか一つのAnchor流下に、Fluidを配置する"ため、
			// ある頂点(A)を流下に持つAnchor頂点群のうち、共通最高子孫(HCD)に頂点(A)を配置する。
			// DeploymentAnchor(A) = HCD(AnchorAncestors(A))
			Function<Set<ArtifactFragment>, ArtifactFragment> highestCommonDescender_as_deployArtifactByAncestorArtifactsF = (
					ascenders) -> {
				return getHighestCommonDescender(artifactConnectedGraph,
						rootArtifact, ascenders);
			};
			Map<Set<ArtifactFragment>, ArtifactFragment> cache_HCD = new ConcurrentHashMap<>();

			// ある頂点(A=referencee)に対応したHCD(AnchorAncestors(A))を全て計算する。
			// そのままレポート
			mapForEachVertex2Followables(
					referenceeAF_2_relocatableConnectedArtifact_ReversedDependencyGraph,
					// vertex must be source of an edge.
					(vertex) -> !referenceeAF_2_relocatableConnectedArtifact_ReversedDependencyGraph
							.outgoingEdgesOf(vertex).isEmpty(),
					// A => HCD(AnchorAncestors(A)) => A
					(referencee, anscestors) -> {
						// leave only Anchor Artifact Fragment in the
						// referencers=anscestors
						Set<ArtifactFragment> ancestorArtifacts_as_keyForHCD = Sets
								.intersection(anscestors,
										artifactConnectedGraph.vertexSet());

						ArtifactFragment HCD_as_deploymentArtifact = cache_HCD
								.computeIfAbsent(ancestorArtifacts_as_keyForHCD,
										(key) -> /* HCD */ highestCommonDescender_as_deployArtifactByAncestorArtifactsF
												.apply(key));

						planAcceptor.acceptRelocatableFragmentDeploymentPlan(
								HCD_as_deploymentArtifact, referencee,
								FluentIterable
										.from(ancestorArtifacts_as_keyForHCD)
										.<ReferenceInspector> transform(
												(ancestorSubArtifact) -> depshortpathForNonAnchor(
														HCD_as_deploymentArtifact,
														artifactConnectedGraph,
														ancestorSubArtifact,
														fullGraphContainingDebug,
														outputArtifactPred,
														referencee,
														debugVerticesContraction_dst2contracted))
										// .toImmutableList()
										.toList());

						return new Object();

					});

			{
				List<ArtifactFragment> topOrderedArtifacts = Lists
						.newArrayList(new TopologicalOrderIterator<>(
								reversed(artifactConnectedGraph)));

				planAcceptor.acceptArtifactBuildingOrder(
						Lists.newArrayList(Iterables.filter(topOrderedArtifacts,
								outputArtifactPred)));

				artifactConnectedGraph.edgeSet().forEach((e) -> {
					ArtifactFragment from, to;
					from = artifactConnectedGraph.getEdgeSource(e);
					to = artifactConnectedGraph.getEdgeTarget(e);

					planAcceptor.acceptArtifactDeploymentPlan(from, to, () -> {
						if (to == rootArtifact)
							return Lists.newArrayList();

						return dijkstraPath(detailFluidConnectedGraphForARoute(
								fullGraphContainingDebug,
								debugVerticesContraction_dst2contracted,
								artifactPred, from, to), from, to);
						// return dijkstraPathRemovingObstacles(
						// fullGraphContainingDebug, from, to,
						// outputArtifactPred );
					});
				});
			}

		}

	}

	private static <V, E> DirectedGraph<V, E> detailFluidConnectedGraphForARoute(
			DirectedGraph<V, E> fullGraphContainingDebug,
			Multimap<V, V> extraUnroutableInfo,
			Predicate<? super V> unroutableFragmentPred,
			V... exceptionlyRoutableFragments) {

		Set<V> exceptionalyRoutable = ImmutableSet
				.copyOf(exceptionlyRoutableFragments);

		Predicate<V> unroutableTop = (x) -> unroutableFragmentPred.apply(x)
				&& !exceptionalyRoutable.contains(x);

		Multimap<V, V> mustbeRemoved = Multimaps.filterKeys(extraUnroutableInfo,
				unroutableTop);

		Set<V> removedVertices = Sets.newHashSet(
				Iterables.concat(mustbeRemoved.values(), Iterables.filter(
						fullGraphContainingDebug.vertexSet(), unroutableTop)));

		return new DirectedMaskSubgraph<>(fullGraphContainingDebug,
				new MaskFunctor<V, E>() {
					@Override
					public boolean isEdgeMasked(E edge) {
						return false;
					}

					@Override
					public boolean isVertexMasked(V vertex) {
						return removedVertices.contains(vertex);
					}
				});
	}

	private static <V> DefaultDirectedGraph<V, DefaultEdge> copyAsDefaultGraph(
			DirectedGraph<V, DefaultEdge> g) {
		return DefaultDirectedGraph
				.<V, DefaultEdge> builder((v1, v2) -> new DefaultEdge())
				.addGraph(g).build();
	}

	private static <V> SimpleDirectedGraph<V, DefaultEdge> copyAsSimpleGraph(
			DirectedGraph<V, DefaultEdge> g) {
		return SimpleDirectedGraph
				.<V, DefaultEdge> builder((v1, v2) -> new DefaultEdge())
				.addGraph(g).build();
	}

	private static <V> DirectedGraph<V, DefaultEdge> reversed(
			DirectedGraph<V, DefaultEdge> g) {
		return new org.jgrapht.graph.EdgeReversedGraph<>(g);
	}

	// private static<V,E> List<V> dijkstraPathRemovingObstacles(
	// DirectedGraph<V, E> g, V from, V to, Predicate<?super V> obstaclePred ){
	// return dijkstraPath(
	// new DirectedMaskSubgraph<>(g,
	// new MaskFunctor<V, E>() {
	// @Override
	// public boolean isEdgeMasked( E edge) {
	// return false;
	// }
	// @Override
	// public boolean isVertexMasked( V vertex) {
	// return obstaclePred.apply(vertex) && vertex != from && vertex != to ;
	// }
	// })
	// ,
	// from, to);
	// }
	//
	private static <V> List<V> dijkstraPath(DirectedGraph<V, ?> g, V start,
			V end) {
		DijkstraShortestPath<V, ?> dj = new DijkstraShortestPath<>(g, start,
				end);
		return Graphs.getPathVertexList(dj.getPath());
	}

	private static <X, Y> ReferenceInspector depshortpathForNonAnchor(
			ArtifactFragment deployArtifact_HCD,
			DirectedGraph<ArtifactFragment, X> g_btw_artifacts,
			ArtifactFragment fluidReachingArtifact,
			DirectedGraph<ArtifactFragment, Y> g_btw_fragment2artifact,
			Predicate<? super ArtifactFragment> artifactPred,
			ArtifactFragment fluidAF,
			Multimap<ArtifactFragment, ArtifactFragment> extraUnroutableInfo) {
		return new ReferenceInspector() {
			@Override
			public ArtifactFragment getDirectlyDependingArtifact() {
				return fluidReachingArtifact;
			}

			@Override
			public List<ArtifactFragment> computeDependingShortestPathFromDirectlyDependingArtifactToRelocatableFrament() {
				return dijkstraPath(
						detailFluidConnectedGraphForARoute(
								g_btw_fragment2artifact, extraUnroutableInfo,
								artifactPred, fluidReachingArtifact),
						fluidReachingArtifact, fluidAF);
				// return dijkstraPathRemovingObstacles(g_btw_fragment2artifact,
				// fluidReachingArtifact, fluidAF, artifactPred);
			}

			@Override
			public List<ArtifactFragment> computeDependedShortestPathFromLocatedToDirectlyDependingArtifact() {
				return dijkstraPath(new EdgeReversedGraph<>(g_btw_artifacts),
						deployArtifact_HCD, fluidReachingArtifact);
			}
		};
	}

	/**
	 * 
	 * @param gr
	 * @param vertices_to_remove
	 */
	public static <V, E> void removeVerticesPresevingReachability(
			DirectedGraph<V, E> gr, Set<V> vertices_to_remove) {

		Map<V, Integer> cache = new HashMap<>();

		vertices_to_remove.parallelStream()
				.sorted(Comparator
						.<V, Integer> comparing(
								(x) -> cache.computeIfAbsent(x,
										(v) -> (gr.inDegreeOf(v)
												+ gr.outDegreeOf(v)))))
				.sequential().forEach((v) -> {
					List<V> sucset = Graphs.successorListOf(gr, v);
					List<V> preset = Graphs.predecessorListOf(gr, v);

					for (V in : preset)
						for (V out : sucset) {
							Graphs.addEdgeWithVertices(gr, in, out);
						}

					gr.removeVertex(v);
				});
		// for ( Set<V> connVerts : divideIntoConnnectedVertices(gr,
		// vertices_to_contract) )
		// contractConnectedVertices(gr, connVerts);
	}

	/**
	 * https://en.wikipedia.org/wiki/Edge_contraction
	 * 
	 * @param gr
	 * @param vertices_to_contract_from_to
	 */
	public static <V, E> DirectedGraph<V, E> contractVertices(
			DirectedGraph<V, E> gr, Map<V, V> vertices_to_contract_from_to) {

		Multimaps
				.invertFrom(Multimaps.forMap(vertices_to_contract_from_to),
						HashMultimap.create())
				.asMap().forEach((dstVtx, contractionVtxs) -> {

					Set<V> sucs = Sets.newHashSet();
					Set<V> pres = Sets.newHashSet();

					for (V contv : contractionVtxs) {
						sucs.addAll(Lists.transform(
								Graphs.successorListOf(gr, contv),
								(v) -> vertices_to_contract_from_to
										.getOrDefault(v, v)));
						pres.addAll(Lists.transform(
								Graphs.predecessorListOf(gr, contv),
								(v) -> vertices_to_contract_from_to
										.getOrDefault(v, v)));
						gr.removeVertex(contv);
					}

					sucs.stream().forEach((sv) -> {
						Graphs.addEdgeWithVertices(gr, dstVtx, sv);
					});

					pres.stream().forEach((pv) -> {
						Graphs.addEdgeWithVertices(gr, pv, dstVtx);
					});

				});

		return gr;
	}

	// private static<V,E> Iterable<Set<V>> divideIntoConnnectedVertices(
	// DirectedGraph<V, E> gr, Set<V> vertices ){
	// DirectedMaskSubgraph<V, E> m = new DirectedMaskSubgraph<>(gr, new
	// MaskFunctor<V, E>() {
	// @Override
	// public boolean isEdgeMasked(E edge) {
	// return false;
	// }
	//
	// @Override
	// public boolean isVertexMasked(V vertex) {
	// //recovered
	// return !vertices.contains(vertex);
	// }
	// });
	//
	// ConnectivityInspector<V, E> s = new ConnectivityInspector<>(m);
	//
	// return s.connectedSets();
	// }

	// /**
	// * the calculation order is linear to the number of edges of
	// connectedVertices.
	// *
	// * @param gr
	// * @param connectedVertices
	// */
	// private static<V,E> void contractConnectedVertices( DirectedGraph<V, E>
	// gr, Set<V> connectedVertices ){
	// for ( V v : connectedVertices ){
	// List<V> sucset = Graphs.successorListOf(gr, v);
	// List<V> preset = Graphs.predecessorListOf(gr, v);
	//
	// for ( V in : preset )
	// for ( V out : sucset ){
	// Graphs.addEdgeWithVertices(gr, in, out);
	// }
	//
	// gr.removeVertex(v);
	// }
	// }

	private static <V, E> DirectedGraph<V, E> maskSelfCircularGraph(
			DirectedGraph<V, E> gr) {
		return new DirectedMaskSubgraph<>(gr, new MaskFunctor<V, E>() {
			@Override
			public boolean isEdgeMasked(E edge) {
				return gr.getEdgeSource(edge) == gr.getEdgeTarget(edge);
			}

			@Override
			public boolean isVertexMasked(V vertex) {
				return false;
			}
		});
	}

}