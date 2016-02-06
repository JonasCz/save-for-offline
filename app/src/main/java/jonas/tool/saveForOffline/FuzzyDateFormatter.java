/**
 This file is part of Save For Offline, an Android app which saves / downloads complete webpages for offine reading.
 **/
 
/**
 * @author Ionut G. Stan <ionut.g.stan@gmail.com>
 * Modified by JonasCz (Refactored a bit)
 */

package jonas.tool.saveForOffline;

import java.util.Calendar;
import java.text.*;
import java.util.*;

public class FuzzyDateFormatter {

    private final static int SECONDS = 1;
    private final static int MINUTES = 60 * SECONDS;
    private final static int HOURS   = 60 * MINUTES;
    private final static int DAYS    = 24 * HOURS;
    private final static int WEEKS   =  7 * DAYS;
    private final static int MONTHS  =  4 * WEEKS;
    private final static int YEARS   = 12 * MONTHS;

    private final Calendar currentTime;

    public FuzzyDateFormatter(Calendar currentTime) {
        this.currentTime = currentTime;
    }
	
	public String getFuzzy (String dateTime) throws ParseException {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		Calendar cal = Calendar.getInstance();
		cal.setTime(sdf.parse(dateTime));
		return timeAgo(cal);
	}

    public String timeAgo(Calendar before) {
        int beforeSeconds = (int) (before.getTimeInMillis() / 1000);
        int nowSeconds = (int) (currentTime.getTimeInMillis() / 1000);
        int timeDifference = nowSeconds - beforeSeconds;

        int unit;

        if (timeDifference < MINUTES) {
            unit = SECONDS;
        } else if (timeDifference < HOURS) {
            unit = MINUTES;
        } else if (timeDifference < DAYS) {
            unit = HOURS;
        } else if (timeDifference < WEEKS) {
            unit = DAYS;
        } else if (timeDifference < MONTHS) {
            unit = WEEKS;
        } else if (timeDifference < YEARS) {
            unit = MONTHS;
        } else {
            unit = YEARS;
        }

        return callUnit(unit, timeDifference);
    }

    private String callUnit(int unit, int difference) {
        difference = difference / unit;
        
        if (difference == 1) {
            return callSingleUnit(unit);
        } else {
            return callMultiUnit(unit, difference);
        }
    }

    private String callMultiUnit(int unit, int difference) {
        switch (unit) {
            case SECONDS:
                return FuzzyMessages.someSecondsAgo(difference);
            case MINUTES:
                return FuzzyMessages.someMinutesAgo(difference);
            case HOURS:
                return FuzzyMessages.someHoursAgo(difference);
            case DAYS:
                return FuzzyMessages.someDaysAgo(difference);
            case WEEKS:
                return FuzzyMessages.someWeeksAgo(difference);
            case MONTHS:
                return FuzzyMessages.someMonthsAgo(difference);
            case YEARS:
                return FuzzyMessages.someYearsAgo(difference);
            default:
                throw new RuntimeException("Unknown multi unit");
        }
    }

    private String callSingleUnit(int unit) {
        switch (unit) {
            case SECONDS:
                return FuzzyMessages.oneSecondAgo();
            case MINUTES:
                return FuzzyMessages.oneMinuteAgo();
            case HOURS:
                return FuzzyMessages.oneHourAgo();
            case DAYS:
                return FuzzyMessages.oneDayAgo();
            case WEEKS:
                return FuzzyMessages.oneWeekAgo();
            case MONTHS:
                return FuzzyMessages.oneMonthAgo();
            case YEARS:
                return FuzzyMessages.oneYearAgo();
            default:
                throw new RuntimeException("Unknown single unit");
        }
    }
	
	private static class FuzzyMessages {

		public static String oneSecondAgo() {
			return "just now";
		}

		public static String someSecondsAgo(int numberOfSeconds) {
			return "just now";
		}

		public static String oneMinuteAgo() {
			return "just now";
		}

		public static String someMinutesAgo(int numberOfMinutes) {
			if (numberOfMinutes <=  15) {
				return "just now";
			} else {
				return "a few minutes ago";
			}
		}

		public static String oneHourAgo() {
			return "one hour ago";
		}

		public static String someHoursAgo(int numberOfHours) {
			return "today";
		}

		public static String oneDayAgo() {
			return "yesterday";
		}

		public static String someDaysAgo(int numberOfDays) {
			return "this week";
		}

		public static String oneWeekAgo() {
			return " last week";
		}

		public static String someWeeksAgo(int numberOfWeeks) {
			return numberOfWeeks + " weeks ago";
		}

		public static String oneMonthAgo() {
			return " one month ago";
		}

		public static String someMonthsAgo(int numberOfMonths) {
			return numberOfMonths + " months ago";
		}

		public static String oneYearAgo() {
			return "last year";
		}

		public static String someYearsAgo(int numberOfYears) {
			return numberOfYears + " years ago";
		}
	}
}
