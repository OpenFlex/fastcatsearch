package org.fastcatsearch.ir.dictionary;

import java.io.IOException;
import java.io.OutputStream;

public interface WritableDictionary {
	
	//caseSensitive : 대소문자 구분
	public void addEntry(String line, boolean caseSensitive);
	
	public void writeTo(OutputStream out) throws IOException;
	
}
