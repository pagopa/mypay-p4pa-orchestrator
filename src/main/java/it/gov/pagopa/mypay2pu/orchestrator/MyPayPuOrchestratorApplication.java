package it.gov.pagopa.mypay2pu.orchestrator;

import it.gov.pagopa.mypay2pu.orchestrator.utils.Constants;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorMvcAutoConfiguration;

import java.util.TimeZone;

@SpringBootApplication(exclude = {ErrorMvcAutoConfiguration.class})
public class MyPayPuOrchestratorApplication {

	public static void main(String[] args) {
    TimeZone.setDefault(Constants.DEFAULT_TIMEZONE);
		SpringApplication.run(MyPayPuOrchestratorApplication.class, args);
	}

}
