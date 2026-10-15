# ADR-0004: Almacenamiento de evidencias en Neon Object Storage

- **Estado:** Aceptado
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

Se adopta Neon Object Storage con el bucket **privado** `incident-evidence`. Spring Boot decidirá quién puede adjuntar o consultar fotografías y guardará en PostgreSQL la referencia estable al objeto y sus metadatos. No se almacenarán URLs públicas permanentes como único mecanismo de acceso. Inicialmente solo se admitirán imágenes.

## Consecuencias

**Positivas:** simplicidad administrativa, compatibilidad S3, separación de datos y binarios y entornos de prueba coherentes con ramas.

**Riesgos:** dependencia de Neon, cuota gratuita, necesidad de limpiar objetos huérfanos y ausencia de transacción atómica entre SQL y Object Storage. La existencia de un proveedor común no elimina estas diferencias.

## Aprovisionamiento y verificación (15/10/2026)

- Bucket `incident-evidence` creado como **privado** en Neon Object Storage (AWS Frankfurt); visible en las ramas `production` y `development`, con aislamiento de cambios por rama.
- Credenciales de almacenamiento con permisos de lectura/escritura diferenciadas por entorno; la credencial inicial de almacenamiento fue revocada. **Nunca se publicarán secretos ni se incluirán en el frontend.**
- `backend/pom.xml` incorpora AWS SDK for Java 2.x **2.56.0** y su BOM. `StorageProperties` tipa y valida `storage.s3.*`; `StorageConfig` configura el `S3Client` con direccionamiento **path-style**.
- La prueba explícita `NeonConnectivityIT` ha superado `HeadBucket` usando las credenciales de `development`: confirma acceso al bucket, **no demuestra todavía subida, descarga, borrado ni URLs firmadas**.

## Consideraciones pendientes

Implementar y probar operaciones de subida, descarga, borrado y acceso autorizado (o URLs firmadas); definir tamaños máximos, MIME aceptados, revisión/anonimización, EXIF, retención y limpieza de objetos huérfanos. El acceso público a una incidencia no implica acceso público automático a su fotografía. La persistencia de metadatos SQL y de objetos no es atómica.
