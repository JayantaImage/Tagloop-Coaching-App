package com.flipkart.tagloopcoaching;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {

    private static final String FLO_URL = "http://10.24.1.71/flo-lite/";
    private static final String KRATOS_URL = "http://10.83.32.70/kratos/";
    private static final String GAS_URL = "https://script.google.com/a/macros/flipkart.com/s/AKfycbzxNqnX1HrawsRVwBX33ZEscXY6OX-tomn4dPWEQLDtrfmv30q9gJFktJPtDE_5bpI_/exec";

    private EditText toteInput;
    private ImageButton btnScan;
    private Button btnGo, btnCloseChecking;
    private WebView webView;       // Flo-lite (main, visible)
    private WebView kratosView;    // Kratos (hidden, same-origin check)
    private LinearLayout scannerSection;

    private JSONArray pendingItems;
    private String pendingTote = "";
    private String pendingPutlist = "";
    private boolean checkStarted = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toteInput = findViewById(R.id.toteInput);
        btnScan = findViewById(R.id.btnScan);
        btnGo = findViewById(R.id.btnGo);
        btnCloseChecking = findViewById(R.id.btnCloseChecking);
        webView = findViewById(R.id.webView);
        scannerSection = findViewById(R.id.scannerSection);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        }

        // PC Chrome এর chrome://inspect থেকে console দেখার জন্য
        WebView.setWebContentsDebuggingEnabled(true);

        // ---------- Main WebView (Flo-lite) ----------
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("script.google.com")) {
                    runOnUiThread(() -> {
                        scannerSection.setVisibility(View.GONE);
                        btnCloseChecking.setVisibility(View.VISIBLE);
                    });
                }
            }
        });

        webView.loadUrl(FLO_URL);

        // ---------- Hidden WebView (Kratos) ----------
        kratosView = new WebView(this);
        WebSettings ks = kratosView.getSettings();
        ks.setJavaScriptEnabled(true);
        ks.setDomStorageEnabled(true);
        ks.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        kratosView.setWebChromeClient(new WebChromeClient());
        kratosView.addJavascriptInterface(new WebAppInterface(), "AndroidApp");
        kratosView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (pendingItems != null && !checkStarted && url != null && url.startsWith("http://10.83.32.70/kratos")) {
                    runKratosCheck();
                }
            }
        });
        addContentView(kratosView, new ViewGroup.LayoutParams(1, 1));

        // ---------- Buttons ----------
        btnGo.setOnClickListener(v -> {
            String toteId = toteInput.getText().toString().trim().toUpperCase();
            if (toteId.isEmpty()) {
                Toast.makeText(this, "Please enter Tote ID", Toast.LENGTH_SHORT).show();
                return;
            }
            injectMasterScript(toteId);
        });

        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);

        btnScan.setOnClickListener(v -> {
            scanner.startScan()
                    .addOnSuccessListener(barcode -> {
                        String scannedId = barcode.getRawValue();
                        if (scannedId != null) {
                            toteInput.setText(scannedId.trim());
                        }
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(MainActivity.this, "Scan Failed", Toast.LENGTH_SHORT).show();
                    });
        });

        btnCloseChecking.setOnClickListener(v -> {
            scannerSection.setVisibility(View.VISIBLE);
            btnCloseChecking.setVisibility(View.GONE);
            toteInput.setText("");
            pendingItems = null;
            webView.loadUrl(FLO_URL);
        });
    }

    private void toast(String m, boolean longer) {
        Toast.makeText(this, m, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    // ================= STEP 1: Flo-lite data (main WebView, flo-lite origin) =================
    private void injectMasterScript(String toteId) {
        // NOTE: এই string এর ভিতরে কখনো // comment দেবে না (সব এক লাইন হয়ে যায়)
        String js = "(async function(){ " +
                "try { " +
                "   if(window.AndroidApp) window.AndroidApp.showLoading('Fetching Flo-lite data...'); " +
                "   let tote = '" + toteId + "'; " +
                "   let O = 'http://10.24.1.71'; " +
                "   let getHeaders = () => { " +
                "       let fac = (window.location.pathname.match(/\\/flo-lite\\/([^\\/]+)\\//)||[])[1] || localStorage.getItem('facilityId') || ''; " +
                "       let ck = document.cookie.match(/(?:csrf-token|XSRF-TOKEN|_csrf)=([^;]+)/); " +
                "       let csrf = document.querySelector('meta[name=\"csrf-token\"]')?.content || localStorage.getItem('csrf-token') || (ck?decodeURIComponent(ck[1]):'') || ''; " +
                "       let headers = {'accept':'application/json','content-type':'application/json','x-tenant-id':'FKI','x-client-id':'WH','x-requested-by':'auto_extractor','x-facility-id':fac}; " +
                "       if(csrf) headers['csrf-token'] = csrf; " +
                "       return headers; " +
                "   }; " +
                "   function getV(obj,k){ " +
                "       if(!obj||typeof obj!=='object') return null; " +
                "       if(obj[k]!==undefined && obj[k]!==null) return obj[k]; " +
                "       for(let key in obj){ let val=getV(obj[key],k); if(val!==null) return val; } " +
                "       return null; " +
                "   } " +
                "   let h = getHeaders(); " +
                "   let r1 = await fetch(`${O}/inbound-apollo-flo-lite-routes-api/inbound-flo-lite-proxy-new/api/v2/putlists/container-details?containerLabel=${tote}`, {method:'GET',headers:h,credentials:'include'}); " +
                "   if(!r1.ok) throw new Error('Tote not found in Flo-lite or Session Expired (HTTP '+r1.status+')'); " +
                "   let d1 = await r1.json(); " +
                "   let pid = d1?.putlist_details?.display_id || ''; " +
                "   let items = d1?.item_details || []; " +
                "   let d3 = d1?.audit_details ? d1 : null; " +
                "   if(!d3 && pid){ " +
                "       try{ " +
                "           let r3 = await fetch(`${O}/inbound-apollo-flo-lite-routes-api/inbound-flo-lite-proxy-new/api/v2/putlists/${pid}/item-details`, {method:'GET',headers:h,credentials:'include'}); " +
                "           if(r3.ok) d3 = await r3.json(); " +
                "       } catch(e){} " +
                "   } " +
                "   if(!items.length) throw new Error('No items found in Tote.'); " +
                "   let allItems = []; " +
                "   for(let item of items){ " +
                "       let wid = item.item_label || item.derived_item_label || ''; " +
                "       let qty = item.quantity || 0; " +
                "       let fsn = item.product?.fsn || ''; " +
                "       let title = (item.product?.title || '').replace(/\"/g,''); " +
                "       let r2img = null; " +
                "       if(!title && wid){ " +
                "           try{ " +
                "               let r2 = await fetch(`${O}/hyperlocal-flo-lite-api/inbound-proxy/api/v1/commons/fetch_product_details`, {method:'POST',headers:h,credentials:'include',body:JSON.stringify({wid:wid,fsn:fsn})}); " +
                "               if(r2.ok){ " +
                "                   let d2 = await r2.json(); " +
                "                   let prod = Array.isArray(d2) ? d2[0]?.products : d2?.products; " +
                "                   r2img = getV(d2,'high_res_image_url'); " +
                "                   if(prod){ fsn = prod.fsn || fsn; title = (prod.title || title).replace(/\"/g,''); } " +
                "               } " +
                "           }catch(e){} " +
                "       } " +
                "       let it3 = (d3?.item_details||[]).find(x=>(x.item_label||x.derived_item_label)===wid); " +
                "       let cby = String(d3?.audit_details?.created_by || d1?.audit_details?.created_by || '').replace(/\"/g,''); " +
                "       let img = String(it3?.product?.high_res_image_url || item.product?.high_res_image_url || r2img || '').replace(/\"/g,''); " +
                "       allItems.push({fsn:fsn||'N/A', product_name:title||'Unknown', image_url:img, wid:wid, quantity:qty, created_by:cby}); " +
                "   } " +
                "   window.AndroidApp.onFloData(tote, pid, JSON.stringify(allItems)); " +
                "} catch(e) { " +
                "   if(window.AndroidApp) window.AndroidApp.showError(e.message); " +
                "   else alert(e.message); " +
                "} " +
                "})();";

        webView.evaluateJavascript(js, null);
    }

    // ================= STEP 2: Kratos check (hidden WebView, kratos origin) =================
    private void startKratos(String tote, String pid, String itemsJson) {
        try {
            pendingItems = new JSONArray(itemsJson);
        } catch (Exception e) {
            toast("Flo-lite data parse error", true);
            return;
        }
        pendingTote = tote;
        pendingPutlist = pid;
        checkStarted = false;
        toast("Checking Tagloop Eligibility (Kratos)...", false);
        kratosView.loadUrl(KRATOS_URL);
    }

    private void runKratosCheck() {
        checkStarted = true;

        JSONArray fsns = new JSONArray();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < pendingItems.length(); i++) {
            JSONObject it = pendingItems.optJSONObject(i);
            String f = (it == null) ? "" : it.optString("fsn", "");
            if (!f.isEmpty() && !f.equals("N/A") && seen.add(f)) {
                fsns.put(f);
            }
        }
        if (fsns.length() == 0) {
            pendingItems = null;
            toast("No FSN found in Tote", true);
            return;
        }

        // তোমার PC bookmarklet #2 এর same logic (relative URL = same-origin)
        String js = "(async function(){try{" +
                "const fsns=" + fsns.toString() + ";" +
                "const res={};let next=0;" +
                "async function one(fsn){let last='ERR';" +
                "for(let a=0;a<3;a++){try{" +
                "const r=await fetch('/kratos/product/lookup?fsn='+encodeURIComponent(fsn),{credentials:'include'});" +
                "if(r.ok){const h=await r.text();" +
                "const x=h.match(/class=[\"']bold[\"'][^>]*>\\s*tagloop_eligible\\s*<\\/td>\\s*<td[^>]*>\\s*(?:<div[^>]*>)?\\s*([^<\\s]+)/i);" +
                "if(x)return x[1].toUpperCase()==='TRUE'?'TRUE':'FALSE';return 'NOFIELD';}" +
                "last='HTTP '+r.status;}catch(e){last='ERR';}}return last;}" +
                "async function w(){while(next<fsns.length){const i=next++;res[fsns[i]]=await one(fsns[i]);}}" +
                "await Promise.all(Array.from({length:Math.min(10,fsns.length)},w));" +
                "AndroidApp.onKratosResult(JSON.stringify(res));" +
                "}catch(e){AndroidApp.onKratosError(String(e&&e.message));}})();";

        kratosView.evaluateJavascript(js, null);
    }

    // ================= STEP 3: merge + Dashboard =================
    private void finishFlow(String json) {
        try {
            JSONObject res = new JSONObject(json);
            JSONArray eligible = new JSONArray();
            int valid = 0;
            for (int i = 0; i < pendingItems.length(); i++) {
                JSONObject it = pendingItems.optJSONObject(i);
                if (it == null) continue;
                String r = res.optString(it.optString("fsn", ""), "");
                if (r.equals("TRUE")) {
                    eligible.put(it);
                    valid++;
                } else if (r.equals("FALSE")) {
                    valid++;
                }
            }

            if (valid == 0) {
                // কোনো FSN এর বৈধ উত্তর আসেনি = Kratos login/session নেই
                pendingItems = null;
                toast("Kratos session nai. Login kore Close chapo, tarpor abar GO dao", true);
                scannerSection.setVisibility(View.GONE);
                btnCloseChecking.setVisibility(View.VISIBLE);
                webView.loadUrl(KRATOS_URL);
                return;
            }
            if (eligible.length() == 0) {
                pendingItems = null;
                toast("No Tagloop Eligible items found in this Tote!", true);
                return;
            }

            JSONObject payload = new JSONObject();
            payload.put("tote_id", pendingTote);
            payload.put("putlist_id", pendingPutlist);
            payload.put("items", eligible);
            pendingItems = null;

            toast("Opening Dashboard...", false);
            String enc = URLEncoder.encode(payload.toString(), "UTF-8").replace("+", "%20");
            webView.loadUrl(GAS_URL + "?data=" + enc);
        } catch (Exception e) {
            pendingItems = null;
            toast("Error: " + e.getMessage(), true);
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void showLoading(String message) {
            runOnUiThread(() -> toast(message, false));
        }

        @JavascriptInterface
        public void showError(String message) {
            runOnUiThread(() -> toast("Error: " + message, true));
        }

        @JavascriptInterface
        public void onFloData(String tote, String pid, String itemsJson) {
            runOnUiThread(() -> startKratos(tote, pid, itemsJson));
        }

        @JavascriptInterface
        public void onKratosResult(String json) {
            runOnUiThread(() -> finishFlow(json));
        }

        @JavascriptInterface
        public void onKratosError(String m) {
            runOnUiThread(() -> {
                pendingItems = null;
                toast("Kratos error: " + m, true);
            });
        }
    }
}
