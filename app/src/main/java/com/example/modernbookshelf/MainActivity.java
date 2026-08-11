package com.example.modernbookshelf;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int PICK_TEXT = 101;
    private static final String UPDATE_MANIFEST = "https://raw.githubusercontent.com/haorucheng/read/main/update.json";
    private final List<Book> books = new ArrayList<>();
    private ArrayAdapter<String> bookshelfAdapter;
    private EditText query;
    private ListView list;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        books.addAll(BookStore.load(this));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText("轻阅书架"); title.setTextSize(26); title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout actions = new LinearLayout(this);
        Button importButton = button("导入 TXT");
        Button searchButton = button("网络搜索");
        Button updateButton = button("\u68c0\u67e5\u66f4\u65b0");
        actions.addView(importButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        actions.addView(searchButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        actions.addView(updateButton, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(actions);

        query = new EditText(this);
        query.setHint("输入书名，搜索公开图书目录");
        root.addView(query, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView hint = new TextView(this);
        hint.setText("我的书架（轻点阅读，长按删除）"); hint.setTextSize(14);
        root.addView(hint, new LinearLayout.LayoutParams(-1, dp(36)));
        list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        refreshBookshelf();
        importButton.setOnClickListener(v -> pickTextFile());
        searchButton.setOnClickListener(v -> searchOnline(query.getText().toString().trim()));
        updateButton.setOnClickListener(v -> checkForUpdates());
        list.setOnItemClickListener((parent, v, pos, id) -> openBook(books.get(pos)));
        list.setOnItemLongClickListener((parent, v, pos, id) -> {
            Book selected = books.get(pos);
            confirmDelete(selected);
            return true;
        });
    }

    private Button button(String text) { Button b = new Button(this); b.setText(text); return b; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    private void refreshBookshelf() {
        List<String> labels = new ArrayList<>();
        for (Book book : books) labels.add(book.title + "\n已读 " + Math.round(book.progress * 100) + "%");
        bookshelfAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        list.setAdapter(bookshelfAdapter);
    }

    private void pickTextFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(intent, PICK_TEXT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_TEXT || resultCode != RESULT_OK || data == null) return;
        List<Uri> selectedFiles = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                selectedFiles.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            selectedFiles.add(data.getData());
        }
        if (selectedFiles.isEmpty()) return;
        int imported = 0;
        for (Uri uri : selectedFiles) {
            if (importFile(uri)) imported++;
        }
        if (imported > 0) {
            BookStore.save(this, books);
            refreshBookshelf();
            Toast.makeText(this, "已导入 " + imported + " 本书", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean importFile(Uri uri) {
        try {
            String title = fileName(uri);
            File dir = new File(getFilesDir(), "books");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("无法创建书籍目录");
            File target = new File(dir, UUID.randomUUID() + ".txt");
            try (InputStream input = getContentResolver().openInputStream(uri);
                 FileOutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[8192]; int count;
                while (input != null && (count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            books.add(new Book(UUID.randomUUID().toString(), title, target.getAbsolutePath(), 0));
            return true;
        } catch (Exception error) {
            Toast.makeText(this, "导入失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void confirmDelete(Book selected) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("删除书籍")
                .setMessage("确定从书架删除《" + selected.title + "》吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    books.remove(selected);
                    new File(selected.path).delete();
                    BookStore.save(this, books);
                    refreshBookshelf();
                })
                .show();
    }

    private String fileName(Uri uri) {
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index).replaceFirst("\\.[^.]+$", "");
            }
        }
        return "未命名书籍";
    }

    private void openBook(Book book) {
        Intent intent = new Intent(this, ReaderActivity.class);
        intent.putExtra("book_id", book.id); startActivity(intent);
    }

    private void searchOnline(String text) {
        if (text.isEmpty()) { query.setError("请输入书名"); return; }
        Toast.makeText(this, "正在搜索…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(text, StandardCharsets.UTF_8.name());
                HttpURLConnection connection = (HttpURLConnection) new URL("https://openlibrary.org/search.json?title=" + encoded + "&limit=15&fields=title,author_name,first_publish_year,key,ebook_access").openConnection();
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "ModernBookshelf/1.0 (Android)");
                connection.setConnectTimeout(10_000); connection.setReadTimeout(10_000);
                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("HTTP " + responseCode);
                }
                InputStream in = connection.getInputStream();
                String raw;
                try (InputStream response = in) {
                    raw = new String(readAll(response), StandardCharsets.UTF_8);
                }
                JSONArray docs = new JSONObject(raw).getJSONArray("docs");
                List<SearchResult> results = new ArrayList<>();
                for (int i = 0; i < docs.length(); i++) {
                    JSONObject item = docs.getJSONObject(i);
                    String author = item.optJSONArray("author_name") == null ? "未知作者" : item.optJSONArray("author_name").optString(0, "未知作者");
                    String key = item.optString("key", "");
                    String access = item.optString("ebook_access", "");
                    String availability = "public".equals(access) ? "可查看公开版本" : "打开图书详情/借阅页";
                    results.add(new SearchResult(item.optString("title", "未命名"), author,
                            item.optInt("first_publish_year", 0), key, availability));
                }
                runOnUiThread(() -> showSearchResults(results));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "网络搜索失败：" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showSearchResults(List<SearchResult> results) {
        if (results.isEmpty()) { Toast.makeText(this, "没有匹配的公开目录记录", Toast.LENGTH_SHORT).show(); return; }
        String[] labels = new String[results.size()];
        for (int i = 0; i < results.size(); i++) labels[i] = results.get(i).label();
        new android.app.AlertDialog.Builder(this).setTitle("公开图书目录搜索结果")
                .setItems(labels, (dialog, which) -> openSearchResult(results.get(which)))
                .setPositiveButton("关闭", null).show();
    }

    private void openSearchResult(SearchResult result) {
        if (result.key.isEmpty()) {
            Toast.makeText(this, "此条记录没有可打开的详情链接", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://openlibrary.org" + result.key)));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开浏览器：" + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static final class SearchResult {
        final String title;
        final String author;
        final int year;
        final String key;
        final String availability;

        SearchResult(String title, String author, int year, String key, String availability) {
            this.title = title; this.author = author; this.year = year; this.key = key; this.availability = availability;
        }

        String label() { return title + "\n" + author + "  ·  " + year + "\n" + availability; }
    }

    private static byte[] readAll(InputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private void checkForUpdates() {
        Toast.makeText(this, "\u6b63\u5728\u68c0\u67e5\u66f4\u65b0\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(UPDATE_MANIFEST).openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                JSONObject value;
                try (InputStream input = connection.getInputStream()) {
                    value = new JSONObject(new String(readAll(input), StandardCharsets.UTF_8));
                }
                int remoteCode = value.getInt("versionCode");
                int localCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (remoteCode <= localCode) {
                    runOnUiThread(() -> Toast.makeText(this, "\u5df2\u662f\u6700\u65b0\u7248\u672c", Toast.LENGTH_SHORT).show());
                    return;
                }
                String apkUrl = value.getString("apkUrl");
                String checksum = value.getString("sha256");
                if (apkUrl.isEmpty() || checksum.isEmpty()) throw new IllegalStateException("\u66f4\u65b0\u5305\u5c1a\u672a\u53d1\u5e03");
                UpdateInfo update = new UpdateInfo(value.getString("versionName"), value.optString("notes", ""), apkUrl, checksum);
                runOnUiThread(() -> showUpdateDialog(update));
            } catch (Exception error) {
                runOnUiThread(() -> Toast.makeText(this, "\u68c0\u67e5\u66f4\u65b0\u5931\u8d25\uff1a" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void showUpdateDialog(UpdateInfo update) {
        new android.app.AlertDialog.Builder(this).setTitle("\u53d1\u73b0\u65b0\u7248\u672c " + update.versionName)
                .setMessage(update.notes).setNegativeButton("\u6682\u4e0d\u66f4\u65b0", null)
                .setPositiveButton("\u7acb\u5373\u66f4\u65b0", (dialog, which) -> downloadUpdate(update)).show();
    }

    private void downloadUpdate(UpdateInfo update) {
        Toast.makeText(this, "\u6b63\u5728\u4e0b\u8f7d\u66f4\u65b0\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File target = new File(getCacheDir(), "update.apk");
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(update.apkUrl).openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(target)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                    }
                }
                if (!toHex(digest.digest()).equalsIgnoreCase(update.sha256.replace(" ", ""))) {
                    throw new SecurityException("\u66f4\u65b0\u5305\u6821\u9a8c\u5931\u8d25");
                }
                runOnUiThread(this::installUpdate);
            } catch (Exception error) {
                target.delete();
                runOnUiThread(() -> Toast.makeText(this, "\u66f4\u65b0\u4e0b\u8f7d\u5931\u8d25\uff1a" + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void installUpdate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "\u8bf7\u5141\u8bb8\u672c\u5e94\u7528\u5b89\u88c5\u672a\u77e5\u6765\u6e90\u66f4\u65b0\uff0c\u518d\u6b21\u70b9\u51fb\u68c0\u67e5\u66f4\u65b0", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            return;
        }
        Intent installer = new Intent(Intent.ACTION_VIEW);
        installer.setDataAndType(UpdateFileProvider.apkUri(), "application/vnd.android.package-archive");
        installer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(installer);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder output = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) output.append(String.format("%02x", value));
        return output.toString();
    }

    private static final class UpdateInfo {
        final String versionName, notes, apkUrl, sha256;
        UpdateInfo(String versionName, String notes, String apkUrl, String sha256) {
            this.versionName = versionName;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.sha256 = sha256;
        }
    }

    @Override protected void onResume() { super.onResume(); if (!books.isEmpty()) { books.clear(); books.addAll(BookStore.load(this)); refreshBookshelf(); } }
}
