package es.grupo18.urbanpulse.urban_context.api;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import es.grupo18.urbanpulse.shared.error.ApiException;
import es.grupo18.urbanpulse.urban_context.dto.UrbanAssetDTO;
import es.grupo18.urbanpulse.urban_context.service.UrbanAssetService;

/**
 * Consulta de activos urbanos para cuentas activas con correo verificado y JWT vigente. La cadena compartida revalida
 * identidad y revocación en cada petición; no hay autorización por propietario ni operaciones de escritura.
 */
@RestController
@RequestMapping("/api/v1/urban-assets")
public class UrbanAssetController {

    private final UrbanAssetService service;

    /**
     * Vincula la ruta de lectura con el servicio del catálogo.
     *
     * @param service servicio de consulta espacial
     */
    public UrbanAssetController(UrbanAssetService service) {
        this.service = service;
    }

    /**
     * Filtra activos por tipo y, opcionalmente, por proximidad.
     *
     * @param type         tabla de origen; opcional
     * @param latitude     latitud central WGS84; opcional (requiere longitude y radiusMeters)
     * @param longitude    longitud central WGS84; opcional (requiere latitude y radiusMeters)
     * @param radiusMeters radio en metros; opcional (requiere latitude y longitude)
     * @return activos coincidentes; lista vacía si no hay resultados
     * @throws ApiException si los filtros son inválidos
     */
    @GetMapping
    public ResponseEntity<List<UrbanAssetDTO>> search(@RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "latitude", required = false) String latitude,
            @RequestParam(name = "longitude", required = false) String longitude,
            @RequestParam(name = "radiusMeters", required = false) String radiusMeters) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.search(type, numero(latitude), numero(longitude), numero(radiusMeters)));
    }

    // Spring convierte un Double opcional vacío en null. Solo la ausencia desactiva
    // el filtro.
    private static Double numero(String valor) {
        if (valor == null)
            return null;
        try {
            return Double.valueOf(valor);
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Parámetro numérico no interpretable.");
        }
    }
}
