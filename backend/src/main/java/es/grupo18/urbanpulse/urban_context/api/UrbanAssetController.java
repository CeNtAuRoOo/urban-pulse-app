package es.grupo18.urbanpulse.urban_context.api;

import java.util.List;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.grupo18.urbanpulse.urban_context.dto.UrbanAssetDTO;
import es.grupo18.urbanpulse.urban_context.service.UrbanAssetService;

/**
 * Consulta pública de activos urbanos. Requiere JWT válido (gestionado por el
 * filtro de seguridad); no hay autorización por propietario.
 */
@RestController
@RequestMapping("/api/v1/urban-assets")
@RequiredArgsConstructor
public class UrbanAssetController {

    private final UrbanAssetService service;

    /**
     * Filtra activos por tipo y, opcionalmente, por proximidad.
     *
     * @param type         tabla de origen; opcional
     * @param latitude     latitud central WGS84; opcional (requiere longitude y
     *                     radiusMeters)
     * @param longitude    longitud central WGS84; opcional (requiere latitude y
     *                     radiusMeters)
     * @param radiusMeters radio en metros; opcional (requiere latitude y longitude)
     * @return activos coincidentes; lista vacía si no hay resultados
     */
    @GetMapping
    public ResponseEntity<List<UrbanAssetDTO>> search(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "latitude", required = false) Double latitude,
            @RequestParam(name = "longitude", required = false) Double longitude,
            @RequestParam(name = "radiusMeters", required = false) Double radiusMeters) {
        return ResponseEntity.ok(service.search(type, latitude, longitude, radiusMeters));
    }
}
