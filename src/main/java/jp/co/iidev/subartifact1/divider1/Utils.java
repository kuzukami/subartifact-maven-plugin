package jp.co.iidev.subartifact1.divider1;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public class Utils {
	

	public static<V,R> R sequencialFold(
			Stream<V> values, 
			R accumulator,
			BiFunction<R, ?super V, ?extends R> folder ){
		R[] x = (R[])new Object[]{ accumulator };
		values.forEachOrdered((v) -> {
			x[0] = folder.apply(x[0], v);
		});
		return x[0];
	}

}
