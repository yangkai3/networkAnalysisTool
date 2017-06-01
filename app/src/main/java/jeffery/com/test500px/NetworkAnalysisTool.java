package jeffery.com.test500px;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.system.ErrnoException;
import android.text.TextUtils;
import android.util.Log;

/**
 * Created by Annie on 2017/5/27.
 */

public class NetworkAnalysisTool {

  /** 尝试三次 **/
  private static final String PING_CMD = "ping -c 3 %s";
  /** 用 ping 的方式，查找路由 **/
  private static final String TRACE_CMD = "ping -c 1 -t %d %s";
  /** 从 ping 结果中 grep 出数据 **/
  private static final String PING_RESULT_PATTEN =
      "([*\\d]) packets transmitted, ([*\\d]) received[^\\n]*";
  private static final String TRACE_RESULT_PATTERN = "[fF]rom (.*\\(.*\\)|.*): .*";
  private static final String TRACE_TARGET_IP_PATTERN = ".*\\((\\d+\\.\\d+\\.\\d+\\.\\d+).*";
  private static final String TRACE_RECEIVE_BYTE_PATTERN =
      "\\d+ bytes from (\\d+\\.\\d+.\\d+.\\d+):.*";
  private static final int TRACE_MAX_TTL = 30;

  private static final String TAG = NetworkAnalysisTool.class.getSimpleName();

  private NetworkAnalysisTool() {}

  public static void ping(List<String> urls, NetworkAnalysisResult result) {
    if (urls == null) {
      return;
    }

    for (String url : urls) {
      try {
        ping(url, result);
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void ping(String url, NetworkAnalysisResult result) throws IOException {
    String cmd = String.format(PING_CMD, url);
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

  public static void trace(List<String> urls, NetworkAnalysisResult result, Callback callback) {
    if (urls == null) {
      return;
    }

    for (String url : urls) {
      try {
        trace(url, result, callback);
        if (callback != null) {
          callback.onFinished(result);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  private static void trace(String url, NetworkAnalysisResult request, Callback callback)
      throws IOException {
    final NetworkAnalysisResult.TraceResult traceResult = new NetworkAnalysisResult.TraceResult();
    request.traceResults.add(traceResult);
    traceResult.hostName = url;

    int ttl = 1;
    do {
      NetworkAnalysisResult.Router result = traceOneStep(url, ttl, traceResult);
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

  private static NetworkAnalysisResult.Router traceOneStep(String url, int ttl,
      NetworkAnalysisResult.TraceResult traceResult) throws IOException {
    final NetworkAnalysisResult.Router router = new NetworkAnalysisResult.Router();
    final String command = String.format(TRACE_CMD, ttl, url);
    final Process process = Runtime.getRuntime().exec(command);

    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));

    String routerLine = ""; // 记录路由信息
    String targetIp = null; // 记录最后一跳的 ip 信息
    String s;
    while ((s = stdInput.readLine()) != null) {
      Log.d(TAG, "trace find line : " + s);
      if (ttl == 1 && s.toLowerCase().startsWith("ping")) {
        final Pattern pattern = Pattern.compile(TRACE_TARGET_IP_PATTERN);
        final Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
          String ip = matcher.group(1);
          if (TextUtils.isEmpty(ip)) {
            throw new IOException("can't find target ip");
          }
          traceResult.ip = ip;
          Log.d(TAG, "trace find target ip : " + ip);
        }
      }

      if (s.toLowerCase().startsWith("from")) {
        routerLine = s;
        Log.e(TAG, "trace find router ip, line : " + s);
        break;
      } else {
        final Pattern pattern = Pattern.compile(TRACE_RECEIVE_BYTE_PATTERN);
        final Matcher matcher = pattern.matcher(s);
        if (matcher.find()) {
          targetIp = matcher.group(1);
          if (TextUtils.isEmpty(targetIp)) {
            throw new IOException("can't find target ip");
          }
        }
      }
    }

    destroyProcess(process);
    if (TextUtils.isEmpty(routerLine) && TextUtils.isEmpty(targetIp)) {
      Log.e(TAG, "trace can' find router, may block the ping");
      return router;
    }

    if (TextUtils.isEmpty(targetIp)) {
      final Pattern pattern = Pattern.compile(TRACE_RESULT_PATTERN);
      final Matcher matcher = pattern.matcher(routerLine);
      if (matcher.find()) {
        final String routerString = matcher.group(1);
        if (TextUtils.isEmpty(routerString)) {
          return router;
        }

        final int index = routerString.indexOf('(');
        if (index == -1) {
          router.ip = routerString;
        } else {
          router.ip = routerString.substring(index + 1, routerString.length() - 1);
          router.host = routerString.substring(0, index);
        }
      }
    } else {
      router.ip = targetIp;
      router.host = traceResult.hostName;
    }

    Log.e(TAG, "trace find router ip : " + router.ip + " host: " + router.host);
    return router;
  }

  private static void destroyProcess(Process process) {
    try {
      process.destroy();
    } catch (ErrnoException e) {
      e.printStackTrace();
    }
  }

  public interface Callback {
    void onProgressChanged(NetworkAnalysisResult result);

    void onFinished(NetworkAnalysisResult result);
  }
}
