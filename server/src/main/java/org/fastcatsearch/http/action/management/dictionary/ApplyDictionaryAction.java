package org.fastcatsearch.http.action.management.dictionary;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.fastcatsearch.cluster.NodeService;
import org.fastcatsearch.db.dao.DictionaryDAO;
import org.fastcatsearch.http.ActionAuthority;
import org.fastcatsearch.http.ActionAuthorityLevel;
import org.fastcatsearch.http.ActionMapping;
import org.fastcatsearch.http.action.ActionRequest;
import org.fastcatsearch.http.action.ActionResponse;
import org.fastcatsearch.http.action.AuthAction;
import org.fastcatsearch.plugin.Plugin;
import org.fastcatsearch.plugin.PluginService;
import org.fastcatsearch.plugin.analysis.AnalysisPlugin;
import org.fastcatsearch.plugin.analysis.AnalysisPluginSetting;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.util.ResponseWriter;

@ActionMapping(value="/management/dictionary/apply", authority=ActionAuthority.Dictionary, authorityLevel=ActionAuthorityLevel.WRITABLE)
public class ApplyDictionaryAction extends AuthAction {

	@Override
	public void doAuthAction(ActionRequest request, ActionResponse response) throws Exception {

		String pluginId = request.getParameter("pluginId");
		String dictionaryId = request.getParameter("dictionaryId");

		PluginService pluginService = ServiceManager.getInstance().getService(PluginService.class);
		Plugin plugin = pluginService.getPlugin(pluginId);
		AnalysisPlugin analysisPlugin = (AnalysisPlugin) plugin;
		AnalysisPluginSetting analysisPluginSetting = analysisPlugin.getPluginSetting();
		
		String[] dictionaryIdList = dictionaryId.split(",");
		
		List<String> successList = new ArrayList<String>();
		List<Integer> successCountList = new ArrayList<Integer>();
		List<String> failList = new ArrayList<String>();
		
		for (int i = 0; i < dictionaryIdList.length; i++) {
			logger.info("# Compile dictionary {}", dictionaryIdList[i]);
			long st = System.nanoTime();
			try{
				//컴파일.
				int count = analysisPlugin.compileDictionaryFromDAO(dictionaryIdList[i]);
				successList.add(dictionaryIdList[i]);
				successCountList.add(count);
				logger.info("# Compile {} dictionary {} : {} Done! {}ms", pluginId, dictionaryIdList[i], count, (System.nanoTime() - st) / (1000 * 1000));
			}catch(IOException e){
				failList.add(dictionaryIdList[i]);
				logger.error("# Compile {} dictionary {} Fail! {}ms", pluginId, dictionaryIdList[i], (System.nanoTime() - st) / (1000 * 1000), e);
			}
		}
		
		//TODO 1. 타 서버 전파 및 리로드 요청.
//		NodeService nodeService = ServiceManager.getInstance().getService(NodeService.class);
//		nodeService.sendRequest(node, job);
		
		//2. 로컬 사전리로드 
		analysisPlugin.reloadDictionary();
		for (int i = 0; i < successList.size(); i++) {
			String id = successList.get(i);
			int applyEntrySize = successCountList.get(i);
			analysisPlugin.dictionaryStatusDAO().updateApplyStatus(id, applyEntrySize);
		}
		
		
		Writer writer = response.getWriter();
		ResponseWriter resultWriter = getDefaultResponseWriter(writer);
		resultWriter.object().key("success").value(failList.size() == 0 && successList.size() > 0);
		resultWriter.key("successList").array();
		for(String id : successList){
			resultWriter.value(id);
		}
		resultWriter.endArray();
		resultWriter.key("failList").array();
		for(String id : failList){
			resultWriter.value(id);
		}
		resultWriter.endArray();
		
		resultWriter.endObject();
		resultWriter.done();

	}

}
