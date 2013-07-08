package org.fastcatsearch.servlet;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.fastcatsearch.ir.group.GroupResult;
import org.fastcatsearch.ir.group.GroupResults;
import org.fastcatsearch.ir.query.Result;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.util.ResultStringer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSearchServlet extends WebServiceHttpServlet {

	private static final long serialVersionUID = 4165287616751500253L;
	protected static AtomicLong taskSeq = new AtomicLong();
	protected static Logger searchLogger = LoggerFactory.getLogger("SEARCH_LOG");

	public static final int DEFAULT_TIMEOUT = 5; //5초.
	public static final int IS_ALIVE = 3;

	protected String requestCharset;
	protected String responseCharset;
	protected boolean isAdmin;
	protected int timeout;

	protected String collectionName;
	protected String fields;
	protected String searchCondition;
	protected String groupFields;
	protected String groupCondition;
	protected String groupFilter;
	protected String sortFields;
	protected String filterFields;
	protected String startNumber;
	protected String resultLength;
	protected String highlightTags;
	protected String searchOption;
	protected String userData;
	protected String jsonCallback;

	public AbstractSearchServlet(int resultType) {
		super(resultType);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doReal(request, response);
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doReal(request, response);
	}

	private void doReal(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		prepare(request);

		if (resultType == IS_ALIVE) {
			response.setContentType("text/html; charset=" + responseCharset);
			response.getWriter().write("FastCat/OK\n<br/>" + new Date());
			return;
		}

		String queryString = queryString();

		logger.debug("queryString = " + queryString);
		logger.debug("timeout = " + timeout + " s");

		long requestId = taskSeq.incrementAndGet();
		searchLogger.info("{},{}", requestId, queryString);

		doSearch(requestId, queryString, response);

	}

	protected abstract void doSearch(long requestId, String queryString, HttpServletResponse response) throws ServletException, IOException;

	protected abstract Job createSearchJob(String queryString);

	protected abstract AbstractSearchResultWriter createSearchResultWriter(Writer writer);

	public void prepare(HttpServletRequest request) {
		requestCharset = getParameter(request, "requestCharset", "UTF-8");
		responseCharset = getParameter(request, "responseCharset", "UTF-8");
		String timeoutStr = getParameter(request, "timeout", Integer.toString(DEFAULT_TIMEOUT));
		try {
			timeout = Integer.parseInt(timeoutStr);
		} catch (NumberFormatException e) {
			timeout = DEFAULT_TIMEOUT;
		}
		isAdmin = "true".equals(request.getParameter("admin"));

		collectionName = getParameter(request, "cn", "");
		fields = getParameter(request, "fl", "");
		searchCondition = getParameter(request, "se", "");
		groupFields = getParameter(request, "gr", "");
		groupCondition = getParameter(request, "gc", "");
		groupFilter = getParameter(request, "gf", "");
		sortFields = getParameter(request, "ra", "");
		filterFields = getParameter(request, "ft", "");
		startNumber = getParameter(request, "sn", "");
		resultLength = getParameter(request, "ln", "");
		highlightTags = getParameter(request, "ht", "");
		searchOption = getParameter(request, "so", "");
		userData = getParameter(request, "ud", "");
		jsonCallback = getParameter(request, "jsoncallback", "");
	}

	public String queryString() {
		try {
			//2013-7-8
			//URL에 +로 들어오는 경우는 없으며, +로 들어오면 검색식의 일부이므로 decode시 공백으로 치환되지 않도록 강제 인코딩을 해준다.
			searchCondition = searchCondition.replaceAll("\\+", URLEncoder.encode("+", requestCharset));
			return "cn=" + collectionName + "&fl=" + URLDecoder.decode(fields, requestCharset) + "&se="
			+ URLDecoder.decode(searchCondition, requestCharset) + "&gr="
			+ URLDecoder.decode(groupFields, requestCharset) + "&gc=" + URLDecoder.decode(groupCondition, requestCharset)
			+ "&gf=" + URLDecoder.decode(groupFilter, requestCharset) + "&ra="
			+ URLDecoder.decode(sortFields, requestCharset) + "&ft=" + URLDecoder.decode(filterFields, requestCharset)
			+ "&sn=" + URLDecoder.decode(startNumber, requestCharset) + "&ln="
			+ URLDecoder.decode(resultLength, requestCharset) + "&ht=" + URLDecoder.decode(highlightTags, requestCharset)
			+ "&so=" + URLDecoder.decode(searchOption, requestCharset) + "&ud="
			+ URLDecoder.decode(userData, requestCharset);
		} catch (UnsupportedEncodingException e) {
			logger.error("", e);
		}
		return "";
	}
	
	public void writeHeader(HttpServletResponse response, ResultStringer stringer) {
		writeHeader(response, stringer, responseCharset);
	}
	
	public ResultStringer getResultStringer() {
		return getResultStringer("fastcat",isAdmin, jsonCallback);
	}

	protected void writeSearchLog(long requestId, Object obj, long searchTime){
    	if(obj instanceof Result){
			Result result = (Result) obj;
			String logStr = requestId+","+searchTime+","+result.getCount()+","+result.getTotalCount();
			if(result.getGroupResult() != null){
				String grStr = ",";
				GroupResults groupResults = result.getGroupResult();
				GroupResult[] gr = groupResults.groupResultList();
				for (int i = 0; i < gr.length; i++) {
					if(i > 0)
						grStr += ",";
					grStr += gr[i].size();
				}
				logStr += grStr;
			}
			searchLogger.info(logStr);
			
		}else if(obj instanceof GroupResults){
			GroupResults groupResults = (GroupResults) obj;
			GroupResult[] gr = groupResults.groupResultList();
			String grStr = requestId+",0,0,0";
			for (int i = 0; i < gr.length; i++) {
				if(i > 0)
					grStr += ",";
				grStr += gr[i].size();
			}
			searchLogger.info(grStr);
		}
    }
}
