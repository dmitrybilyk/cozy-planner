package com.linkease

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.*
import com.linkease.db.AndroidAvailabilityRepository
import com.linkease.db.AndroidClientRepository
import com.linkease.db.AndroidLocationRepository
import com.linkease.db.AndroidSessionRepository
import com.linkease.db.LinkDatabaseHelper
import java.util.concurrent.TimeUnit

class DriveBackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("linkease_prefs", Context.MODE_PRIVATE)
        val folderUriStr = prefs.getString("backup_folder_uri", null) ?: return Result.success()

        return try {
            val folderUri = Uri.parse(folderUriStr)
            val db = LinkDatabaseHelper(applicationContext)
            val json = DataSyncHelper.buildExportJson(
                AndroidSessionRepository(db).getAll(),
                AndroidClientRepository(db).getAll(),
                AndroidLocationRepository(db).getAll(),
                AndroidAvailabilityRepository(db).getAll(),
            )

            val resolver = applicationContext.contentResolver
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocId)

            var existingUri: Uri? = null
            resolver.query(childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(1) == "linkease_backup.json") {
                        existingUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, c.getString(0))
                    }
                }
            }

            val targetUri = existingUri
                ?: DocumentsContract.createDocument(
                    resolver,
                    DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId),
                    "application/json",
                    "linkease_backup"
                ) ?: return Result.failure()

            resolver.openOutputStream(targetUri, "wt")?.use { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
            Result.success()
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "linkease_daily_backup"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DriveBackupWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
