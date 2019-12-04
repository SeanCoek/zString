import com.zstring.utils.FileUtil;


interface ParentIter {
    public void inIter();
}

public class test {

    public String f;

    public static void main(String[] args) {
        String record;
        String recTypeName = "ClassA";
        record = recTypeName + "INVOKE method1()";
        System.out.println(record);
        recTypeName = "ClassB";
        record = recTypeName + "INVOKE method2()";
        System.out.println(record);
        fun();
        FileUtil.writeResult(record, record);
        Decompressor dec = new Decompressor(new Children(), new ChildChild());
    }

    public static void fun() {
        System.out.println("test.fun();");
    }

    public test() {
        doSomething();
    }

    public void doSomething() {

    }

    public void forChildChild() {

    }
}

class Children extends test {

    public String f;

    @Override
    public void doSomething() {
        System.out.println("in children");
    }

    public void test() {
        this.doSomething();
        super.doSomething();
        this.f = "aaa";
        super.f = "aaa";
    }

    public void forChildChild() {

    }
}

class ChildChild extends Children implements ParentIter{

    public void testChildChild() {
        super.forChildChild();
        this.inIter();
    }

    @Override
    public void inIter() {

    }
}

class CompBase {
    protected Children input;
    protected ChildChild output;

    public CompBase(Children in, ChildChild out) {
        this.input = in;
        this.output = out;
    }
}

class Decompressor extends CompBase{
    private test tabPrefix;

    public Decompressor(Children in, ChildChild out) {
        super(in, out);
        input.doSomething();
    }

    public void testField() {
        input.doSomething();
    }
}