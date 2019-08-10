package com.zstring.datalog;


import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;

public abstract class Fact {
    public abstract String generateFactString();
    public static boolean writeToFile(String outputDir, List<Fact> facts) {
        try {
            Writer writer = new FileWriter(outputDir);
            Iterator<Fact> i = facts.iterator();
            while(i.hasNext()) {
                Fact f = i.next();
                writer.write(f.generateFactString());
            }
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
