public class TestA {
    public static void main(String[] args) {
        try {
            int a = 1 / 0;
        } catch (Exception ignore) {
        }
        System.out.println("111");
    }
}
