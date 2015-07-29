package jp.co.iidev.subartifact1.divider1;

import java.util.Set;
import java.util.stream.Collectors;

import org.objectweb.asm.ClassReader;

import com.google.common.collect.FluentIterable;

import jp.co.iidev.subartifact1.divider1.asmhack.DependenciesClassAdapter;

class ResourceAnalysis {


	
	public static FluentIterable<String> getDependentDistinctClassNames(
			byte[] classFile) {
		return FluentIterable.from(
				getDependenciesOfClass(classFile) );
	}
	
	public static FluentIterable<FragmentName> getDependentDistinctClassNamesAsFragmentName(
			byte[] classFile) {
		return getDependentDistinctClassNames(classFile).transform((n) -> FragmentName.forClassName(n) );
	}

	private static Set<String> getDependenciesOfClass(final byte[] classfile) {
		final DependenciesClassAdapter v = new DependenciesClassAdapter();
		new ClassReader(classfile).accept(v,
				ClassReader.EXPAND_FRAMES | ClassReader.SKIP_DEBUG);
		final Set<String> depNames = v.getDependencies();
		return depNames;
	}

}
