package org.fastcatsearch.cli.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.fastcatsearch.cli.CommandException;
import org.fastcatsearch.cli.CommandResult;
import org.fastcatsearch.cli.ConsoleSessionContext;
import org.fastcatsearch.cli.command.exception.CollectionNotDefinedException;
import org.fastcatsearch.cli.command.exception.CollectionNotFoundException;
import org.fastcatsearch.db.DBService;
import org.fastcatsearch.db.dao.IndexingSchedule;
import org.fastcatsearch.db.vo.IndexingScheduleVO;

public class ShowScheduleCommand extends CollectionExtractCommand {

	private String[] header = new String[] { "Collection Name", "Type", "Start Time", "period", "Active" };
	private ArrayList<Object[]> data = new ArrayList<Object[]>();

	@Override
	public boolean isCommand(String[] cmd) {
		return isCommand(cmd, CMD_SHOW_SCHEDULE);
	}

	@Override
	public CommandResult doCommand(String[] cmd, ConsoleSessionContext context) throws IOException, CommandException {
		// collection이 정의되지 않았다면 넘긴다 바로.
		String collection = "";

		try {
			collection = extractCollection(context);
			checkCollectionExists(collection);
		} catch (CollectionNotDefinedException e) {
			return new CommandResult("collection is not define\r\nuse like this\r\nuse collection collectionName;",
					CommandResult.Status.SUCCESS);
		} catch (CollectionNotFoundException e) {
			return new CommandResult("collection " + collection + " is not exists", CommandResult.Status.SUCCESS);
		}

		if (cmd.length != 2)
			return new CommandResult("invald command", CommandResult.Status.SUCCESS);

		DBService dbHandler = DBService.getInstance();
		IndexingSchedule indexingSchedule = dbHandler.db().getDAO("IndexingSchedule");
		IndexingScheduleVO fullIndexSchedule = indexingSchedule.select(collection, "F");
		IndexingScheduleVO incIndexSchedule = indexingSchedule.select(collection, "I");

		if (fullIndexSchedule == null && incIndexSchedule == null)
			return new CommandResult("thes is no Schedule set in collection [" + collection + "]",
					CommandResult.Status.SUCCESS);

		if (fullIndexSchedule != null)
			addRecord(data, fullIndexSchedule);

		if (incIndexSchedule != null)
			addRecord(data, incIndexSchedule);

		return new CommandResult(printData(data, header), CommandResult.Status.SUCCESS);

	}

	private void addRecord(List<Object[]> data, IndexingScheduleVO schedule) {
		data.add(new Object[] { schedule.collection, schedule.type, schedule.period + "",
				schedule.startTime.toString(), (schedule.isActive ? "Active" : "DeActive") });
	}

}
