package com.zstring.structs;

import java.util.List;
import java.util.Map;

public class JsonCG {
    // key:line number, val:method invocation with "receiver:type->method"
    private Map<Integer, String> methodInvoke;

    public Map<Integer, String> getMethodInvoke() {
        return methodInvoke;
    }

    public void setMethodInvoke(Map<Integer, String> methodInvoke) {
        this.methodInvoke = methodInvoke;
    }
}
