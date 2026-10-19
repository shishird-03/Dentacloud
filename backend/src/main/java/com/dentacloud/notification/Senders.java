package com.dentacloud.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Delivery channels. Each has a logging fallback so the system runs end to end without provider
 * accounts; the real senders switch on when their credentials are configured (.env.example).
 * Message content is a template key plus non-clinical variables (clinic name, time), never PHI.
 */
final class Senders {
    private Senders() {}

    /** Provider reference on success; throws on a failure worth retrying. */
    interface SmsSender {
        String send(String phone, String templateKey, Map<String, String> vars) throws Exception;
    }

    interface PushSender {
        /** Data-only message to staff devices; returns how many were accepted. */
        int ping(List<String> fcmTokens, Map<String, String> data) throws Exception;
    }

    static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "****" : "****" + phone.substring(phone.length() - 4);
    }

    static class LoggingSms implements SmsSender {
        private static final Logger log = LoggerFactory.getLogger(LoggingSms.class);

        @Override
        public String send(String phone, String templateKey, Map<String, String> vars) {
            log.info("SMS (logging sender) template={} to={}", templateKey, mask(phone));
            return "log-" + System.nanoTime();
        }
    }

    static class LoggingPush implements PushSender {
        private static final Logger log = LoggerFactory.getLogger(LoggingPush.class);

        @Override
        public int ping(List<String> fcmTokens, Map<String, String> data) {
            log.info("Push (logging sender) to {} device(s), type={}", fcmTokens.size(), data.get("type"));
            return fcmTokens.size();
        }
    }
}
