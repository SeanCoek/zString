import com.zstring.analyzer.PointsToAnalyzer;
import org.junit.Before;
import org.junit.Test;

public class CHATest {
    public PointsToAnalyzer analyzer;

    @Before
    public void beforeTest() {
        analyzer = new PointsToAnalyzer();
        PointsToAnalyzer.cp = "/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/rt.jar";
//        PointsToAnalyzer.pp = "/home/sean/specjvm_compile/";
//        CG.outputDir = "/home/sean/IdeaProjects/zString/outputDot/serial/";
    }

    @Test
    public void compress() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/compress/";
        System.out.println("compress");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void crypto() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/crypto/";
        System.out.println("crypto");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void bootstrap() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/bootstrap.jar";
        System.out.println("bootstrap");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void codec() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/commons-codec.jar";
        System.out.println("commons-codec");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void junit() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/junit.jar";
        System.out.println("junit");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void httpclient() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/commons-httpclient.jar";
        System.out.println("commons-httpclient");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void serializer() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/serializer.jar";
        System.out.println("serializer");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void xerces() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/xerces.jar";
        System.out.println("xerces");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void eclipse() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/eclipse.jar";
        System.out.println("eclipse");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void derby() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/derby.jar";
        System.out.println("derby");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void xalan() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/xalan.jar";
        System.out.println("xalan");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void antlr() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/antlr.jar";
        System.out.println("antlr");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void jython() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/jython.jar";
        System.out.println("jython");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

    @Test
    public void batik() {
        PointsToAnalyzer.pp = "/home/sean/bench_compared/batik.jar";
        System.out.println("batik");
        analyzer.analyze(PointsToAnalyzer.cp, PointsToAnalyzer.pp);
    }

}
