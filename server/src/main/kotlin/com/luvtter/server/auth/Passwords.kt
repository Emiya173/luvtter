package com.luvtter.server.auth

import de.mkammerer.argon2.Argon2Factory

object Passwords {
    private val argon2 = Argon2Factory.create()
    private const val ITERATIONS = 3
    private const val MEMORY = 65536
    private const val PARALLELISM = 1

    fun hash(raw: String): String {
        val chars = raw.toCharArray()
        return try {
            argon2.hash(ITERATIONS, MEMORY, PARALLELISM, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }

    fun verify(hash: String, raw: String): Boolean {
        val chars = raw.toCharArray()
        return try {
            argon2.verify(hash, chars)
        } finally {
            argon2.wipeArray(chars)
        }
    }
}
