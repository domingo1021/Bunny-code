package com.bunnycode.storage;

/**
 * Object storage for user files (saved code, battle winner code).
 *
 * <p>Keys are absolute paths such as {@code /record/user_1/project_2/version_3/1700000000000-main.js}
 * — the same format v1 stored in the database, so existing rows stay valid. Objects are served
 * publicly through the CDN at {@link #publicUrl(String)}.
 */
public interface FileStorage {

    void store(String key, byte[] content, String contentType);

    String publicUrl(String key);
}
