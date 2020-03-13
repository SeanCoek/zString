//import com.zstring.utils.FileUtil;
//
//
//interface ParentIter {
//    public void inIter();
//}
//
//public class test {
//
//    public String f;
//
//    public static void main(String[] args) {
//        String record;
//        String recTypeName = "ClassA";
//        record = recTypeName + "INVOKE method1()";
//        System.out.println(record);
//        recTypeName = "ClassB";
//        record = recTypeName + "INVOKE method2()";
//        System.out.println(record);
//        try
//        {
//            fun();
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//        FileUtil.writeResult(record, record);
////        Decompressor dec = new Decompressor(new Children(), new ChildChild());
//    }
//
//    public static void fun() throws Exception {
//        throw new Exception();
////        System.out.println("test.fun();");
//    }
//
//    public test() {
//        test t = new test();
//        t.doSomething();t.doSomething();
//    }
//
//    public void doSomething() {
//
//    }
//
//    public void forChildChild() {
//
//    }
//}
//
//class Children extends test {
//
//    public String f;
//
////    @Override
////    public void doSomething() {
////        System.out.println("in children");
////    }
//
//    public void test() {
//        this.doSomething();
//        super.doSomething();
//        this.f = "aaa";
//        super.f = "aaa";
//    }
//
//    public void forChildChild() {
//
//    }
//}
//
//class ChildChild extends Children implements ParentIter{
//
//    public void testChildChild() {
//        super.forChildChild();
//        this.inIter();
//    }
//
//    @Override
//    public void inIter() {
//
//    }
//}
//
//class CompBase {
//    protected Children input;
//    protected ChildChild output;
//
//    public CompBase(Children in, ChildChild out) {
//        this.input = in;
//        this.output = out;
//    }
//}
//
//class Decompressor extends CompBase{
//    private test tabPrefix;
//
//    public Decompressor(Children in, ChildChild out) {
//        super(in, out);
//        input.doSomething();
//    }
//
//    public void testField() {
//        input.doSomething();
//    }
//}
//
//class P {
//    void m() {
//
//    }
//    void test() {
//        new C3().m();
//        new C4().m();
//    }
//}
//
//class C extends P {
//    void m() {
//
//    }
//}
//
//class C2 extends P {
//
//}
//
//class C3 extends C {
//
//}
//class C4 extends C {
//    void m() {
//
//    }
//}