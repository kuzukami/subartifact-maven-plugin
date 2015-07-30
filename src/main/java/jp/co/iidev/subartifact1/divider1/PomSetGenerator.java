package jp.co.iidev.subartifact1.divider1;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.AbstractContext;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IContext;
import org.thymeleaf.templatemode.StandardTemplateModeHandlers;
import org.thymeleaf.templateresolver.FileTemplateResolver;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
import com.google.common.io.Files;
import com.google.common.io.Resources;

import jp.co.iidev.subartifact1.api.SubArtifactDefinition;
import jp.co.iidev.subartifact1.divider1.DivisionExecutor.SubArtifactDeployment;

public class PomSetGenerator {
	final Path fullArtifactPomRelativeByNewProjectDir;
	final Path pomSetGenerationDir;
	final Path templatePomSetGenerationDir;

	public PomSetGenerator(Path fullArtifactPom, Path pomSetGenerationDir, Path templatePomSetGenerationDir) {
		this.pomSetGenerationDir = pomSetGenerationDir;
		this.templatePomSetGenerationDir = templatePomSetGenerationDir;
		this.fullArtifactPomRelativeByNewProjectDir = pomSetGenerationDir
				.resolve("tesueito")//once deeper 
				.relativize(fullArtifactPom);
	}

	public static class GenProject {
		final Model project;
//		final List<ObjectTypeClassName> includeclasses;
		final List<String> resources;
		final FullArtifact fullartifact;

		protected GenProject(Model project,
//				List<ObjectTypeClassName> includeclasses,
				List<String> resources,
				FullArtifact mainartifact) {
			super();
			this.project = project;
//			this.includeclasses = includeclasses;
			this.fullartifact = mainartifact;
			this.resources = resources;
		}

		public FullArtifact getFullartifact() {
			return fullartifact;
		}

		public Model getProject() {
			return project;
		}

//		public List<ObjectTypeClassName> getIncludeclasses() {
//			return includeclasses;
//		}
		
		public Iterable<String> getResourcePathsOfResourcesAndClasss(){
			return Iterables.concat(
//					Lists.transform( includeclasses, (x) -> x.getResourcePath() )
					Arrays.asList()
					, resources);
		}
		public static class FullArtifact {
			final Model project;
			final String resolvedClassifier;
			final String resolvedGroupId;

			public Model getProject() {
				return project;
			}

			public String getResolvedClassifier() {
				return resolvedClassifier;
			}

			public String getResolvedGroupId() {
				return resolvedGroupId;
			}

			protected FullArtifact(Model project, String classifier,
					String groupId) {
				super();
				this.project = project;
				this.resolvedClassifier = classifier;
				this.resolvedGroupId = groupId;
			}

		}
	}
	
	private Dependency d( String grp, String art){
		Dependency d = new Dependency();
		d.setGroupId(grp);
		d.setArtifactId(art);
		return d;
	}
	
	//test only
//	private static Dependency buildDep( File f ){
//		Map<String,Dependency> pat2Dep = 
//				Maps.newLinkedHashMap();
//		
//		
//		pat2Dep.put(
//				"guava-13.0.1.jar"
//				, d("com.google.guava", "guava") );
//		
//		pat2Dep.put(
//				"commons-primitives-1.0.jar"
//				,
//				d("commons-primitives", "commons-primitives"));
//		
//		return pat2Dep.get( f.getName() );
//		
//	}
	
	private static GenProject buildParentOfSubartifacts(
			String groupId, String version, String artifactId,
			LinkedHashMap<SubArtifactDefinition, DivisionExecutor.SubArtifactDeployment> generatesBySequence,
			Path fullArtifactPomRelative,
			Model fullArtfactMainPOM
			){
		
//		List<ObjectTypeClassName> incclasses = 
//				Lists.newArrayList();
//		;
		
		List<String> incresources = 
				Lists.newArrayList();
		;
		
		Model m = new Model();
		
		m.setArtifactId(artifactId);
		m.setGroupId(groupId);
		m.setVersion(version);
		m.setPackaging("pom");
		m.setName("Subartifact Root Project for " + fullArtfactMainPOM.getArtifactId() );
		
		for ( Map.Entry<SubArtifactDefinition,SubArtifactDeployment> subart :
				generatesBySequence.entrySet() ){
			m.getModules().add( "../" + subart.getKey().getArtifactId());// artifact id foldername
		}
		
		Parent p;
		if ( fullArtfactMainPOM.getParent() == null ){
			p = null;
		}else{
			p = fullArtfactMainPOM.getParent().clone();
			if ( p.getRelativePath() != null )
				p.setRelativePath(
						Joiner.on("/").join(
						fullArtifactPomRelative.getParent()
							.resolve( p.getRelativePath() )
							)
							);
		}
		m.setParent( p );
		
		
		return new GenProject(m, /*incclasses, */ incresources, new GenProject.FullArtifact(fullArtfactMainPOM, null, groupId));
		
	}
	
	private static GenProject buildSubartifact(
			String groupId, String version,
			SubArtifactDeployment deploymentDescription,
			Path parentPomRelative,
			Model parentPom,
			Model fullArtifactPom
//			, Predicate<File> usableJAR
			){
		
//		List<ObjectTypeClassName> incclasses = 
//		FluentIterable.from(
//				Ordering.natural().sortedCopy( deploymentDescription.getResources() ) )
//		.transform( ObjectTypeClassName::forClassName )
////		.toImmutableList()
//		.toList()
//		;
		
		Model m = new Model();
		
		m.setArtifactId(deploymentDescription.getTarget().getArtifactId());
		m.setGroupId(groupId);
		m.setVersion(version);
		m.setPackaging("jar");
		m.setName(deploymentDescription.getTarget().toString());
		
		for ( Map.Entry<File,Dependency> jar :
				deploymentDescription.getJarDeps().entrySet() ){
			Dependency d = jar.getValue();;
//			if ( d != null )
			m.addDependency( d );
		}
		
		for ( SubArtifactDefinition sa :
			deploymentDescription.getSubartDeps()
			){
			Dependency d = new Dependency();
			d.setArtifactId( sa.getArtifactId() );
			d.setGroupId(groupId);
			d.setVersion(version);
			//		if ( d != null )
			m.addDependency( d );
		}
		
		Parent p;
		{
			p = new Parent();
			p.setVersion( parentPom.getVersion() );
			p.setGroupId( parentPom.getGroupId() );
			p.setArtifactId( parentPom.getArtifactId() );
			p.setRelativePath( Joiner.on("/").join( parentPomRelative ) );
		}
		m.setParent( p );
		
		
		return new GenProject(m, deploymentDescription.getResources(),  new GenProject.FullArtifact(fullArtifactPom, null, groupId));
		
	}
	
	static byte[] stdtempalte() throws IOException{
		return resource("template-pom.xml");
	}
	
	static byte[] stdparenttempalte() throws IOException{
		return resource("template-parent-pom.xml");
	}
	
	static byte[] resource(String resource) throws IOException{
		byte[] x= Resources.toByteArray(
				Resources.getResource(
						Joiner.on("/").join(
								Iterables.concat(
								ObjectTypeClassName
								.forClass(PomSetGenerator.class)
								.getPackageHierarchy()
								,
								Arrays.asList(resource)
								)
								)
						) );
		return x;
	}
	private  class PomSetup{
		private final Path pomDir, templatePomDir;
		private final File pomFileGenerated, templatePomFileNowExists;
		protected PomSetup(Path pomDir, Path templatePomDir, File pomFile,
				File templatePomFile) {
			super();
			this.pomDir = pomDir;
			this.templatePomDir = templatePomDir;
			this.pomFileGenerated = pomFile;
			this.templatePomFileNowExists = templatePomFile;
		}
		
	}
	
	private PomSetup initializePOM(
			String subartifactId
			,
			String templatename
			,
			byte[] tempalteByte
			)throws IOException{
		Path parentPomDir = pomSetGenerationDir.resolve(subartifactId);
		Path tempalteParentPomDir = templatePomSetGenerationDir.resolve(subartifactId);
		
		File genDir = parentPomDir.toFile();
		genDir.mkdirs();
		
		
		File tempalteDir = tempalteParentPomDir.toFile();
		tempalteDir.mkdirs();
		
		File tempatePomFile = new File( tempalteDir, templatename);// "template-parent-pom.xml" );
		if ( !tempatePomFile.exists() ){
			//outtput default template
			Files.write(tempalteByte, tempatePomFile);
		}
		
		return new PomSetup( parentPomDir, tempalteParentPomDir, new File( genDir, "pom.xml" ), tempatePomFile );
	}

	public void generate(
			String groupId, String version, String subartsParentArtifactId,
			LinkedHashMap<SubArtifactDefinition, DivisionExecutor.SubArtifactDeployment> generatesBySequence
			) throws IOException, XmlPullParserException {
		
		
		Path parentPomDir = pomSetGenerationDir.resolve(subartsParentArtifactId);
		File fullArtifactPomFile = parentPomDir.resolve( fullArtifactPomRelativeByNewProjectDir ) .toFile();
		File parentPomGenerated;
		
		{
			//generate parent pom to build every subartifact
			PomSetup p =
					initializePOM(subartsParentArtifactId,
					"template-parent-pom.xml",
					stdparenttempalte());
//			File genDir = parentPomDir.toFile();
//			genDir.mkdirs();
//			File tempatePomFile = new File( genDir, "template-parent-pom.xml" );
//			if ( !tempatePomFile.exists() ){
//				//outtput default template
//				Files.write(stdparenttempalte(), tempatePomFile);
//			}
//			File genPomFile = new File( genDir, "pom.xml" );
//			parentPomGenerated = genPomFile;
			
			Model fullPom = new MavenXpp3Reader().read(new FileInputStream(fullArtifactPomFile));
			
			GenProject ge =
			buildParentOfSubartifacts(
					groupId, version, subartsParentArtifactId, generatesBySequence
					, fullArtifactPomRelativeByNewProjectDir
					, fullPom);
			
			String renderedXML = Thymeleafs.start()
					.add("genproject", ge)
					.render(p.templatePomFileNowExists);
			Files.write(renderedXML, p.pomFileGenerated, Charsets.UTF_8);
			parentPomGenerated = p.pomFileGenerated; 
		}
		
		
		
		//generate each subartifact
		for (Map.Entry<SubArtifactDefinition, DivisionExecutor.SubArtifactDeployment> me : generatesBySequence
				.entrySet()) {
//			if ( me.getKey() != JavaLibSubModuleV2.core ) continue;
			SubArtifactDefinition def = me.getKey();
			SubArtifactDeployment dep = me.getValue();

			String artid = def.getArtifactId();
			
			PomSetup p =
					initializePOM(artid,
					"template-pom.xml",
					stdtempalte());
			
//			Path pomDir = pomSetGenerationDir.resolve(artid);
//			File genDir = pomDir.toFile();
//			genDir.mkdirs();
//			File tempatePomFile = new File( genDir, "template-pom.xml" );
//			if ( !tempatePomFile.exists() ){
//				//outtput default template
//				Files.write(stdtempalte(), tempatePomFile);
//			}
//			File genPomFile = new File( genDir, "pom.xml" );
			
			Model parentPom = new MavenXpp3Reader().read(new FileInputStream(parentPomGenerated));
			Model fullPom = new MavenXpp3Reader().read(new FileInputStream(fullArtifactPomFile));
			GenProject ge = buildSubartifact(
					groupId,
					version,
					dep
					, p.pomDir.relativize(parentPomDir)
					, parentPom
					, fullPom
					);

			String renderedXML = Thymeleafs.start()
					.add("genproject", ge)
					.render(p.templatePomFileNowExists);
			Files.write(renderedXML, p.pomFileGenerated, Charsets.UTF_8);
		}
	}

	/**
	 * Low performance but Sufficient Implementation.
	 * 
	 * @author kuzukami
	 *
	 */
	private static class Thymeleafs {
		private final static Map<String, Object> globalEnvironments = Collections
				.synchronizedMap(Maps.<String, Object> newHashMap());

		// public static OnMemoryTemplateResolver staticMemoryTemplateResolver =
		// new OnMemoryTemplateResolver();
		private static FileTemplateResolver resolver() {
			FileTemplateResolver resolver = new FileTemplateResolver();
			resolver.setTemplateMode(
					StandardTemplateModeHandlers.XML.getTemplateModeName());
			// resolver.setPrefix("/WEB-INF/templates/");
			resolver.setCacheable(true);
			resolver.setCacheTTLMs(60000L);
			resolver.setCharacterEncoding("utf-8");
			return resolver;
		}

		private static TemplateEngine engine() {
			TemplateEngine engine = new TemplateEngine();
			engine.setTemplateResolver(resolver());
			return engine;
		}

		public static RuntimeEnvironment start() {
			return new RuntimeEnvironment(Maps.newHashMap(globalEnvironments));
		}

		public static class RuntimeEnvironment {
			protected Map<String, Object> environmentBase;

			private RuntimeEnvironment(Map<String, Object> environmentBase) {
				super();
				this.environmentBase = environmentBase;
			}

			public RuntimeEnvironment add(String variable, Object env) {
				environmentBase.put(variable, env);
				return this;
			}

//			public RuntimeEnvironment addIfNotNull(String variable,
//					Object env) {
//				if (env != null)
//					add(variable, env);
//				return this;
//			}
//
//			public RuntimeEnvironment addArtifact(String prefix,
//					Dependency dep) {
//				return add(prefix + ".groupId", dep.getGroupId())
//						.add(prefix + ".artifactId", dep.getArtifactId())
//						.add(prefix + ".version", dep.getVersion())
//						.add(prefix + ".scope", dep.getScope())
//						.add(prefix + ".classifier", dep.getClassifier());
//			}

			<C extends AbstractContext> C initializeContext(C web) {
				for (Map.Entry<String, Object> m : environmentBase.entrySet())
					web.setVariable(m.getKey(), m.getValue());
				return web;
			}

			public String render(File templateName) {
				return renderXML(templateName, this);
			}
		}

		static void installGlobalVariableValue(String variable, Object env) {
			globalEnvironments.put(variable, env);
		}

		static String renderXML(File templateName, RuntimeEnvironment context) {
			Context cx = new Context();
			context.initializeContext(cx);
			return engine().process(templateName.getAbsolutePath(), cx);
		}

		static String renderXML(File templateName, IContext context) {
			return engine().process(templateName.getAbsolutePath(), context);
		}

	}
}
