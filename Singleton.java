package ticketingsystem;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public enum Singleton {
    INSTANCE;
    private static final String errorFile = "error.txt";
    private static final String logFile = "log.txt";
    private static OutputStreamWriter eOSW;
    private static OutputStreamWriter lOSW;
    static {
        try {
            eOSW = new OutputStreamWriter(new FileOutputStream(new File(errorFile)));
            lOSW = new OutputStreamWriter(new FileOutputStream(new File(logFile)));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void errorMsg(String msg) {
        try {
            eOSW.write(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void closeWriter() {
        try {
            eOSW.flush(); eOSW.close();
            lOSW.flush(); lOSW.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public void logMsg(String msg) {
        try {
            lOSW.write(msg + '\n');
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
