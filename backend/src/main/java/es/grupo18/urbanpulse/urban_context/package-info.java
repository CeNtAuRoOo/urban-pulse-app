/**
 * Consulta autenticada del catálogo de activos urbanos. El controlador delega en
 * una transacción de lectura que selecciona por origen o proximidad en PostGIS.
 * La proyección HTTP conserva el ID local y la geometría WKT en EPSG:25830.
 * La adquisición de activos y su asociación con incidencias pertenecen a fases posteriores.
 *
 * @see es.grupo18.urbanpulse.urban_context.api.UrbanAssetController
 * @see es.grupo18.urbanpulse.urban_context.model.UrbanAsset
 */
package es.grupo18.urbanpulse.urban_context;
