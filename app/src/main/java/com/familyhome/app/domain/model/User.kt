package com.familyhome.app.domain.model

data class User(
    val id: String,
    val name: String,
    val role: Role,
    /** null for Father; points to father's id for Wife; points to father/mother id for Kid */
    val parentId: String?,
    val avatarUri: String?,
    /** SHA-256 hash of the 4-digit PIN */
    val pin: String,
    val createdAt: Long,
)

enum class Role {
    FATHER,
    WIFE,
    KID;

    val displayName: String
        get() = when (this) {
            FATHER -> "Leader"
            WIFE   -> "Partner"
            KID    -> "Kid"
        }
}
