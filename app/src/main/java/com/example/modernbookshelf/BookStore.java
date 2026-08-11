package com.example.modernbookshelf;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class BookStore {
    private static final String PREFS = "bookshelf";
    private static final String BOOKS = "books";

    private BookStore() { }

    static List<Book> load(Context context) {
        List<Book> result = new ArrayList<>();
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(BOOKS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) result.add(Book.fromJson(array.getJSONObject(i)));
        } catch (Exception ignored) { }
        return result;
    }

    static void save(Context context, List<Book> books) {
        JSONArray array = new JSONArray();
        try {
            for (Book book : books) array.put(book.toJson());
        } catch (Exception ignored) { }
        SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        editor.putString(BOOKS, array.toString()).apply();
    }
}
