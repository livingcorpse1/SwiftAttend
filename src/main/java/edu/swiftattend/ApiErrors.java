package edu.swiftattend;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,Object>> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("success",false,"message",e.getReason()==null?"Request could not be completed.":e.getReason(),"timestamp",Instant.now()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException e){String message=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getDefaultMessage()==null?"Check the submitted fields.":x.getDefaultMessage()).orElse("Check the submitted fields.");return ResponseEntity.badRequest().body(Map.of("success",false,"message",message,"timestamp",Instant.now()));}
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> fallback(Exception e){return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("success",false,"message","Something went wrong. Please try again.","timestamp",Instant.now()));}
}
