package com.tengames.push;

/**
 * What a player sees on their lock screen.
 *
 * @param url  where tapping it should land, relative to the app
 * @param tag  notifications sharing a tag replace one another, so a device
 *             that was offline shows the latest state and not a pile of
 *             stale reminders
 */
public record PushMessage(String title, String body, String url, String tag) {
}
