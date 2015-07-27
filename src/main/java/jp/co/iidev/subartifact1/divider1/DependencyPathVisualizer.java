package jp.co.iidev.subartifact1.divider1;

import static jp.co.iidev.subartifact1.divider1.StringsIID.replaceTemplateAsSLF4J;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import jp.co.iidev.subartifact1.divider1.ArtifactDivisionPlanner.ArtifactFragment;

public class DependencyPathVisualizer {

	public DependencyPathVisualizer() {
	}
	
	private static String tabPad = "  ";
	
	static String tab( String line, int tabindex ){
		return StringUtils.repeat(tabPad, tabindex);
	}
	
	static List<String> visualizeReversedDepedencyPath(
			List<ArtifactFragment> dependingPathIncludingEnd /*size() >= 1 */,
			List<ArtifactFragment> dependedPathFromStartOfDependingPath /* size() >= 1*/
			, int tabIndent ){
		
		List<String> k = Lists.newArrayList();;
		{

			
			
			dependingPathIncludingEnd = Lists.newArrayList(dependingPathIncludingEnd);
			Collections.reverse(dependingPathIncludingEnd);
			{
				ArtifactFragment af = dependingPathIncludingEnd.get(0);
				k.add( tab(
						replaceTemplateAsSLF4J("{}{}", "", af.toString())
						, tabIndent ++ )
						);
			}
			
			for (ArtifactFragment af : Iterables.skip( dependingPathIncludingEnd, 1 ) ) {
				k.add( tab(
						replaceTemplateAsSLF4J("{}{}", "<=", af.toString())
						, tabIndent ++ )
						);
			}
		}
		{
			dependedPathFromStartOfDependingPath = Lists.newArrayList(dependedPathFromStartOfDependingPath);
			Collections.reverse(dependedPathFromStartOfDependingPath);
			
			for (ArtifactFragment af : Iterables
					.skip(dependedPathFromStartOfDependingPath, 1)) {
				k.add( tab(
						replaceTemplateAsSLF4J("{}{}", "=>", af.toString())
						, tabIndent ++ )
						);
			}
		}
		return k;
	}

}
