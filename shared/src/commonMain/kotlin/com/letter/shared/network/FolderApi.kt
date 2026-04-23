package com.letter.shared.network

import com.letter.contract.dto.*
import io.ktor.client.*
import io.ktor.client.request.*

class FolderApi(private val client: HttpClient) {
    suspend fun list(): List<FolderDto> =
        client.get("/api/v1/folders").unwrap()

    suspend fun create(req: CreateFolderRequest): FolderDto =
        client.post("/api/v1/folders") { setBody(req) }.unwrap()

    suspend fun update(id: String, req: UpdateFolderRequest): FolderDto =
        client.patch("/api/v1/folders/$id") { setBody(req) }.unwrap()

    suspend fun delete(id: String) {
        client.delete("/api/v1/folders/$id").ensureSuccess()
    }

    suspend fun reorder(orderedIds: List<String>) {
        client.post("/api/v1/folders/reorder") { setBody(ReorderFoldersRequest(orderedIds)) }.ensureSuccess()
    }

    suspend fun assign(letterId: String, folderId: String?) {
        client.post("/api/v1/letters/$letterId/folder") { setBody(AssignFolderRequest(folderId)) }.ensureSuccess()
    }
}
