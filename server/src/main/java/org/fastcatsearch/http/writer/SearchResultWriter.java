package org.fastcatsearch.http.writer;

import java.io.IOException;
import java.text.DecimalFormat;

import org.fastcatsearch.ir.field.ScoreField;
import org.fastcatsearch.ir.group.GroupResults;
import org.fastcatsearch.ir.query.Result;
import org.fastcatsearch.ir.query.Row;
import org.fastcatsearch.ir.util.Formatter;
import org.fastcatsearch.util.ResponseWriter;
import org.fastcatsearch.util.ResultWriterException;

public class SearchResultWriter extends AbstractSearchResultWriter {
	
	private String[] fieldNames;
	 
	public SearchResultWriter(ResponseWriter resultStringer) {
		super(resultStringer);
	}

	@Override
	public void writeResult(Object obj, long searchTime, boolean isSuccess) throws ResultWriterException, IOException {
		if(!isSuccess){
			String errorMsg = null;
			if(obj == null){
				errorMsg = "null";
			}else{
				errorMsg = obj.toString();
			}
			resultWriter.object()
				.key("status").value(1)
				.key("time").value(Formatter.getFormatTime(searchTime))
				.key("total_count").value(0)
				.key("error_msg").value(errorMsg).endObject();
		}else{
			Result result = (Result)obj;
			
			fieldNames = result.getFieldNameList();
			resultWriter.object()
			.key("status").value(0)
			.key("time").value(Formatter.getFormatTime(searchTime))
			.key("start").value(result.getStart())
			.key("total_count").value(result.getTotalCount())
			.key("count").value(result.getCount())
			.key("field_count").value(fieldNames.length)
			.key("fieldname_list")
			.array("name");

			for (int i = 0; i < fieldNames.length; i++) {
				resultWriter.value(fieldNames[i]);
			}
			resultWriter.endArray();
			writeBody(result,resultWriter, searchTime);
			resultWriter.endObject();
			
			resultWriter.done();
		}
		
	}
	DecimalFormat decimalFormat = new DecimalFormat("#0.0");
	public void writeBody(Result result, ResponseWriter resultWriter, long searchTime) throws ResultWriterException {
		resultWriter.key("result");
		//data
		Row[] rows = result.getData();
		//int start = result.getStart();

		if(rows.length == 0){
			resultWriter.array("item").endArray();
		}else{
			resultWriter.array("item");
			for (int i = 0; i < rows.length; i++) {
				Row row = rows[i];

				resultWriter.object();
//				.key("_no_").value(String.valueOf(start+i));

				for(int k = 0; k < fieldNames.length; k++) {
					String fdata = null;
					if(fieldNames[k].equalsIgnoreCase(ScoreField.fieldName)){
						fdata = decimalFormat.format(row.getScore());
					}else{
						char[] f = row.get(k);
						fdata = new String(f).trim();
					}
					resultWriter.key(fieldNames[k]).value(fdata);
				}
				resultWriter.endObject();
			}
			resultWriter.endArray();
			
			GroupResults groupResult = result.getGroupResult();
			
			new GroupResultWriter(null).writeBody(groupResult, resultWriter);
		}
	}
}
