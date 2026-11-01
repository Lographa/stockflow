package com.stockFlow.stockflow_api.exception;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.stockFlow.stockflow_api.movements.exception.InsufficientStockException;
import com.stockFlow.stockflow_api.products.exception.ProductNotFoundException;
import com.stockFlow.stockflow_api.products.exception.SameProductException;

/**
 * Tratamento global de erros da API (todas as rotas), no formato de
 * {@link ErrorResponseDTO}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotFound(ProductNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponseDTO("produto não encontrado", ex.getMessage()));
    }

    @ExceptionHandler(SameProductException.class)
    public ResponseEntity<ErrorResponseDTO> handleSameProduct(SameProductException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDTO("Produto já cadastrado com nome ou sku", ex.getMessage()));
    }

    @ExceptionHandler(InsufficientStockException.class)
    public ResponseEntity<ErrorResponseDTO> handleInsufficientStock(InsufficientStockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDTO("Estoque insuficiente", ex.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponseDTO("Violação de integridade",
                        "A operação viola uma restrição de integridade dos dados (ex.: valor duplicado)"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().stream()
                .sorted(Comparator.comparing(FieldError::getField)
                        .thenComparing(error -> String.valueOf(error.getDefaultMessage())))
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "valor inválido"));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(),
                        error.getDefaultMessage() != null ? error.getDefaultMessage() : "valor inválido"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("Validação falhou",
                        "Um ou mais campos são inválidos", fieldErrors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("Requisição inválida",
                        "Corpo da requisição ausente, malformado ou com tipo/valor inválido"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponseDTO("Requisição inválida",
                        "Valor inválido para o parâmetro '" + ex.getName() + "'"));
    }

    /**
     * Rede de segurança. Exceções do próprio Spring MVC que já carregam um status
     * (rota inexistente, método não suportado, parâmetro ausente, etc. —
     * {@link ErrorResponse}) preservam esse status; o restante vira 500 genérico,
     * com o detalhe apenas no log.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is4xxClientError()) {
                HttpStatus resolved = HttpStatus.resolve(status.value());
                String reason = resolved != null ? resolved.getReasonPhrase() : "Requisição inválida";
                return ResponseEntity.status(status)
                        .body(new ErrorResponseDTO(reason, errorResponse.getBody().getDetail()));
            }
        }

        log.error("Erro inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponseDTO("Erro interno", "Ocorreu um erro inesperado. Tente novamente mais tarde."));
    }
}
