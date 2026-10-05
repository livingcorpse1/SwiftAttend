package edu.swiftattend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SwiftAttendApplication {
    public static void main(String[] args) { SpringApplication.run(SwiftAttendApplication.class, args); }
}
