package com.example.modernbookshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A true page-based TXT reader: one layout-sized page is shown at a time. */
public class ReaderActivity extends Activity {
    private Book book;
    private FrameLayout pageFrame;
    private TextView pageText;
    private TextView pageIndicator;
    private String content = "";
    private float fontSize = 19;
    private float touchStartX;
    private float touchStartY;
    private int currentPage;
    private final List<Page> pages = new ArrayList<>();
    private final List<Integer> chapterOffsets = new ArrayList<>();
    private final List<String> chapterTitles = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("book_id");
        for (Book candidate : BookStore.load(this)) {
            if (candidate.id.equals(id)) { book = candidate; break; }
        }
        if (book == null) { finish(); return; }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout topBar = new LinearLayout(this);
        Button back = button("\u8fd4\u56de");
        Button chapters = button("\u76ee\u5f55");
        Button smaller = button("A\u2212");
        Button larger = button("A+");
        addEqual(topBar, back); addEqual(topBar, chapters); addEqual(topBar, smaller); addEqual(topBar, larger);
        root.addView(topBar);

        pageFrame = new FrameLayout(this);
        pageText = new TextView(this);
        pageText.setTextColor(0xff202020);
        pageText.setTextSize(fontSize);
        pageText.setLineSpacing(dp(8), 1f);
        pageText.setIncludeFontPadding(false);
        pageText.setGravity(Gravity.TOP | Gravity.START);
        pageText.setPadding(dp(10), dp(10), dp(10), dp(10));
        pageFrame.addView(pageText, new FrameLayout.LayoutParams(-1, -1));
        root.addView(pageFrame, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bottomBar = new LinearLayout(this);
        Button previous = button("\u4e0a\u4e00\u9875");
        pageIndicator = new TextView(this);
        pageIndicator.setGravity(Gravity.CENTER);
        Button next = button("\u4e0b\u4e00\u9875");
        bottomBar.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomBar.addView(pageIndicator, new LinearLayout.LayoutParams(0, dp(48), 1));
        bottomBar.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(bottomBar);
        setContentView(root);

        loadBook();
        back.setOnClickListener(v -> finish());
        chapters.setOnClickListener(v -> showChapters());
        smaller.setOnClickListener(v -> changeFont(-1));
        larger.setOnClickListener(v -> changeFont(1));
        previous.setOnClickListener(v -> showPage(currentPage - 1, -1, true));
        next.setOnClickListener(v -> showPage(currentPage + 1, 1, true));
        pageFrame.setOnTouchListener((view, event) -> handlePageSwipe(event));
        pageFrame.post(this::paginateFromSavedProgress);
        pageFrame.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) && !pages.isEmpty()) {
                int character = pages.get(Math.min(currentPage, pages.size() - 1)).start;
                view.post(() -> {
                    paginate();
                    showPage(pageForOffset(character), 0, false);
                });
            }
        });
    }

    private void loadBook() {
        try (FileInputStream input = new FileInputStream(new File(book.path))) {
            content = new String(readAll(input), StandardCharsets.UTF_8);
            indexChapters(content);
        } catch (Exception error) {
            content = "\u65e0\u6cd5\u6253\u5f00\u8be5\u6587\u4ef6\uff1a\n" + error.getMessage();
        }
    }

    private void paginateFromSavedProgress() {
        paginate();
        int target = pages.size() <= 1 ? 0 : Math.round(book.progress * (pages.size() - 1));
        showPage(target, 0, false);
    }

    private void paginate() {
        pages.clear();
        int width = pageFrame.getWidth() - pageText.getPaddingLeft() - pageText.getPaddingRight();
        int height = pageFrame.getHeight() - pageText.getPaddingTop() - pageText.getPaddingBottom();
        if (width <= 0 || height <= 0 || content.isEmpty()) return;
        TextPaint paint = new TextPaint(pageText.getPaint());
        StaticLayout layout = StaticLayout.Builder.obtain(content, 0, content.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(dp(8), 1f)
                .build();
        int line = 0;
        while (line < layout.getLineCount()) {
            int firstLine = line;
            int top = layout.getLineTop(firstLine);
            while (line < layout.getLineCount() && layout.getLineBottom(line) - top <= height) line++;
            if (line == firstLine) line++;
            int start = layout.getLineStart(firstLine);
            int end = layout.getLineEnd(line - 1);
            pages.add(new Page(start, end));
        }
    }

    private void showPage(int target, int direction, boolean animate) {
        if (pages.isEmpty() || target < 0 || target >= pages.size()) return;
        Runnable replaceText = () -> {
            currentPage = target;
            Page page = pages.get(target);
            pageText.setText(content.substring(page.start, page.end));
            pageIndicator.setText((target + 1) + " / " + pages.size());
        };
        if (!animate || direction == 0) {
            replaceText.run();
            return;
        }
        float distance = pageFrame.getWidth();
        pageText.animate().translationX(direction > 0 ? -distance : distance).alpha(0f).setDuration(140)
                .withEndAction(() -> {
                    replaceText.run();
                    pageText.setTranslationX(direction > 0 ? distance : -distance);
                    pageText.animate().translationX(0).alpha(1f).setDuration(180).start();
                }).start();
    }

    private boolean handlePageSwipe(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchStartX = event.getX();
            touchStartY = event.getY();
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            float deltaX = event.getX() - touchStartX;
            float deltaY = event.getY() - touchStartY;
            if (Math.abs(deltaX) >= dp(56) && Math.abs(deltaX) > Math.abs(deltaY)) {
                showPage(currentPage + (deltaX < 0 ? 1 : -1), deltaX < 0 ? 1 : -1, true);
                return true;
            }
        }
        return true;
    }

    private void changeFont(int delta) {
        if (pages.isEmpty()) return;
        int character = pages.get(currentPage).start;
        fontSize = Math.max(13, Math.min(34, fontSize + delta));
        pageText.setTextSize(fontSize);
        pageFrame.post(() -> {
            paginate();
            showPage(pageForOffset(character), 0, false);
        });
    }

    private void indexChapters(String body) {
        Matcher matcher = Pattern.compile("(?m)^\\s*(\u7b2c.{1,30}[\u7ae0\u8282\u5377\u56de\u90e8\u96c6\u7bc7].*)$").matcher(body);
        while (matcher.find()) {
            chapterOffsets.add(matcher.start());
            chapterTitles.add(matcher.group(1).trim());
        }
    }

    private void showChapters() {
        if (chapterTitles.isEmpty()) {
            Toast.makeText(this, "\u672a\u8bc6\u522b\u5230\u7ae0\u8282\u6807\u9898", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("\u76ee\u5f55")
                .setItems(chapterTitles.toArray(new String[0]), (dialog, which) -> showPage(pageForOffset(chapterOffsets.get(which)), 0, false))
                .setNegativeButton("\u5173\u95ed", null).show();
    }

    private int pageForOffset(int offset) {
        for (int i = 0; i < pages.size(); i++) if (offset < pages.get(i).end) return i;
        return Math.max(0, pages.size() - 1);
    }

    private void addEqual(LinearLayout row, Button button) {
        row.addView(button, new LinearLayout.LayoutParams(0, dp(46), 1));
    }

    private Button button(String label) { Button button = new Button(this); button.setText(label); return button; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density); }

    private static byte[] readAll(FileInputStream input) throws java.io.IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    @Override protected void onPause() {
        super.onPause();
        book.progress = pages.size() <= 1 ? 0 : (float) currentPage / (pages.size() - 1);
        List<Book> all = BookStore.load(this);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(book.id)) all.set(i, book);
        BookStore.save(this, all);
    }

    private static final class Page {
        final int start;
        final int end;
        Page(int start, int end) { this.start = start; this.end = end; }
    }
}
