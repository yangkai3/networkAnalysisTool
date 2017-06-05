package jeffery.com.test500px;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yangkai
 */
public class NetworkAnalysisTaskBuilder {

  private static final int TRACE_MAX_TTL = 30;
  private static final int DEFAULT_PACKAGE_SIZE = 56;
  private static final int DEFAULT_PING_TRY_COUNT = 3;

  private List<Config> mPingConfigs;
  private List<Config> mTraceConfigs;
  private List<Config> mPackageLostConfigs;

  public NetworkAnalysisResult() {

  }

  public void ping(String url) {
    ping(url, DEFAULT_PING_TRY_COUNT);
  }

  public void ping(String url, int times) {
    ping(url, times, DEFAULT_PACKAGE_SIZE);
  }

  public void ping(String url, int times, int packageSize) {
    if (mPingConfigs == null) {
      mPingConfigs = new ArrayList<>();
    }

    mPingConfigs.add(new Config(url, times, packageSize));
  }

  public void trace(String url) {
    trace(url, DEFAULT_PACKAGE_SIZE);
  }

  public void trace(String url, int packageSize) {
    if (mTraceConfigs == null) {
      mTraceConfigs = new ArrayList<>();
    }
    mTraceConfigs.add(new Config(url, 1, packageSize));
  }

  public void packageLost(String url) {
    this.packageLost(url, DEFAULT_PING_TRY_COUNT);
  }

  public void packageLost(String url, int times) {
    this.packageLost(url, times, DEFAULT_PACKAGE_SIZE);
  }

  public void packageLost(String url, int times, int packageSize) {
    if (mPackageLostConfigs == null) {
      mPackageLostConfigs = new ArrayList<>();
    }
    mPackageLostConfigs.add(new Config(url, times, packageSize));
  }

  public void start() {
    // start network analysis

    // report
  }

  public class Config {
    String mUrl;
    int mTimes;
    int mPackageSize;

    Config(String url, int times, int packageSize) {
      this.mUrl = url;
      this.mTimes = times;
      this.mPackageSize = packageSize;
    }
  }
}
