/*
 * Copyright (c) 2013 Websquared, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     swsong - initial API and implementation
 */

package org.fastcatsearch.server;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.fastcatsearch.alert.ClusterAlertService;
import org.fastcatsearch.cluster.NodeService;
import org.fastcatsearch.control.JobService;
import org.fastcatsearch.data.DataService;
import org.fastcatsearch.db.DBService;
import org.fastcatsearch.env.Environment;
import org.fastcatsearch.ir.IRService;
import org.fastcatsearch.log.EventDBLogger;
import org.fastcatsearch.management.ManagementInfoService;
import org.fastcatsearch.notification.NotificationService;
import org.fastcatsearch.plugin.PluginService;
import org.fastcatsearch.processlogger.ProcessLoggerService;
import org.fastcatsearch.service.KeywordService;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.service.WebService;
import org.fastcatsearch.settings.IRSettings;
import org.fastcatsearch.statistics.StatisticsInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CatServer {

	private ServiceManager serviceManager;

	public static long startTime;
	public static CatServer instance;
	private static Logger logger;
	private boolean isRunning;

	private Thread shutdownHook;
	protected boolean keepAlive;

	private String serverHome;
	private static volatile Thread keepAliveThread;
	private static volatile CountDownLatch keepAliveLatch;

	public static void main(String... args) throws FastcatSearchException {
		if (args.length < 1) {
			usage();
			return;
		}
		
		CatServer server = new CatServer(args[0]);
		if (server.load(args)) {
			server.start();
		}
	}

	public void setKeepAlive(boolean b) {
		keepAlive = b;
	}

	public boolean isKeepAlive() {
		return keepAlive;
	}

	public boolean isRunning() {
		return isRunning;
	}

	public static CatServer getInstance() {
		return instance;
	}

	public CatServer() {
	}

	public CatServer(String serverHome) {
		this.serverHome = serverHome;
	}

	public boolean load(){
		return load(null);
	}
	public boolean load(String[] args) {

		boolean isConfig = false;

		//load 파라미터는 없을수도 있다.
		if(args != null){
			for (int i = 0; i < args.length; i++) {
				if (isConfig) {
					isConfig = false;
				} else if (args[i].equals("-config")) {
					isConfig = true;
				} else if (args[i].equals("-help")) {
					usage();
					return false;
				}
			}
		}
		
		setKeepAlive(true);
		return true;

	}

	protected static void usage() {

        System.out.println
            ("usage: java "+ CatServer.class.getName()
             + " [ -help -config ]"
             + " {HomePath}");

    }

	public void start() throws FastcatSearchException {
		// 초기화 및 서비스시작을 start로 옮김.
		// 초기화로직이 init에 존재할 경우, 관리도구에서 검색엔진을 재시작할때, init을 호출하지 않으므로, 초기화를 건너뛰게
		// 됨.
		instance = this;

		if (serverHome == null) {
			System.err.println("Warning! No argument for \"server.home\".");
			usage();
			System.exit(1);
		}

		File f = new File(serverHome);
		if (!f.exists()) {
			System.err.println("Warning! Path \"" + serverHome + "\" is not exist!");
			usage();
			System.exit(1);
		}

		IRSettings.setHome(serverHome);
		Environment environment = new Environment(serverHome).init();
		// Settings settings = environment.settingManager().getSettings();
		this.serviceManager = new ServiceManager(environment);
		serviceManager.asSingleton();

		PluginService pluginService = serviceManager.createService("plugin", PluginService.class);
		pluginService.asSingleton();
		DBService dbService = serviceManager.createService("db", DBService.class);
		dbService.asSingleton();
		KeywordService keywordService = serviceManager.createService("keyword", KeywordService.class);
		JobService jobService = serviceManager.createService("job", JobService.class);
		jobService.asSingleton();
		IRService irService = serviceManager.createService("ir", IRService.class);
		irService.asSingleton();
		StatisticsInfoService statisticsInfoService = serviceManager.createService("statistics_info", StatisticsInfoService.class);
		ManagementInfoService managementInfoService = serviceManager.createService("management_info", ManagementInfoService.class);
		NodeService nodeService = serviceManager.createService("node", NodeService.class);
		nodeService.asSingleton();
		DataService dataService = serviceManager.createService("data", DataService.class);

		WebService webService = serviceManager.createService("web", WebService.class);
		if (webService == null) {
			throw new FastcatSearchException("웹서비스를 초기화하지 못했습니다.");
		}
		
		NotificationService notificationService = serviceManager.createService("notification", NotificationService.class);
		ClusterAlertService clusterAlertService = serviceManager.createService("alert", ClusterAlertService.class);
		clusterAlertService.asSingleton();
		ProcessLoggerService processLoggerService = serviceManager.createService("processlogger", ProcessLoggerService.class);
		
		logger = LoggerFactory.getLogger(CatServer.class);
		logger.info("ServerHome = {}", serverHome);
		try {
			//plugin은 여타service보다 먼저 시작되어야한다.
			pluginService.start();
			
			
			dbService.start();
			jobService.start();
			nodeService.start();
			
			
			irService.start();
			statisticsInfoService.start();
//			keywordService.start();
			dataService.start();
			
			
			
			if (webService != null)
				webService.start();
			
			notificationService.start();
			clusterAlertService.start();
			processLoggerService.start();
			
		} catch (FastcatSearchException e) {
			logger.error("CatServer 시작에 실패했습니다.", e);
			stop();
		}

		if (shutdownHook == null) {
			shutdownHook = new ServerShutdownHook();
		}
		Runtime.getRuntime().addShutdownHook(shutdownHook);

		startTime = System.currentTimeMillis();

		// try {
		// boolean isValidLicense = LicenseSettings.getInstance().load();
		// if(isValidLicense){
		// logger.info("유효한 라이선스입니다. 기한 = {}",
		// LicenseSettings.getInstance().getLicenseInfo().getDisplayExpiredDate());
		// }else{
		// logger.warn("라이선스가 유효하지 않습니다.");
		// }
		// } catch (LicenseException e1) {
		// logger.error("라이선스 에러.", e1);
		// }

		logger.info("CatServer started!");
		EventDBLogger.info(EventDBLogger.CATE_MANAGEMENT, "검색엔진이 시작했습니다.", "");
		isRunning = true;

		if (keepAlive) {
			setKeepAlive();
		}
	}

	private void setKeepAlive() {
		keepAliveLatch = new CountDownLatch(1);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				keepAliveLatch.countDown();
			}
		});

		keepAliveThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					keepAliveLatch.await();
				} catch (InterruptedException e) {
					// bail out
				}
			}
		}, "CatServer[keepAlive]");
		keepAliveThread.setDaemon(false);
		keepAliveThread.start();

	}

	public void stop() throws FastcatSearchException {

		// FIXME 뜨는 도중 에러 발생시 NullPointerException 발생가능성.
		serviceManager.getService(NotificationService.class).stop();
		serviceManager.getService(ClusterAlertService.class).stop();
		serviceManager.getService(ProcessLoggerService.class).stop();
		
		serviceManager.getService(PluginService.class).stop();
		serviceManager.getService(NodeService.class).stop();
		serviceManager.getService(StatisticsInfoService.class).stop();
		serviceManager.getService(ManagementInfoService.class).stop();
		serviceManager.getService(KeywordService.class).stop();
		serviceManager.getService(IRService.class).stop();
		serviceManager.getService(WebService.class).stop();
		serviceManager.getService(JobService.class).stop();
		serviceManager.getService(DataService.class).stop();
		serviceManager.getService(DBService.class).stop();

		logger.info("CatServer shutdown!");
		// EventDBLogger.info(EventDBLogger.CATE_MANAGEMENT, "검색엔진이 정지했습니다.",
		// "");
		isRunning = false;

		// Runtime.getRuntime().removeShutdownHook(shutdownHook);
	}

	public void close() throws FastcatSearchException {
		serviceManager.getService(NotificationService.class).close();
		serviceManager.getService(ClusterAlertService.class).close();
		serviceManager.getService(ProcessLoggerService.class).close();
		
		serviceManager.getService(PluginService.class).close();
		serviceManager.getService(NodeService.class).close();
		serviceManager.getService(StatisticsInfoService.class).close();
		serviceManager.getService(ManagementInfoService.class).close();
		serviceManager.getService(KeywordService.class).close();
		serviceManager.getService(IRService.class).close();
		serviceManager.getService(WebService.class).close();
		serviceManager.getService(JobService.class).close();
		serviceManager.getService(DBService.class).close();
		serviceManager.getService(DataService.class).close();
	}

	protected class ServerShutdownHook extends Thread {

		@Override
		public void run() {
			try {
				logger.info("Server Shutdown Requested!");
				CatServer.this.stop();
				// TODO shutdown 시 할일들을 적는다.
			} catch (Throwable ex) {
				logger.error("CatServer.shutdownHookFail", ex);
			} finally {
				logger.info("Server Shutdown Complete!");
			}
		}
	}

}
