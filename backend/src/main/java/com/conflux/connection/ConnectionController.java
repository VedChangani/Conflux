package com.conflux.connection;

import com.conflux.common.web.ApiPaths;
import com.conflux.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConnectionController {

	public static final String INTEREST_PATH = ApiPaths.API_V1 + "/listings/{id}/interest";

	public static final String BASE_PATH = ApiPaths.API_V1 + "/connections";

	private final ConnectionService connectionService;

	public ConnectionController(ConnectionService connectionService) {
		this.connectionService = connectionService;
	}

	@PostMapping(INTEREST_PATH)
	public ResponseEntity<ConnectionResponse> expressInterest(@PathVariable Long id) {
		InterestResult result = this.connectionService.expressInterest(id);
		return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.connection());
	}

	@GetMapping(BASE_PATH + "/sent")
	public PageResponse<ConnectionResponse> sent(@RequestParam(required = false) ConnectionStatus status,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
		return this.connectionService.sent(status, page, size);
	}

	@GetMapping(BASE_PATH + "/received")
	public PageResponse<ConnectionResponse> received(@RequestParam(required = false) ConnectionStatus status,
			@RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
			@RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
		return this.connectionService.received(status, page, size);
	}

	@GetMapping(BASE_PATH + "/{id}")
	public ConnectionResponse detail(@PathVariable Long id) {
		return this.connectionService.detail(id);
	}

	@PostMapping(BASE_PATH + "/{id}/accept")
	public ConnectionResponse accept(@PathVariable Long id) {
		return this.connectionService.accept(id);
	}

	@PostMapping(BASE_PATH + "/{id}/reject")
	public ConnectionResponse reject(@PathVariable Long id) {
		return this.connectionService.reject(id);
	}

	@DeleteMapping(BASE_PATH + "/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void withdraw(@PathVariable Long id) {
		this.connectionService.withdraw(id);
	}

}
