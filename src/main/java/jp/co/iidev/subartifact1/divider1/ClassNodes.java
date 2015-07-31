package jp.co.iidev.subartifact1.divider1;

import java.util.List;
import java.util.stream.Stream;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InnerClassNode;

import com.google.inject.internal.util.Lists;

public class ClassNodes {

	public static List<String> getNestCanonicalClassNames( ClassNode cn ){
		List<InnerClassNode> icnl =  (List<InnerClassNode>)cn.innerClasses;
		
		String header = cn.name + "$";
		List<String> r = Lists.newArrayList();
		
		for ( InnerClassNode icn : icnl ){
			if ( 
					 //nested
					icn.name.startsWith(header)
					&&
					 //directly nested
					!icn.name.substring(header.length()).contains("$")
				){
				r.add( icn.name  );
			}
		}
		
		return r;
	}
	public static boolean isPublic( ClassNode cn ){
		return ( cn.access & Opcodes.ACC_PUBLIC ) != 0;
	}
	public static boolean isTopLevel( ClassNode cn ){
		return !cn.name.contains("$");
	}

}
