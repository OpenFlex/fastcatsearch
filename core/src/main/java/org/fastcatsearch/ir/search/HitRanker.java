/*
 * Copyright 2013 Websquared, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.fastcatsearch.ir.search;

import java.io.IOException;
import java.util.List;

import org.fastcatsearch.ir.field.HitField;
import org.fastcatsearch.ir.field.ScoreField;
import org.fastcatsearch.ir.io.FixedMaxPriorityQueue;
import org.fastcatsearch.ir.query.Sort;
import org.fastcatsearch.ir.settings.FieldIndexSetting;
import org.fastcatsearch.ir.settings.FieldSetting;
import org.fastcatsearch.ir.settings.Schema;
import org.fastcatsearch.ir.sort.SortFunction;


/**
 * 쿼리에서 요청한 Sorts조건에 따라 정렬해주는 랭커.
 * HitElement 에는 byte[] 데이터만 있기 때문에 비교시에는 sort조건에 따라 동작하는 SortFunction이 사용된다.  
 * 이 heap에서 pop한 결과는 역순으로 이용된다.
 * @see HitMerger
 * @author swsong
 *
 */
public class HitRanker extends FixedMaxPriorityQueue<HitElement>{
	private SortFunction[] sortFunctions;
	
	public HitRanker(List<Sort> querySortList, Schema schema, int maxSize) throws IOException{
		super(maxSize);
		int size = querySortList.size();
		sortFunctions = new SortFunction[size];
		
		for (int i = 0; i < size; i++) {
			Sort sort = querySortList.get(i);
			String fieldIndexId = sort.fieldIndexId();
			int idx = schema.getFieldIndexSequence(fieldIndexId);
			////////_HIT , _SCORE 필드의 경우 처리해준다.
			if(idx == -1){
				if(fieldIndexId.equalsIgnoreCase(ScoreField.fieldName)){
					sortFunctions[i] = sort.createSortFunction(ScoreField.field);
				}else if(fieldIndexId.equalsIgnoreCase(HitField.fieldName)){
					sortFunctions[i] = sort.createSortFunction(HitField.field);
				}else{
					throw new IOException("Unknown sort field name = "+fieldIndexId);
				}
			}else{
				FieldIndexSetting fieldIndexSetting = schema.getFieldIndexSetting(fieldIndexId);
				String refId = fieldIndexSetting.getRef();
				FieldSetting fieldSetting = schema.getFieldSetting(refId);
				sortFunctions[i] = sort.createSortFunction(fieldSetting);
			}
			
			logger.debug("sortFunctions[{}]=[{}]=", i, sortFunctions[i]);
		}
	}
	
	@Override
	protected int compare(HitElement one, HitElement two) {
		
		for (int i = 0; i < sortFunctions.length; i++) {
			//하나씩 비교해가면서 각 funtion의 비교결과가 0이 아닐때 까지 비교한다.
			int r = sortFunctions[i].compare(one.rankData(i), two.rankData(i));
			if(r != 0){
				return r;
			}
		}
		
		return one.compareTo(two);
		
//		//최신세그먼트 우선.
//		if(one.segmentSequence() != two.segmentSequence()){
//			return two.segmentSequence() - one.segmentSequence();
//		}
//		
//		//정렬 데이터가 모두 같다면 문서번호가 최신인걸 보여준다. 
//		return two.docNo() - one.docNo();
	}
	
}
