package jp.co.iidev.subartifact1.divider1;

import java.io.IOException;

import org.junit.Test;

import jp.co.iidev.subartifact1.divider1.PomSetGenerator;
import junit.framework.Assert;

public class TestPomtSetGenerator {

	@Test
	public void testLoadStandardResource() throws IOException{
		
		Assert.assertNotSame(
				"template-pom.xml resource loading",
				0, PomSetGenerator.stdtempalte().length );
		
		Assert.assertNotSame(
				"template-parent-pom.xml resource loading",
				0, PomSetGenerator.stdparenttempalte().length );
	}

}
