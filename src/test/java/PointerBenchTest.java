import com.zstring.analyzer.PointsToAnalyzer;
import com.zstring.env.SootEnvironment;
import com.zstring.structs.CG;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import soot.*;

import java.util.Map;

public class PointerBenchTest {
    public PointsToAnalyzer analyzer;
    PointsToAnalysis pta;
    CG cg;

    @Before
    public void beforeTest() {
        analyzer = new PointsToAnalyzer();
        PointsToAnalyzer.cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
        PointsToAnalyzer.pp = "/home/sean/specjvm_compile/";
        CG.outputDir = "/home/sean/IdeaProjects/zString/outputDot/serial/";
        analyzer.analyze();
        pta = analyzer.pta;
        cg = SootEnvironment.cg;
    }

    @Test
    public void testAssignInVarInitial() {
        String className = "callgraph.Hierarchy";
        String methodSig = "<callgraph.Hierarchy: void main(java.lang.String[])>";
        String sig = "<callgraph.Hierarchy: void diff(basic.Parent)>";
        Map<Integer, Value> line2valueMap = SootEnvironment.locals.get(className).get(sig);

        // a1:10, a2:11, a3:14, a4:15
        PointsToSet a1 = pta.reachingObjects((Local) line2valueMap.get(10));
        PointsToSet a2 = pta.reachingObjects((Local) line2valueMap.get(11));
        PointsToSet a3 = pta.reachingObjects((Local) line2valueMap.get(14));
        PointsToSet a4 = pta.reachingObjects((Local) line2valueMap.get(15));


        System.out.println(a1.possibleTypes());
        System.out.println(a1.possibleClassConstants());
        System.out.println(a1.possibleStringConstants());
        System.out.println(a1.hasNonEmptyIntersection(a3));
        Assert.assertTrue(a3.hasNonEmptyIntersection(a1));
        Assert.assertTrue(a4.hasNonEmptyIntersection(a2));

        // b:18, b.f3:19, b.a_arr:20, a5:21, a6:22
        Local b = (Local) line2valueMap.get(22);
        SootClass b_class = Scene.v().getSootClass(b.getType().toString());
        PointsToSet b_point = pta.reachingObjects(b);
        PointsToSet b_f3_point = pta.reachingObjects(b, b_class.getFieldByName("f3"));
        PointsToSet b_a_arr_point = pta.reachingObjects(b, b_class.getFieldByName("a_arr"));
        PointsToSet a5 = pta.reachingObjects((Local) line2valueMap.get(25));
        PointsToSet a6 = pta.reachingObjects((Local) line2valueMap.get(26));

        Assert.assertTrue(!b_point.isEmpty());
        Assert.assertTrue(a5.hasNonEmptyIntersection(b_f3_point));
        Assert.assertTrue(a6.hasNonEmptyIntersection(b_a_arr_point));


        // a7:25, a8:26, a9:29, a10:30
        PointsToSet a7 = pta.reachingObjects((Local) line2valueMap.get(25));
        PointsToSet a8 = pta.reachingObjects((Local) line2valueMap.get(26));
        PointsToSet a9 = pta.reachingObjects((Local) line2valueMap.get(29));
        PointsToSet a10 = pta.reachingObjects((Local) line2valueMap.get(30));



    }

    @Test
    public void testCG() {
//        CallGraph cg = Scene.v().getCallGraph();
//        SootMethod m = Scene.v().getMethod("<basic.Parent: void m1()>");
//        SootMethod mc = Scene.v().getMethod("<basic.ChildA: void m1()>");
//        SootMethod diff = Scene.v().getMethod("<callgraph.Hierarchy: void diff(basic.Parent)>");
//        Sources s = new Sources(cg.edgesInto(m));
//        Sources sc = new Sources(cg.edgesInto(mc));
//        Sources in = new Sources(cg.edgesInto(diff));
//        Targets out = new Targets(cg.edgesOutOf(diff));
//        while(in.hasNext()) {
//            SootMethod me = (SootMethod) in.next();
//            String n = me.getSignature();
//        }
//        while(out.hasNext()) {
//            SootMethod me = (SootMethod) out.next();
//            String n = me.getSignature();
//        }
    }
}