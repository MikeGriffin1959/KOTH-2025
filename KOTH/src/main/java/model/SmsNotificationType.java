package model;

/**
 * SMS notification types for KOTH (ported from GolferFest, adapted to
 * survivor-pool events). type_key values in sms_notification_types MUST
 * match these enum names.
 *
 * USER category: the user can opt in/out per type on the Update User Info page.
 * COMMISSIONER category: always sent if the phone is verified (no opt-out).
 */
public enum SmsNotificationType {

    // ── User-controlled ────────────────────────────────────────
    PICKS_REMINDER("Picks Reminder", Category.USER),
    ELIMINATION_ALERT("Elimination Alert", Category.USER),
    WEEK_RECAP("Week Recap", Category.USER),
    COMMENTARY_EVENT("Live Commentary", Category.USER),

    // ── Commissioner-controlled ────────────────────────────────
    COMMISSIONER_MESSAGE("Commissioner Message", Category.COMMISSIONER),
    ACCOUNT_ALERT("Account Alert", Category.COMMISSIONER);

    public enum Category { USER, COMMISSIONER }

    private final String displayName;
    private final Category category;

    SmsNotificationType(String displayName, Category category) {
        this.displayName = displayName;
        this.category = category;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Category getCategory() {
        return category;
    }

    public boolean isUserControlled() {
        return category == Category.USER;
    }

    public boolean isCommissionerControlled() {
        return category == Category.COMMISSIONER;
    }
}
