package org.example.where.gae;

public class WhereLocation {
    public static final String UNKNOWN = "unknown";

    private String activity = UNKNOWN;
    private String location = UNKNOWN;

    public WhereLocation() {
    }

    public WhereLocation(String activity, String location) {
        this.activity = activity;
        this.location = location;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        WhereLocation that = (WhereLocation) o;

        if (!activity.equals(that.activity)) return false;
        if (!location.equals(that.location)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = activity.hashCode();
        result = 31 * result + location.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "activity='" + activity + '\'' +
                ", location='" + location + '\'';
    }
}
