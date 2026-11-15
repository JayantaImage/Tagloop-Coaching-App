package com.flipkart.tagloopcoaching;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
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

public class MainActivity extends AppCompatActivity {

    private EditText toteInput;
    private ImageButton btnScan;
    private Button btnGo, btnCloseChecking;
    private WebView webView;
    private LinearLayout scannerSection;

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

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);
        // দুটো আলাদা সার্ভার (Mixed Content) যাতে ব্লক না হয় তার জন্য এই লাইনটা দেওয়া হলো
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

        // শুরুতে ফ্লো-লাইটের পেজ লোড হবে
        webView.loadUrl("http://10.24.1.71/flo-lite/");

        btnGo.setOnClickListener(v -> {
            String toteId = toteInput.getText().toString().trim().toUpperCase();
            if (toteId.isEmpty()) {
                Toast.makeText(this, "Please enter Tote ID", Toast.LENGTH_SHORT).show();
                return;
            }
            injectMasterScript(toteId);
        });

        // Google Play Services Scanner Setup (Native & Fast)
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
            webView.loadUrl("http://10.24.1.71/flo-lite/");
        });
    }

    // মাস্টার জাভাস্ক্রিপ্ট (Flo-lite এবং Kratos দুটোর ডেটাই একসাথে আনবে)
    private void injectMasterScript(String toteId) {
        String js = "javascript:(async function(){ " +
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
                "   if(!r1.ok) throw new Error('Tote not found in Flo-lite or Session Expired'); " +
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
                "   if(window.AndroidApp) window.AndroidApp.showLoading('Checking Tagloop Eligibility (Kratos)...'); " +
                "   let tagloopEligibleItems = []; " +
                "   for(let item of allItems) { " +
                "       let fsn = item.fsn; " +
                "       if(!fsn || fsn === 'N/A') continue; " +
                "       try { " +
                "           // এখানে ক্র্যাটোসের সঠিক IP দেওয়া হলো (10.83.32.70) " +
                "           let kr = await fetch(`http://10.83.32.70/kratos/product/lookup?fsn=${encodeURIComponent(fsn)}`, {credentials:'include'}); " +
                "           if(kr.ok) { " +
                "               let html = await kr.text(); " +
                "               let match = html.match(/class=[\"']bold[\"'][^>]*>\\s*tagloop_eligible\\s*<\\/td>\\s*<td[^>]*>\\s*(?:<div[^>]*>)?\\s*([^<\\s]+)/i); " +
                "               if(match && match[1].toUpperCase() === 'TRUE') { " +
                "                   tagloopEligibleItems.push(item); " +
                "               } " +
                "           } " +
                "       } catch(e) {} " +
                "   } " +
                "   if(tagloopEligibleItems.length === 0) throw new Error('No Tagloop Eligible items found in this Tote!'); " +
                "   if(window.AndroidApp) window.AndroidApp.showLoading('Opening Dashboard...'); " +
                "   let payload = {tote_id:tote, putlist_id:pid, items:tagloopEligibleItems}; " +
                "   let gasUrl = 'https://script.google.com/a/macros/flipkart.com/s/AKfycbzxNqnX1HrawsRVwBX33ZEscXY6OX-tomn4dPWEQLDtrfmv30q9gJFktJPtDE_5bpI_/exec'; " +
                "   window.location.href = gasUrl + '?data=' + encodeURIComponent(JSON.stringify(payload)); " +
                "} catch(e) { " +
                "   if(window.AndroidApp) window.AndroidApp.showError(e.message); " +
                "   else alert(e.message); " +
                "} " +
                "})();";
        
        webView.evaluateJavascript(js, null);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void showLoading(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void showError(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + message, Toast.LENGTH_LONG).show());
        }
    }
}
