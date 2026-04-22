package com.letter.shared.network

import com.letter.contract.dto.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*

class AddressApi(private val client: HttpClient) {
    suspend fun list(): List<AddressDto> =
        client.get("/api/v1/me/addresses").body<ApiResponse<List<AddressDto>>>().data

    suspend fun create(req: CreateAddressRequest): AddressDto =
        client.post("/api/v1/me/addresses") { setBody(req) }.body<ApiResponse<AddressDto>>().data

    suspend fun update(id: String, req: UpdateAddressRequest): AddressDto =
        client.patch("/api/v1/me/addresses/$id") { setBody(req) }.body<ApiResponse<AddressDto>>().data

    suspend fun delete(id: String) {
        client.delete("/api/v1/me/addresses/$id")
    }

    suspend fun setDefault(id: String): AddressDto =
        client.post("/api/v1/me/addresses/$id/default").body<ApiResponse<AddressDto>>().data

    suspend fun listAnchors(): List<VirtualAnchorDto> =
        client.get("/api/v1/virtual-anchors").body<ApiResponse<List<VirtualAnchorDto>>>().data
}

class ContactApi(private val client: HttpClient) {
    suspend fun list(): List<ContactDto> =
        client.get("/api/v1/contacts").body<ApiResponse<List<ContactDto>>>().data

    suspend fun create(req: CreateContactRequest): ContactDto =
        client.post("/api/v1/contacts") { setBody(req) }.body<ApiResponse<ContactDto>>().data

    suspend fun delete(id: String) {
        client.delete("/api/v1/contacts/$id")
    }

    suspend fun lookup(handle: String): LookupResult =
        client.get("/api/v1/contacts/lookup") { url { parameters.append("handle", handle) } }
            .body<ApiResponse<LookupResult>>().data
}

class CatalogApi(private val client: HttpClient) {
    suspend fun stamps(): List<StampDto> =
        client.get("/api/v1/stamps").body<ApiResponse<List<StampDto>>>().data

    suspend fun stationeries(): List<StationeryDto> =
        client.get("/api/v1/stationeries").body<ApiResponse<List<StationeryDto>>>().data

    suspend fun stickers(): List<StickerDto> =
        client.get("/api/v1/stickers").body<ApiResponse<List<StickerDto>>>().data

    suspend fun myAssets(): MyAssetsDto =
        client.get("/api/v1/me/assets").body<ApiResponse<MyAssetsDto>>().data
}
