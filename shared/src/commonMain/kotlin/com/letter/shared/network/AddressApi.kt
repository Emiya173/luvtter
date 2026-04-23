package com.letter.shared.network

import com.letter.contract.dto.*
import io.ktor.client.*
import io.ktor.client.request.*

class AddressApi(private val client: HttpClient) {
    suspend fun list(): List<AddressDto> =
        client.get("/api/v1/me/addresses").unwrap()

    suspend fun create(req: CreateAddressRequest): AddressDto =
        client.post("/api/v1/me/addresses") { setBody(req) }.unwrap()

    suspend fun update(id: String, req: UpdateAddressRequest): AddressDto =
        client.patch("/api/v1/me/addresses/$id") { setBody(req) }.unwrap()

    suspend fun delete(id: String) {
        client.delete("/api/v1/me/addresses/$id").ensureSuccess()
    }

    suspend fun setDefault(id: String): AddressDto =
        client.post("/api/v1/me/addresses/$id/default").unwrap()

    suspend fun listAnchors(): List<VirtualAnchorDto> =
        client.get("/api/v1/virtual-anchors").unwrap()

    suspend fun listForRecipient(handle: String): List<RecipientAddressDto> =
        client.get("/api/v1/users/by-handle/$handle/addresses").unwrap()
}

class ContactApi(private val client: HttpClient) {
    suspend fun list(): List<ContactDto> =
        client.get("/api/v1/contacts").unwrap()

    suspend fun create(req: CreateContactRequest): ContactDto =
        client.post("/api/v1/contacts") { setBody(req) }.unwrap()

    suspend fun delete(id: String) {
        client.delete("/api/v1/contacts/$id").ensureSuccess()
    }

    suspend fun lookup(handle: String): LookupResult =
        client.get("/api/v1/contacts/lookup") { url { parameters.append("handle", handle) } }.unwrap()

    suspend fun listBlocks(): List<BlockDto> =
        client.get("/api/v1/blocks").unwrap()

    suspend fun block(targetId: String): BlockDto =
        client.post("/api/v1/blocks") { setBody(CreateBlockRequest(targetId)) }.unwrap()

    suspend fun unblock(targetId: String) {
        client.delete("/api/v1/blocks/$targetId").ensureSuccess()
    }
}

class CatalogApi(private val client: HttpClient) {
    suspend fun stamps(): List<StampDto> =
        client.get("/api/v1/stamps").unwrap()

    suspend fun stationeries(): List<StationeryDto> =
        client.get("/api/v1/stationeries").unwrap()

    suspend fun stickers(): List<StickerDto> =
        client.get("/api/v1/stickers").unwrap()

    suspend fun myAssets(): MyAssetsDto =
        client.get("/api/v1/me/assets").unwrap()
}
