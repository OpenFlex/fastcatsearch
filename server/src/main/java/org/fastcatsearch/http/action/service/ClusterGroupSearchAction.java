package org.fastcatsearch.http.action.service;

import org.fastcatsearch.http.ActionMapping;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.job.search.ClusterGroupSearchJob;
import org.fastcatsearch.query.QueryMap;

@ActionMapping("/service/search/group")
public class ClusterGroupSearchAction extends AbstractSearchAction {

	@Override
	protected Job createSearchJob(QueryMap queryMap) {
		Job searchJob = new ClusterGroupSearchJob();
		searchJob.setArgs(queryMap);
    	return searchJob;
	}

}
