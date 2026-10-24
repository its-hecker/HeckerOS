package androidx.annotation
annotation class VisibleForTesting(val otherwise: Int = 0) {
    companion object { const val PRIVATE = 2 }
}
