package jeffery.com.test500px;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络分析结果，可以作为暂时存储测试结果的数据，并且可以输出到 log、上报 log
 *
 * @author yankgai
 */
public class NetworkAnalysisResult {

  public List<PingResult> pingResults = new ArrayList<>();
  public List<TraceResult> traceResults = new ArrayList<>();
  public List<PackageLostResult> packageLostResults = new ArrayList<>();

  public NetworkAnalysisResult() {

  }

  static class PingResult {
    String url;
    boolean result;
    int packageTransmitted;
    int packageReceived;
  }

  static class TraceResult {
    /** 最终要到达的 ip 地址 **/
    String ip;
    /** 需要 ping 的地址，如果原来就是 ip 这里就是空 **/
    String hostName;
    /** 过程中的路由 ip 地址 **/
    List<Router> routers;
    boolean result;

    public TraceResult() {
      routers = new ArrayList<>();
    }
  }

  static class Router {
    String host;
    String ip;
  }

  static class PackageLostResult {
    String url;
    int totalCount;
    int successCount;
  }
}
