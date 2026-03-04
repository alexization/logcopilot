package com.logcopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogcopilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(LogcopilotApplication.class, args);
	}

}
