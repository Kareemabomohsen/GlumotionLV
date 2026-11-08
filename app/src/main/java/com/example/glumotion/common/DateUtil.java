package com.example.glumotion.common;

import java.util.Calendar;

/**
 * Small helper class for dates and times.
 * (Written without String.format on purpose - simple Java only)
 */
public class DateUtil {

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private static final String[] DAYS = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};

    // Adds a leading zero: 5 → "05"
    public static String twoDigits(int number) {
        if (number < 10) {
            return "0" + number;
        }
        return String.valueOf(number);
    }

    // Returns a key for Firebase like "2026-11-02"
    public static String dateKey(Calendar cal) {
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH) + 1;
        int day = cal.get(Calendar.DAY_OF_MONTH);
        return year + "-" + twoDigits(month) + "-" + twoDigits(day);
    }

    // Today's key
    public static String todayKey() {
        return dateKey(Calendar.getInstance());
    }

    // Returns a nice label like "Nov 2"
    public static String shortDate(Calendar cal) {
        return MONTHS[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.DAY_OF_MONTH);
    }

    // Returns a short day name like "Mon"
    public static String dayName(Calendar cal) {
        return DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1];
    }

    // Converts 24h time to "08:30 AM"
    public static String formatTime12(int hour, int minute) {
        String amPm = "AM";
        if (hour >= 12) {
            amPm = "PM";
        }
        int displayHour = hour % 12;
        if (displayHour == 0) {
            displayHour = 12;
        }
        return twoDigits(displayHour) + ":" + twoDigits(minute) + " " + amPm;
    }

    // Converts seconds to "1:30"
    public static String formatSeconds(int totalSeconds) {
        if (totalSeconds < 0) {
            totalSeconds = 0;
        }
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return minutes + ":" + twoDigits(seconds);
    }

    // Returns true if both calendars are on the same day
    public static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }
}
