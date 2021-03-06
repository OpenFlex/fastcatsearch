///*
// * Copyright (c) 2013 Websquared, Inc.
// * All rights reserved. This program and the accompanying materials
// * are made available under the terms of the GNU Public License v2.0
// * which accompanies this distribution, and is available at
// * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
// * 
// * Contributors:
// *     swsong - initial API and implementation
// */
//
//package org.fastcatsearch.job;
//
//import java.io.File;
//import java.util.List;
//
//
//import org.fastcatsearch.db.DBService;
//import org.fastcatsearch.db.dao.MapDictionary;
//import org.fastcatsearch.db.dao.SetDictionary;
//import org.fastcatsearch.db.vo.SetDictionaryVO;
//import org.fastcatsearch.ir.common.IRException;
//import org.fastcatsearch.ir.config.IRConfig;
//import org.fastcatsearch.ir.dic.HashMapDictionary;
//import org.fastcatsearch.ir.dic.HashSetDictionary;
//import org.fastcatsearch.ir.io.CharVector;
//import org.fastcatsearch.exception.FastcatSearchException;
//import org.fastcatsearch.settings.IRSettings;
//
//
//public class HashMapDictionaryCompileApplyJob extends Job {
//
//	private static int BUCKET_SIZE = 16 * 1024;
//	
//	@Override
//	public JobResult doRun() throws FastcatSearchException {
//		String[] args = getStringArrayArgs();
//		if("synonymDic".equals(args[0])){
//			try {
//				compileSynonymDic();
////				return new JobResult(Dic.reload("synonym"));
//			} catch (IRException e) {
//				throw new FastcatSearchException(e.getMessage(), e);
//			}
//		}
//		
//		return new JobResult(-1);
//	}
//	
//	
//	public int compileSynonymDic() throws IRException{
//		IRConfig irConfig = IRSettings.getConfig();
//		String twoWayStr = irConfig.getString("synonym.two-way");
//		boolean isTwoWay = false;
//		if(twoWayStr != null && "true".equalsIgnoreCase(twoWayStr)){
//			isTwoWay = true;
//		}
//		
//		String dicPath = IRSettings.path(irConfig.getString("synonym.dic.path"));
//		
//		HashMapDictionary dic = new HashMapDictionary(BUCKET_SIZE);
//		
//		DBService dbService = DBService.getInstance();
//		SetDictionary synonymDictionary = dbService.getDAO("SynonymDictionary");
//		int count = synonymDictionary.selectCount();
//		
//		int BULK_SIZE = 100;
//		
//		for(int startRow = 0; startRow < count; startRow += BULK_SIZE){
//			List<SetDictionaryVO> list = synonymDictionary.selectPage(startRow, BULK_SIZE);
//			
//			//bulk insert
//			for (int i = 0; i < list.size(); i++) {
//				SetDictionaryVO  synonym = list.get(i);
//				char[] charArr = synonym.keyword.toCharArray();
//				int size = 1;
//				for (int j = 0; j < charArr.length; j++) {
//					if (charArr[j] == ',') {
//						size++;
//					}
//				}
//				
//				
//				//
//				//
//				//
//				//
//				//
//				//
//				// TODO @단어, 단어2, 단어3 에서 대표단어 및 유사어를 뽑아내어 컴파일한다.
//				//
//				//
//				//
//				//
//				//
//				//
//				//
//				//
//				CharVector[] values = new CharVector[size + 1];
//				int p = 0;
//				values[p++] = new CharVector(synonym.keyword).toUpperCase();
//				
//				int prev = 0;
//				for (int j = 0; j < charArr.length; j++) {
//					if (charArr[j] == ',') {
//						values[p++] = new CharVector(charArr, prev, j - prev).toUpperCase();
//						prev = j + 1;
//					}
//				}
//				values[p++] = new CharVector(charArr, prev, charArr.length - prev).toUpperCase();
//				
//				if(isTwoWay){
//					//양방향
//					for (int j = 0; j < values.length; j++) {
//						CharVector[] val = new CharVector[values.length - 1];
//						int m = 0;
//						for (int k = 0; k < values.length; k++) {
//							if(k != j){
//								val[m++] = values[k];
////								logger.debug("syn = "+values[j] +" => "+values[k]);
//							}
//						}
//						dic.put(values[j], val);
//						
//
//					}
//					
//				}else{
//					//단방향
//					CharVector[] val = new CharVector[values.length - 1];
//					int m = 0;
//					for (int k = 1; k < values.length; k++) {
//						val[m++] = values[k];
//					}
//					dic.put(values[0], val);
//				}
//				
//			}
//		}
//		
//		dic.save(new File(dicPath));
//		
//		return count;
//	}
//
//}
