package com.stockFlow.stockflow_api.movements;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.stockFlow.stockflow_api.movements.dto.CreateMovementRequest;
import com.stockFlow.stockflow_api.movements.dto.MovementResponse;
import com.stockFlow.stockflow_api.movements.dto.MovementSearchFilter;
import com.stockFlow.stockflow_api.movements.enums.MovementTypeEnum;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/movements")
public class MovementsController {

    private final StockMovementService stockMovementService;

    public MovementsController(StockMovementService stockMovementService) {
        this.stockMovementService = stockMovementService;
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse register(@Valid @RequestBody CreateMovementRequest body) {
        return stockMovementService.register(body);
    }

    @GetMapping("")
    public List<MovementResponse> search(
            @RequestParam(name = "product_id", required = false) Long productId,
            @RequestParam(required = false) MovementTypeEnum type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {

        return stockMovementService.search(new MovementSearchFilter(productId, type, from, to));
    }
}
