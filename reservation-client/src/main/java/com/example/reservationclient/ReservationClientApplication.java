package com.example.reservationclient;


import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.hystrix.EnableHystrix;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import static org.springframework.http.HttpMethod.GET;

@EnableZuulProxy
@EnableDiscoveryClient
@SpringBootApplication
@EnableCircuitBreaker
@EnableBinding(Source.class)
public class ReservationClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}
}

@Configuration
class PublicAPIConfiguration {
	@LoadBalanced
	@Bean
	RestTemplate restTemplate() {
		return new RestTemplate();
	}
}

@RestController
@RequestMapping("/reservations")
class ReservationApiGatewayRestController{

	@LoadBalanced
	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	@Output(Source.OUTPUT)
	private MessageChannel messageChannel;

	@RequestMapping(method = RequestMethod.POST)
	public void write(@RequestBody Reservation r){
		String reservationName = r.getReservationName();
		MessageBuilder<String> stringMessageBuilder = MessageBuilder.withPayload(reservationName);
		Message<String> build = stringMessageBuilder.build();
		this.messageChannel.send(build);
	}

	public Collection<String> getReservationNameFallback() {
		return Collections.emptyList();
	}

	public String getReservationServiceMessageFallback() {
		return "Unable to contact Reservation Service";
	}

	@HystrixCommand(fallbackMethod = "getReservationNameFallback")
	@RequestMapping("/names")
	public Collection<String> getReservationNames(){

		ParameterizedTypeReference<CollectionModel<Reservation>> ptr = new ParameterizedTypeReference<CollectionModel<Reservation>>() {};
		ResponseEntity<CollectionModel<Reservation>> responseEntity = this.restTemplate.exchange("http://reservation-service/reservations", GET, null, ptr);

		return responseEntity
				.getBody()
				.getContent()
				.stream()
				.map(Reservation::getReservationName)
				.collect(Collectors.toList());
	}

	@HystrixCommand(fallbackMethod = "getReservationServiceMessageFallback")
	@RequestMapping(path = "/service-message", method = RequestMethod.GET)
	public String getReservationServiceMessage() {
		return this.restTemplate.getForObject(
				"http://reservation-service/message",
				String.class);
	}
}

class Reservation{
	private Long id;
	private String reservationName;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getReservationName() {
		return reservationName;
	}

	public void setReservationName(String reservationName) {
		this.reservationName = reservationName;
	}

	@Override
	public String toString() {
		return "Reservation{" +
				"id=" + id +
				", reservationName='" + reservationName + '\'' +
				'}';
	}
}