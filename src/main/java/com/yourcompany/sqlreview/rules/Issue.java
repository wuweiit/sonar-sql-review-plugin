package com.yourcompany.sqlreview.rules;

/**
 * 单条审查问题
 *
 * @author marker
 */
public class Issue {
    private final String ruleId;
    private final String severity;
    private final String message;
    private final String suggestion;

    public Issue(String ruleId, String severity, String message, String suggestion) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.suggestion = suggestion;
    }

    public String getRuleId() { return ruleId; }
    public String getSeverity() { return severity; }
    public String getMessage() { return message; }
    public String getSuggestion() { return suggestion; }
}
