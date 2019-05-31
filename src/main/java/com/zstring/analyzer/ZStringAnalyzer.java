package com.zstring.analyzer;

import com.zstring.env.SootEnvironment;

public class ZStringAnalyzer {

    public static void main(String[] args) {
        String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        String pp = "/home/sean/bench_compile/";
        SootEnvironment.init(cp, pp);
    }
}
