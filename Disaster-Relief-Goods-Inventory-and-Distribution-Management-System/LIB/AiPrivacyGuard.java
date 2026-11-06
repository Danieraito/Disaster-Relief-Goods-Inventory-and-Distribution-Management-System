package LIB;

import java.util.List;
import java.util.regex.Pattern;

/** Conservative guard for personal details in messages sent to a cloud AI provider. */
public final class AiPrivacyGuard {
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?63|0)[\\s()-]*9\\d{2}[\\s()-]*\\d{3}[\\s()-]*\\d{4}(?!\\d)");
    private static final Pattern PERSONAL_IDENTIFIERS = Pattern.compile(
            "(?i)\\b(?:head_name|household_id|member_id|contact_number|phone_number|home_address)\\b");
    private static final Pattern HOUSEHOLD_DETAILS = Pattern.compile(
            "(?i)\\b(?:household|beneficiary|family member|resident)\\b.{0,60}"
                    + "\\b(?:for|named)\\s+[\\p{L}][\\p{L}'-]+(?:\\s+[\\p{L}][\\p{L}'-]+){1,2}");
    private static final Pattern STREET_ADDRESS = Pattern.compile(
            "(?i)\\b\\d{1,5}\\s+[\\p{L}\\d.'-]+(?:\\s+[\\p{L}\\d.'-]+){0,4}\\s+"
                    + "(?:street|st\\.?|road|rd\\.?|avenue|ave\\.?|highway|hwy\\.?)\\b");
    private static final Pattern PERSONAL_FIELD = Pattern.compile(
            "(?i)\\b(?:head|member|resident|contact|address|phone|email)\\s*(?:name)?\\s*[:=]\\s*\\S+");

    private AiPrivacyGuard() {
    }

    public static boolean containsPersonalData(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return EMAIL.matcher(text).find() || PHONE.matcher(text).find()
                || PERSONAL_IDENTIFIERS.matcher(text).find() || HOUSEHOLD_DETAILS.matcher(text).find()
                || STREET_ADDRESS.matcher(text).find() || PERSONAL_FIELD.matcher(text).find();
    }

    static boolean messagesContainPersonalData(List<AiAssistant.Message> messages) {
        if (messages == null) {
            return false;
        }
        for (AiAssistant.Message message : messages) {
            if (message == null) {
                continue;
            }
            if (("user".equals(message.role) || "tool".equals(message.role) || "assistant".equals(message.role))
                    && containsPersonalData(message.content)) {
                return true;
            }
            if (message.toolCalls != null) {
                for (AiAssistant.ToolCall call : message.toolCalls) {
                    if (containsPersonalData(call.arguments)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
