package com.luvtter.server.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

object Users : Table("users") {
    val id = uuid("id")
    val handle = varchar("handle", 64).uniqueIndex()
    val handleFinalized = bool("handle_finalized").default(false)
    val displayName = varchar("display_name", 64)
    val avatarUrl = text("avatar_url").nullable()
    val bio = text("bio").nullable()
    val onlyFriends = bool("only_friends").default(false)
    val currentAddressId = uuid("current_address_id").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object AuthCredentials : Table("auth_credentials") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val provider = varchar("provider", 16)
    val identifier = varchar("identifier", 255)
    val secretHash = text("secret_hash").nullable()
    val verified = bool("verified").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(provider, identifier)
    }
}

object AuthSessions : Table("auth_sessions") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val refreshToken = varchar("refresh_token", 128).uniqueIndex()
    val deviceName = varchar("device_name", 64).nullable()
    val platform = varchar("platform", 16).nullable()
    val lastActiveAt = timestampWithTimeZone("last_active_at")
    val expiresAt = timestampWithTimeZone("expires_at")
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object VirtualAnchors : Table("virtual_anchors") {
    val id = uuid("id")
    val code = varchar("code", 32).uniqueIndex()
    val name = varchar("name", 64)
    val description = text("description").nullable()
    val latitude = double("latitude")
    val longitude = double("longitude")
    val imageUrl = text("image_url").nullable()
    val orderIndex = integer("order_index").default(0)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserAddresses : Table("user_addresses") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val label = varchar("label", 32)
    val type = varchar("type", 16) // real | virtual
    val latitude = double("latitude").nullable()
    val longitude = double("longitude").nullable()
    val city = varchar("city", 64).nullable()
    val country = varchar("country", 64).nullable()
    val anchorId = uuid("anchor_id").nullable()
    val anchorLat = double("anchor_lat").nullable()
    val anchorLng = double("anchor_lng").nullable()
    val virtualDistance = integer("virtual_distance").nullable()
    val isDefault = bool("is_default").default(false)
    val deletedAt = timestampWithTimeZone("deleted_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object Contacts : Table("contacts") {
    val id = uuid("id")
    val ownerId = uuid("owner_id").references(Users.id)
    val targetId = uuid("target_id").references(Users.id)
    val note = varchar("note", 64).nullable()
    val relation = varchar("relation", 16).nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(ownerId, targetId)
    }
}

object Blocks : Table("blocks") {
    val id = uuid("id")
    val ownerId = uuid("owner_id").references(Users.id)
    val targetId = uuid("target_id").references(Users.id)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(ownerId, targetId)
    }
}

object Stamps : Table("stamps") {
    val id = uuid("id")
    val code = varchar("code", 32).uniqueIndex()
    val name = varchar("name", 64)
    val tier = integer("tier")
    val imageUrl = text("image_url")
    val weightCapacity = integer("weight_capacity")
    val speedFactor = double("speed_factor")
    val isDefault = bool("is_default").default(false)
    val isLimited = bool("is_limited").default(false)
    val availableFrom = timestampWithTimeZone("available_from").nullable()
    val availableTo = timestampWithTimeZone("available_to").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Stationeries : Table("stationeries") {
    val id = uuid("id")
    val code = varchar("code", 32).uniqueIndex()
    val name = varchar("name", 64)
    val backgroundUrl = text("background_url")
    val thumbnailUrl = text("thumbnail_url")
    val isDefault = bool("is_default").default(false)
    val isLimited = bool("is_limited").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Stickers : Table("stickers") {
    val id = uuid("id")
    val code = varchar("code", 32).uniqueIndex()
    val name = varchar("name", 64)
    val imageUrl = text("image_url")
    val weight = integer("weight")
    val isDefault = bool("is_default").default(false)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object UserAssets : Table("user_assets") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val assetType = varchar("asset_type", 16) // stamp | stationery | sticker
    val assetId = uuid("asset_id")
    val quantity = integer("quantity").default(1)
    val acquiredFrom = varchar("acquired_from", 32).nullable()
    val acquiredAt = timestampWithTimeZone("acquired_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(userId, assetType, assetId)
    }
}

object DailyRewards : Table("daily_rewards") {
    val userId = uuid("user_id").references(Users.id)
    val rewardDate = date("reward_date")
    val claimedAt = timestampWithTimeZone("claimed_at")
    override val primaryKey = PrimaryKey(userId, rewardDate)
}

private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

object Letters : Table("letters") {
    val id = uuid("id")
    val senderId = uuid("sender_id").nullable()
    val recipientId = uuid("recipient_id").nullable()
    val recipientAddressId = uuid("recipient_address_id").nullable()
    val senderAddressId = uuid("sender_address_id").nullable()
    val stampId = uuid("stamp_id").nullable()
    val stationeryId = uuid("stationery_id").nullable()
    val status = varchar("status", 16).default("draft")
    val sealedUntil = timestampWithTimeZone("sealed_until").nullable()
    val sentAt = timestampWithTimeZone("sent_at").nullable()
    val deliveryAt = timestampWithTimeZone("delivery_at").nullable()
    val deliveredAt = timestampWithTimeZone("delivered_at").nullable()
    val readAt = timestampWithTimeZone("read_at").nullable()
    val distanceValue = integer("distance_value").nullable()
    val totalWeight = integer("total_weight").default(0)
    val replyToLetterId = uuid("reply_to_letter_id").nullable()
    val wearLevel = integer("wear_level").default(0)
    val senderHiddenAt = timestampWithTimeZone("sender_hidden_at").nullable()
    val recipientHiddenAt = timestampWithTimeZone("recipient_hidden_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}

object LetterContents : Table("letter_contents") {
    val letterId = uuid("letter_id").references(Letters.id)
    val contentType = varchar("content_type", 16)
    val fontCode = varchar("font_code", 32).nullable()
    val bodyJson = jsonb<JsonElement>("body_json", lenientJson).nullable()
    val bodyUrl = text("body_url").nullable()
    val scanObjectKey = text("scan_object_key").nullable()
    val handwritingObjectKey = text("handwriting_object_key").nullable()
    val indexText = text("index_text").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(letterId)
}

object LetterAttachments : Table("letter_attachments") {
    val id = uuid("id")
    val letterId = uuid("letter_id").references(Letters.id)
    val attachmentType = varchar("attachment_type", 16)
    val mediaUrl = text("media_url").nullable()
    val thumbnailUrl = text("thumbnail_url").nullable()
    val objectKey = text("object_key").nullable()
    val stickerId = uuid("sticker_id").nullable()
    val positionX = double("position_x").nullable()
    val positionY = double("position_y").nullable()
    val rotation = double("rotation").nullable()
    val weight = integer("weight")
    val orderIndex = integer("order_index").default(0)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object LetterEvents : Table("letter_events") {
    val id = uuid("id")
    val letterId = uuid("letter_id").references(Letters.id)
    val eventType = varchar("event_type", 32)
    val title = varchar("title", 128).nullable()
    val content = text("content").nullable()
    val imageUrl = text("image_url").nullable()
    val triggeredAt = timestampWithTimeZone("triggered_at")
    val visibleAt = timestampWithTimeZone("visible_at")
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Folders : Table("folders") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val name = varchar("name", 32)
    val icon = varchar("icon", 32).nullable()
    val orderIndex = integer("order_index").default(0)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
    init {
        uniqueIndex(userId, name)
    }
}

object LetterFolders : Table("letter_folders") {
    val letterId = uuid("letter_id").references(Letters.id)
    val userId = uuid("user_id").references(Users.id)
    val folderId = uuid("folder_id").references(Folders.id)
    override val primaryKey = PrimaryKey(letterId, userId)
}

object Favorites : Table("favorites") {
    val userId = uuid("user_id").references(Users.id)
    val letterId = uuid("letter_id").references(Letters.id)
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(userId, letterId)
}

object UserNotificationPrefs : Table("user_notification_prefs") {
    val userId = uuid("user_id").references(Users.id)
    val newLetter = bool("new_letter").default(true)
    val postcard = bool("postcard").default(true)
    val reply = bool("reply").default(true)
    val quietStart = short("quiet_start").nullable()
    val quietEnd = short("quiet_end").nullable()
    val timezone = varchar("timezone", 64).nullable()
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(userId)
}

object Notifications : Table("notifications") {
    val id = uuid("id")
    val userId = uuid("user_id").references(Users.id)
    val type = varchar("type", 32)
    val letterId = uuid("letter_id").nullable()
    val eventId = uuid("event_id").nullable()
    val addressId = uuid("address_id").nullable()
    val title = varchar("title", 128)
    val preview = varchar("preview", 256).nullable()
    val readAt = timestampWithTimeZone("read_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    override val primaryKey = PrimaryKey(id)
}

object AsyncTasks : Table("async_tasks") {
    val id = uuid("id")
    val taskType = varchar("task_type", 32)
    val payload = jsonb<JsonElement>("payload", lenientJson)
    val status = varchar("status", 16).default("pending")
    val attempts = integer("attempts").default(0)
    val maxAttempts = integer("max_attempts").default(3)
    val lastError = text("last_error").nullable()
    val scheduledAt = timestampWithTimeZone("scheduled_at")
    val startedAt = timestampWithTimeZone("started_at").nullable()
    val finishedAt = timestampWithTimeZone("finished_at").nullable()
    val createdAt = timestampWithTimeZone("created_at")
    val updatedAt = timestampWithTimeZone("updated_at")
    override val primaryKey = PrimaryKey(id)
}
