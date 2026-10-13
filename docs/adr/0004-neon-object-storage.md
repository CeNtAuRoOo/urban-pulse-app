# ADR-0004: Almacenamiento de evidencias en Neon Object Storage

- **Estado:** Propuesto
- **Fecha:** 2026-10-11
- **Ámbito:** UrbanPulse

## Contexto

Las incidencias admiten fotografías. Su contenido binario no debe guardarse en PostgreSQL; necesitan un almacén de objetos con acceso controlado. La opción de almacenamiento de Neon permite alojar evidencias cerca del proyecto de datos y usar una API compatible con S3.

## Opciones consideradas

1. **Neon Object Storage:** compatible con S3, almacenamiento privado y ramificación junto a los datos.
2. **Cloudinary:** optimización y transformación multimedia; funcionalidades no prioritarias para simples evidencias.
3. **Supabase Storage:** almacenamiento de objetos con cuotas y servicios adicionales.
4. **Cloudflare R2:** alternativa S3, con condiciones de facturación propias.

## Decisión

Se propone Neon Object Storage con un bucket **privado**. Spring Boot decidirá quién puede adjuntar o consultar fotografías y guardará en PostgreSQL la referencia estable al objeto y sus metadatos. No se almacenarán URLs públicas permanentes como único mecanismo de acceso. Inicialmente solo se admitirán imágenes.

## Consecuencias

**Positivas:** simplicidad administrativa, compatibilidad S3, separación de datos y binarios y entornos de prueba coherentes con ramas.

**Riesgos:** dependencia de Neon, cuota gratuita, necesidad de limpiar objetos huérfanos y ausencia de transacción atómica entre SQL y Object Storage. La existencia de un proveedor común no elimina estas diferencias.

## Consideraciones pendientes

Validar en una prueba técnica la disponibilidad regional, límites y compatibilidad de subida/lectura firmadas desde el SDK Java. Definir tamaños máximos, MIME aceptados, acceso a imágenes revisadas, borrado, tratamiento EXIF y mecanismo de subida. El acceso público a los datos de una incidencia no implica acceso público automático a su fotografía.
