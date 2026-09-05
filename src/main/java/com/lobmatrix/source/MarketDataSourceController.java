package com.lobmatrix.source;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sources")
public class MarketDataSourceController {

    private final MarketDataSourceService service;

    public MarketDataSourceController(MarketDataSourceService service) {
        this.service = service;
    }

    @GetMapping
    public List<SourceStatusResponse> listSources() {
        return service.listSources();
    }

    @PutMapping("/{source}")
    public SourceStatusResponse updateSource(
            @PathVariable String source,
            @RequestBody SourceSelectionRequest request
    ) {
        if (request == null || request.source() == null || !source.equalsIgnoreCase(request.source())) {
            throw new IllegalArgumentException("URL source must match request source.");
        }
        return service.selectAndUpdate(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidRequest(IllegalArgumentException exception) {
        return Map.of("error", exception.getMessage());
    }
}
