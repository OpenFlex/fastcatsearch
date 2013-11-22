package org.fastcatsearch.http.action.management.collections;

import java.io.Writer;

import org.fastcatsearch.http.ActionMapping;
import org.fastcatsearch.http.action.ActionRequest;
import org.fastcatsearch.http.action.ActionResponse;
import org.fastcatsearch.http.action.AuthAction;
import org.fastcatsearch.ir.IRService;
import org.fastcatsearch.ir.config.CollectionContext;
import org.fastcatsearch.ir.search.ShardHandler;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.util.CollectionContextUtil;
import org.fastcatsearch.util.ResponseWriter;

@ActionMapping("/management/collections/shard/remove")
public class DeleteShardAction extends AuthAction {

	@Override
	public void doAuthAction(ActionRequest request, ActionResponse response) throws Exception {
		
		String collectionId = request.getParameter("collectionId");
		String shardId = request.getParameter("shardId");
		
		boolean isSuccess = false;
		IRService irService = ServiceManager.getInstance().getService(IRService.class);
		try{
			ShardHandler shardHandler = irService.collectionHandler(collectionId).getShardHandler(shardId);
			shardHandler.close();
			
			CollectionContext collectionContext = irService.collectionContext(collectionId);
			CollectionContextUtil.removeShard(collectionContext, shardId);
			isSuccess = true;
		}catch(Exception e){
			logger.error("{}", e);
		}
		
		Writer writer = response.getWriter();
		ResponseWriter responseWriter = getDefaultResponseWriter(writer);
		responseWriter.object()
		.key("collectionId").value(collectionId)
		.key("shardId").value(shardId)
		.key("success").value(isSuccess)
		.endObject();
		responseWriter.done();
	}

}