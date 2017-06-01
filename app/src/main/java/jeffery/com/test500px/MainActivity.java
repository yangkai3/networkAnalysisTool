package jeffery.com.test500px;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 1. ping 某个地址
 * 2. trace
 * 3. 看丢包率 (直接看就好)
 */
public class MainActivity extends Activity {

  Handler h;
  TextView tvInfo;
  Button btnPing, btnTrace;
  ProgressBar progressBar;
  EditText editText;

  NetworkAnalysisResult result = new NetworkAnalysisResult();

  /**
   * Called when the activity is first created.
   */
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    tvInfo = (TextView) findViewById(R.id.tvInfo);
    btnPing = (Button) findViewById(R.id.btnPing);
    btnTrace = (Button) findViewById(R.id.btnTrace);
    editText = (EditText) findViewById(R.id.editText);

    progressBar = (ProgressBar) findViewById(R.id.progressBar);
    progressBar.setVisibility(View.INVISIBLE);

    h = new Handler() {
      public void handleMessage(Message msg) {
        StringBuilder stringBuilder = new StringBuilder();
        // print ping
        for (NetworkAnalysisResult.PingResult r : result.pingResults) {
          stringBuilder.append(r.url + "\n");
          stringBuilder.append(r.packageTransmitted + " transmitted " + r.packageReceived
              + " received.\n");
          stringBuilder.append("result: " + r.result + "\n");
        }

        for (NetworkAnalysisResult.TraceResult traceResult : result.traceResults) {
          stringBuilder.append("trace host: " + traceResult.hostName + "\n");
          stringBuilder.append("trace target ip " + traceResult.ip + "\n\n");

          for (NetworkAnalysisResult.Router router : traceResult.routers) {
            if (router == null || (router.ip == null && router.host == null)) {
              stringBuilder.append("  --unknown router--\n\n");
              continue;
            }

            if (router.host != null) {
              stringBuilder.append("  router host: " + router.host + "\n");
            }
            if (router.ip != null) {
              stringBuilder.append("  router ip " + router.ip + "\n");
            }
            stringBuilder.append('\n');
          }
        }

        tvInfo.setText(stringBuilder.toString());

        if (msg.what == 10) {
          btnPing.setEnabled(true);
          btnTrace.setEnabled(true);
          progressBar.setVisibility(View.INVISIBLE);
        }
      }
    };
  }

  public void onclick(View v) {
    switch (v.getId()) {
      case R.id.btnPing:
        btnPing.setEnabled(false);
        btnTrace.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        Thread tPing = new Thread(new Runnable() {
          Message msg;
          String ping = "";

          @Override
          public void run() {
            // 漫长的过程
            List<String> urls = new ArrayList<>();
            urls.add(editText.getText().toString());
            NetworkAnalysisTool.ping(urls, result);

            h.sendEmptyMessage(10);
          }
        });
        tPing.start();
        break;
      case R.id.btnTrace:
        btnPing.setEnabled(false);
        btnTrace.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        Thread tTrace = new Thread(new Runnable() {
          Message msg;
          String trace = "";

          @Override
          public void run() {
            List<String> urls = new ArrayList<>();
            urls.add(editText.getText().toString());
            NetworkAnalysisTool.trace(urls, result, new NetworkAnalysisTool.Callback() {
              @Override
              public void onProgressChanged(NetworkAnalysisResult result) {
                h.sendEmptyMessage(1);
              }

              @Override
              public void onFinished(NetworkAnalysisResult result) {
                h.sendEmptyMessage(10);
              }
            });
          }
        });
        tTrace.start();
      default:
        break;
    }
  }
}
