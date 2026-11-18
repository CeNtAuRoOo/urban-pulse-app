package es.grupo18.urbanpulse.urban_context.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Proyección del catálogo consultable, distinta del activo UUID del diseño futuro.
 * Las coordenadas WKT son métricas EPSG:25830, sin prefijo SRID. El catálogo no
 * implica una ingesta municipal ni una licencia de reutilización acreditadas.
 *
 * @param id identidad numérica local; no identifica una fila de un proveedor
 * @param sourceTable identificador del conjunto de origen, no un nombre SQL ejecutable
 * @param name nombre opcional del activo, revisado antes de importar
 * @param category categoría opcional de origen, sin normalización inferida
 * @param description descripción opcional revisada, sin datos personales
 * @param address reservado; null hasta acreditar publicación de datos de contacto
 * @param phone reservado; null hasta acreditar publicación de datos de contacto
 * @param email reservado; null hasta acreditar publicación de datos de contacto
 * @param url reservado; null hasta acreditar procedencia y seguridad del enlace
 * @param geom geometría bidimensional válida y no vacía en WKT EPSG:25830
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record UrbanAssetDTO(Long id, String sourceTable, String name, String category,
        String description, String address, String phone, String email, String url, String geom) {
}
