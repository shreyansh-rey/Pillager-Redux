package com.yan.projectpillagers;

public class Debug {

    public static void Log(String message){
        System.out.println("[Debug] " + message);
    }

    public static void LogCoOrds(float x, float y, float z){
        Log("X: " + x + ", Y: " + y + ", Z: " + z);
    }
}