import com.zstring.analyzer.PointsToAnalyzer;
import com.zstring.analyzer.RelationAnalyzer;
import com.zstring.structs.Relation;
import org.junit.Before;
import org.junit.Test;

public class RelationTest {
    public RelationAnalyzer analyzer;
    String cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
    String pp = null;

    @Before
    public void beforeTest() {
        analyzer = new RelationAnalyzer();
        RelationAnalyzer.isSplit = true;
    }

    @Test
    public void compress() {
        pp = "/home/sean/bench_compared/compress/";
        System.out.println("compress");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void crypto() {
        pp  = "/home/sean/bench_compared/crypto/";
        System.out.println("crypto");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void bootstrap() {
        pp = "/home/sean/bench_compared/bootstrap.jar";
        System.out.println("bootstrap");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void codec() {
        pp = "/home/sean/bench_compared/commons-codec.jar";
        System.out.println("commons-codec");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void junit() {
        pp = "/home/sean/bench_compared/junit.jar";
        System.out.println("junit");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void httpclient() {
        pp = "/home/sean/bench_compared/commons-httpclient.jar";
        System.out.println("commons-httpclient");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void serializer() {
        pp = "/home/sean/bench_compared/serializer.jar";
        System.out.println("serializer");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void xerces() {
        pp = "/home/sean/bench_compared/xerces.jar";
//        RelationAnalyzer.isSplit = false;
        System.out.println("xerces");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void eclipse() {
        pp = "/home/sean/bench_compared/eclipse.jar";
        System.out.println("eclipse");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void derby() {
        pp = "/home/sean/bench_compared/derby.jar";
        System.out.println("derby");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void xalan() {
        pp = "/home/sean/bench_compared/xalan.jar";
        System.out.println("xalan");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void antlr() {
        pp = "/home/sean/bench_compared/antlr.jar";
        System.out.println("antlr");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void jython() {
        pp = "/home/sean/bench_compared/jython.jar";
        System.out.println("jython");
        analyzer.analyze(cp, pp);
    }

    @Test
    public void batik() {
        pp = "/home/sean/bench_compared/batik.jar";
        System.out.println("batik");
        analyzer.analyze(cp, pp);
    }

}
