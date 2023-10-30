package org.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("hello world");
        for (int i = 0; i < 10; i++) {
            logger.error("test exception", new IllegalArgumentException("Test test" + i));
        }
    }
}