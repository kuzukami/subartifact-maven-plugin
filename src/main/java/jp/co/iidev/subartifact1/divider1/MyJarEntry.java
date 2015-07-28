package jp.co.iidev.subartifact1.divider1;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;


class MyJarEntry{
	private final String jarEntryName;
	private final boolean isDirectory;
	private final OpenJAR sourceJAR;

	MyJarEntry(String jarEntryName, boolean isDirectory, OpenJAR sourceJAR) {
		super();
		this.jarEntryName = jarEntryName;
		this.isDirectory = isDirectory;
		this.sourceJAR = sourceJAR;
	}
	
	
	private static Splitter sp = Splitter.on(".");
	public Iterable<String> getDiretoryPath(){
		int k = getBasenameStartIndex();
		if ( k == 0 )  return Lists.newArrayList();
		return  sp.split( jarEntryName.substring(0, k-1)  );
	}
	
	private int getBasenameStartIndex(){
		return jarEntryName.lastIndexOf("/") + 1;
	}
	public String getBasename(){
		return jarEntryName.substring(getBasenameStartIndex());
	}
	public boolean isClassFile(){
		return jarEntryName.endsWith(".class");
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
	
	static class OpenJAR {
		final JarFile jarfile;
		final File baseFile;
		
		OpenJAR(JarFile jarfie, File baseFile) {
			super();
			this.jarfile = jarfie;
			this.baseFile = baseFile;
		}
		static OpenJAR open( File jarfile ) throws IOException{
			return new OpenJAR(
					new JarFile(jarfile)
					, jarfile);
		}
		
		synchronized byte[] read( String resourcePath ) throws IOException{
			try ( InputStream isx = 
					jarfile.getInputStream(
							jarfile.getJarEntry(resourcePath) ) ){
				return ByteStreams.toByteArray(isx);
			}
		}
	}
	
	static FluentIterable<MyJarEntry> extractEntries( File jarfile ) throws FileNotFoundException, IOException{
		OpenJAR o = OpenJAR.open(jarfile);
		return
				FluentIterable.from(
		o.jarfile
		.stream()
		.map(je -> new MyJarEntry(je.getName(), je.isDirectory(), o))
		.collect(Collectors.toList())
		)
		;
	}
}