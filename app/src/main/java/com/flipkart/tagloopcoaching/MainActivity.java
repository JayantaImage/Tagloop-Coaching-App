package com.flipkart.tagloopcoaching;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import java.util.Arrays;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {

    private static final String FLO_URL = "http://10.24.1.71/flo-lite/";
    private static final String KRATOS_URL = "http://10.83.32.70/kratos/";
    private static final String GAS_URL = "https://script.google.com/a/macros/flipkart.com/s/AKfycbzxNqnX1HrawsRVwBX33ZEscXY6OX-tomn4dPWEQLDtrfmv30q9gJFktJPtDE_5bpI_/exec";

    // সব facility (তোমার Tracking bookmarklet থেকে)। প্রথমে ulub_bts চেষ্টা হবে।
    private static final String[] FACILITIES = new String[]{
            "nad_har_wh_kl_nl_01nl", "gur_san_wh_nl_01nl", "pit_dha_al_swa_nl_01nl", "nag_rtm_wh_nl_01nl",
            "frk_bts", "cbe_oth_01", "bhi_pad_wh_nl_01nl", "ran_mfc_wh_nl_01nl", "vij_ved_wh_nl_01nl",
            "malur_bts", "ind_teh_wh_nl_01nl", "bhi_vas_wh_nl_01nl", "ban_ven_wh_nl_01nl",
            "guw_kam_wh_nl_02nl", "luc_has_wh_nl_02nl", "ulub_bts", "chn_tvr_04", "hyderabad_medchal_01",
            "ahm_khe_wh_nl_01nl", "lud_kha_wh_nl_01nl", "jai_san_wh_nl_01nl", "jal_bin_wh_nl_01nl",
            "bhu_pan_wh_nl_01nl", "pat_haj_wh_nl_01nl", "pun_bad_wh_nl_01nl", "bhi_sse_wh_nl_01nl",
            "var_mfc_wh_nl_01nl", "rai_dha_wh_nl_02nl", "gur_bho_al_adi_nl_01nl", "sur_mfc_wh_nl_01nl",
            "gur_gop_al_emi_nl_01nl", "ben_kat_al_ema_nl_01nl", "mal_ban_al_fat_nl_01nl",
            "dha_mol_al_ema_nl_03nl", "ban_may_al_bra_nl_01nl", "coc_mfc_wh_nl_01nl", "grk_mfc_wh_nl_01nl",
            "ban_may_al_bra_nl_02nl", "bhi_tha_al_emi_nl_01nl", "gur_sam_al_abr_nl_01nl",
            "new_ham_al_glo_nl_01nl", "kol_gab_3pl_g_01", "hyd_dev_al_dro_nl_02nl", "dha_kot_wh_nl_01nl",
            "ind_las_al_bum_nl_01nl", "kal_gur_wh_kl_nl_01nl", "how_arg_al_wil_nl_01nl",
            "ban_ban_al_ind_nl_01nl", "ban_mal_al_ban_nl_01nl", "kur_moh_al_pir_nl_01nl",
            "bhi_mum_al_dro_nl_01nl", "jab_mfc_wh_nl_01nl", "kol_sal_al_dol_nl_01nl",
            "how_san_al_emi_nl_01nl", "del_bud_al_ban_nl_02nl", "bhi_tha_al_ema_nl_01nl",
            "kol_how_al_rak_nl_01nl", "har_jha_al_fat_nl_01nl", "kol_how_al_nex_nl_03nl",
            "tir_ang_al_dol_nl_02nl"
    };

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

    // ---- Camera / file chooser (Take Picture button) ----
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (fileCallback == null) return;
                Uri[] res = null;
                if (result.getResultCode() == RESULT_OK) {
                    Intent d = result.getData();
                    if (d != null && d.getData() != null) {
                        res = new Uri[]{d.getData()};
                    } else if (cameraUri != null) {
                        res = new Uri[]{cameraUri};
                    }
                }
                if (res == null && cameraUri != null) {
                    try { getContentResolver().delete(cameraUri, null, null); } catch (Exception ignored) { }
                }
                fileCallback.onReceiveValue(res);
                fileCallback = null;
                cameraUri = null;
            });

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

        WebView.setWebContentsDebuggingEnabled(true);

        // ---------- Main WebView (Flo-lite) ----------
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView w, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                cameraUri = null;
                try {
                    Intent cam = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.Images.Media.DISPLAY_NAME, "tagloop_" + System.currentTimeMillis() + ".jpg");
                        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                        cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TagloopCoaching");
                        cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        if (cameraUri != null) {
                            cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        }
                    }
                    boolean wantsImage = p.isCaptureEnabled();
                    for (String t : p.getAcceptTypes()) {
                        if (t != null && t.startsWith("image")) wantsImage = true;
                    }
                    Intent launch;
                    if (cam != null && wantsImage) {
                        launch = cam;
                    } else {
                        launch = Intent.createChooser(p.createIntent(), "Select");
                        if (cam != null) launch.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                    }
                    fileLauncher.launch(launch);
                } catch (Exception e) {
                    fileCallback = null;
                    cameraUri = null;
                    cb.onReceiveValue(null);
                    toast("Camera open hoy ni: " + e.getMessage(), true);
                }
                return true;
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url != null && url.contains("script.google.com")) {
                    runOnUiThread(() -> setCheckingMode(true));
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
            setCheckingMode(false);
            toteInput.setText("");
            pendingItems = null;
            webView.loadUrl(FLO_URL);
        });
    }

    // Close button দেখালে WebView টা তার নিচে নামে, যাতে header ঢাকা না পড়ে
    private void setCheckingMode(boolean on) {
        scannerSection.setVisibility(on ? View.GONE : View.VISIBLE);
        btnCloseChecking.setVisibility(on ? View.VISIBLE : View.GONE);
        btnCloseChecking.post(() -> {
            ViewGroup.LayoutParams lp = webView.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) lp).topMargin = on ? btnCloseChecking.getHeight() : 0;
                webView.setLayoutParams(lp);
            }
        });
    }

    private void toast(String m, boolean longer) {
        Toast.makeText(this, m, longer ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
    }

    // ================= STEP 1: Flo-lite data (main WebView, flo-lite origin) =================
    private void injectMasterScript(String toteId) {
        String facsJson = new JSONArray(Arrays.asList(FACILITIES)).toString();

        // NOTE: এই string এর ভিতরে কখনো // comment দেবে না (সব এক লাইন হয়ে যায়)
        String js = "(async function(){ " +
                "try { " +
                "   if(window.AndroidApp) window.AndroidApp.showLoading('Fetching Flo-lite data...'); " +
                "   let tote = '" + toteId.replace("'", "") + "'; " +
                "   let O = 'http://10.24.1.71'; " +
                "   let facs = " + facsJson + "; " +
                "   function getFac(){ " +
                "       let p = location.pathname; " +
                "       let m = p.match(/\\/flo-lite\\/([^\\/?#]+)/i) || p.match(/\\/([^\\/]+)\\/(?:desktop|mobile)(?:\\/|$)/i); " +
                "       if(m) return decodeURIComponent(m[1]); " +
                "       let q = new URLSearchParams(location.search); " +
                "       return q.get('facility_id') || q.get('facilityId') || localStorage.getItem('facilityId') || null; " +
                "   } " +
                "   let getHeaders = (fac) => { " +
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
                "   let cFac = getFac(); " +
                "   let first = cFac ? cFac : 'ulub_bts'; " +
                "   let fList = [first].concat(facs.filter(x=>x!==first)); " +
                "   let h = null; let d1 = null; let lastErr = ''; " +
                "   for(let f of fList){ " +
                "       try{ " +
                "           let hh = getHeaders(f); " +
                "           let rr = await fetch(`${O}/inbound-apollo-flo-lite-routes-api/inbound-flo-lite-proxy-new/api/v2/putlists/container-details?containerLabel=${tote}`, {method:'GET',headers:hh,credentials:'include'}); " +
                "           if(!rr.ok){ lastErr='HTTP '+rr.status+' fac='+f; continue; } " +
                "           let dd = await rr.json(); " +
                "           if((dd?.item_details||[]).length>0){ h=hh; d1=dd; break; } " +
                "           lastErr='no items fac='+f; " +
                "       }catch(e){ lastErr=String(e&&e.message)+' fac='+f; } " +
                "   } " +
                "   if(!d1) throw new Error('Tote not found in any facility or Session Expired | last: '+lastErr); " +
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
                "       let category = item.product?.category || ''; " +
                "       let r2img = null; " +
                "       if((!title || !category || !fsn) && wid){ " +
                "           try{ " +
                "               let r2 = await fetch(`${O}/hyperlocal-flo-lite-api/inbound-proxy/api/v1/commons/fetch_product_details`, {method:'POST',headers:h,credentials:'include',body:JSON.stringify({wid:wid,fsn:fsn})}); " +
                "               if(r2.ok){ " +
                "                   let d2 = await r2.json(); " +
                "                   let prod = Array.isArray(d2) ? d2[0]?.products : d2?.products; " +
                "                   r2img = getV(d2,'high_res_image_url'); " +
                "                   if(prod){ fsn = prod.fsn || fsn; title = (prod.title || title).replace(/\"/g,''); category = prod.vertical || prod.category || category; } " +
                "               } " +
                "           }catch(e){} " +
                "       } " +
                "       let it3 = (d3?.item_details||[]).find(x=>(x.item_label||x.derived_item_label)===wid); " +
                "       let cby = String(d3?.audit_details?.created_by || d1?.audit_details?.created_by || '').replace(/\"/g,''); " +
                "       let img = String(it3?.product?.high_res_image_url || item.product?.high_res_image_url || r2img || '').replace(/\"/g,''); " +
                "       allItems.push({fsn:fsn||'N/A', product_name:title||'Unknown', image_url:img, wid:wid, vertical:category, quantity:qty, created_by:cby}); " +
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
                "if(x)return x[1].toUpperCase()==='TRUE'?'TRUE':'FALSE';return /Identifying Attributes|Other Product Attributes/i.test(h)?'FALSE':'NOFIELD';}" +
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
                pendingItems = null;
                toast("Kratos session nai. Login kore Close chapo, tarpor abar GO dao", true);
                setCheckingMode(true);
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
