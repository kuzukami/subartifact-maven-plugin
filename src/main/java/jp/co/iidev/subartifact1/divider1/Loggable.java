package jp.co.iidev.subartifact1.divider1;

public interface Loggable {
	void info(String text);
	void warn(String text);
	void error(String text);
	void debug(String text);
	
	default void info(String template_slf4j, Object ...args){
		info( StringsIID.replaceTemplateAsSLF4J(template_slf4j, args));
	}
		
	default void warn(String template_slf4j, Object ...args){
		warn( StringsIID.replaceTemplateAsSLF4J(template_slf4j, args));
	}
	default void error(String template_slf4j, Object ...args){
		error( StringsIID.replaceTemplateAsSLF4J(template_slf4j, args));
	}
	
	default void debug(String template_slf4j, Object ...args){
		debug( StringsIID.replaceTemplateAsSLF4J(template_slf4j, args));
	}
}
