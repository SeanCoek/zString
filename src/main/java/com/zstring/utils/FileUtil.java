package com.zstring.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FileUtil {

    public static List<String> getFiles(String dir, String extension) {
        Iterator<File> files = FileUtils.iterateFiles(new File(dir), new String[]{extension}, true);
        List<String> filelist = new ArrayList<String>();
        while (files.hasNext()) {
            File file = files.next();
            String filePath = file.getAbsolutePath();
            filelist.add(filePath);
        }
        return filelist;
    }

    public static void writeResult(String[] dataOutput, String filename) {
        File file = new File(filename);
        FileWriter fw = null;
        try {
            if (!file.exists()) {
                file.createNewFile();
            }
            fw = new FileWriter(file, true);
            for (String s : dataOutput) {
                if(s == null) {
                    s = "";
                }
                fw.write(s);
                fw.write("\r\n");
            }
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
