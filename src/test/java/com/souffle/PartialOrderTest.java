package com.souffle;

import com.zstring.datalog.Fact;
import com.zstring.datalog.PartialOrder;
import com.zstring.datalog.Variable;
import org.junit.Test;
import soot.*;
import soot.jimple.internal.JimpleLocal;

public class PartialOrderTest {

    @Test
    public void testWrite() {
        Value v = new JimpleLocal("r1", new RefType(null));
        Variable var = new Variable(v);
        PartialOrder po = new PartialOrder(var, var);
        PartialOrder po2 = new PartialOrder(var, var);
        System.out.println(Fact.writeToFile("po.facts", PartialOrder.facts));
    }
}
