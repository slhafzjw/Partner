package experimental;

import org.junit.jupiter.api.Test;

import java.io.IOException;

public class SystemTest {
    @Test
    void execTest() {
        // exec("pwd");
        // exec("ls", "-la");
        String r = exec("pip", "st", "--format=freeze");
        System.out.println(r);
    }

    private String exec(String... command) {
        StringBuilder s = new StringBuilder();
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        try {
            Process process = processBuilder.start();
            java.io.InputStream inputStream = process.getInputStream();
            java.util.Scanner scanner = new java.util.Scanner(inputStream).useDelimiter("\\A");
            if (scanner.hasNext()) {
                s.append(scanner.next());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return s.toString();
    }

}
