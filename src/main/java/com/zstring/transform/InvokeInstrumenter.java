package com.zstring.transform;

import soot.*;

public class InvokeInstrumenter {
    public static String CLASS_SYSTEM = "java.lang.System";
    public static String CLASS_PRINT_STREAM = "java.io.PrintStream";
    public static String CLASS_FILEUTIL = "com.zstring.utils.FileUtil";

    private static InvokeInstrumenter instance = new InvokeInstrumenter();
    public static InvokeInstrumenter v() {
        return instance;
    }


    public static void main(String[] args) {
//        PackManager.v().getPack("wjtp").add(new Transform("wjtp.intru", OnlyVirtualCall.v()));
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.intru", ReachableTransformer.v()));

//        Scene.v().addBasicClass(CLASS_PRINT_STREAM, SootClass.SIGNATURES);
//        Scene.v().addBasicClass(CLASS_SYSTEM, SootClass.SIGNATURES);
        Scene.v().addBasicClass(CLASS_FILEUTIL, SootClass.SIGNATURES);
        soot.Main.main(args);

    }

}
