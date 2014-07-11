package com.xpatterns.jaws.data.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.SortedSet;

import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

import com.xpatterns.jaws.data.DTO.LogDTO;
import com.xpatterns.jaws.data.DTO.ScriptMetaDTO;
import com.xpatterns.jaws.data.DTO.StateDTO;
import com.xpatterns.jaws.data.contracts.IJawsLogging;
import com.xpatterns.jaws.data.utils.JobType;
import com.xpatterns.jaws.data.utils.Utils;

public class JawsHdfsLogging implements IJawsLogging {

	Configuration configuration;
	public static final String JOBID_SEPARATOR = "-----";

	private static Logger logger = Logger.getLogger(JawsHdfsLogging.class.getName());

	public JawsHdfsLogging() {

	}

	public JawsHdfsLogging(Configuration configuration) throws Exception {
		// getting the properties from the properties file

		this.configuration = configuration;
		boolean forcedMode = configuration.getBoolean(Utils.FORCED_MODE, false);

		Utils.createFolderIfDoesntExist(configuration, configuration.get(Utils.LOGGING_FOLDER), forcedMode);
		Utils.createFolderIfDoesntExist(configuration, configuration.get(Utils.STATUS_FOLDER), forcedMode);
		Utils.createFolderIfDoesntExist(configuration, configuration.get(Utils.DETAILS_FOLDER), forcedMode);
		Utils.createFolderIfDoesntExist(configuration, configuration.get(Utils.METAINFO_FOLDER), forcedMode);
	}

	@Override
	public void setState(String uuid, JobType type) throws Exception {

		logger.debug("Writing job state " + type.toString() + " to job " + uuid + " on hdfs");
		Utils.rewriteFile(type.name(), configuration, configuration.get(Utils.STATUS_FOLDER) + "/" + uuid);

	}

	@Override
	public void setScriptDetails(String uuid, String scriptDetails) throws Exception {

		logger.info("Writing script details " + scriptDetails + " to job " + uuid);
		Utils.rewriteFile(scriptDetails, configuration, configuration.get(Utils.DETAILS_FOLDER) + "/" + uuid);

	}

	@Override
	public void addLog(String uuid, String jobId, Long time, String log) throws Exception {

		logger.debug("Writing log " + log + " to job " + uuid + " at time " + time);
		String folderName = configuration.get(Utils.LOGGING_FOLDER) + "/" + uuid;
		String fileName = folderName + "/" + time.toString();
		String logMessage = jobId + JOBID_SEPARATOR + log;
		Utils.createFolderIfDoesntExist(configuration, folderName, false);
		Utils.rewriteFile(logMessage, configuration, fileName);

	}

	@Override
	public JobType getState(String uuid) throws IOException {

		logger.info("Reading job state for job: " + uuid);
		String state = Utils.readFile(configuration, configuration.get(Utils.STATUS_FOLDER) + "/" + uuid);
		return JobType.valueOf(state);
	}

	@Override
	public String getScriptDetails(String uuid) throws IOException {

		logger.info("Reading script details for job: " + uuid);
		return Utils.readFile(configuration, configuration.get(Utils.DETAILS_FOLDER) + "/" + uuid);

	}

	@Override
	public Collection<LogDTO> getLogs(String uuid, Long time, int limit) throws IOException {

		logger.info("Reading logs for job: " + uuid + " from date: " + time);
		Collection<LogDTO> logs = new ArrayList<LogDTO>();

		String folderName = configuration.get(Utils.LOGGING_FOLDER) + "/" + uuid;

		SortedSet<String> files = Utils.listFiles(configuration, folderName, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				return o1.compareTo(o2);
			}

		});

		if (files.contains(time.toString())) {
			files = files.tailSet(time.toString());
		}

		Collection<String> filesToBeRead = getSubset(limit, files);

		for (String file : filesToBeRead) {
			String[] logedInfo = Utils.readFile(configuration, folderName + "/" + file).split(JOBID_SEPARATOR);
			if (logedInfo.length == 2) {
				logs.add(new LogDTO(logedInfo[1], logedInfo[0], Long.parseLong(file)));
			}

		}

		return logs;
	}

	private Collection<String> getSubset(int limit, SortedSet<String> files) {
		Collection<String> filesToBeRead = new LinkedList<String>();

		Iterator<String> iterator = files.iterator();
		while (iterator.hasNext() && limit > 0) {
			String file = iterator.next();
			filesToBeRead.add(file);
			limit--;
		}

		return filesToBeRead;
	}

	@Override
	public Collection<StateDTO> getStateOfJobs(String uuid, int limit) throws IOException {

		logger.info("Reading states for jobs starting with the job: " + uuid);
		Collection<StateDTO> stateList = new ArrayList<StateDTO>();

		String folderName = configuration.get(Utils.STATUS_FOLDER);
		String startFilename = folderName + "/" + uuid;
		SortedSet<String> files = Utils.listFiles(configuration, folderName, new Comparator<String>() {

			@Override
			public int compare(String o1, String o2) {
				return o2.compareTo(o1);
			}

		});

		if (files.contains(startFilename)) {
			files = files.tailSet(startFilename);
		}

		Collection<String> filesToBeRead = getSubset(limit, files);

		for (String file : filesToBeRead) {
			stateList.add(new StateDTO(JobType.valueOf(Utils.readFile(configuration, folderName + "/" + file)), Utils.getNameFromPath(file)));

		}

		return stateList;
	}

	@Override
	public void setMetaInfo(String uuid, ScriptMetaDTO metainfo) throws Exception {
		logger.info("Writing script metainfo " + metainfo + " to job " + uuid);
		String buffer = metainfo.toJson();
		Utils.rewriteFile(buffer.getBytes(), configuration, configuration.get(Utils.METAINFO_FOLDER) + "/" + uuid);

	}

	@Override
	public ScriptMetaDTO getMetaInfo(String uuid) throws IOException {
		logger.info("Reading job metainfo for job: " + uuid);
		byte[] bytes = Utils.readBytesFromFile(configuration, configuration.get(Utils.METAINFO_FOLDER) + "/" + uuid);
		ScriptMetaDTO result = ScriptMetaDTO.fromJson(new String(bytes));

		return result;
	}

}