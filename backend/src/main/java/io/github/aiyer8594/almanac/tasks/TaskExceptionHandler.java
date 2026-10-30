package io.github.aiyer8594.almanac.tasks;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps API failures to RFC 9457 problem details. Extending
 * {@link ResponseEntityExceptionHandler} gives the same format to the errors Spring MVC
 * raises itself, such as malformed JSON or a non-numeric id.
 *
 * <p>Details for persistence failures are fixed strings, because those exception
 * messages can expose class names and SQL.
 */
@RestControllerAdvice
class TaskExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler
	ProblemDetail handleNotFound(TaskNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler
	ProblemDetail handleInvalidTransition(InvalidStatusTransitionException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setProperty("from", ex.getFrom());
		problem.setProperty("to", ex.getTo());
		return problem;
	}

	@ExceptionHandler
	ProblemDetail handleConcurrentUpdate(OptimisticLockingFailureException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"The task was changed by another request. Reload it and try again.");
	}

	/** A client-supplied {@code sort} naming a field that does not exist is a bad request. */
	@ExceptionHandler
	ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"Unknown sort property '%s'".formatted(ex.getPropertyName()));
	}

	/** Adds which fields failed, which the default body leaves out. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage,
					(first, second) -> first));
		ProblemDetail problem = ex.getBody();
		problem.setProperty("errors", errors);
		return handleExceptionInternal(ex, problem, headers, status, request);
	}

}
