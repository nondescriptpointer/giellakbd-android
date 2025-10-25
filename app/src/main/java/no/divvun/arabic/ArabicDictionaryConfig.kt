package no.divvun.arabic

import android.content.Context
import java.io.File

object ArabicDictionaryConfig {
    
    // MARK: - Configuration
    
    /// URL for downloading the Arabic language model database
    const val DATABASE_DOWNLOAD_URL = "https://arab-easy.in-maa-1.linodeobjects.com/arabic-lm.sqlite3"
    
    /// Local filename for the downloaded database
    const val DATABASE_FILE_NAME = "arabic-lm.sqlite3"
    
    /// Maximum number of suggestions to return
    const val MAX_SUGGESTIONS = 8
    
    /// Cache size for suggestions (number of entries to keep)
    const val CACHE_SIZE = 1000
    
    /// Whether to enable debug logging
    const val ENABLE_DEBUG_LOGGING = true
    
    // MARK: - Database Schema Validation
    
    /// Expected tables in the database
    val EXPECTED_TABLES = listOf(
        "tokens",
        "unigram", 
        "bigram",
        "trigram",
        "lexicon_fts"
    )
    
    /// Expected columns for each table
    val EXPECTED_COLUMNS = mapOf(
        "tokens" to listOf("id", "value"),
        "unigram" to listOf("token", "freq"),
        "bigram" to listOf("w1", "w2", "freq"),
        "trigram" to listOf("w1", "w2", "w3", "freq"),
        "lexicon_fts" to listOf("token")
    )
    
    // MARK: - Helper Methods
    
    fun log(message: String) {
        if (ENABLE_DEBUG_LOGGING) {
            android.util.Log.d("ArabicDictionary", message)
        }
    }
    
    fun validateDatabaseURL(): Boolean {
        return try {
            val url = java.net.URL(DATABASE_DOWNLOAD_URL)
            url.protocol == "https"
        } catch (e: Exception) {
            log("Invalid database download URL: $DATABASE_DOWNLOAD_URL")
            false
        }
    }
    
    fun getDatabasePath(context: Context): String {
        return File(context.filesDir, DATABASE_FILE_NAME).absolutePath
    }
}
