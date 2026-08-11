package com.example.modernbookshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReaderActivity extends Activity {
    private Book book;
    private TextView text;
    private ScrollView scroll;
    private float fontSize = 19;
    private final List<Integer> chapterOffsets = new ArrayList<>();
    private final List<String> chapterTitles = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("book_id");
        for (Book candidate : BookStore.load(this)) if (candidate.id.equals(id)) { book = candidate; break; }
        if (book == null) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout bar = new LinearLayout(this);
        Button back = button("返回"); Button chapters = button("目录");
        Button smaller = button("A−"); Button larger = button("A+");
        bar.addView(back, new LinearLayout.LayoutParams(0, dp(46), 1));
        bar.addView(chapters, new LinearLayout.LayoutParams(0, dp(46), 1));
        bar.addView(smaller, new LinearLayout.LayoutParams(0, dp(46), 1));
        bar.addView(larger, new LinearLayout.LayoutParams(0, dp(46), 1));
        root.addView(bar);

        scroll = new ScrollView(this);
        text = new TextView(this);
        text.setTextColor(0xff202020); text.setTextSize(fontSize); text.setLineSpacing(dp(8), 1f);
        text.setPadding(dp(10), dp(10), dp(10), dp(24));
        scroll.addView(text);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout pageBar = new LinearLayout(this);
        Button previousPage = button("上一页");
        Button nextPage = button("下一页");
        pageBar.addView(previousPage, new LinearLayout.LayoutParams(0, dp(48), 1));
        pageBar.addView(nextPage, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(pageBar);
        setContentView(root);

        try {
            String content;
            try (FileInputStream input = new FileInputStream(new File(book.path))) {
                content = new String(readAll(input), StandardCharsets.UTF_8);
            }
            text.setText(content); indexChapters(content);
        } catch (Exception error) {
            text.setText("无法打开该文件：\n" + error.getMessage());
        }
        scroll.post(() -> scrollToProgress(book.progress));
        back.setOnClickListener(v -> finish());
        smaller.setOnClickListener(v -> resize(-1));
        larger.setOnClickListener(v -> resize(1));
        chapters.setOnClickListener(v -> showChapters());
        previousPage.setOnClickListener(v -> turnPage(-1));
        nextPage.setOnClickListener(v -> turnPage(1));
    }

    private Button button(String label) { Button button = new Button(this); button.setText(label); return button; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }
    private void resize(int difference) { fontSize = Math.max(13, Math.min(34, fontSize + difference)); text.setTextSize(fontSize); }

    private void indexChapters(String content) {
        Matcher matcher = Pattern.compile("(?m)^\\s*(第.{1,30}[章节卷回部集篇].*)$").matcher(content);
        while (matcher.find()) { chapterOffsets.add(matcher.start()); chapterTitles.add(matcher.group(1).trim()); }
    }

    private void showChapters() {
        if (chapterTitles.isEmpty()) { Toast.makeText(this, "未识别到章节标题", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("目录")
                .setItems(chapterTitles.toArray(new String[0]), (dialog, which) -> scrollToCharacter(chapterOffsets.get(which)))
                .setNegativeButton("关闭", null).show();
    }

    private void scrollToCharacter(int offset) {
        CharSequence body = text.getText();
        float ratio = body.length() == 0 ? 0 : (float) offset / body.length();
        scroll.post(() -> scrollToProgress(ratio));
    }

    private void scrollToProgress(float progress) {
        int range = Math.max(0, text.getHeight() - scroll.getHeight());
        scroll.scrollTo(0, Math.round(range * progress));
    }

    private void turnPage(int direction) {
        int pageHeight = Math.max(1, scroll.getHeight());
        int range = Math.max(0, text.getHeight() - scroll.getHeight());
        int target = Math.max(0, Math.min(range, scroll.getScrollY() + direction * pageHeight));
        scroll.smoothScrollTo(0, target);
    }

    private static byte[] readAll(FileInputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    @Override protected void onPause() {
        super.onPause();
        int range = Math.max(1, text.getHeight() - scroll.getHeight());
        book.progress = Math.max(0, Math.min(1, (float) scroll.getScrollY() / range));
        List<Book> all = BookStore.load(this);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(book.id)) all.set(i, book);
        BookStore.save(this, all);
    }
}
