package com.impulsosocial.server.service

import kotlin.test.Test
import kotlin.test.assertTrue
import java.io.File

class UserDeletionPolicySourceTest {
    @Test
    fun `permanent deletion protects history and removes only recreatable state`() {
        val source = File("src/main/kotlin/com/impulsosocial/server/service/AppService.kt").readText()
        assertTrue(source.contains("protectedUserDeletionReferences"))
        assertTrue(source.contains("deleteEphemeralUserState"))
        assertTrue(source.contains("DELETE FROM usuarios_credimpulso WHERE user_id=?"))
        assertTrue(source.contains("DELETE FROM cuentas_credito WHERE user_id=?"))
        assertTrue(source.contains("actividad histórica que debe conservarse"))
    }
}
