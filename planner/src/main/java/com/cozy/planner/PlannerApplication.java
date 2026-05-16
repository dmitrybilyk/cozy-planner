package com.cozy.planner;

import com.cozy.planner.config.PlannerRuntimeHints;
import com.cozy.planner.config.TelegramConfig;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ImportRuntimeHints(PlannerRuntimeHints.class)
@ConfigurationPropertiesScan(basePackageClasses = TelegramConfig.class)
public class PlannerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlannerApplication.class, args);
	}

}
