package jp.co.iidev.subartifact1.divider1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;


import org.vafer.jdependency.utils.DependencyUtils;

import com.google.common.collect.FluentIterable;

public class ClassFileDepedencies {
	

	public static FluentIterable<String> getDependentClassNames( byte[] bytecode ){
		try {
			return
					FluentIterable.from(
							DependencyUtils.getDependenciesOfClass(new ByteArrayInputStream(bytecode)));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
