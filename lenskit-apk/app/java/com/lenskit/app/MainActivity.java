package com.lenskit.app;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private static final int REQ_PERM = 1;
    private static final int REQ_FILE = 100;

    private static final class SaveBuffer {
        String name;
        int kind;
        StringBuilder data = new StringBuilder();
        int expectedChunks;
        int receivedChunks;
        boolean active;
    }

    private SaveBuffer saveBuffer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF0B0B0F);
        getWindow().setNavigationBarColor(0xFF0B0B0F);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQ_PERM);
        }

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        webView.setBackgroundColor(0xFF0B0B0F);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, "选择照片"), REQ_FILE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        try {
            LocalServer server = new LocalServer(getAssets());
            server.start();
            webView.loadUrl("http://127.0.0.1:" + server.getPort() + "/");
        } catch (Exception e) {
            Toast.makeText(this, "初始化失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCallback != null) {
                fileCallback.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        // 相机被拒绝时，网页端会提示改用相册模式
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private class Bridge {

        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @JavascriptInterface
        public void saveBegin(String name, int chunks, int kind) {
            saveBuffer = new SaveBuffer();
            saveBuffer.name = name;
            saveBuffer.kind = kind;
            saveBuffer.expectedChunks = chunks;
            saveBuffer.receivedChunks = 0;
            saveBuffer.active = true;
        }

        @JavascriptInterface
        public void saveChunk(String chunk) {
            if (saveBuffer != null && saveBuffer.active) {
                saveBuffer.data.append(chunk);
                saveBuffer.receivedChunks++;
            }
        }

        @JavascriptInterface
        public void saveEnd() {
            final SaveBuffer buf = saveBuffer;
            saveBuffer = null;
            if (buf == null || !buf.active) {
                return;
            }
            buf.active = false;
            try {
                final byte[] bytes = Base64.decode(buf.data.toString(), Base64.DEFAULT);
                final int kind = buf.kind;
                final String name = buf.name;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        persist(bytes, name, kind);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        toast("保存失败");
                    }
                });
            }
        }
    }

    private void persist(byte[] bytes, String name, int kind) {
        try {
            boolean isVideo = kind == 1;
            String mime = "image/jpeg";
            String relPath = Environment.DIRECTORY_PICTURES + "/LensKit";
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            if (isVideo) {
                mime = name.endsWith(".webm") ? "video/webm" : "video/mp4";
                relPath = Environment.DIRECTORY_MOVIES + "/LensKit";
                collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, relPath);
            cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(collection, cv);
            if (uri == null) {
                toast("保存失败");
                return;
            }
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(bytes);
            os.flush();
            os.close();
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            toast(isVideo ? "视频已保存到 Movies/LensKit" : "已保存到 相册/LensKit");
        } catch (Exception e) {
            toast("保存失败: " + e.getMessage());
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
