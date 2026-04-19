package com.familyhome.app.data.mapper

import com.familyhome.app.data.local.entity.UserEntity
import com.familyhome.app.domain.model.Role
import com.familyhome.app.domain.model.User
import com.familyhome.app.domain.model.UserDto

fun UserEntity.toDomain() = User(
    id        = id,
    name      = name,
    role      = Role.valueOf(role),
    parentId  = parentId,
    avatarUri = avatarUri,
    createdAt = createdAt,
)

fun User.toEntity() = UserEntity(
    id        = id,
    name      = name,
    role      = role.name,
    parentId  = parentId,
    avatarUri = avatarUri,
    createdAt = createdAt,
)

fun User.toDto() = UserDto(
    id        = id,
    name      = name,
    role      = role.name,
    parentId  = parentId,
    avatarUri = avatarUri,
    createdAt = createdAt,
)

fun UserDto.toDomain() = User(
    id        = id,
    name      = name,
    role      = Role.valueOf(role),
    parentId  = parentId,
    avatarUri = avatarUri,
    createdAt = createdAt,
)
