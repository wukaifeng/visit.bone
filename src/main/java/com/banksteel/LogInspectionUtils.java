package com.banksteel;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

public class LogInspectionUtils {
	private static final Logger logger = LoggerFactory.getLogger(LogInspectionUtils.class);
	private static final String DATE_FORMAT_SECOND = "yyyy-MM-dd HH:mm:ss";

	private static final String DATE_FORMAT_DAY = "yyyy-MM-dd";

	private static final String DATE_FORMT_DAY = "yyyy.MM.dd";

	private static final String FILE_PATH = "D://apply//es.json";

	private static final String APP_CONFIG_PATH = "D://apply//appConifg.txt";

	private static final String URL_REQUEST = "http://172.17.253.184:9200/logstash-%s/application/_search";

	private static List<String> appList;

	// 正则表达式规则ERROR
	// private static String regEx = "ERROR
	// \\[DubboMonitorSendTimer-thread-\\d\\]
	// c\\.a\\.dubbo\\.monitor\\.dubbo\\.DubboMonitor";
	// 编译正则表达式
	// private static Pattern pattern = Pattern.compile(regEx);
	// 忽略大小写的写法
	// Pattern pat = Pattern.compile(regEx, Pattern.CASE_INSENSITIVE);

	public static void main(String[] args) throws Exception {

		doLetGo(); // 去采集数据

	}

	private static void doLetGo() throws Exception {
		Stopwatch watch = Stopwatch.createStarted();
		String params = getRequestMode(FILE_PATH); // 获取请求模块
		// String startTime = getDateInterval(-1, dateFormatSecond); //获取开始时间
		String startTime = getDateIntegerl(-1, DATE_FORMAT_SECOND); // 获取前一天
		String endTime = formatDate(System.currentTimeMillis()); // 获取结束时间
		// String urlTime = formatDate(new Date(), dateFormtDay); //当天日期索引
		String urlTime = getDateInterval(-1, DATE_FORMT_DAY); // 获取请求url时间
																// 昨天日期索引
		String requestUrl = getRequestUrl(urlTime); // 获取请求地址
		long startTimeLong = getStartTimeLong();
		long endTimeLong = new Date().getTime();
		String timeReplace = params.replace("@startTime", startTimeLong + "").replace("@endTime", endTimeLong + "");
		logger.info("巡检开始时间：{}~~结束时间:{}", startTime, endTime);
		// doArrangementData(requestUrl, timeReplace); //excel
		doFile(requestUrl, timeReplace); // 文件操作
		logger.info("执行完成,所花时间为：{},毫秒", watch.stop().elapsed(TimeUnit.MILLISECONDS));
	}

	private static long getStartTimeLong() {
		Calendar c = Calendar.getInstance(); // 获取日志操作对象
		c.setTime(new Date());
		c.add(Calendar.DATE, -1);
		c.set(Calendar.HOUR, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return c.getTimeInMillis();
	}

	private static void doFile(String requestUrl, String timeReplace) throws Exception {
		int i = 0;
		List<String> appList = getAppList();
		for (String appName : appList) { // 循环调用应用进行请求
			logger.info("--------->>>>>>>读取{}应用日志------>>>>>>>>>>", appName);
			String tmpMode = timeReplace.replace("@app", appName);
			// System.out.println("处理字符串的结果是：" + tmpMode);
			String response = request(requestUrl, tmpMode); // 获取请求结果
			ArrayNode array = getArray(response, "/_source");
			if (array.size() < 1) {
				// doFile(StringUtils.EMPTY, i, appName); // 未发现异常也要进行通知
				// doExcel(row, cell, exption, "当前应用未发现异常信息", i, appName);
				continue;
			}
			for (JsonNode jsonNode : array) {
				i++;
				jsonNode = jsonNode.get("_source");
				JsonNode jsonNodeMsg = jsonNode.get("msg");
				JsonNode level = jsonNode.get("level");
				JsonNode timestamp = jsonNode.get("@timestamp");
				String time = getFormat(timestamp.asText());
				String msg = time + "======level:" + level.asText() + "==========msg:" + jsonNodeMsg.asText();

				writeErrorInfo(msg, appName);
				// Matcher matcher = pattern.matcher(msg);
				// 查找字符串中是否有匹配正则表达式的字符/字符串
				// boolean rs = matcher.find();
				// if (!rs) {
				// writeErrorInfo(msg, i, appName); // 写入文件操作(错误些人多个文件)
				// writeErrorInfo(msg,appName); // 写入文件操作（换行写入一个文件）
				// doExcel(row, cell,exption, msg, i, appName, style);
				// }

			}
			logger.info("<<<<<<<<---------读取{}应用日志<<<<<<<<------", appName);
		}
	}

	private static String getFormat(String date) throws ParseException {
		date = date.replace("Z", " UTC");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z");
		Date d = format.parse(date);
		format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		return format.format(d);
	}

	private static void doArrangementData(String requestUrl, String timeReplace) throws Exception {

		try (InputStream fis = LogInspectionUtils.class.getResourceAsStream("异常模板.xlsx"); OutputStream os = getOs();) {
			XSSFWorkbook workbook = new XSSFWorkbook(fis);
			XSSFSheet exption = workbook.getSheet("异常信息");
			exption.setColumnWidth(1, 100 * 256); // 设置单元格的高度与宽度
			XSSFCellStyle style = workbook.createCellStyle();
			style.setWrapText(true);
			Row row = null;
			Cell cell = null;
			List<String> appList = getAppList();
			Integer i = 0;
			for (String appName : appList) { // 循环调用应用进行请求
				String tmpMode = timeReplace.replace("@app", appName);
				// System.out.println("处理字符串的结果是：" + tmpMode);
				String response = request(requestUrl, tmpMode); // 获取请求结果
				ArrayNode array = getArray(response, "/_source");
				if (array.size() < 1) {
					// doFile(StringUtils.EMPTY, i, appName); // 未发现异常也要进行通知
					// doExcel(row, cell, exption, "当前应用未发现异常信息", i, appName);
					continue;
				}
				for (JsonNode jsonNode : array) {
					i++;
					String msg = jsonNode.get("_source").get("msg").asText();
					// doFile(msg, i, appName); // 写入文件操作
					doExcel(row, cell, exption, msg, i, appName, style);
				}
			}
			workbook.write(os);
		} catch (Exception ex) {
			logger.error("异常信息：", ex);
		}

	}

	private static void doExcel(Row row, Cell cell, XSSFSheet exption, String context, Integer i, String appName, XSSFCellStyle style) {
		System.out.println("异常应用的名称：" + appName + ":i = " + i);

		row = exption.createRow(i);
		Short cellhight = 1000;
		row.setHeight(cellhight);

		cell = row.createCell(0);
		cell.setCellValue(appName);

		cell = row.createCell(1);
		if (context.length() > 100) {
			cell.setCellStyle(style);
		}
		if (context.length() > 2000) {
			String result = context.substring(0, 2000);
			cell.setCellValue(result);
		} else {
			cell.setCellValue(context);
		}
	}

	private static OutputStream getOs() throws Exception {
		String path = System.getProperty("user.dir");
		String date = formatDate(new Date(), DATE_FORMAT_DAY);
		String filePath = path.concat(File.separator).concat("钢银钱庄线上异常日志(" + date + ").xlsx");
		File file = new File(filePath);
		Files.deleteIfExists(file.toPath()); // 先删除在创建
		if (!file.exists()) {
			boolean result = file.createNewFile();
			if (!result) {
				logger.error("更新表格文件失败");
				return null;
			}
		}
		return new FileOutputStream(file);
	}

	/**
	 * 处理请求到的es数据
	 * 
	 * @param response
	 * @param nodeSource
	 * @return
	 */
	public static ArrayNode getArray(String response, String nodeSource) {
		ObjectMapper objectMapper = new ObjectMapper();
		ArrayNode array = objectMapper.createArrayNode();
		if (StringUtils.isBlank(response)) {
			return array;
		}
		try {
			JsonNode node = objectMapper.readTree(response);
			long total = node.at("/hits/total").longValue();
			if (total > 0) {
				ArrayNode hits = (ArrayNode) node.at("/hits/hits");
				array.addAll(hits);
			}
			return array;
		} catch (IOException e) {
			logger.error("处理数据异常：", e);
		}
		return array;
	}

	public static void writeErrorInfo(String context, int i, String appName) throws Exception {
		String fileName = "errlog".concat(String.valueOf(i)).concat(".txt"); // 文件名
																				// //进行文件处理
		String parentPath = System.getProperty("user.dir");
		String dir = parentPath.concat(File.separator).concat("errlog"); // errlog
																			// 日志文件
																			// 包含各个应用错误日志
		File fileDir = new File(dir);
		if (!fileDir.exists()) {
			fileDir.mkdir(); // 创建 总文件夹 errlog
		}
		String appFileLog = dir.concat(File.separator).concat(appName); // 创建具体应用文件夹
		File appDir = new File(appFileLog);
		if (!appDir.exists()) {
			appDir.mkdir();
		}
		String outFileAddress = appFileLog.concat(File.separator).concat(fileName);
		try (OutputStream os = new FileOutputStream(outFileAddress, true); BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));) {
			if (StringUtils.isEmpty(context)) {
				writer.write("请求当前应用:" + appName + ",未发现有异常信息");
			}
			writer.write(context + System.getProperty("line.separator")); // 文件写入操作
		} catch (Exception e) {
			logger.error("文件写入操作异常：", e);
		}

	}

	public static void writeErrorInfo(String context, String appName) throws Exception {
		String fileName = appName.concat("_ERROR-LOG".concat(".txt")); // 文件名
		// //进行文件处理
		String parentPath = System.getProperty("user.dir");
		String dir = parentPath.concat(File.separator).concat(formatDate(new Date(System.currentTimeMillis()), DATE_FORMAT_DAY) + "_ERROR-LOG"); // errlog
		// 日志文件
		// 包含各个应用错误日志
		File fileDir = new File(dir);
		if (!fileDir.exists()) {
			fileDir.mkdir(); // 创建 总文件夹 errlog
		}
		String appFileLog = dir.concat(File.separator).concat(appName); // 创建具体应用文件夹
		File appDir = new File(appFileLog);
		if (!appDir.exists()) {
			appDir.mkdir();
		}
		String outFileAddress = appFileLog.concat(File.separator).concat(fileName);
		try (OutputStream os = new FileOutputStream(outFileAddress, true); BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os));) {
			if (StringUtils.isEmpty(context)) {
				writer.write("请求当前应用:" + appName + ",未发现有异常信息");
			}
			writer.write(context + System.getProperty("line.separator")); // 文件写入操作
		} catch (Exception e) {
			logger.error("文件写入操作异常：", e);
		}

	}

	/**
	 * 请求es服务器获取数据
	 * 
	 * @param url
	 * @param jsonBody
	 * @return
	 * @throws Exception
	 */
	public static String request(String url, String jsonBody) throws Exception {
		OkHttpClient httpClient = new OkHttpClient();
		RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonBody);
		Request request = new Request.Builder().url(url).post(requestBody).build();
		try {
			Response response = httpClient.newCall(request).execute();
			if (response.isSuccessful()) {
				return response.body().string();
			} else if (response.code() == 404) {
				return StringUtils.EMPTY;
			} else {
				throw new Exception("获取Es数据异常，" + response.message() + "【Url:" + url + "】 【Code:" + response.code() + "】");
			}
		} catch (IOException e) {
			throw new Exception("获取Es数据异常:" + url, e);
		}
	}

	/**
	 * 获取请求模板
	 * 
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public static String getRequestMode(String path) throws Exception {
		File file = new File(path);
		try (InputStream is = new FileInputStream(file);) {
			return IOUtils.toString(is);
		}
	}

	/**
	 * 将Long类型格式化成日期类型
	 * 
	 * @param date
	 * @return
	 */
	public static String formatDate(Long date) {
		if (date == null) {
			return formatDate(System.currentTimeMillis());
		} else {
			return formatDate(new Date(date), StringUtils.EMPTY);
		}
	}

	/**
	 * 日期格式化
	 * 
	 * @param date
	 * @param sFormat
	 * @return
	 */
	public static String formatDate(Date date, String sFormat) {
		if (date == null) {
			return "";
		}
		if (StringUtils.isEmpty(sFormat)) {
			sFormat = DATE_FORMAT_SECOND;
		}
		return new SimpleDateFormat(sFormat).format(date);
	}

	/**
	 * 获取间隔多少天的日期 负数为前 正数为后
	 * 
	 * @param integer
	 * @return
	 */
	public static String getDateInterval(int integer, String dateFormat) {
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat); // 格式化日志
		Calendar c = Calendar.getInstance(); // 获取日志操作对象
		c.setTime(new Date());
		c.add(Calendar.DATE, integer);
		return sdf.format(c.getTime()); // 获取处理结果
	}

	/**
	 * 获取前一天开始时间 方法存在bug，但是只要早上运行就是没有问题的。
	 * 
	 * @param integer
	 * @param dateFormat
	 * @return
	 */
	public static String getDateIntegerl(int integer, String dateFormat) {
		SimpleDateFormat sdf = new SimpleDateFormat(dateFormat); // 格式化日志
		Calendar c = Calendar.getInstance(); // 获取日志操作对象
		c.setTime(new Date());
		c.add(Calendar.DATE, integer);
		c.set(Calendar.HOUR, 0);
		c.set(Calendar.MINUTE, 0);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);
		return sdf.format(c.getTime()); // 获取处理结果
	}

	/**
	 * 格式化请求url
	 * 
	 * @param urlTime
	 * @return
	 */
	public static String getRequestUrl(String urlTime) {
		return String.format(URL_REQUEST, urlTime);
	}

	/**
	 * 获取所有应用的集合
	 * 
	 * @return
	 */
	public static List<String> getAppList() {
		if (appList == null) {
			return getAppSource(APP_CONFIG_PATH);
		}
		return appList;
	}

	/**
	 * 从文件中获取文件应用的名称
	 * 
	 * @param appConfigPath
	 * @return
	 */
	private static List<String> getAppSource(String appConfigPath) {
		List<String> resultList = Lists.newArrayList(); // 预设返回结果
		File file = new File(appConfigPath);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));) {
			String appName;
			while ((appName = reader.readLine()) != null) {
				resultList.add(appName);
			}
		} catch (Exception e) {
			logger.error("获取应用文件流异常", e);
		}
		return resultList;
	}

}
