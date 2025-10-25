package no.divvun.arabic

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ArabicDictionary(private val context: Context) {
    
    private var database: SQLiteDatabase? = null
    private val dbPath = ArabicDictionaryConfig.getDatabasePath(context)
    
    // Database table names
    private object Tables {
        const val TOKENS = "tokens"
        const val UNIGRAM = "unigram"
        const val BIGRAM = "bigram"
        const val TRIGRAM = "trigram"
        const val LEXICON_FTS = "lexicon_fts"
    }
    
    // Column names
    private object Columns {
        const val ID = "id"
        const val VALUE = "value"
        const val TOKEN = "token"
        const val FREQ = "freq"
        const val W1 = "w1"
        const val W2 = "w2"
        const val W3 = "w3"
    }
    
    init {
        initializeDatabase()
    }
    
    private fun initializeDatabase() {
        val dbFile = File(dbPath)
        
        if (!dbFile.exists()) {
            ArabicDictionaryConfig.log("Database not found, downloading...")
            downloadDatabase()
        }
        
        try {
            database = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READONLY)
            database?.execSQL("PRAGMA query_only = ON")
            database?.execSQL("PRAGMA temp_store = MEMORY")
            database?.execSQL("PRAGMA journal_mode = WAL")
            
            validateDatabase()
            ArabicDictionaryConfig.log("Database initialized successfully")
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error initializing database: ${e.message}")
            Timber.e(e, "Failed to initialize Arabic dictionary database")
        }
    }
    
    private fun downloadDatabase() {
        if (!ArabicDictionaryConfig.validateDatabaseURL()) {
            ArabicDictionaryConfig.log("Database URL validation failed")
            return
        }
        
        try {
            val url = URL(ArabicDictionaryConfig.DATABASE_DOWNLOAD_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val outputStream = FileOutputStream(dbPath)
                
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                }
                
                inputStream.close()
                outputStream.close()
                
                ArabicDictionaryConfig.log("Database downloaded successfully to: $dbPath")
            } else {
                ArabicDictionaryConfig.log("Failed to download database. Response code: ${connection.responseCode}")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error downloading database: ${e.message}")
            Timber.e(e, "Failed to download Arabic dictionary database")
        }
    }
    
    private fun validateDatabase() {
        database?.let { db ->
            for (tableName in ArabicDictionaryConfig.EXPECTED_TABLES) {
                try {
                    val cursor = db.rawQuery("SELECT COUNT(*) FROM $tableName", null)
                    if (cursor.moveToFirst()) {
                        val count = cursor.getInt(0)
                        ArabicDictionaryConfig.log("Table '$tableName' validated with $count rows")
                    }
                    cursor.close()
                } catch (e: Exception) {
                    ArabicDictionaryConfig.log("Warning: Table '$tableName' not found or invalid")
                }
            }
        }
    }
    
    private fun getTokenId(token: String): Long? {
        val db = database ?: return null
        
        val query = "SELECT ${Columns.ID} FROM ${Tables.TOKENS} WHERE ${Columns.VALUE} = ? LIMIT 1"
        val cursor = db.rawQuery(query, arrayOf(token))
        
        return try {
            if (cursor.moveToFirst()) {
                cursor.getLong(0)
            } else {
                null
            }
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error getting token ID: ${e.message}")
            null
        } finally {
            cursor.close()
        }
    }
    
    fun nextWords(
        prev2: String? = null,
        prev1: String? = null,
        prefix: String? = null,
        limit: Int = ArabicDictionaryConfig.MAX_SUGGESTIONS
    ): List<Pair<String, Long>> {
        val db = database ?: return emptyList()
        
        val w1Id = prev2?.let { getTokenId(it) }
        val w2Id = prev1?.let { getTokenId(it) }
        val pref = prefix?.let { "$it*" }
        
        // Try trigram first
        if (w1Id != null && w2Id != null) {
            val results = if (pref != null) {
                getTrigramWithPrefix(w1Id, w2Id, pref, limit, db)
            } else {
                getTrigram(w1Id, w2Id, limit, db)
            }
            
            if (results.isNotEmpty()) {
                return results
            }
        }
        
        // Try bigram
        if (w2Id != null) {
            val results = if (pref != null) {
                getBigramWithPrefix(w2Id, pref, limit, db)
            } else {
                getBigram(w2Id, limit, db)
            }
            
            if (results.isNotEmpty()) {
                return results
            }
        }
        
        // Fall back to unigram
        return if (pref != null) {
            getUnigramWithPrefix(pref, limit, db)
        } else {
            getUnigram(limit, db)
        }
    }
    
    fun containsWord(word: String): Boolean {
        return getTokenId(word) != null
    }
    
    fun getSuggestions(input: String, limit: Int = 5): List<String> {
        // For backward compatibility, use the input as a prefix for unigram suggestions
        val results = nextWords(prev2 = null, prev1 = null, prefix = input, limit = limit)
        return results.map { it.first }
    }
    
    fun getWordCount(): Int {
        val db = database ?: return 0
        
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${Tables.TOKENS}", null)
        return try {
            if (cursor.moveToFirst()) {
                cursor.getInt(0)
            } else {
                0
            }
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error getting word count: ${e.message}")
            0
        } finally {
            cursor.close()
        }
    }
    
    fun getMostFrequentWords(limit: Int = 10): List<String> {
        val results = nextWords(prev2 = null, prev1 = null, prefix = null, limit = limit)
        return results.map { it.first }
    }
    
    // Private helper methods for n-gram queries
    
    private fun getUnigram(limit: Int, db: SQLiteDatabase): List<Pair<String, Long>> {
        val query = """
            SELECT t.${Columns.VALUE} AS word, u.${Columns.FREQ} AS score
            FROM ${Tables.UNIGRAM} u
            JOIN ${Tables.TOKENS} t ON t.${Columns.ID} = u.${Columns.TOKEN}
            ORDER BY u.${Columns.FREQ} DESC
            LIMIT ?
        """.trimIndent()
        
        val cursor = db.rawQuery(query, arrayOf(limit.toString()))
        return try {
            val results = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val score = cursor.getLong(1)
                results.add(Pair(word, score))
            }
            results
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error executing unigram query: ${e.message}")
            emptyList()
        } finally {
            cursor.close()
        }
    }
    
    private fun getUnigramWithPrefix(prefix: String, limit: Int, db: SQLiteDatabase): List<Pair<String, Long>> {
        val query = """
            SELECT t.${Columns.VALUE} AS word, u.${Columns.FREQ} AS score
            FROM ${Tables.LEXICON_FTS}
            JOIN ${Tables.UNIGRAM} u ON u.${Columns.TOKEN} = ${Tables.LEXICON_FTS}.rowid
            JOIN ${Tables.TOKENS} t ON t.${Columns.ID} = ${Tables.LEXICON_FTS}.rowid
            WHERE ${Tables.LEXICON_FTS} MATCH ?
            ORDER BY u.${Columns.FREQ} DESC
            LIMIT ?
        """.trimIndent()
        
        val cursor = db.rawQuery(query, arrayOf(prefix, limit.toString()))
        return try {
            val results = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val score = cursor.getLong(1)
                results.add(Pair(word, score))
            }
            results
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error executing unigram with prefix query: ${e.message}")
            emptyList()
        } finally {
            cursor.close()
        }
    }
    
    private fun getBigram(w2Id: Long, limit: Int, db: SQLiteDatabase): List<Pair<String, Long>> {
        val query = """
            SELECT t.${Columns.VALUE} AS word, b.${Columns.FREQ} AS score, u.${Columns.FREQ} AS u_freq
            FROM ${Tables.BIGRAM} b
            JOIN ${Tables.TOKENS} t ON t.${Columns.ID} = b.${Columns.W2}
            JOIN ${Tables.UNIGRAM} u ON u.${Columns.TOKEN} = b.${Columns.W2}
            WHERE b.${Columns.W1} = ?
            ORDER BY b.${Columns.FREQ} DESC, u.${Columns.FREQ} DESC
            LIMIT ?
        """.trimIndent()
        
        val cursor = db.rawQuery(query, arrayOf(w2Id.toString(), limit.toString()))
        return try {
            val results = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val score = cursor.getLong(1)
                results.add(Pair(word, score))
            }
            results
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error executing bigram query: ${e.message}")
            emptyList()
        } finally {
            cursor.close()
        }
    }
    
    private fun getBigramWithPrefix(w2Id: Long, prefix: String, limit: Int, db: SQLiteDatabase): List<Pair<String, Long>> {
        val query = """
            SELECT t.${Columns.VALUE} AS word, b.${Columns.FREQ} AS score, u.${Columns.FREQ} AS u_freq
            FROM ${Tables.LEXICON_FTS}
            JOIN ${Tables.BIGRAM} b ON b.${Columns.W2} = ${Tables.LEXICON_FTS}.rowid
            JOIN ${Tables.UNIGRAM} u ON u.${Columns.TOKEN} = b.${Columns.W2}
            JOIN ${Tables.TOKENS} t ON t.${Columns.ID} = b.${Columns.W2}
            WHERE ${Tables.LEXICON_FTS} MATCH ? AND b.${Columns.W1} = ?
            ORDER BY b.${Columns.FREQ} DESC, u.${Columns.FREQ} DESC
            LIMIT ?
        """.trimIndent()
        
        val cursor = db.rawQuery(query, arrayOf(prefix, w2Id.toString(), limit.toString()))
        return try {
            val results = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val score = cursor.getLong(1)
                results.add(Pair(word, score))
            }
            results
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error executing bigram with prefix query: ${e.message}")
            emptyList()
        } finally {
            cursor.close()
        }
    }
    
    private fun getTrigram(w1Id: Long, w2Id: Long, limit: Int, db: SQLiteDatabase): List<Pair<String, Long>> {
        val query = """
            SELECT t.${Columns.VALUE} AS word, tr.${Columns.FREQ} AS score, u.${Columns.FREQ} AS u_freq
            FROM ${Tables.TRIGRAM} tr
            JOIN ${Tables.TOKENS} t ON t.${Columns.ID} = tr.${Columns.W3}
            JOIN ${Tables.UNIGRAM} u ON u.${Columns.TOKEN} = tr.${Columns.W3}
            WHERE tr.${Columns.W1} = ? AND tr.${Columns.W2} = ?
            ORDER BY tr.${Columns.FREQ} DESC, u.${Columns.FREQ} DESC
            LIMIT ?
        """.trimIndent()
        
        val cursor = db.rawQuery(query, arrayOf(w1Id.toString(), w2Id.toString(), limit.toString()))
        return try {
            val results = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val score = cursor.getLong(1)
                results.add(Pair(word, score))
            }
            results
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error executing trigram query: ${e.message}")
            emptyList()
        } finally {
            cursor.close()
        }
    }
    
    private fun getTrigramWithPrefix(w1Id: Long, w2Id: Long, prefix: String, limit: Int, db: SQLiteDatabase): List<Pair<String, Long>> {
        val query = """
            SELECT t.${Columns.VALUE} AS word, tr.${Columns.FREQ} AS score, u.${Columns.FREQ} AS u_freq
            FROM ${Tables.LEXICON_FTS}
            JOIN ${Tables.TRIGRAM} tr ON tr.${Columns.W3} = ${Tables.LEXICON_FTS}.rowid
            JOIN ${Tables.UNIGRAM} u ON u.${Columns.TOKEN} = tr.${Columns.W3}
            JOIN ${Tables.TOKENS} t ON t.${Columns.ID} = tr.${Columns.W3}
            WHERE ${Tables.LEXICON_FTS} MATCH ? AND tr.${Columns.W1} = ? AND tr.${Columns.W2} = ?
            ORDER BY tr.${Columns.FREQ} DESC, u.${Columns.FREQ} DESC
            LIMIT ?
        """.trimIndent()
        
        val cursor = db.rawQuery(query, arrayOf(prefix, w1Id.toString(), w2Id.toString(), limit.toString()))
        return try {
            val results = mutableListOf<Pair<String, Long>>()
            while (cursor.moveToNext()) {
                val word = cursor.getString(0)
                val score = cursor.getLong(1)
                results.add(Pair(word, score))
            }
            results
        } catch (e: Exception) {
            ArabicDictionaryConfig.log("Error executing trigram with prefix query: ${e.message}")
            emptyList()
        } finally {
            cursor.close()
        }
    }
    
    fun close() {
        database?.close()
        database = null
    }
}
