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
  public List<PingResult> packageLostResults = new ArrayList<>();

  public NetworkAnalysisResult() {
    pingResults = new ArrayList<>();
    traceResults = new ArrayList<>();
    packageLostResults = new ArrayList<>();
  }

  static class PingResult {
    String command;
    boolean result;
    int packageTransmitted;
    int packageReceived;
    /** 每次 ping 的时候的耗时、ttl 等信息（只存，不解析） **/
    List<String> detailInfo;

    public PingResult() {
      detailInfo = new ArrayList<>();
    }
  }

  static class TraceResult {
    /** 最终要到达的 ip 地址 **/
    String ip;
    /** 需要 ping 的地址，如果原来就是 ip 这里就是空 **/
    String hostName;
    /** 过程中的路由信息 **/
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
}
