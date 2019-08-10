package com.souffle;

import com.zstring.datalog.Fact;
import com.zstring.datalog.Type;
import com.zstring.datalog.TypeFlow;
import com.zstring.datalog.Variable;
import org.junit.Test;
import soot.Value;
import soot.jimple.internal.JimpleLocal;

public class TypeFlowTest {

    @Test
    public void testWrite() {
        Type t = new Type();
        t.name = "com.zstring.Fact";
        Value vt = new JimpleLocal("r1", null);
        Variable v = new Variable(vt);
        Variable v2 = new Variable(vt);
        TypeFlow tf = new TypeFlow(t, v);
        TypeFlow tf2 = new TypeFlow(t, v2);
        Fact.writeToFile("tf.facts", TypeFlow.facts);
    }
}
