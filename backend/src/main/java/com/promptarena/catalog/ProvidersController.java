package com.promptarena.catalog;

import com.promptarena.dto.ProvidersResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint describing every supported provider for the composer (FR-020): whether it is
 * configured and the models selectable for it — exactly what each provider's own API reports.
 * Protected like every other {@code /api} route — requires an authenticated session (FR-001).
 */
@RestController
@RequestMapping("/api/providers")
public class ProvidersController {

  private final ModelCatalogService modelCatalog;

  public ProvidersController(ModelCatalogService modelCatalog) {
    this.modelCatalog = modelCatalog;
  }

  /** All five providers in canonical order, each with its selectable models. */
  @GetMapping
  public ProvidersResponse list() {
    return new ProvidersResponse(modelCatalog.fullCatalog());
  }
}
