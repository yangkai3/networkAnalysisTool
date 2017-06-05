package jeffery.com.test500px;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.TextUtils;
import android.util.Log;

/**
 * @author yangkai
 */
public class NetworkAnalysisTool {

  /** 尝试三次 **/
  private static final String PING_CMD = "ping -c %d -s %d %s";
  /** 用 ping 的方式，查找路由 **/
  private static final String TRACE_CMD = "ping -c 1 -t %d -s %d %s";
  /** 从 ping 结果中 grep 出数据 **/
  private static final String PING_RESULT_PATTEN =
      "(\\d+) packets transmitted, (\\d+) received[^\\n]*";
  private static final String TRACE_RESULT_PATTERN = "[fF]rom (.*\\(.*\\)|.*): .*";
  private static final String TRACE_TARGET_IP_PATTERN = ".*\\((\\d+\\.\\d+\\.\\d+\\.\\d+).*";
  private static final String TRACE_RECEIVE_BYTE_PATTERN =
      "\\d+ bytes from (\\d+\\.\\d+.\\d+.\\d+):.*";
  private static final int TRACE_MAX_TTL = 30;
  private static final int DEFAULT_PACKAGE_SIZE = 56;

  private static final String TAG = NetworkAnalysisTool.class.getSimpleName();

  private NetworkAnalysisTool() {}

  public static void ping(List<String> urls, NetworkAnalysisResult result) {
    if (urls == null) {
      return;
    }

    for (String url : urls) {
      try {
        ping(url, 1, DEFAULT_PACKAGE_SIZE, result);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void ping(String url, int packageCount, int packageSize,
      NetworkAnalysisResult result)
      throws IOException {
    String cmd = String.format(PING_CMD, packageCount, packageSize, url);
    Process p = Runtime.getRuntime().exec(cmd);
    BufferedReader stdInput = new BufferedReader(new InputStreamReader(p.getInputStream()));

    String s;
    while ((s = stdInput.readLine()) != null) {
      Log.d(TAG, "ping line: " + s);
      if (s.matches(PING_RESULT_PATTEN)) {
        break;
      }
    }

    int[] packageTransmitResult = getPackageTransmitResult(s);

    NetworkAnalysisResult.PingResult pingResult = new NetworkAnalysisResult.PingResult();
    if (packageTransmitResult == null) {
      pingResult.result = false;
      pingResult.url = url;
      return;
    }

    pingResult.packageTransmitted = packageTransmitResult[0];
    pingResult.packageReceived = packageTransmitResult[1];
    pingResult.result = packageTransmitResult[0] == packageTransmitResult[1];
    pingResult.url = url;

    result.pingResults.add(pingResult);
  }

  /**
   * 读取 package transmitted 等数据
   * 
   * @return 总发送数、到达数
   */
  private static int[] getPackageTransmitResult(String buffer) {
    if (TextUtils.isEmpty(buffer)) {
      return null;
    }

    Pattern p = Pattern.compile(PING_RESULT_PATTEN);
    Matcher m = p.matcher(buffer);
    if (m.find()) { // Find each match in turn; String can't do this.
      try {
        return new int[] {Integer.valueOf(m.group(1)), Integer.valueOf(m.group(2))};
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  public static void packageLost(List<String> urls, NetworkAnalysisResult result) {
    if (urls == null) {
      return;
    }

    for (String url : urls) {
      try {
        packageLost(url, result);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void packageLost(String urls, NetworkAnalysisResult result) throws IOException {
    ping(urls, 10, DEFAULT_PACKAGE_SIZE, result);
  }

  public static void trace(List<String> urls, NetworkAnalysisResult result, Callback callback) {
    if (urls == null) {
      return;
    }

    for (String url : urls) {
      try {
        trace(url, DEFAULT_PACKAGE_SIZE, result, callback);
        if (callback != null) {
          callback.onFinished(result);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void trace(String url, int packageSize, NetworkAnalysisResult request,
      Callback callback)
      throws IOException {
    final NetworkAnalysisResult.TraceResult traceResult = new NetworkAnalysisResult.TraceResult();
    request.traceResults.add(traceResult);
    traceResult.hostName = url;

    int ttl = 1;
    do {
      NetworkAnalysisResult.Router result = traceOneStep(url, ttl, packageSize, traceResult);
      traceResult.routers.add(result);
      if (callback != null) {
        callback.onProgressChanged(request);
      }

      if (result.ip != null && result.ip.equals(traceResult.ip)) {
        traceResult.result = true;
        break;
      } else {
        ttl++;
      }

      if (ttl > TRACE_MAX_TTL) {
        traceResult.result = false;
        break;
      }
    } while (true);
  }

  private static NetworkAnalysisResult.Router traceOneStep(String url, int ttl, int packageSize,
      NetworkAnalysisResult.TraceResult traceResult) throws IOException {
    final NetworkAnalysisResult.Router router = new NetworkAnalysisResult.Router();
    final String command = String.format(TRACE_CMD, ttl, packageSize, url);
    final Process process = Runtime.getRuntime().exec(command);

    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

    String targetIp = null; // 记录最后一跳的 ip 信息
    String routerIp = null;
    String routerHost = null;

    String s;
    while ((s = stdInput.readLine()) != null) {
      Log.d(TAG, "trace find line : " + s);

      // 找到 host 对应的 ip 地址
      if (ttl == 1 && s.toLowerCase().startsWith("ping")) {
        final Pattern pattern = Pattern.compile(TRACE_TARGET_IP_PATTERN);
        final Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
          String ip = matcher.group(1);
          if (TextUtils.isEmpty(ip)) {
            throw new IOException("can't find target ip");
          }
          traceResult.ip = ip;
          Log.e(TAG, "trace find target ip : " + ip);
        }
      }

      if (s.matches(TRACE_RESULT_PATTERN)) { // 找到当前路由信息行
        final Pattern pattern = Pattern.compile(TRACE_RESULT_PATTERN);
        final Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
          final String routerString = matcher.group(1);
          if (!TextUtils.isEmpty(routerString)) {
            final int index = routerString.indexOf('(');
            if (index == -1) {
              routerIp = routerString;
            } else {
              routerIp = routerString.substring(index + 1, routerString.length() - 1);
              routerHost = routerString.substring(0, index);
            }
            Log.e(TAG, "trace find router ip : " + router.ip + " host: " + router.host);
          }
        }
      } else if (s.matches(TRACE_RECEIVE_BYTE_PATTERN)) {// 确定时候已经从目标 host 收到数据
        final Pattern pattern = Pattern.compile(TRACE_RECEIVE_BYTE_PATTERN);
        final Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
          targetIp = matcher.group(1);
          if (TextUtils.isEmpty(targetIp)) {
            throw new IOException("can't find target ip");
          } else {
            routerIp = targetIp;
            Log.e(TAG, "trace find target ip: " + routerIp);
          }
        }
      }
    }

    destroyProcess(process);

    router.ip = routerIp;
    router.host = routerHost;

    return router;
  }

  private static void destroyProcess(Process process) {
    try {
      process.destroy();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public interface Callback {
    void onProgressChanged(NetworkAnalysisResult result);

    void onFinished(NetworkAnalysisResult result);
  }
}
