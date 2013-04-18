package org.fastcatsearch.cli.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.fastcatsearch.cli.Command;
import org.fastcatsearch.cli.CommandException;
import org.fastcatsearch.cli.CommandResult;
import org.fastcatsearch.cli.ConsoleSessionContext;
import org.fastcatsearch.control.JobController;
import org.fastcatsearch.control.JobResult;
import org.fastcatsearch.ir.group.GroupEntry;
import org.fastcatsearch.ir.group.GroupResult;
import org.fastcatsearch.ir.group.GroupResults;
import org.fastcatsearch.ir.query.Result;
import org.fastcatsearch.ir.query.Row;
import org.fastcatsearch.job.SearchJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchActionCommand extends Command {
	
	private static final Logger logger = LoggerFactory.getLogger(SearchActionCommand.class);

	@Override
	public boolean isCommand(String[] cmd) {
		return isCommand(CMD_SEARCH, cmd);
	}

	@Override
	public CommandResult doCommand(String[] cmd, ConsoleSessionContext context)
			throws IOException, CommandException {
		
		StringBuilder msg = new StringBuilder();
		StringBuilder queryBuffer = new StringBuilder();
		String[] split;
		
		for(int inx=0;inx<cmd.length;inx++) {
			String cmdstr = cmd[inx];
			if(cmdstr.startsWith(CMD_SEARCH[0])) {
				cmdstr = cmdstr.substring(CMD_SEARCH[0].length());
			}
			queryBuffer.append(cmdstr);
		}
		
		split = queryBuffer.toString().split("[&]");
		
		for(int inx=0;inx<split.length;inx++) {
			String[] params = split[inx].split("=");
			String key = params[0].trim();
			String value = "";
			if(params.length > 0) {
				value = params[1].trim();
			}
			split[inx] = key+"="+value;
		}
		queryBuffer.setLength(0);
		
		for(int inx=0;inx<split.length;inx++) {
			String splitstr = split[inx];
			queryBuffer.append(splitstr).append("&");
		}
		
		logger.debug("query : {}", queryBuffer);
		
		SearchJob job = new SearchJob();
		job.setArgs(new String[] { queryBuffer.toString() });
		Result result = null;
		JobResult jobResult = JobController.getInstance().offer(job);
		Object obj = jobResult.poll(100);
		
		if(jobResult.isSuccess()) {
			result = (Result)obj;
			int columns = result.getFieldCount();
			String[] header = result.getFieldNameList();
			List<Object[]> data = new ArrayList<Object[]>();
			for(Row row : result.getData()) {
				Object[] record = new Object[columns];
				for(int colInx=0;colInx < columns; colInx++) {
					record[colInx] = new String(row.get(colInx));
				}
				data.add(record);
			}
			msg.append(super.printData(data, header));
			
			GroupResults gresults = result.getGroupResult();
			if(gresults!=null) {
				for(int rowInx=0;rowInx<gresults.groupSize();rowInx++) {
					GroupResult gresult = gresults.getGroupResult(rowInx);
					List<Object[]> gdata = new ArrayList<Object[]>();
					String[] gheader = new String[] {
							"no","key", gresult.functionName()
					};
					
					for(int recordInx=0;recordInx<gresult.size();recordInx++) {
						Object[] record = new Object[3];
						GroupEntry entry = gresult.getEntry(recordInx);
						record[0] = recordInx;
						record[1] = entry.key.getKeyString();
						record[2] = entry.count();//entry.getGroupingObjectResultString();
						gdata.add(record);
					}
					msg.append(":group result [").append(rowInx+1).append("]\n");
					msg.append(super.printData(gdata,gheader));
				}
			}
			
			return new CommandResult(msg.toString(), CommandResult.Status.SUCCESS);
		}
		return new CommandResult(msg.toString(), CommandResult.Status.FAIL);
	}
}