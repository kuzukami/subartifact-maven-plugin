package jp.co.iidev.subartifact1.divider1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.BiMap;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

import jp.co.iidev.subartifact1.divider1.mojo.ResourceType;

class JARIndex implements AutoCloseable{
	private final JARHandle jarhandle;
	private final BiMap<FragmentName, MyJarEntry> entries;
	
	private JARIndex( JARHandle handl ) {
		entries  = HashBiMap.create();
		this.jarhandle = handl;
	}
	
	public MyJarEntry getEntry(String entryname ){
		return entries.get(
				FragmentName.forJarEntryName(entryname));
	}
	
	public BiMap<FragmentName, MyJarEntry> getEntries() {
		return entries;
	}
	

	public Stream<Map.Entry<MyJarEntry, Supplier<byte[]>>> dataStream() throws IOException{
		return
		jarhandle.dataStream()
		.map((j2d) -> {
			return Maps.immutableEntry(
					getEntry( j2d.getKey().getName() ),
					j2d.getValue() );
		});
	}

	static JARIndex index( File jarfile ) throws FileNotFoundException, IOException{
		JARHandle o = JARHandle.open(jarfile);
		FluentIterable<MyJarEntry> me = extractEntries(o);
		JARIndex  j = new JARIndex(o );
		me.forEach((e) -> {
			if ( e.isFile() ){
				j.entries.put(
						FragmentName.forJarEntry( e )
						, e);
			}
		});
		return j;
	}
	
	static FluentIterable<MyJarEntry> extractEntries( JARHandle o ) throws FileNotFoundException, IOException{
		return
				FluentIterable.from(
		o.jarfile
		.stream()
		.map(je -> new MyJarEntry(je.getName(), je.isDirectory(), o))
		.collect(Collectors.toList())
		)
		;
	}

	@Override
	public void close() throws Exception {
		jarhandle.close();;
	}

	static class JARHandle implements AutoCloseable{
		final JarFile jarfile;
		final File baseFile;
		
		JARHandle(JarFile jarfie, File baseFile) {
			super();
			this.jarfile = jarfie;
			this.baseFile = baseFile;
		}
		static JARHandle open( File jarfile ) throws IOException{
			return new JARHandle(
					new JarFile(jarfile)
					, jarfile);
		}
		
		Stream<Map.Entry<JarEntry,Supplier<byte[]>>> dataStream() throws IOException{
			JarFile newent = new JarFile(baseFile);
			Stream<JarEntry> jel = 
			newent.stream().sequential()
			;
			return
			jel
			.map((je) ->{
				Supplier<byte[]> 
				s = () -> {
				try {
					return ByteStreams.toByteArray(newent.getInputStream(je));
				} catch (Exception e) {
					throw new RuntimeException(e);
				}};
				return Maps.immutableEntry(je, s );
			})
			.onClose(() -> {
				try {
					newent.close();
				}catch (IOException i ){
					throw new RuntimeException(i);
				}
			})
			;
		}
		
		synchronized byte[] read( String resourcePath ) throws IOException{
			try ( InputStream isx = 
					jarfile.getInputStream(
							jarfile.getJarEntry(resourcePath) ) ){
				return ByteStreams.toByteArray(isx);
			}
		}
		@Override
		public void close() throws Exception {
			jarfile.close();
		}
	}
	static boolean isClassfileResourceName(String name ){
		return name.endsWith(".class");
	}
	
	public static String fromObjectClassNameToCannonicalName(String x) {
		return x.replace(".", "/");
	}
	
	static class MyJarEntry{
		private final String jarEntryName;
		private final boolean isDirectory;
		private final JARIndex.JARHandle sourceJAR;

		MyJarEntry(String jarEntryName, boolean isDirectory, JARIndex.JARHandle sourceJAR) {
			super();
			this.jarEntryName = jarEntryName;
			this.isDirectory = isDirectory;
			this.sourceJAR = sourceJAR;
		}
		
		
		private static Splitter sp = Splitter.on(".");
		/**
		 * resource path without basename => directory path
		 * @return
		 */
		public Iterable<String> getDiretoryPath(){
			int k = getBasenameStartIndex();
			if ( k == 0 )  return Lists.newArrayList();
			return  sp.split( jarEntryName.substring(0, k-1)  );
		}
		
		public String getDiretoryPathAlikePackage( String packageJoiner ){
			return Joiner.on(packageJoiner).join(getDiretoryPath());
		}
		
//		public ResourceType mapAsResourceType(){
//			if ( isClassFile() ){
//				if ( DetailJarAnalysis.isToplevelClass(this) )
//					return ResourceType.TopLevelClass;
//				else
//					return ResourceType.InnerClass;
//			}
//			return ResourceType.Resource;
//		}
		
		private int getBasenameStartIndex(){
			return jarEntryName.lastIndexOf("/") + 1;
		}
		public String getBasename(){
			return jarEntryName.substring(getBasenameStartIndex());
		}
		
		public String getJarEntryName(){
			return jarEntryName;
		}
		public boolean isClassFile(){
			return isClassfileResourceName(jarEntryName);
		}
		public boolean isFile(){
			return !isDirectory;
		}
		public byte[] getBytes(){
			try {
				return sourceJAR.read(jarEntryName);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}




}
