package org.fastcatsearch.ir.dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HashSetDictionary extends SourceDictionary implements ReadableDictionary {
	private static Logger logger = LoggerFactory.getLogger(HashSetDictionary.class);
	
	private Set<CharVector> set;

	public HashSetDictionary() {
		set = new HashSet<CharVector>();
	}

	public HashSetDictionary(Set<CharVector> set) {
		this.set = set;
	}

	public HashSetDictionary(File file) {
		if(!file.exists()){
			set = new HashSet<CharVector>();
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is;
		try {
			is = new FileInputStream(file);
			readFrom(is);
			is.close();
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	public HashSetDictionary(InputStream is){
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}
	
	@Override
	public void addEntry(String line, boolean caseSensitive) {
		line = line.trim();
		if(!caseSensitive){
			line = line.toUpperCase();
		}
		if (line.length() > 0) {
			set.add(new CharVector(line));
		}
	}

	public Set<CharVector> getSet() {
		return Collections.unmodifiableSet(set);
	}
	
	@Override
	public void writeTo(OutputStream out) throws IOException {
		
		@SuppressWarnings("resource")
		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharVector> valueIter = set.iterator();
		//write size of set
		
		output.writeInt(set.size());
		//write values
		for(;valueIter.hasNext();) {
			CharVector value = valueIter.next();
			output.writeString(value.toString());
		}
	}

	@Override
	public void readFrom(InputStream in) throws IOException {
		
		@SuppressWarnings("resource")
		DataInput input = new InputStreamDataInput(in);
		set = new HashSet<CharVector>();
		int size = input.readInt();
		
		for(int entryInx=0;entryInx < size; entryInx++) {
			set.add(new CharVector(input.readString()));
		}
	}

	@Override
	public List<CharVector> find(CharVector token) {
		if(set.contains(token)) {
			Arrays.asList(new CharVector[] { token });
		}
		return null;
	}

	@Override
	public int size() {
		return set.size();
	}
}
