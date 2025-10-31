import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import org.junit.jupiter.api.Test;

public class NetTest {
    @Test
    void httpTest() {
        HttpRequest request = HttpRequest.get("slhaf.work");
        request.setConnectionTimeout(2);
        request.setReadTimeout(2);
        HttpResponse execute = request.execute();
        System.out.println(execute.toString());
        execute.close();
    }
}
