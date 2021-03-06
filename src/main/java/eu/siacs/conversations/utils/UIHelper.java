package eu.siacs.conversations.utils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import eu.siacs.conversations.R;
import eu.siacs.conversations.entities.Account;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.ui.ConversationActivity;
import eu.siacs.conversations.ui.ManageAccountActivity;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class UIHelper {
	private static final int SHORT_DATE_FLAGS = DateUtils.FORMAT_SHOW_DATE
			| DateUtils.FORMAT_NO_YEAR | DateUtils.FORMAT_ABBREV_ALL;
	private static final int FULL_DATE_FLAGS = DateUtils.FORMAT_SHOW_TIME
			| DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

	public static String readableTimeDifference(Context context, long time) {
		return readableTimeDifference(context, time, false);
	}

	public static String readableTimeDifferenceFull(Context context, long time) {
		return readableTimeDifference(context, time, true);
	}

	private static String readableTimeDifference(Context context, long time,
			boolean fullDate) {
		if (time == 0) {
			return context.getString(R.string.just_now);
		}
		Date date = new Date(time);
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return context.getString(R.string.just_now);
		} else if (difference < 60 * 2) {
			return context.getString(R.string.minute_ago);
		} else if (difference < 60 * 15) {
			return context.getString(R.string.minutes_ago,
					Math.round(difference / 60.0));
		} else if (today(date)) {
			java.text.DateFormat df = DateFormat.getTimeFormat(context);
			return df.format(date);
		} else {
			if (fullDate) {
				return DateUtils.formatDateTime(context, date.getTime(),
						FULL_DATE_FLAGS);
			} else {
				return DateUtils.formatDateTime(context, date.getTime(),
						SHORT_DATE_FLAGS);
			}
		}
	}

	private static boolean today(Date date) {
		Calendar cal1 = Calendar.getInstance();
		Calendar cal2 = Calendar.getInstance();
		cal1.setTime(date);
		cal2.setTimeInMillis(System.currentTimeMillis());
		return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)
				&& cal1.get(Calendar.DAY_OF_YEAR) == cal2
						.get(Calendar.DAY_OF_YEAR);
	}

	public static String lastseen(Context context, long time) {
		if (time == 0) {
			return context.getString(R.string.never_seen);
		}
		long difference = (System.currentTimeMillis() - time) / 1000;
		if (difference < 60) {
			return context.getString(R.string.last_seen_now);
		} else if (difference < 60 * 2) {
			return context.getString(R.string.last_seen_min);
		} else if (difference < 60 * 60) {
			return context.getString(R.string.last_seen_mins,
					Math.round(difference / 60.0));
		} else if (difference < 60 * 60 * 2) {
			return context.getString(R.string.last_seen_hour);
		} else if (difference < 60 * 60 * 24) {
			return context.getString(R.string.last_seen_hours,
					Math.round(difference / (60.0 * 60.0)));
		} else if (difference < 60 * 60 * 48) {
			return context.getString(R.string.last_seen_day);
		} else {
			return context.getString(R.string.last_seen_days,
					Math.round(difference / (60.0 * 60.0 * 24.0)));
		}
	}

	public static String getReadableTimeout(Context context, long timeout) {
		long diff = (timeout - System.currentTimeMillis()) / 1000;
		if (diff > 60 * 60 * 24 * 29) {
			return context.getString(R.string.one_month);
		} else if (diff > 60 * 60 * 24 * 2) {
			return context.getString(R.string.days,Math.round(diff /(60 * 60 * 24.0)));
		} else if (diff > 60 * 60 * 23) {
			return context.getString(R.string.one_day);
		} else if (diff > 60 * 60 * 2) {
			return context.getString(R.string.hours,Math.round(diff / (60 * 60.0)));
		} else if (diff > 60 * 59) {
			return context.getString(R.string.one_hour);
		} else if (diff > 60 * 2) {
			return context.getString(R.string.minutes,Math.round(diff / 60.0));
		} else if (diff > 0) {
			return context.getString(R.string.one_minute);
		} else {
			return context.getString(R.string.destroyed);
		}
	}

	private final static class EmoticonPattern {
		Pattern pattern;
		String replacement;

		EmoticonPattern(String ascii, int unicode) {
			this.pattern = Pattern.compile("(?<=(^|\\s))" + ascii
					+ "(?=(\\s|$))");
			this.replacement = new String(new int[] { unicode, }, 0, 1);
		}

		String replaceAll(String body) {
			return pattern.matcher(body).replaceAll(replacement);
		}
	}

	private static final EmoticonPattern[] patterns = new EmoticonPattern[] {
			new EmoticonPattern(":-?D", 0x1f600),
			new EmoticonPattern("\\^\\^", 0x1f601),
			new EmoticonPattern(":'D", 0x1f602),
			new EmoticonPattern("\\]-?D", 0x1f608),
			new EmoticonPattern(";-?\\)", 0x1f609),
			new EmoticonPattern(":-?\\)", 0x1f60a),
			new EmoticonPattern("[B8]-?\\)", 0x1f60e),
			new EmoticonPattern(":-?\\|", 0x1f610),
			new EmoticonPattern(":-?[/\\\\]", 0x1f615),
			new EmoticonPattern(":-?\\*", 0x1f617),
			new EmoticonPattern(":-?[Ppb]", 0x1f61b),
			new EmoticonPattern(":-?\\(", 0x1f61e),
			new EmoticonPattern(":-?[0Oo]", 0x1f62e),
			new EmoticonPattern("\\\\o/", 0x1F631), };

	public static String transformAsciiEmoticons(String body) {
		if (body != null) {
			for (EmoticonPattern p : patterns) {
				body = p.replaceAll(body);
			}
			body = body.trim();
		}
		return body;
	}

	public static int getColorForName(String name) {
		int colors[] = {0xFFe91e63, 0xFF9c27b0, 0xFF673ab7, 0xFF3f51b5,
				0xFF5677fc, 0xFF03a9f4, 0xFF00bcd4, 0xFF009688, 0xFFff5722,
				0xFF795548, 0xFF607d8b,
                0xFF7CB860, 0xFFD4DB42, 0xFFDB5E42, 0xFF4942DB, 0xFF428FDB, 0xFF2B12B8, 0xFFA7B812, 0xFFB81212, 0xFF8A5383, 0xFFEB9B10, 0xFF23D9D9,
                0xFF80E843, 0xFFC91E1E
        };
		return colors[(int) ((name.hashCode() & 0xffffffffl) % colors.length)];
	}
}
