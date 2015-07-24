package jp.co.iidev.subartifact1.divider1;

import java.util.Map;
import java.util.Set;

import com.google.common.base.Functions;
import com.google.common.collect.Maps;

class MapsIID {

	static<K> Map<K,Void> forSet( Set<K> s ){
		return Maps.asMap(s, Functions.constant((Void)null));
	}

}
