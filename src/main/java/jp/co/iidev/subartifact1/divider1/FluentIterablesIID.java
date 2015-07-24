package jp.co.iidev.subartifact1.divider1;

import com.google.common.collect.FluentIterable;

class FluentIterablesIID {

	static<E> 	FluentIterable<E> ofConcat(
			Iterable<? extends E> ...elm ){
		return FluentIterable.of(elm).transformAndConcat((e)-> e);
	}

	static<E> 	FluentIterable<E> from(
			E ...e ){
		return FluentIterable.of(e);
	}
}
