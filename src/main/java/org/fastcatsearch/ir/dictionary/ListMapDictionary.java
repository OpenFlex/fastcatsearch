package org.fastcatsearch.ir.dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.store.DataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.InputStreamDataInput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.fastcatsearch.ir.io.CharVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListMapDictionary extends SourceDictionary implements ReadableDictionary {
	private static Logger logger = LoggerFactory.getLogger(ListMapDictionary.class);

	private Map<CharVector, CharVector[]> map;
	private Set<CharVector> synonymSet;

	public ListMapDictionary() {
		map = new HashMap<CharVector, CharVector[]>();
		synonymSet = new HashSet<CharVector>();
	}

	public ListMapDictionary(Map<CharVector, CharVector[]> map) {
		this.map = map;
	}

	public ListMapDictionary(File file) {
		if(!file.exists()){
			map = new HashMap<CharVector, CharVector[]>();
			synonymSet = new HashSet<CharVector>();
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			readFrom(is);
			is.close();
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	public ListMapDictionary(InputStream is) {
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	@Override
	public void addEntry(String line, boolean caseSensitive) {
		ArrayList<CharVector> list = new ArrayList<CharVector>(4);
		if(!caseSensitive){
			line = line.toUpperCase();
		}
		String[] synonyms = line.split(",");
		if (synonyms.length > 1) {
			CharVector mainWord = null;
			for (int k = 0; k < synonyms.length; k++) {
				String synonym = synonyms[k].trim();
				if (synonym.length() > 0) {
					if (synonym.startsWith("@")) {
						mainWord = new CharVector(synonym.substring(1));
						synonymSet.add(mainWord);
					} else {
						CharVector word = new CharVector(synonym);
						list.add(word);
						synonymSet.add(word);
					}
				}
			}

			if (mainWord == null) {
				// 양방향.
				for (int j = 0; j < list.size(); j++) {
					CharVector key = list.get(j);
					CharVector[] value = new CharVector[list.size() - 1];
					int idx = 0;
					for (int k = 0; k < list.size(); k++) {
						CharVector val = list.get(k);
						if (!key.equals(val)) {
							// 다른것만 value로 넣는다.
							value[idx++] = val;
						}
					}
					//유사어사전 데이터에 대표단어와 동일한 단어가 여러개 있을경우, 최종리스트는 더 적어지게 되므로 전체 array 길이를 줄여준다. 
					if(idx < value.length){
						value = Arrays.copyOf(value, idx);
					}
					map.put(key, value);
//					logger.debug("유사어 양방향 {} >> {} {}", key, value[0], value[1]);
				}
				
			} else {
				// 단방향.
				CharVector[] value = new CharVector[list.size()];
				for (int j = 0; j < value.length; j++) {
					value[j] = list.get(j);
				}
				map.put(mainWord, value);
			}
		}
	}

	public Map<CharVector, CharVector[]> getMap() {
		return Collections.unmodifiableMap(map);
	}

	public Set<CharVector> getWordSet() {
		return Collections.unmodifiableSet(synonymSet);
	}

	@Override
	public void writeTo(OutputStream out) throws IOException {
		
		@SuppressWarnings("resource")
		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharVector> keySet = map.keySet().iterator();
		//write size of map
		output.writeInt(map.size());
		//write key and value map
		for(;keySet.hasNext();) {
			//write key
			CharVector key = keySet.next();
			output.writeString(key.toString());
			//write values
			CharVector[] values = map.get(key);
			output.writeInt(values.length);
			for(CharVector value : values) {
				output.writeString(value.toString());
			}
		}
		
		//write size of synonyms 
		output.writeInt(synonymSet.size());
		
		//write synonyms
		Iterator<CharVector> synonymIter = synonymSet.iterator();
		for(;synonymIter.hasNext();) {
			CharVector value = synonymIter.next();
			output.writeString(value.toString());
		}
	}

	@Override
	public void readFrom(InputStream in) throws IOException {
		
		@SuppressWarnings("resource")
		DataInput input = new InputStreamDataInput(in);
		
		map = new HashMap<CharVector, CharVector[]>();
		synonymSet = new HashSet<CharVector>();
		int size = input.readInt();

		for(int entryInx=0;entryInx < size; entryInx++) {
			CharVector key = new CharVector(input.readString());
			
			int valueLength = input.readInt();
			
			CharVector[] values = new CharVector[valueLength];
			
			for(int valueInx=0; valueInx < valueLength; valueInx++) {
				values[valueInx] = new CharVector(input.readString());
			}
			map.put(key, values);
		}
		
		size = input.readInt();
		for(int entryInx=0;entryInx < size; entryInx++) {
			synonymSet.add(new CharVector(input.readString()));
		}
	}

	@Override
	public List<CharVector> find(CharVector token) {
		if(map.containsKey(token)) {
			return Arrays.asList(map.get(token));
		}
		return null;
	}

	@Override
	public int size() {
		return map.size();
	}
}