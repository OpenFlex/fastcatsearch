package org.fastcatsearch.servlet;

import java.io.IOException;
import java.io.Writer;

import org.fastcatsearch.common.Strings;
import org.fastcatsearch.ir.group.GroupEntry;
import org.fastcatsearch.ir.group.GroupResult;
import org.fastcatsearch.ir.group.GroupResults;
import org.fastcatsearch.util.ResultWriter;
import org.fastcatsearch.util.StringifyException;

public class GroupResultWriter extends AbstractSearchResultWriter {
	
	public GroupResultWriter(ResultWriter resultWriter) {
		super(resultWriter);
	}
	
	@Override
	public void writeResult(Object result, long searchTime, boolean isSuccess) throws StringifyException, IOException{
		
		resultWriter.object();
		
		if(!isSuccess){
			String errorMsg = null;
			if(result == null){
				errorMsg = "null";
			}else{
				errorMsg = result.toString();
			}
			resultWriter.key("status").value(1)
			.key("time").value(Strings.getHumanReadableTimeInterval(searchTime))
			.key("total_count").value(0)
			.key("count").value(0)
			.key("error_msg").value(errorMsg);
			
		}else{
			
			GroupResults groupResults = (GroupResults) result;
			resultWriter.key("status").value(0)
			.key("time").value(Strings.getHumanReadableTimeInterval(searchTime))
			.key("total_count").value(groupResults.totalSearchCount())
			.key("count").value(groupResults.totalSearchCount());
			
			writeBody(groupResults, resultWriter);
		}
		
		resultWriter.endObject();
		resultWriter.done();
	}
	
	public void writeBody(GroupResults groupResults, ResultWriter resultStringer) throws StringifyException {
		
		if(groupResults == null){
    		resultStringer.key("group_result").array("group_list").endArray();
		} else {
			GroupResult[] groupResultList = groupResults.groupResultList();
    		resultStringer.key("group_result").array("group_list");
    		for (int i = 0; i < groupResultList.length; i++) {
    			GroupResult groupResult = groupResultList[i];
    			resultStringer.object()
    			.key("label").value(groupResult.fieldId())
    			.key("result").array("group_item");
				int size = groupResult == null ? 0 : groupResult.size();
				for (int k = 0; k < size; k++) {
					GroupEntry e = groupResult.getEntry(k);
					String keyData = e.key;
					String[] functionName = groupResult.headerNameList();
					
					resultStringer.object()
						.key("_NO").value(k+1)
						.key("_KEY").value(keyData);
					
					for (int j = 0; j < functionName.length; j++) {
						resultStringer.key(functionName[j]).value(e.groupingValue(j));
					}
					
					resultStringer.endObject();
				}//for
				resultStringer.endArray();//group_item
				resultStringer.endObject();
    		}//for
    		resultStringer.endArray();
		}//if else
	}
}
