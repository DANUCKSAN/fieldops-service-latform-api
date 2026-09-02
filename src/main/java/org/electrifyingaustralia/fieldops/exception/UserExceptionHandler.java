package org.electrifyingaustralia.fieldops.exception;

import org.electrifyingaustralia.fieldops.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {

    @ExceptionHandler({
            InvalidIdentityClaimsException.class,
            UserAccessDisabledException.class
    })
    public ResponseEntity<ProblemDetail>
    handleForbidden(RuntimeException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.FORBIDDEN,
                        exception.getMessage()
                );

        problem.setTitle("FieldOps access denied");
        problem.setProperty("code", "FIELDOPS_ACCESS_DENIED");

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(problem);
    }

    @ExceptionHandler(UserIdentityConflictException.class)
    public ResponseEntity<ProblemDetail>
    handleIdentityConflict(
            UserIdentityConflictException exception
    ) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        exception.getMessage()
                );

        problem.setTitle("User identity conflict");
        problem.setProperty("code", "USER_IDENTITY_CONFLICT");

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(problem);
    }
}
