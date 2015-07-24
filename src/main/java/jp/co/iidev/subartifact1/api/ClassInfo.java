package jp.co.iidev.subartifact1.api;

import java.lang.reflect.Modifier;


public interface ClassInfo extends ResourceInfo{
	/**
	 * @see Modifier#isPublic(int)
	 * @see Class#getModifiers()
	 * @return
	 */
	public int getModifiers();
	/**
	 * This equals to {@link Class#getName()}
	 * 
	 * @return
	 */
	public String getClassName();
}