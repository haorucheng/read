package com.example.modernbookshelf;

import org.json.JSONException;
import org.json.JSONObject;

final class Book {
    final String id;
    final String title;
    final String path;
    float progress;

    Book(String id, String title, String path, float progress) {
        this.id = id;
        this.title = title;
        this.path = path;
        this.progress = progress;
    }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("id", id);
        value.put("title", title);
        value.put("path", path);
        value.put("progress", progress);
        return value;
    }

    static Book fromJson(JSONObject value) throws JSONException {
        return new Book(value.getString("id"), value.getString("title"), value.getString("path"),
                (float) value.optDouble("progress", 0));
    }
}
