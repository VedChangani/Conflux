package com.conflux.common.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private static final String INTERNAL_MESSAGE = "secret internal failure detail";

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
	}

	@Test
	void unexpectedExceptionReturnsGeneric500ProblemDetail() throws Exception {
		mockMvc.perform(get("/test/failure"))
			.andExpect(status().isInternalServerError())
			.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
			.andExpect(jsonPath("$.status").value(500))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
			.andExpect(content().string(not(containsString(INTERNAL_MESSAGE))));
	}

	@RestController
	static class FailingController {

		@GetMapping("/test/failure")
		String fail() {
			throw new RuntimeException(INTERNAL_MESSAGE);
		}

	}

}
