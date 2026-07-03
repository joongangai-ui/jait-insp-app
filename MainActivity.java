package com.joongang.insp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    // ★ 앱이 열 모바일 페이지 주소 (http/https 모두 지원). 여기만 바꾸면 됨.
    private static final String START_URL = "https://jaitpms.com/m.php";

    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraImageUri;

    private static final int REQ_FILE = 1001;
    private static final int REQ_PERMS = 2002;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestStartupPermissions();

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                String scheme = u.getScheme();
                // http/https 는 앱 내부 WebView 에서, 그 외(tel/mailto/intent 등)는 외부 앱으로
                if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "처리할 앱이 없습니다", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            // getUserMedia(카메라/마이크) 권한 자동 허용
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
            // 파일/사진 첨부 (input type=file, 서명 사진 등)
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                openFileChooser(params);
                return true;
            }
        });

        // 다운로드(첨부파일 저장)는 시스템 다운로드 매니저로
        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "다운로드를 열 수 없습니다", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 뒤로가기 = 웹 히스토리 우선
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);

        // 앱 켤 때 새 버전 있으면 업데이트 안내(백그라운드 확인 · 시작 방해 안 되게 약간 지연)
        web.postDelayed(this::checkForUpdate, 2500);
    }

    /* ─────────── 인앱 자동 업데이트 (사이드로드 APK) ─────────── */
    private static final String VERSION_URL = "https://jaitpms.com/printer-monitor/nas-web/app_version.json";
    private long updDownloadId = -1;
    private BroadcastReceiver updReceiver;

    private void checkForUpdate() {
        new Thread(() -> {
            try {
                java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                        new java.net.URL(VERSION_URL + "?_=" + System.currentTimeMillis()).openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(8000);
                c.setRequestProperty("Cache-Control", "no-cache");
                if (c.getResponseCode() != 200) return;
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                org.json.JSONObject j = new org.json.JSONObject(sb.toString());
                int serverCode = j.optInt("versionCode", 0);
                String serverName = j.optString("versionName", "");
                String url = j.optString("url", "");
                String notes = j.optString("notes", "");
                int myCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (serverCode > myCode && !url.isEmpty()) {
                    runOnUiThread(() -> showUpdateDialog(serverName, notes, url));
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void showUpdateDialog(String ver, String notes, String url) {
        try {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("업데이트 있음" + (ver.isEmpty() ? "" : " (v" + ver + ")"))
                    .setMessage((notes.isEmpty() ? "새 버전이 있습니다." : notes) + "\n\n지금 업데이트할까요?")
                    .setCancelable(true)
                    .setNegativeButton("나중에", null)
                    .setPositiveButton("업데이트", (d, w) -> startUpdateDownload(url))
                    .show();
        } catch (Exception ignored) {}
    }

    private void startUpdateDownload(String url) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName())));
                    Toast.makeText(this, "‘이 출처 설치 허용’을 켠 뒤 다시 업데이트를 눌러주세요", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "설정에서 ‘알 수 없는 앱 설치’를 허용해주세요", Toast.LENGTH_LONG).show();
                }
                return;
            }
            Toast.makeText(this, "업데이트 다운로드 중…", Toast.LENGTH_SHORT).show();
            File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File apk = new File(dir, "update.apk");
            if (apk.exists()) apk.delete();
            android.app.DownloadManager dm = (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            android.app.DownloadManager.Request req = new android.app.DownloadManager.Request(Uri.parse(url));
            req.setTitle("중앙아이티 점검 업데이트");
            req.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "update.apk");
            req.setMimeType("application/vnd.android.package-archive");
            registerUpdReceiver();
            updDownloadId = dm.enqueue(req);
        } catch (Exception e) {
            Toast.makeText(this, "업데이트 다운로드 실패", Toast.LENGTH_SHORT).show();
        }
    }

    private void registerUpdReceiver() {
        if (updReceiver != null) return;
        updReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                long id = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id != updDownloadId) return;
                try {
                    File apk = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "update.apk");
                    if (!apk.exists()) { Toast.makeText(MainActivity.this, "업데이트 파일을 찾지 못했습니다", Toast.LENGTH_SHORT).show(); return; }
                    Uri uri = FileProvider.getUriForFile(MainActivity.this, getPackageName() + ".fileprovider", apk);
                    Intent install = new Intent(Intent.ACTION_VIEW);
                    install.setDataAndType(uri, "application/vnd.android.package-archive");
                    install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(install);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "설치를 시작하지 못했습니다", Toast.LENGTH_SHORT).show();
                }
                try { unregisterReceiver(updReceiver); } catch (Exception ignored) {}
                updReceiver = null;
            }
        };
        IntentFilter f = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(updReceiver, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(updReceiver, f);
    }

    private void openFileChooser(WebChromeClient.FileChooserParams params) {
        // 갤러리/문서 선택
        Intent content = new Intent(Intent.ACTION_GET_CONTENT);
        content.addCategory(Intent.CATEGORY_OPENABLE);
        content.setType("*/*");
        String[] accept = params.getAcceptTypes();
        if (accept != null && accept.length > 0 && accept[0] != null && !accept[0].isEmpty()) {
            content.setType(accept[0].contains("image") ? "image/*" : accept[0]);
        } else {
            content.setType("*/*");
        }
        // ★ 웹 input 이 multiple 이면 여러 장 선택 허용(결과는 onActivityResult 의 clipData 로 처리됨)
        if (params != null && params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
            content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        // 카메라 촬영(권한 있을 때만)
        List<Intent> extras = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (cam.resolveActivity(getPackageManager()) != null) {
                try {
                    File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "");
                    if (!dir.exists()) dir.mkdirs();
                    String name = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";
                    File f = new File(dir, name);
                    cameraImageUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                    cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri);
                    cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    extras.add(cam);
                } catch (Exception ignored) {}
            }
        }

        Intent chooser = Intent.createChooser(content, "사진·파일 선택");
        if (!extras.isEmpty()) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toArray(new Intent[0]));
        }
        try {
            startActivityForResult(chooser, REQ_FILE);
        } catch (Exception e) {
            if (filePathCallback != null) { filePathCallback.onReceiveValue(null); filePathCallback = null; }
            Toast.makeText(this, "파일 선택을 열 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK) {
                if (data != null && data.getDataString() != null) {
                    results = new Uri[]{ Uri.parse(data.getDataString()) };
                } else if (data != null && data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    results = new Uri[n];
                    for (int i = 0; i < n; i++) results[i] = data.getClipData().getItemAt(i).getUri();
                } else if (cameraImageUri != null) {
                    results = new Uri[]{ cameraImageUri };
                }
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            cameraImageUri = null;
        }
    }

    private void requestStartupPermissions() {
        List<String> need = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            need.add(android.Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                need.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!need.isEmpty())
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), REQ_PERMS);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updReceiver != null) { try { unregisterReceiver(updReceiver); } catch (Exception ignored) {} updReceiver = null; }
    }
}
