package no.divvun.arabic

data class WordContext(
    val secondBefore: String? = null,
    val firstBefore: String? = null,
    val word: String,
    val firstAfter: String? = null,
    val secondAfter: String? = null
) {
    
    fun isContinuation(of other: WordContext): Boolean {
        return this.word == other.word && 
               this.firstBefore == other.firstBefore &&
               this.secondBefore == other.secondBefore
    }
    
    fun adding(context: WordContext): WordContext? {
        // Combine contexts if they represent the same word with additional context
        if (this.word != context.word) return null
        
        return WordContext(
            secondBefore = this.secondBefore ?: context.secondBefore,
            firstBefore = this.firstBefore ?: context.firstBefore,
            word = this.word,
            firstAfter = this.firstAfter ?: context.firstAfter,
            secondAfter = this.secondAfter ?: context.secondAfter
        )
    }
    
    fun isMoreDesirableThan(other: WordContext): Boolean {
        return this.desirabilityScore > other.desirabilityScore
    }
    
    private val desirabilityScore: UInt
        get() {
            return when {
                firstBefore != null && firstAfter != null -> 3u
                (firstBefore != null && secondBefore != null) || 
                (firstAfter != null && secondAfter != null) -> 2u
                firstBefore != null || firstAfter != null -> 1u
                else -> 0u
            }
        }
}
