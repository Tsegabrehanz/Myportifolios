package com.eems.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default SmsSender used in this codebase. It does NOT send a real SMS -
 * it logs the message so you can see the OTP code during local
 * development and testing.
 *
 * This exists because wiring a real provider requires an account and
 * live API credentials (Twilio, Vonage, AWS SNS, etc.) that aren't
 * available here. To go live, implement SmsSender against your chosen
 * provider's SDK and either:
 *   - annotate your implementation @Primary so it's picked over this one, or
 *   - delete this class and register only your real implementation.
 *
 * Example shape for a Twilio-backed implementation:
 *
 *   @Component
 *   @Primary
 *   public class TwilioSmsSender implements SmsSender {
 *       private final com.twilio.rest.api.v2010.account.Message.Builder builder; // etc.
 *       public void send(String phoneNumberE164, String message) {
 *           Message.creator(new PhoneNumber(phoneNumberE164), new PhoneNumber(fromNumber), message).create();
 *       }
 *   }
 */
@Component
public class ConsoleSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);

    @Override
    public void send(String phoneNumberE164, String message) {
        log.warn("==========================================================");
        log.warn(" [DEV SMS STAND-IN] No real SMS provider configured.");
        log.warn(" Would send to: {}", phoneNumberE164);
        log.warn(" Message: {}", message);
        log.warn(" See com.eems.sms.SmsSender javadoc to wire up a real provider.");
        log.warn("==========================================================");
    }
}
