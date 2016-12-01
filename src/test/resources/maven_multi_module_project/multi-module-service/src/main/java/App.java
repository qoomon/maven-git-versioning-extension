import java.util.LinkedList;
import java.util.List;

public class App {
    public static void main(String[] args) {
        List<String> list = new LinkedList<>();
        long count = list.stream().count();
        System.out.println(count);
        com.google.common.base.Preconditions.checkArgument(true);
    }
}