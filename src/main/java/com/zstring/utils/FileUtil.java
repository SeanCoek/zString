package com.zstring.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
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
}
