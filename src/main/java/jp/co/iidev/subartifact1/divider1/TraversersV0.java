package jp.co.iidev.subartifact1.divider1;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collector;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;

public class TraversersV0 {
	
	public static<T,A,R> R stackTraverse(
			T start, Function<? super T,? extends Iterable<T>> next, Collector<T,A,R> collector ){
		return stackTraverseL(Arrays.asList(start), next, collector);
	}
		
	public static<T,A,R> R stackTraverseL(
			Iterable<? extends T> start, Function<? super T,? extends Iterable<T>> next, Collector<T,A,R> collector ){
		return collector.finisher().apply(
				stackTraverseL(start, next, collector.supplier().get(), collector, Sets.newHashSet())
				);
	}
	
	
	private static<T,A,R> A stackTraverseL(
			Iterable<? extends T> start, Function<? super T,? extends Iterable<T>> next, A currentA, Collector<T,A,R> collector,
			Set<T> seen ){
		for ( T x : start ){
			stackTraverseS(x, next, currentA, collector, seen);
		}
		return currentA;
	}
	
	private static<T,A,R> A stackTraverseS(
			T start, Function<? super T,? extends Iterable<T>> next, A currentA, Collector<T,A,R> collector,
			Set<T> seen ){
		if ( seen.contains(start) ) return currentA;
		
		collector.accumulator().accept(currentA, start);
		seen.add(start);
		
		stackTraverseL(
				next.apply(start),
				next,
				currentA,
				collector,
				seen );
		
		return currentA;
	}
	
	public static<T,A,R> R queueTraverse(
			T start, Function<? super T,? extends Iterable<T>> next, Collector<T,A,R> collector ){
		BiMap<T, Integer> b = HashBiMap.create();
		
		b.put(start, b.size());
		
		A a = collector.supplier().get();
		
		for ( int i = 0; i < b.size(); i ++ ){
			T x = b.inverse().get(i);
			collector.accumulator().accept(a, x);
			for ( T y : next.apply(x) )
				if ( !b.containsKey(y) )
					b.put(y, b.size());
			
		}
		
		 return collector.finisher().apply(a);
	}
	


}
