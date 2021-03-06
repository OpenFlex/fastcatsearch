package org.fastcatsearch.http;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.fastcatsearch.common.ThreadPoolFactory;
import org.fastcatsearch.env.Environment;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.http.action.AuthAction;
import org.fastcatsearch.http.action.HttpAction;
import org.fastcatsearch.http.action.ServiceAction;
import org.fastcatsearch.service.AbstractService;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.settings.Settings;
import org.fastcatsearch.util.DynamicClassLoader;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpRequestService extends AbstractService implements HttpServerAdapter {

	private HttpTransportModule transportModule;
	private HttpServiceController serviceController;
	private HttpSessionManager httpSessionManager;
	
	public HttpRequestService(Environment environment, Settings settings, ServiceManager serviceManager) throws FastcatSearchException {
		super(environment, settings, serviceManager);
	}

	@Override
	protected boolean doStart() throws FastcatSearchException {
		int servicePort = environment.settingManager().getIdSettings().getInt("servicePort");
		transportModule = new HttpTransportModule(environment, settings, servicePort);
		transportModule.httpServerAdapter(this);
		ExecutorService executorService = ThreadPoolFactory.newCachedDaemonThreadPool("http-execute-pool", 300);
		httpSessionManager = new HttpSessionManager(settings.getInt("session_expire_hour", 1));
		serviceController = new HttpServiceController(executorService, httpSessionManager);
		if (!transportModule.load()) {
			throw new FastcatSearchException("can not load transport module!");
		}

		Map<String, HttpAction> actionMap = new HashMap<String, HttpAction>();

		if (environment.isMasterNode()) {
			// master노드에서만 실행되는 액션들 등록..
			String[] actionBasePackageList = settings.getStringArray("master-action-base-package", ",");
			if (actionBasePackageList != null) {
				scanActions(actionMap, actionBasePackageList);
			}
		}
		String[] actionBasePackageList = settings.getStringArray("action-base-package", ",");
		if (actionBasePackageList != null) {
			scanActions(actionMap, actionBasePackageList);
		}

		serviceController.setActionMap(actionMap);
		return true;
	}

	private void scanActions(Map<String, HttpAction> actionMap, String[] actionBasePackageList) {
		for (String actionBasePackage : actionBasePackageList) {
			scanActions(actionMap, actionBasePackage);
		}
	}

	private void addDirectory(Set<File> set, File d){
		File[] files = d.listFiles();
		for (int i = 0; i < files.length; i++) {
			if(files[i].isDirectory()){
				addDirectory(set, files[i]);
			}else{
				set.add(files[i]);
			}
		}
	}
	
	// 하위패키지까지 모두 포함되도록 한다.
	private void scanActions(Map<String, HttpAction> actionMap, String actionBasePackage) {
		String path = actionBasePackage.replace(".", "/");
		if (!path.endsWith("/")) {
			path = path + "/";
		}
		try {
			String findPath = path;// +"**/*.class";
			Enumeration<URL> em = DynamicClassLoader.getResources(findPath);
			// logger.debug("findPath >> {}, {}", findPath, em);
			while (em.hasMoreElements()) {
				String urlstr = em.nextElement().toString();
				// logger.debug("urlstr >> {}", urlstr);
				if (urlstr.startsWith("jar:file:")) {
					String jpath = urlstr.substring(9);
					int st = jpath.indexOf("!/");
					String jarPath = jpath.substring(0, st);
					String entryPath = jpath.substring(st + 2);
					JarFile jf = new JarFile(jarPath);
					Enumeration<JarEntry> jee = jf.entries();
					while (jee.hasMoreElements()) {
						JarEntry je = jee.nextElement();
						String ename = je.getName();
						if(ename.startsWith(entryPath)){
							registerAction(actionMap, ename, true);
						}
					}
				} else if (urlstr.startsWith("file:")) {
					// logger.debug("urlstr >> {}", urlstr);
					String rootPath = urlstr.substring(5);
					int prefixLength = rootPath.indexOf(path);
					File file = new File(rootPath);
					
					Set<File> actionFileSet = new HashSet<File>();
					if(file.isDirectory()){
						addDirectory(actionFileSet, file);
					}else{
						actionFileSet.add(file);
					}
					for(File f : actionFileSet){
						String classPath = f.toURI().toURL().toString().substring(5)
								.substring(prefixLength);
						//logger.debug("file >> {}", classPath);
						registerAction(actionMap, classPath, true);
					}
				}
			}
		} catch (IOException e) {
			logger.error("action load error!", e);
		}
	}
	public void registerAction(String className, String pathPrefix) {
		registerAction(serviceController.getActionMap(), className, pathPrefix, false);
	}
	
	private void registerAction(Map<String, HttpAction> actionMap, String className, boolean isFile) {
		registerAction(actionMap, className, null, isFile);
	}
	private void registerAction(Map<String, HttpAction> actionMap, String className, String pathPrefix, boolean isFile) {
		if(className == null){
			logger.warn("Cannot register action class name >> {} : {}", className, pathPrefix);
			return;
		}
		if (isFile) {
			if (className.endsWith(".class")) {
				className = className.substring(0, className.length() - 6);
				className = className.replaceAll("/", ".");
			} else {
				// file인데 class로 끝나지 않으면 무시.
				return;
			}
		}

		try {
			Class<?> actionClass = DynamicClassLoader.loadClass(className);
			// logger.debug("className > {} => {}",className , actionClass);
			// actionClass 가 serviceAction을 상속받은 경우만 등록.
			if (actionClass != null) {
				if (ServiceAction.class.isAssignableFrom(actionClass)) {
					HttpAction actionObj = null;
					ActionMapping actionMapping = actionClass.getAnnotation(ActionMapping.class);
					// annotation이 존재할 경우만 사용.
					if (actionMapping != null) {
						String path = actionMapping.value();
						if(pathPrefix != null){
							path = pathPrefix + path;
						}
						ActionMethod[] method = actionMapping.method();
						actionObj = (HttpAction) actionClass.newInstance();
						if (actionObj != null) {
							actionObj.setMethod(method);
							actionObj.setEnvironement(environment);
							
							// 권한 필요한 액션일 경우.
							if (actionObj instanceof AuthAction) {
								AuthAction authAction = (AuthAction) actionObj;
								authAction.setAuthority(actionMapping.authority(), actionMapping.authorityLevel());
								 logger.debug("ACTION path={}, action={}, method={}, authority={}, authorityLevel={}", path, actionObj, method, actionMapping.authority(),
										 actionMapping.authorityLevel());
							}else{
								 logger.debug("ACTION path={}, action={}, method={}", path, actionObj, method);	
							}

							actionMap.put(path, actionObj);

						}

					}
				}
			}
		} catch (InstantiationException e) {
			logger.error("action load error!", e);
		} catch (IllegalAccessException e) {
			logger.error("action load error!", e);
		}
	}

	@Override
	protected boolean doStop() throws FastcatSearchException {
		transportModule.doUnload();
		httpSessionManager.close();
		return true;
	}

	@Override
	protected boolean doClose() throws FastcatSearchException {
		serviceController = null;
		transportModule = null;
		httpSessionManager = null;
		return true;
	}

	@Override
	public void dispatchRequest(HttpRequest request, HttpChannel channel) {
		serviceController.dispatchRequest(request, channel);
	}

}
