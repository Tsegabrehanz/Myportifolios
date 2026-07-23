package com.eems.sms;

/**
 * Abstraction over whatever SMS provider actually sends the message.
 * Swap in a real implementation (Twilio, Vonage, AWS SNS, etc.) by
 * implementing this interface and marking it @Primary, or by removing
 * the ConsoleSmsSender bean. Nothing else in the codebase needs to
 * change - PasswordChangeService only depends on this interface.
 */
public interface SmsSender {

    /**
     * @param phoneNumberE164 destination number in E.164 format, e.g. +491701234567
     * @param message         message body to send
     */
    void send(String phoneNumberE164, String message);
}
