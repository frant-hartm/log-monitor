package com.hazelcast.logmonitor.jeetest;

import java.io.IOException;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.security.auth.login.FailedLoginException;

@Singleton
public class ScheduledExceptionGenerator {

    private final Logger logger = Logger.getLogger(getClass().getName());
    private final Random rnd = new Random();

    @Schedule(second = "*/3", minute = "*", hour = "*", persistent = false)
    public void doWork() {
        String message = getMessage();
        logger.info("Logging " + message);
        logger.log(Level.SEVERE, message, createException(message));
    }

    private Exception createException(String message) {
        switch (rnd.nextInt(4)) {
            case 0:
                return new Exception(message);
            case 1:
                return new IOException(message);
            case 2:
                return new FailedLoginException(message);
            default:
                return new RuntimeException(message);
        }
    }

    private String getMessage() {
        switch (rnd.nextInt(3)) {
            case 0:
                return "Something failed";
            case 1:
                return "Unlucky you";
            default:
                return "Watch yourself";
        }
    }

}
