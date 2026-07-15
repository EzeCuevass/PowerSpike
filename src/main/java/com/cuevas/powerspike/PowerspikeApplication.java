package com.cuevas.powerspike;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PowerspikeApplication {

	public static void main(String[] args) {
		SpringApplication.run(PowerspikeApplication.class, args);
	}

}
