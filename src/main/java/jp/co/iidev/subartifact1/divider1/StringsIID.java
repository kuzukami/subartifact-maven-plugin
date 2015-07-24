package jp.co.iidev.subartifact1.divider1;

class StringsIID {
	public static String replaceTemplateAsSLF4J( String template, Object ...values ){
		StringBuilder s = new StringBuilder();
		String repl = "{}";
		for ( int vsindex = 0, tsindex = 0; ; vsindex ++ , tsindex += repl.length()){

			int replindex = ( template.indexOf(repl,tsindex)  );
			if ( replindex < 0 ) {
				//flush all template left
				s.append(template, tsindex, template.length());
				break;
			}

			s.append(template, tsindex, replindex);
			s.append(values[vsindex]);

			tsindex = replindex;
		}
		return s.toString();
	}

}
