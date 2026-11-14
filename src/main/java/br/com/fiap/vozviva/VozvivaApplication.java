package br.com.fiap.vozviva;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VozvivaApplication {

	public static void main(String[] args) {
		SpringApplication.run(VozvivaApplication.class, args);
	}

}
