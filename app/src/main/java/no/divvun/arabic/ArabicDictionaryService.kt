package no.divvun.arabic

import android.content.Context
import timber.log.Timber

class ArabicDictionaryService(private val context: Context) {
    
    // MARK: - Properties
    
    private val dictionary: ArabicDictionary = ArabicDictionary(context)
    private var previousContext: WordContext? = null
    private var currentContext: WordContext? = null
    private var lastSavedContext: WordContext? = null
    private var lastSavedContextId: Long? = null
    
    // MARK: - Public Interface
    
    fun updateContext(context: WordContext) {
        if (currentContext == context) {
            return
        }
        
        previousContext = currentContext
        currentContext = context
        
        saveOrUpdateContextIfNeeded()
    }
    
    fun getSuggestions(word: String): List<String> {
        return dictionary.getSuggestions(word, limit = 5)
    }
    
    fun getNextWords(
        prev2: String? = null,
        prev1: String? = null,
        prefix: String? = null,
        limit: Int = ArabicDictionaryConfig.MAX_SUGGESTIONS
    ): List<Pair<String, Long>> {
        return dictionary.nextWords(prev2, prev1, prefix, limit)
    }
    
    fun isCorrect(word: String): Boolean {
        return dictionary.containsWord(word)
    }
    
    fun contains(word: String): Boolean {
        return dictionary.containsWord(word)
    }
    
    fun getWordCount(): Int {
        return dictionary.getWordCount()
    }
    
    fun getMostFrequentWords(limit: Int = 10): List<String> {
        return dictionary.getMostFrequentWords(limit)
    }
    
    // MARK: - Private Methods
    
    private fun saveOrUpdateContextIfNeeded() {
        val saveCandidateContext = previousContext ?: return
        val context = currentContext ?: return
        
        if (context.isContinuation(of = saveCandidateContext)) {
            return
        }
        
        updateLastContextIfNeeded(saveCandidateContext)
        
        // Only save words that are not in the dictionary
        if (!dictionary.contains(word = saveCandidateContext.word)) {
            // In a real implementation, you might want to add this to a user dictionary
            // For now, we'll just track it
            Timber.d("Unknown word detected: ${saveCandidateContext.word}")
        }
    }
    
    private fun updateLastContextIfNeeded(saveCandidate: WordContext) {
        val lastSavedContext = lastSavedContext ?: return
        val lastSavedContextId = lastSavedContextId ?: return
        
        val combinedContext = lastSavedContext.adding(context = saveCandidate) ?: return
        
        if (combinedContext.isMoreDesirableThan(lastSavedContext)) {
            // In a real implementation, you would update the context in the database
            // For now, we'll just update our local tracking
            this.lastSavedContext = combinedContext
        }
    }
    
    fun close() {
        dictionary.close()
    }
}
