package org.fastcatsearch.notification;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.fastcatsearch.common.EmailSender;
import org.fastcatsearch.common.EmailSender.MailProperties;
import org.fastcatsearch.common.SMSSender;
import org.fastcatsearch.common.io.Streamable;
import org.fastcatsearch.db.DBService;
import org.fastcatsearch.db.InternalDBModule.MapperSession;
import org.fastcatsearch.db.mapper.NotificationHistoryMapper;
import org.fastcatsearch.db.mapper.UserAccountMapper;
import org.fastcatsearch.db.vo.NotificationConfigVO;
import org.fastcatsearch.db.vo.NotificationVO;
import org.fastcatsearch.db.vo.UserAccountVO;
import org.fastcatsearch.exception.FastcatSearchException;
import org.fastcatsearch.ir.io.DataInput;
import org.fastcatsearch.ir.io.DataOutput;
import org.fastcatsearch.job.Job;
import org.fastcatsearch.notification.message.Notification;
import org.fastcatsearch.service.ServiceManager;
import org.fastcatsearch.util.DynamicClassLoader;

public class NotificationJob extends Job implements Streamable {
	private static final long serialVersionUID = 1084526563289625615L;
	private Notification notification;

	public NotificationJob() {
	}

	public NotificationJob(Notification notification) {
		this.notification = notification;
	}

	@Override
	public JobResult doRun() throws FastcatSearchException {
		String message = notification.toMessageString();
		logger.debug("Notification 통지 >> {}", message);

		MapperSession<NotificationHistoryMapper> mapperSession = DBService.getInstance().getMapperSession(NotificationHistoryMapper.class);
		try {
			NotificationHistoryMapper mapper = mapperSession.getMapper();

			NotificationVO vo = new NotificationVO();
			vo.message = message;
			vo.messageCode = notification.messageCode();
			vo.node = notification.origin().toString();
			vo.regtime = new Timestamp(notification.time());
			try {
				mapper.putEntry(vo);
			} catch (Exception e1) {
				logger.error("", e1);
			}

		} finally {
			if (mapperSession != null) {
				mapperSession.closeSession();
			}
		}
		handleAlert(notification);

		return new JobResult(true);
	}

	private void handleAlert(Notification notification) {

		NotificationService notificationService = ServiceManager.getInstance().getService(NotificationService.class);
		Map<String, NotificationConfigVO> map = notificationService.getNotificationConfigMap();
		NotificationConfigVO config = map.get(notification.messageCode());

		String messageString = notification.toMessageString();

		List<String> emailToList = new ArrayList<String>();
		List<String> smsToList = new ArrayList<String>();

		if (config != null) {
			String alertTo = config.getAlertTo();
			if (alertTo != null) {
				MapperSession<UserAccountMapper> mapperSession = DBService.getInstance().getMapperSession(UserAccountMapper.class);
				try {
					UserAccountMapper userAccountMapper = mapperSession.getMapper();
					for (String to : alertTo.split(",")) {
						to = to.trim();
						if (to.length() > 0) {
							String[] kv = to.split(":");

							if (kv.length == 2) {
								String type = kv[0].trim();
								String userId = kv[1].trim();

								UserAccountVO userAccountVO = userAccountMapper.getEntryByUserId(userId);
								if(userAccountVO == null){
									logger.warn("Cannot find user [{}] for notification.", userId);
									continue;
								}
								String email = userAccountVO.email;
								String sms = userAccountVO.sms;
								if (type.equalsIgnoreCase("EMAIL")) {
									if (email != null && email.length() > 0) {
										email = email.trim();
										if (email.length() > 0) {
											emailToList.add(email);
										}
									}else{
										logger.warn("Notification user [{}] do not have email address.", userId);
									}
								} else if (type.equalsIgnoreCase("SMS")) {
									if (sms != null && sms.length() > 0) {
										sms = sms.trim();
										if (sms.length() > 0) {
											smsToList.add(sms);
										}
									}else{
										logger.warn("Notification user [{}] do not have SMS number.", userId);
									}
								}
							}
						}

					}
				} catch (Exception e) {
					logger.error("", e);
				} finally {
					if (mapperSession != null) {
						mapperSession.closeSession();
					}
				}
			}

		}

		// 보낸다.
		if (emailToList.size() > 0) {
			Properties properties = environment.settingManager().getSystemSettings().getStartsWith("smtp-config");
			String idKey = "id";
			String passwordKey = "password";
			String id = properties.getProperty(idKey);
			String password = properties.getProperty(passwordKey);

			if (id != null && password != null) {
				try {
					properties.remove(idKey);
					properties.remove(passwordKey);
					MailProperties mailProperties = new MailProperties(properties);
					mailProperties.setAuthentication(id, password);
					EmailSender emailSender = new EmailSender(mailProperties);
					emailSender.sendText("FastcatSearch", emailToList, "FastcatSearch Notification", messageString);
					logger.debug("EmailSender sent notification message successfully {} to {}", notification.messageCode(), emailToList);
				} catch (Exception e) {
					logger.error("error while sending email", e);
				}
			} else {
				logger.error("Please check smtp id, password. id={}, password={}", id, password);
			}
		}

		if (smsToList.size() > 0) {
			Properties properties = environment.settingManager().getSystemSettings().getStartsWith("sms-config");
			try {
				String className = properties.getProperty("class");
				if (className != null) {
					className = className.trim();
					if(className.length() > 0){
						SMSSender smsSender = DynamicClassLoader.loadObject(className, SMSSender.class, new Class<?>[] { Properties.class }, new Object[] { properties });
						smsSender.send(smsToList, messageString);
						logger.debug("SMSSender sent notification message successfully {} to {}", notification.messageCode(), smsToList);
					}
				}
			} catch (Exception e) {
				logger.error("error while sending SMS", e);
			}

		}
	}

	@Override
	public void readFrom(DataInput input) throws IOException {
		String className = input.readString();
		notification = DynamicClassLoader.loadObject(className, Notification.class);
		notification.readFrom(input);
	}

	@Override
	public void writeTo(DataOutput output) throws IOException {
		output.writeString(notification.getClass().getName());
		notification.writeTo(output);
	}

}
