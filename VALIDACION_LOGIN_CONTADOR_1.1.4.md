# Validación estática

- Gradle: 1.1.4
- Runtime: 1.1.4
- Docker: 1.1.4
- OpenAPI: 1.1.4
- `/auth/login` usa LOWER(TRIM(username/email)).
- Existe fallback canónico `CONTADOR` restringido a un único Contador.
- La contraseña sigue validándose mediante `passwordSecurity.verify`.
- El mensaje `Debes registrarte primero.` fue eliminado del flujo de login.
- No hay cambios de esquema ni migraciones nuevas.

El build/test integral no se pudo ejecutar en este entorno porque Gradle necesita descargar su distribución y el host services.gradle.org no está accesible aquí.
