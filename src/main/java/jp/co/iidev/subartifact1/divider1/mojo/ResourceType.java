package jp.co.iidev.subartifact1.divider1.mojo;

import java.util.Optional;

import org.objectweb.asm.tree.ClassNode;

import jp.co.iidev.subartifact1.divider1.ClassNodes;

public enum ResourceType {
	PublicAccessTopLevelClass,
	DefaultAccessTopLevelClass,
	InnerClass,
	Resource,
	;
	
	public static ResourceType maps( Optional<ClassNode> cn ){
		if ( !cn.isPresent() ){
			return Resource;
		}else{
			ClassNode cnx = cn.get();
			if ( ClassNodes.isTopLevel(cnx) ){
				if ( ClassNodes.isPublic(cnx) )
					return PublicAccessTopLevelClass;
				else
					return DefaultAccessTopLevelClass;
			}else{
				return InnerClass;
			}
		}
	}
}
