package org.fastcatsearch.cluster;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.fastcatsearch.control.JobService;
import org.fastcatsearch.control.ResultFuture;
import org.fastcatsearch.env.Environment;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.service.AbstractService;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.settings.Settings;
import org.fastcatsearch.transport.TransportException;
import org.fastcatsearch.transport.TransportModule;
import org.fastcatsearch.transport.common.SendFileResultFuture;

public class NodeService extends AbstractService {

	private TransportModule transportModule;
	private Node myNode;
	private Node masterNode;
	private List<Node> nodeList;
	protected static NodeService instance;
	public static NodeService getInstance() {
		return instance;
	}

	public void asSingleton(){
		instance = this;
	}
	public NodeService(Environment environment, Settings settings, ServiceManager serviceManager) {
		super(environment, settings, serviceManager);
	}

	@Override
	protected boolean doStart() throws FastcatSearchException {
		JobService jobService = serviceManager.getService(JobService.class);

		String myNodeName = settings.getString("me");
		String masterNodeName = settings.getString("master");

		nodeList = new ArrayList<Node>();
		List<Settings> nodeSettingList = settings.getSettingList("node_list");
		for (int i = 0; i < nodeSettingList.size(); i++) {
			Settings nodeSetting = nodeSettingList.get(i);
			String id = nodeSetting.getString("id");
			String address = nodeSetting.getString("address");
			int port = nodeSetting.getInt("port");
			boolean isEnabled = !nodeSetting.getBoolean("disabled");
			boolean isMe = myNodeName.equals(id);
			boolean isMaster = masterNodeName.equals(id);

			Node node = new Node(id, address, port);
			nodeList.add(node);

			if (isEnabled) {
				node.setEnabled();
			} else {
				node.setDisabled();
			}

			if (isMe) {
				myNode = node;
			}

			if (isMaster) {
				masterNode = node;
			}

		}

		if (myNode == null) {
			throw new FastcatSearchException("ERR-00300");
		}
		if (masterNode == null) {
			throw new FastcatSearchException("ERR-00301");
		}

		transportModule = new TransportModule(environment, settings, jobService);
		if (myNode.port() > 0) {
			transportModule.settings().put("node_port", myNode.port());
		}

		if (!transportModule.load()) {
			throw new FastcatSearchException("ERR-00305");
		}

		for (Node node : nodeList) {
			if (node.isEnabled() && !node.equals(myNode)) {
				try {
					transportModule.connectToNode(node);
					node.setActive();
				} catch (TransportException e) {
					node.setInactive();
				}
			}
		}

		return true;
	}

	@Override
	protected boolean doStop() throws FastcatSearchException {
		return transportModule.unload();
	}

	@Override
	protected boolean doClose() throws FastcatSearchException {
		return false;
	}

	public List<Node> getNodeList() {
		return nodeList;
	}

	public Node getNodeById(String id) {
		for (Node node : nodeList) {
			// logger.debug("find node >> {}:{}", node.id(), id);
			if (node.id().equals(id)) {
				return node;
			}
		}
		return null;
	}

	public Node getMyNode() {
		return myNode;
	}

	public Node getMaserNode() {
		return masterNode;
	}

	public boolean isMaster() {
		if (masterNode != null && myNode != null) {
			return myNode.equals(masterNode);
		}
		return false;
	}

	public boolean isMyNode(Node node) {
		return myNode.equals(node);
	}

	public ResultFuture sendRequestToMaster(final Job job) {
		if (masterNode.equals(myNode)) {
			return JobService.getInstance().offer(job);
		}
		try {
			return transportModule.sendRequest(masterNode, job);
		} catch (TransportException e) {
			logger.error("sendRequest 에러", e);
		}
		return null;
	}

	public ResultFuture sendRequest(final Node node, final Job job) {
		if (node.equals(myNode)) {
			return JobService.getInstance().offer(job);
		}
		try {
			return transportModule.sendRequest(node, job);
		} catch (TransportException e) {
			logger.error("sendRequest 에러", e);
		}
		return null;
	}

	/*
	 * 파일만 전송가능. 디렉토리는 전송불가. 동일노드로는 전송불가.
	 */
	public SendFileResultFuture sendFile(final Node node, File sourcefile, File targetFile) {
		if (node.equals(myNode)) {
			return null;
		}
		if (sourcefile.isDirectory()) {
			return null;
		}
		try {
			return transportModule.sendFile(node, sourcefile, targetFile);
		} catch (TransportException e) {
			logger.error("sendFile 에러", e);
		}
		return null;

	}

}
