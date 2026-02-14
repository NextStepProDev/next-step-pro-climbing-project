package pl.nextsteppro.climbing;

import org.jspecify.annotations.NullMarked;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@NullMarked
@SpringBootApplication
public class ClimbingApplication {

    static void main(String[] args) {
        SpringApplication.run(ClimbingApplication.class, args);
    }
}
