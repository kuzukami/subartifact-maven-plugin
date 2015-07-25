package jp.co.iidev.subartifact1.divider1;

import com.google.common.collect.FluentIterable;
import com.google.inject.internal.util.Lists;

class FluentIterablesIID {

	static<E> 	FluentIterable<E> ofConcat(
			Iterable<? extends E> ...elm ){
		return FluentIterable.of(elm).transformAndConcat((e)-> e);
	}

	static<E> 	FluentIterable<E> from(
			E ...e ){
		return FluentIterable.of(e);
	}
	
	static<E> 	FluentIterable<E> copy( Iterable<E> e ){
		return 
				FluentIterable.from(
						FluentIterable.from(e).copyInto(Lists.newArrayList()));
	}
}
