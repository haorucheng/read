package com.example.modernbookshelf;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private TextView scrollText;
    private TextView pageIndicator;
    private ScrollView scrollReader;
    private LinearLayout pageControls;
    private LinearLayout settingsPanel;
    private Button pageModeButton;
    private String content = "";
    private float fontSize = 19;
    private float touchStartX;
    private float touchStartY;
    private int currentPage;
    private boolean verticalPaging;
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

        FrameLayout root = new FrameLayout(this);
        pageFrame = new FrameLayout(this);
        pageText = new TextView(this);
        pageText.setTextColor(0xff202020);
        pageText.setTextSize(fontSize);
        pageText.setLineSpacing(dp(8), 1f);
        pageText.setIncludeFontPadding(false);
        pageText.setGravity(Gravity.TOP | Gravity.START);
        pageText.setPadding(dp(22), dp(18), dp(22), dp(18));
        pageFrame.addView(pageText, new FrameLayout.LayoutParams(-1, -1));
        scrollReader = new ScrollView(this);
        scrollText = new TextView(this);
        scrollText.setTextColor(0xff202020);
        scrollText.setTextSize(fontSize);
        scrollText.setLineSpacing(dp(8), 1f);
        scrollText.setIncludeFontPadding(false);
        scrollText.setGravity(Gravity.TOP | Gravity.START);
        scrollText.setPadding(dp(22), dp(18), dp(22), dp(18));
        scrollText.setOnClickListener(v -> showSettings());
        scrollReader.addView(scrollText, new ScrollView.LayoutParams(-1, -2));
        scrollReader.setVisibility(View.GONE);
        pageFrame.addView(scrollReader, new FrameLayout.LayoutParams(-1, -1));
        root.addView(pageFrame, new FrameLayout.LayoutParams(-1, -1));

        settingsPanel = new LinearLayout(this);
        settingsPanel.setOrientation(LinearLayout.VERTICAL);
        settingsPanel.setPadding(dp(12), dp(8), dp(12), dp(12));
        settingsPanel.setBackgroundColor(0xfaf7f7f7);
        LinearLayout topBar = new LinearLayout(this);
        Button back = button("\u8fd4\u56de");
        Button chapters = button("\u76ee\u5f55");
        Button smaller = button("A\u2212");
        Button larger = button("A+");
        addEqual(topBar, back); addEqual(topBar, chapters); addEqual(topBar, smaller); addEqual(topBar, larger);
        settingsPanel.addView(topBar);

        pageControls = new LinearLayout(this);
        Button previous = button("\u4e0a\u4e00\u9875");
        pageIndicator = new TextView(this);
        pageIndicator.setGravity(Gravity.CENTER);
        Button next = button("\u4e0b\u4e00\u9875");
        pageControls.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        pageControls.addView(pageIndicator, new LinearLayout.LayoutParams(0, dp(48), 1));
        pageControls.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        settingsPanel.addView(pageControls);
        LinearLayout modeBar = new LinearLayout(this);
        pageModeButton = button("阅读：左右翻页");
        Button closeSettings = button("收起设置");
        modeBar.addView(pageModeButton, new LinearLayout.LayoutParams(0, dp(46), 2));
        modeBar.addView(closeSettings, new LinearLayout.LayoutParams(0, dp(46), 1));
        settingsPanel.addView(modeBar);
        settingsPanel.setVisibility(View.GONE);
        root.addView(settingsPanel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        setContentView(root);

        loadBook();
        back.setOnClickListener(v -> finish());
        chapters.setOnClickListener(v -> showChapters());
        smaller.setOnClickListener(v -> changeFont(-1));
        larger.setOnClickListener(v -> changeFont(1));
        previous.setOnClickListener(v -> showPage(currentPage - 1, -1, true));
        next.setOnClickListener(v -> showPage(currentPage + 1, 1, true));
        pageModeButton.setOnClickListener(v -> togglePagingMode());
        closeSettings.setOnClickListener(v -> hideSettings());
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
        // TextView and StaticLayout can differ by a fractional font descent on some devices.
        // Keep one full rendered line as a safety margin so the last line is never clipped.
        int safeHeight = height - (int) Math.ceil(paint.getFontSpacing() + dp(8));
        if (safeHeight <= 0) return;
        // Lay out the complete book only once. Each page then reuses these exact visual
        // lines, avoiding both slow repeated layouts and different wrapping at page starts.
        StaticLayout layout = StaticLayout.Builder.obtain(content, 0, content.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setLineSpacing(dp(8), 1f)
                .build();
        int line = 0;
        int cursor = 0;
        while (line < layout.getLineCount()) {
            int firstLine = line;
            int top = layout.getLineTop(firstLine);
            while (line < layout.getLineCount() && layout.getLineBottom(line) - top <= safeHeight) line++;
            if (line == firstLine) line++;
            // Keep the source cursor authoritative. StaticLayout can omit separator
            // whitespace at a visual line start; using its next line start directly
            // can make content appear to jump at a page boundary.
            int start = cursor;
            int end = layout.getLineEnd(line - 1);
            StringBuilder display = new StringBuilder(end - start + line - firstLine);
            int copiedUntil = start;
            for (int current = firstLine; current < line; current++) {
                int lineStart = Math.max(copiedUntil, layout.getLineStart(current));
                int lineEnd = layout.getLineEnd(current);
                if (copiedUntil < lineStart) display.append(content, copiedUntil, lineStart);
                display.append(content, lineStart, lineEnd);
                copiedUntil = lineEnd;
                if (current < line - 1 && (lineEnd == lineStart || content.charAt(lineEnd - 1) != '\n')) display.append('\n');
            }
            if (copiedUntil < end) display.append(content, copiedUntil, end);
            pages.add(new Page(start, end, display.toString()));
            cursor = end;
        }
    }

    private void showPage(int target, int direction, boolean animate) {
        if (pages.isEmpty() || target < 0 || target >= pages.size()) return;
        Runnable replaceText = () -> {
            currentPage = target;
            Page page = pages.get(target);
            pageText.setText(page.displayText);
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
            if (Math.abs(deltaX) < dp(12) && Math.abs(deltaY) < dp(12)
                    && touchStartX > pageFrame.getWidth() * .25f && touchStartX < pageFrame.getWidth() * .75f
                    && touchStartY > pageFrame.getHeight() * .25f && touchStartY < pageFrame.getHeight() * .75f) {
                showSettings();
                return true;
            }
            if (!verticalPaging && Math.abs(deltaX) >= dp(56) && Math.abs(deltaX) > Math.abs(deltaY)) {
                showPage(currentPage + (deltaX < 0 ? 1 : -1), deltaX < 0 ? 1 : -1, true);
                return true;
            }
        }
        return true;
    }

    private void togglePagingMode() {
        verticalPaging = !verticalPaging;
        pageModeButton.setText(verticalPaging ? "阅读：上下连续滚动" : "阅读：左右翻页");
        if (verticalPaging) {
            scrollText.setText(content);
            pageText.setVisibility(View.GONE);
            scrollReader.setVisibility(View.VISIBLE);
            pageControls.setVisibility(View.GONE);
            scrollReader.post(() -> {
                int range = Math.max(0, scrollText.getHeight() - scrollReader.getHeight());
                scrollReader.scrollTo(0, Math.round(range * book.progress));
            });
        } else {
            scrollReader.setVisibility(View.GONE);
            pageText.setVisibility(View.VISIBLE);
            pageControls.setVisibility(View.VISIBLE);
            showPage(pageForOffset(Math.round(book.progress * Math.max(0, content.length() - 1))), 0, false);
        }
    }

    private void showSettings() {
        if (settingsPanel.getVisibility() == View.VISIBLE) return;
        settingsPanel.setVisibility(View.VISIBLE);
        settingsPanel.setAlpha(0f);
        settingsPanel.setTranslationY(dp(180));
        settingsPanel.animate().alpha(1f).translationY(0).setDuration(180).start();
    }

    private void hideSettings() {
        settingsPanel.animate().alpha(0f).translationY(dp(180)).setDuration(160)
                .withEndAction(() -> settingsPanel.setVisibility(View.GONE)).start();
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
        if (verticalPaging) {
            int range = Math.max(1, scrollText.getHeight() - scrollReader.getHeight());
            book.progress = Math.min(1f, (float) scrollReader.getScrollY() / range);
        } else {
            book.progress = pages.size() <= 1 ? 0 : (float) currentPage / (pages.size() - 1);
        }
        List<Book> all = BookStore.load(this);
        for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(book.id)) all.set(i, book);
        BookStore.save(this, all);
    }

    private static final class Page {
        final int start;
        final int end;
        final String displayText;
        Page(int start, int end, String displayText) { this.start = start; this.end = end; this.displayText = displayText; }
    }
}
