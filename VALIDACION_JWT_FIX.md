# Validación del fix JWT

- AppConfig.kt validado con `kotlinc` 2.x/JDK 21: OK.
- Prueba directa de persistencia: una clave creada en disco se lee idéntica en el segundo arranque: OK.
- Tamaño de clave generada: 64 caracteres Base64URL (48 bytes aleatorios): OK.
- Ruta por defecto Railway: `/data/.kredi-secrets/jwt-secret`: fuera del endpoint `/uploads`: OK.
- Versión Gradle/runtime/Docker/OpenAPI: 1.1.2: OK.
- La lógica de base de datos y migración 84 no fue revertida.
- No fue posible ejecutar el wrapper Gradle completo en este entorno sin acceso de red a la distribución, pero el error reportado ocurre en runtime y el archivo modificado fue compilado de forma aislada con Kotlin/JDK 21.
