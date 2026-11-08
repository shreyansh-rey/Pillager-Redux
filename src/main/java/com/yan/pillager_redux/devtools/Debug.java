package com.yan.pillager_redux.devtools;

import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Debug {

    public static final Logger logger = Logger.getLogger(Debug.class.getName());
    public static final String path = "log.txt";
    public static final SimpleFormatter sf = new SimpleFormatter();

    public static FileHandler fh;

    public static void Init(){
        if (logger.getHandlers().length > 0) return;

        try{
            fh = new FileHandler(path, true);
            fh.setFormatter(sf);

            logger.addHandler(fh);
        } catch (Exception e){
            System.out.printf("Failed to initialize File Handler: %s%n", e);
        }
    }

    public static void Log(String message){
        if (logger.getHandlers().length == 0) return;

        logger.info(message);
    }

    public static void LogWarning(String warning){
        if (logger.getHandlers().length == 0) return;

        logger.warning(warning);
    }
}