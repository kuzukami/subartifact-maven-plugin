package jp.co.iidev.subartifact1.divider1.mojo;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.standard.expression.DivisionExpression;

import com.google.common.base.Objects;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import jp.co.iidev.subartifact1.api.SubArtifactDefinition;
import jp.co.iidev.subartifact1.divider1.DivisionExecutor;
import jp.co.iidev.subartifact1.divider1.DivisionExecutor.SubArtifactDeployment;
import jp.co.iidev.subartifact1.divider1.Loggable;
import jp.co.iidev.subartifact1.divider1.LoggableFactory;
import jp.co.iidev.subartifact1.divider1.PomSetGenerator;


/**
 * Goal which touches a timestamp file.
 *
 * 
 */
@Mojo(name = "divide", defaultPhase = LifecyclePhase.INSTALL, requiresDependencyResolution= ResolutionScope.COMPILE)
public class ArtifactDividerMojo extends AbstractMojo {
	
	/**
	 * Location of the parent directory of pom output directory.
	 */
	@Parameter(defaultValue = "${basedir}/target/subartifacts", property = "diriver.outputDir", required = true)
	private File outputDirectory;

	/**
	 * Location of the parent directory of template-pom.xml or template-parent-pom.xml output directory.
	 */
	@Parameter(defaultValue = "${basedir}/subartifact-templates", property = "diriver.outputDir", required = true)
	private File templateOutputDirectory;
	
	/**
	 * The current Maven project.
	 */
	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;
	
	/**
	 * The root(=depending) sub-artifact's artifactId for every sub-artifacts newly created.
	 */
	@Parameter(defaultValue= "${project.artifactId}-root", property="divier.rootSubArtifactId", required = true )
	private String rootSubArtifactId;
	
	/**
	 * The parent pom artifactId to build every sub-artifact including the root sub-artifact.
	 */
	@Parameter(defaultValue= "${project.artifactId}-subarts-parent", property="divier.subArtifactsParentArtifactId", required = true )
	private String subArtifactsParentArtifactId;
	
	/**
	 * The resource inclusion rules of 'OR' chains for a sub-artifact.
	 */
	@Parameter
	private PackageResourceInclusion[] packageResourceInclusionORs;
	
	/**
	 * The sub-artifacts definitions to be created from the main artifact.
	 */
	@Parameter
	private SubArtifact[] subartifacts;
	
	
	static enum PackageResourceRule{
		BySimpleClassNameRule
		,
		UnknownRule
		;
		
		
		static PackageResourceRule detect( PackageResourceInclusion r ){
			if ( r.getBySimpleClassNameInPackage() != null )
				return BySimpleClassNameRule;
			return UnknownRule;
		}
	}
	


	public void execute() throws MojoExecutionException {

		Artifact projArt = project.getArtifact();
		Map<Dependency, Artifact> artifactsForDep = Maps.newHashMap();

		projArt = project.getArtifact();

		{
			List<Dependency> dep = project.getDependencies();
			Set<Artifact> arts = project.getDependencyArtifacts();

			for (Dependency dx : dep) {
				String grpid = dx.getGroupId();
				String artid = dx.getArtifactId();
				String clsf = dx.getClassifier();

				for (Artifact art : arts) {
					boolean a = StringUtils.equals(art.getArtifactId(), artid);
					boolean g = StringUtils.equals( art.getGroupId(), grpid);
					boolean c =  StringUtils.equals( art.getClassifier(), clsf);

					if (a && g && c) {
						artifactsForDep.put(dx, art);
					}
				}
			}
		}

		{
			String version = project.getVersion();
			String groupId = project.getGroupId();

			LinkedHashMap<File, Dependency> compiletimeClasspath = Maps.newLinkedHashMap();

			File rtjar = Paths
					.get(System.getProperty("java.home"), "lib", "rt.jar")
					.toFile();
			Dependency rtjar_dummyDep = new Dependency();
			{
				rtjar_dummyDep
						.setGroupId(SystemUtils.JAVA_VENDOR.replace(" ", "."));
				rtjar_dummyDep.setVersion(SystemUtils.JAVA_RUNTIME_VERSION);
				rtjar_dummyDep.setArtifactId(SystemUtils.JAVA_RUNTIME_NAME);
			}

			File targetJar = project.getArtifact().getFile();
			Dependency targetJarDep = new Dependency();
			{
				targetJarDep.setArtifactId(project.getArtifactId());
				targetJarDep.setGroupId(project.getGroupId());
				targetJarDep.setVersion(project.getVersion());
				targetJarDep.setClassifier(projArt.getClassifier());
			}

			compiletimeClasspath.put(rtjar, rtjar_dummyDep);
			compiletimeClasspath.put(targetJar, targetJarDep);
			artifactsForDep.forEach((d, a) -> {
				compiletimeClasspath.put(a.getFile(), d);
			});
			
			LoggableFactory lf = 
			new LoggableFactory() {
				@Override
				public Loggable createLoggable(Class cx) {
					return new Loggable() {
						Logger l = LoggerFactory
								.getLogger(cx);

						@Override
						public void warn(String text) {
							l.warn(text);
						}

						@Override
						public void info(String text) {
							l.info(text);
						}

						@Override
						public void error(String text) {
							l.error(text);
						}

						@Override
						public void debug(String text) {
							l.debug(text);
						}
					};
				}
			};
			try {
				LinkedHashMap<SubArtifactDefinition, SubArtifactDeployment>
				buildPlan =
						new DivisionExecutor( lf.createLoggable(DivisionExecutor.class) )
						.planDivision(
								lf.createLoggable(DivisionExecutor.class)
								, targetJar
								, rootSubArtifactId
								, Arrays.asList(
										subartifacts == null
										? new SubArtifact[0] : subartifacts)
								, compiletimeClasspath
								, not(in(ImmutableSet.of( rtjar, targetJar )))
								, lf
								);

				Set<File> usableJar =
						Sets.newLinkedHashSet(compiletimeClasspath.keySet());
				usableJar.remove(targetJar);
				usableJar.remove(rtjar);
				
				int ix = 0;
				for ( SubArtifact s : subartifacts ){
					for ( Dependency d : s.getExtraDependencies() ){
						buildPlan.get(s).getJarDeps().put(
								new File("x_xx_xyx_duMmy" + (ix++) + ".jar")
								, d);
					}
				}

				new PomSetGenerator(
						project.getBasedir().toPath().resolve("pom.xml"),
						outputDirectory.toPath(),
						templateOutputDirectory.toPath()
						).generate(groupId,
								version,
								this.subArtifactsParentArtifactId,
								buildPlan
								);
			} catch (RuntimeException e) {
				throw e;
			} catch (Exception e) {
				throw new MojoExecutionException("division process error", e);
			}
		}

	}

}
