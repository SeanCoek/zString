public class motivation {
    public static void main(String[] args) {
        A x = new A();
        B b = new B();
        A y = new A();
        C c = new C();
        x.f = b;
        y.f = c;
        A z = x.m();
        z.m();
    }

}

class A {
    A f;
    A m() {
        return this.f;
    }
}

class B extends A {
    A m() {
        return null;
    }
}

class C extends A {
    A m() {
        return null;
    }
}