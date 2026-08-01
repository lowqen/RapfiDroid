package dev.gomoku.yixindroid.data.appearance

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract

/**
 * The little bit of `DocumentFile` this needs, done against the platform.
 *
 * androidx.documentfile would be one line of Gradle, but it is not in the
 * offline dependency cache this project builds from, and walking two known
 * folder names is not worth a network dependency.
 */
class SafTree(private val resolver: ContentResolver, private val tree: Uri) {

    private data class Entry(val documentId: String, val name: String, val isDirectory: Boolean)

    /** Contents of the folder [documentId], or empty when it cannot be listed. */
    private fun children(documentId: String): List<Entry> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val out = ArrayList<Entry>()
        runCatching {
            resolver.query(
                uri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                ),
                null, null, null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    out += Entry(
                        documentId = cursor.getString(0),
                        name = cursor.getString(1) ?: "",
                        isDirectory = cursor.getString(2) ==
                            DocumentsContract.Document.MIME_TYPE_DIR,
                    )
                }
            }
        }
        return out
    }

    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    /** Document id of a direct subfolder, matched case-insensitively. */
    fun folder(name: String): String? =
        children(rootId).firstOrNull { it.isDirectory && it.name.equals(name, true) }?.documentId

    /**
     * Contents of `<folder>/<name>` as text, or null when it is not there.
     * The listing is fetched once per folder by the caller for speed; here it is
     * a plain lookup, which is fine for the handful of files involved.
     */
    fun readFile(folderId: String, name: String): String? {
        val entry = children(folderId)
            .firstOrNull { !it.isDirectory && it.name.equals(name, true) } ?: return null
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
        return runCatching {
            resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull()
    }

    /** Every file name directly under [folderId] — used to see what is available. */
    fun fileNames(folderId: String): List<String> =
        children(folderId).filter { !it.isDirectory }.map { it.name }

    /** The picked folder's own name, for telling the user what they chose. */
    fun displayName(): String? = runCatching {
        resolver.query(
            DocumentsContract.buildDocumentUriUsingTree(tree, rootId),
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { if (it.moveToFirst()) it.getString(0) else null }
    }.getOrNull()
}
