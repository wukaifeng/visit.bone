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
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
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

/**
 * 日志巡检工具
 * 
 * @author WuKaiFeng
 *
 * @描述 xxx
 * @创建时间 2019年8月30日 上午9:26:29
 */
public class LogInspectionUtils {
	private static final String DATE_FORMAT_SECOND = "yyyy-MM-dd HH:mm:ss";

	private static final String DATE_FORMAT_DAY = "yyyy-MM-dd";

	private static final String DATE_FORMT_DAY = "yyyy.MM.dd";

	private static final String FILE_PATH = "config//es.json";

	private static final String APP_CONFIG_PATH = "config//appConifg.txt";

	private static final String URL_REQUEST = "http://172.17.253.184:9200/logstash-%s/application/_search";

	public static void main(String[] args) throws Exception {
		doLetGo(); // 去采集数据
	}

	private static void doLetGo() throws Exception {
		Stopwatch watch = Stopwatch.createStarted();
		// 获取请求模块
		String queryJson = getRequestMode(FILE_PATH);
		// 获取请求地址、昨天日期索引
		String requestUrl = getRequestUrl();
		System.out.println("巡检开始时间：" + getDateIntegerl(-1, DATE_FORMAT_SECOND) + "~~结束时间:" + formatDate(System.currentTimeMillis()));
		doFile(requestUrl, queryJson); // 文件操作
		System.out.println("执行完成,所花时间为：" + watch.stop().elapsed(TimeUnit.MILLISECONDS) + ",毫秒");
	}

	private static void doFile(String requestUrl, String queryJson) throws Exception {
		List<String> appList = getAppSource(APP_CONFIG_PATH);
		for (String appName : appList) { // 循环调用应用进行请求
			System.out.println("--------->>>>>>>读取" + appName + "应用日志------>>>>>>>>>>");
			String tmpMode = queryJson.replace("@app", appName);
			String response = request(requestUrl, tmpMode); // 获取请求结果
			ArrayNode array = getArray(response, "/_source");
			if (array.size() < 1) {
				continue;
			}
			for (JsonNode jsonNode : array) {
				jsonNode = jsonNode.get("_source");
				JsonNode jsonNodeMsg = jsonNode.get("msg");
				JsonNode level = jsonNode.get("level");
				JsonNode timestamp = jsonNode.get("@timestamp");
				String time = getFormat(timestamp.asText());
				String msg = time + "======level:" + level.asText() + "==========msg:" + jsonNodeMsg.asText();

				writeErrorInfo(msg, appName);
			}
		}
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

	private static String getFormat(String date) throws ParseException {
		date = date.replace("Z", " UTC");
		SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS Z");
		Date d = format.parse(date);
		format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		return format.format(d);
	}

	/**
	 * 处理请求到的es数据
	 * 
	 * @param response
	 * @param nodeSource
	 * @return
	 * @throws IOException
	 * @throws JsonProcessingException
	 */
	public static ArrayNode getArray(String response, String nodeSource) throws JsonProcessingException, IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		ArrayNode array = objectMapper.createArrayNode();
		if (StringUtils.isBlank(response)) {
			return array;
		}
		JsonNode node = objectMapper.readTree(response);
		long total = node.at("/hits/total").longValue();
		if (total > 0) {
			ArrayNode hits = (ArrayNode) node.at("/hits/hits");
			array.addAll(hits);
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
			System.err.print("文件写入操作异常：" + e);
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
			System.err.println("文件写入操作异常：" + e);
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
	private static String request(String url, String jsonBody) throws Exception {
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
	private static String getRequestMode(String path) throws Exception {
		URL resource = LogInspectionUtils.class.getResource(path);
		File file = new File(resource.getPath());
		try (InputStream is = new FileInputStream(file);) {
			long startTimeLong = getStartTimeLong();
			long endTimeLong = new Date().getTime();
			return IOUtils.toString(is).replace("@startTime", startTimeLong + "").replace("@endTime", endTimeLong + "");
		}
	}

	/**
	 * 将Long类型格式化成日期类型
	 * 
	 * @param date
	 * @return
	 */
	private static String formatDate(Long date) {
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
	private static String formatDate(Date date, String sFormat) {
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
	private static String getDateInterval(int integer, String dateFormat) {
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
	private static String getDateIntegerl(int integer, String dateFormat) {
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
	private static String getRequestUrl() {
		String urlTime = getDateInterval(-1, DATE_FORMT_DAY);
		return String.format(URL_REQUEST, urlTime);
	}

	/**
	 * 从文件中获取文件应用的名称
	 * 
	 * @param appConfigPath
	 * @return
	 */
	private static List<String> getAppSource(String appConfigPath) {
		List<String> resultList = Lists.newArrayList(); // 预设返回结果
		String path = LogInspectionUtils.class.getResource(appConfigPath).getPath();
		File file = new File(path);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));) {
			String appName;
			while ((appName = reader.readLine()) != null) {
				resultList.add(appName);
			}
		} catch (Exception e) {
			System.err.println("获取应用文件流异常" + e);
		}
		return resultList;
	}

}
