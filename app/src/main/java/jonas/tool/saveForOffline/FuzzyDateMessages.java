package jonas.tool.saveForOffline;

/**
 * @author Ionut G. Stan <ionut.g.stan@gmail.com>
 */

public class FuzzyDateMessages {

    public String oneSecondAgo() {
        return "just now";
    }

    public String someSecondsAgo(int numberOfSeconds) {
        return "just now";
    }

    public String oneMinuteAgo() {
        return "just now";
    }

    public String someMinutesAgo(int numberOfMinutes) {
		if (numberOfMinutes <=  15) {
			return "just now";
		} else {
        	return "a few minutes ago";
		}
    }

    public String oneHourAgo() {
        return "one hour ago";
    }

    public String someHoursAgo(int numberOfHours) {
        return "today";
    }

    public String oneDayAgo() {
        return "yesterday";
    }

    public String someDaysAgo(int numberOfDays) {
        return "this week";
    }

    public String oneWeekAgo() {
        return " last week";
    }

    public String someWeeksAgo(int numberOfWeeks) {
        return numberOfWeeks + " weeks ago";
    }

    public String oneMonthAgo() {
        return " one month ago";
    }

    public String someMonthsAgo(int numberOfMonths) {
        return numberOfMonths + " months ago";
    }

    public String oneYearAgo() {
        return "last year";
    }

    public String someYearsAgo(int numberOfYears) {
        return numberOfYears + " years ago";
    }
}
